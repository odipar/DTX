// Package pack combines a DTX table with the 68000 image that reads it.
//
// One build is one code, so nothing here assembles: it takes the image for
// the build the table needs, writes the five fields the table gives
// into the format block, and appends the column table and the table's bytes.
// doc/abi.md defines the image, the format block and the column table.
package pack

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"

	"dtx/internal/dtx"
	"dtx/internal/image"
)

// The state block's fields, from doc/abi.md 3.
const (
	Cursor = 24
)

// The format block: what it runs to, where it stands, and its fields.
const (
	Format     = 24
	FormatAt   = 24
	StateAt    = 4
	TableAt    = 8
	RowBytesAt = 12
	PeriodAt   = 14
	RingAt     = 16
	UnitAt     = 18
	WidthAt    = 19
	ColumnsAt  = 20
)

// Stream is what one stream record runs to, one a column under DTX2.
const Stream = 16

// PackedHead is what a packed reader's state block contains before its
// decoder states.
const PackedHead = 56

// Plain is the state block DTX0 and DTX1 take: the head, and one cursor.
const Plain = Cursor + 4

// Copies is the flags bit at payload byte 3 that marks every column was
// packed with copies from its own literal stream, R5.10.
const Copies = 1

// Packed gives what a DTX2 payload defines: the ring, the unit, whether its
// columns contain copies from the literal stream, and where each column's data
// set begins in the payload. The DTX2 reader in dtx reads the same payload,
// so the one reader is there.
type Packed = dtx.Packed

// ReadPacked reads those out of a DTX2 payload, SPEC.md 2.3, and checks the
// data sets against it: dtx.ReadPacked.
func ReadPacked(file []byte, header dtx.Header) (Packed, error) {
	return dtx.ReadPacked(file, header)
}

// Decoders gives where the decoder states stand in the state block, doc/abi.md 3.
func Decoders(header dtx.Header) int {
	return PackedHead
}

// Ring gives where the rings stand in the state block.
func Ring(header dtx.Header) int {
	return Decoders(header) + 32*header.Columns
}

// StateBytes gives the state block a plain reader of this table takes.
//
// The same under DTX0 and DTX1, and the same at every C: every column is one
// width, so one cursor walks them all.
func StateBytes(header dtx.Header) int {
	return Plain
}

// PackedStateBytes gives the state block a packaged DTX2 reader takes.
func PackedStateBytes(header dtx.Header, given Packed) int {
	return Ring(header) + given.Ring*header.Columns
}

// Period gives the period a table of these takes, the smallest that meets
// every rule of doc/abi.md 4, or an error naming the rule none meets.
func Period(header dtx.Header, given Packed) (int, error) {
	rows := header.Rows
	width := header.Width
	n := given.Ring
	k := given.Unit
	columns := header.Columns
	if k == 0 || rows*width%k != 0 {
		return 0, fmt.Errorf("a column is %d times %d bytes, which does not"+
			" divide by k of %d", rows, width, k)
	}
	for p := columns; p <= rows; p++ {
		budget := p * width / k
		if n < 2*p*width {
			break
		}
		if n%(p*width) == 0 && p*width%k == 0 &&
			budget >= 1 && budget <= 65535 {
			return p, nil
		}
	}
	return 0, fmt.Errorf("no period from C of %d to R of %d meets N of %d and"+
		" k of %d: N divides by P times the width, is at least twice that,"+
		" and the budget is a whole number of units", columns, rows, n, k)
}

// ColumnTable gives the table behind the image's code: one stream record a
// column, four longs each, and nothing else.
//
// DTX0 and DTX1 do not have one. Every column is one width, so a cursor and
// a stride walk them all: what a plain read takes is arithmetic on R, C and
// the width.
func ColumnTable(file []byte, header dtx.Header) ([]byte, error) {
	if header.Variant != dtx.DTX2 {
		return nil, nil
	}
	given, err := ReadPacked(file, header)
	if err != nil {
		return nil, err
	}
	payload := header.Length()
	out := make([]byte, Stream*header.Columns)
	for i := 0; i < header.Columns; i++ {
		rec := Stream * i
		set := given.At[i]
		dtx.PutLong(out, rec, set+28)
		dtx.PutLong(out, rec+4, set+dtx.GetLong(file, payload+set+8))
		dtx.PutLong(out, rec+8, set+dtx.GetLong(file, payload+set+12))
		dtx.PutLong(out, rec+12, set+dtx.GetLong(file, payload+set+16))
	}
	return out, nil
}

// Combine gives one image: this code, the column table, the table's bytes,
// and the format block written to define the three.
//
// The code is the same bytes any table that follows it, so what a combine
// writes is the five fields the table gives. It checks the three it cannot
// write: the variant, the width the code reads values at, and under DTX2 the
// unit the decoder built into the code decodes at.
func Combine(code, file []byte, header dtx.Header) ([]byte, error) {
	if len(code) < FormatAt+Format {
		return nil, fmt.Errorf("code of %d bytes does not contain a format block",
			len(code))
	}
	if string(code[FormatAt:FormatAt+3]) != "DTX" {
		return nil, fmt.Errorf("the code opens with no format block")
	}
	if int(code[FormatAt+3]) != header.Variant {
		return nil, fmt.Errorf("the code reads DTX%d and the table is DTX%d",
			code[FormatAt+3], header.Variant)
	}
	// The code ends where the format block puts the column table: the
	// two match, or the image reads its own last instruction as a column.
	columns := dtx.GetLong(code, FormatAt+ColumnsAt)
	if columns != len(code) {
		return nil, fmt.Errorf("the code runs to %d bytes and the format"+
			" block puts the column table at %d: it would not land there",
			len(code), columns)
	}
	var given Packed
	if header.Variant == dtx.DTX2 {
		var err error
		if given, err = ReadPacked(file, header); err != nil {
			return nil, err
		}
	}
	if unit := int(code[FormatAt+UnitAt]); unit != given.Unit {
		return nil, fmt.Errorf("the code decodes at a unit of %d and the"+
			" table was packed at %d", unit, given.Unit)
	}
	width := int(code[FormatAt+WidthAt])
	if header.Variant != dtx.DTX0 && width != header.Width {
		return nil, fmt.Errorf("the code reads values of %d bytes and the"+
			" table's are %d", width, header.Width)
	}
	entries, err := ColumnTable(file, header)
	if err != nil {
		return nil, err
	}
	out := make([]byte, 0, len(code)+len(entries)+len(file))
	out = append(out, code...)
	out = append(out, entries...)
	out = append(out, file...)
	state := StateBytes(header)
	period := 1
	if header.Variant == dtx.DTX2 {
		state = PackedStateBytes(header, given)
		if period, err = Period(header, given); err != nil {
			return nil, err
		}
	}
	dtx.PutLong(out, FormatAt+StateAt, state)
	// The table stands behind both, and only the packager has the figure:
	// the column table's size moves with C, so the assembler could not have
	// worked it out.
	dtx.PutLong(out, FormatAt+TableAt, len(code)+len(entries))
	dtx.PutWord(out, FormatAt+RowBytesAt, header.RowBytes())
	dtx.PutWord(out, FormatAt+PeriodAt, period)
	dtx.PutWord(out, FormatAt+RingAt, given.Ring)
	return out, nil
}

// Image gives the image for this table, from the code this build contains.
//
// Which of the twenty-two it takes is the file's to define: the variant, the
// width every value takes, and under DTX2 the unit its data sets are packed
// at and whether they contain copies from the literal stream (R5.10). No
// word from a caller enters it, so no word can differ from the bytes.
func Image(file []byte) ([]byte, error) {
	header, err := dtx.ReadHeader(file)
	if err != nil {
		return nil, err
	}
	unit, copies := 0, false
	if header.Variant == dtx.DTX2 {
		given, err := ReadPacked(file, header)
		if err != nil {
			return nil, err
		}
		unit, copies = given.Unit, given.Copies
	}
	code, err := image.Code(header.Variant, header.Width, unit, copies)
	if err != nil {
		return nil, err
	}
	return Combine(code, file, header)
}

// The state block's fields the figures define, from doc/abi.md 3.
const (
	Row     = 0
	Turn    = 4
	Decoded = 8
	Park    = 12
)

// equ gives one NAME equ VALUE line.
func equ(name string, value int) string {
	pad := ""
	if len(name) < 8 {
		pad = "\t"
	}
	return name + pad + "\tequ\t" + strconv.Itoa(value) + "\n"
}

// Figures gives what one table gives, as a template reads it: the equates,
// and no instruction. R, C and RR reach the code at run time instead, out of
// the table's own header, and what is left is the width, the row's bytes and
// the state block (doc/tools.md).
func Figures(file []byte) (string, error) {
	header, err := dtx.ReadHeader(file)
	if err != nil {
		return "", err
	}
	variant := header.Variant
	if variant != dtx.DTX0 && variant != dtx.DTX1 && variant != dtx.DTX2 {
		return "", fmt.Errorf("the variant is 0, 1 or 2, not %d", variant)
	}
	var given Packed
	period, state := 1, StateBytes(header)
	if variant == dtx.DTX2 {
		if given, err = ReadPacked(file, header); err != nil {
			return "", err
		}
		if period, err = Period(header, given); err != nil {
			return "", err
		}
		state = PackedStateBytes(header, given)
	}
	var out strings.Builder
	fmt.Fprintf(&out, "; What org.dtx.Packager writes of one table, for"+
		" 68k/DTX.S to read.\n; DTX%d, R = %d, C = %d, W = %d, RR = %d\n"+
		"; Every instruction is the template's; nothing here is one.\n\n"+
		"; The state block, doc/abi.md 3.\n",
		variant, header.Rows, header.Columns, header.Width, header.Repeat)
	out.WriteString(equ("DTX_ROW", Row))
	out.WriteString(equ("DTX_TURN", Turn))
	out.WriteString(equ("DTX_DECODED", Decoded))
	out.WriteString(equ("DTX_PARK", Park))
	out.WriteString(equ("DTX_CURSOR", Cursor))
	out.WriteString("\n")
	out.WriteString(equ("DTX_WIDTH", header.Width))
	out.WriteString(equ("DTX_ROWBYTES", header.RowBytes()))
	out.WriteString(equ("DTX_STATE", state))
	if variant == dtx.DTX2 {
		out.WriteString(equ("DTX_PERIOD", period))
		out.WriteString(equ("DTX_N", given.Ring))
		out.WriteString(equ("ST4_UNIT", given.Unit))
		// The payload defines whether its columns contain copies (R5.10), so
		// the decoder built for them is fixed by the file. That build
		// writes the reach into two of its own instructions, and a 68030
		// caller flushes the instruction cache after every call that seeds
		// a decoder.
		if given.Copies {
			out.WriteString(equ("ST4_WINDOW", 1))
			out.WriteString("; the columns were packed with st4 -c, so the" +
				" decoder takes the copy code\n")
		}
	}
	return out.String(), nil
}

// Templates gives where the templates and the carried decoder stand.
func Templates() string {
	if named := os.Getenv("DTX_68K"); named != "" {
		return named
	}
	return "68k"
}

// Code gives rmac's assembly of the variant's template for this table, the
// code alone. This is the one step an assembler is needed for.
//
// templates is where 68k/ stands. A tool run from outside this repository
// does not have a directory to resolve a relative one against, so the
// caller names it rather than taking Templates.
func Code(file []byte, rmac, templates string) ([]byte, error) {
	header, err := dtx.ReadHeader(file)
	if err != nil {
		return nil, err
	}
	figures, err := Figures(file)
	if err != nil {
		return nil, err
	}
	work, err := os.MkdirTemp("", "dtx68")
	if err != nil {
		return nil, err
	}
	defer os.RemoveAll(work)
	if err := os.WriteFile(filepath.Join(work, "DTX_table.i"),
		[]byte(figures), 0o644); err != nil {
		return nil, err
	}
	out := filepath.Join(work, "image.bin")
	template := filepath.Join(templates,
		fmt.Sprintf("DTX%d.S", header.Variant))
	said, err := exec.Command(rmac, "-m68000", "-fr", "+o3", "-i"+work,
		"-i"+templates, "-o", out, template).CombinedOutput()
	if err != nil {
		return nil, fmt.Errorf("%s gave %s", rmac, strings.TrimSpace(string(said)))
	}
	code, err := os.ReadFile(out)
	if err != nil {
		return nil, fmt.Errorf("%s did not write an image: %s", rmac, said)
	}
	return code, nil
}

// Blank zeroes the five fields a combine writes.
//
// Built code does not define a table. The assembler read one to build it, and
// what it read stands in the format block: zeroing those five makes the
// file a function of the template alone, and what makes code shipped without
// a combine read a state block of zero bytes rather than some other table's.
func Blank(code []byte) {
	dtx.PutLong(code, FormatAt+StateAt, 0)
	dtx.PutLong(code, FormatAt+TableAt, 0)
	dtx.PutWord(code, FormatAt+RowBytesAt, 0)
	dtx.PutWord(code, FormatAt+PeriodAt, 0)
	dtx.PutWord(code, FormatAt+RingAt, 0)
}
