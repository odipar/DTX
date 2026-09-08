// Package pack combines a DTX table with the 68000 image that reads it.
//
// One build is one code, so nothing here assembles: it takes the image for
// the build the table needs, writes the six fields the table gives into the
// format block, and appends the column table and the table's bytes.
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
	Turn    = 0
	Park    = 4
	Pointer = 8
)

// The format block: what it runs to, where it stands behind the four slots,
// and its fields.
const (
	Format     = 28
	FormatAt   = 16
	StateAt    = 4
	TableAt    = 8
	RowBytesAt = 12
	PeriodAt   = 14
	RingAt     = 16
	UnitAt     = 18
	WidthAt    = 19
	ColumnsAt  = 20
	StrideAt   = 24
)

// Stream is what one stream record runs to, one a column under DTX2.
const Stream = 16

// State is what one decoder state takes: the eight registers, the ring's
// end, where the registers go at a loop, and the budget.
const State = 48

// Saved is what the copy of a decoder's registers takes, where a pass is
// replayed.
const Saved = 32

// PackedHead is what a packed reader's state block contains before its
// decoder states.
const PackedHead = 72

// Plain is the state block DTX0 and DTX1 take: the head, and one pointer.
const Plain = Pointer + 4

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
func Decoders() int {
	return PackedHead
}

// Ring gives where the rings stand in the state block: behind a decoder
// state a turn.
func Ring(header dtx.Header, given Packed) (int, error) {
	period, err := Period(header, given)
	if err != nil {
		return 0, err
	}
	return Decoders() + State*period, nil
}

// StateBytes gives the state block a plain reader takes.
//
// The same under DTX0 and DTX1, and the same at every C and every width:
// every column is one width, so one pointer walks them all.
func StateBytes() int {
	return Plain
}

// PackedStateBytes gives the state block a packaged DTX2 reader takes. A
// replayed payload takes a copy of the registers a column behind the rings,
// where the reader puts them at the row its loop begins (abi.md 4).
func PackedStateBytes(header dtx.Header, given Packed) (int, error) {
	ring, err := Ring(header, given)
	if err != nil {
		return 0, err
	}
	out := ring + given.Ring*header.Columns
	if given.Replayed {
		out += Saved * header.Columns
	}
	return out, nil
}

// Stride gives the stride from one column's value to the next, in the row an
// advance points at: the width under DTX0, where a row's values stand one
// after another; a column's length under DTX1, R times the width up to a
// word; and N under DTX2, where every column has a ring of that size.
// DTX_metadata gives it, out of the format block.
func Stride(header dtx.Header, given Packed) int {
	switch header.Variant {
	case dtx.DTX0:
		return header.Width
	case dtx.DTX1:
		return dtx.StrideDtx1(header.Rows, header.Width)
	default:
		return given.Ring
	}
}

// Period gives the period a table of these takes, the smallest that meets
// every rule of doc/abi.md 4, or an error listing the rules and the figures.
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
	// A table shorter than a period takes the period all the same: where
	// its sets end the reader seeds its rows and the first period's budget
	// is 0, and where they loop it seeds a period's rows round the loop
	// (abi.md 4).
	for p := columns; ; p++ {
		budget := p * width / k
		if n < 2*p*width {
			break
		}
		if n%(p*width) == 0 && p*width%k == 0 &&
			budget >= 1 && budget <= 65535 {
			// A refill meets one mark at most, so a replayed loop is a period
			// long at least (abi.md 4).
			if loop := rows - header.Repeat; given.Replayed && loop < p {
				return 0, fmt.Errorf("a replayed loop of %d rows is under the period"+
					" of %d: a refill meets one mark at most", loop, p)
			}
			return p, nil
		}
	}
	return 0, fmt.Errorf("no period from C of %d up meets N of %d and k of %d:"+
		" N divides by P times the width, is at least twice that, and the"+
		" budget is a whole number of units", columns, n, k)
}

// ColumnTable gives the table behind the image's code: one stream record a
// column, four longs each, and nothing else.
//
// DTX0 and DTX1 do not have one. Every column is one width, so a pointer and
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
// writes is the six fields the table gives. It checks the three it cannot
// write: the variant, the width the code reads values at, under DTX1 and
// DTX2, and the unit the decoder decodes at, zero under the plain variants.
func Combine(code, file []byte, header dtx.Header) ([]byte, error) {
	if len(code) < FormatAt+Format {
		return nil, fmt.Errorf("code of %d bytes does not contain a format block",
			len(code))
	}
	if string(code[FormatAt:FormatAt+3]) != "DTX" {
		return nil, fmt.Errorf("the code does not contain a format block at +16")
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
	state := StateBytes()
	period := 1
	if header.Variant == dtx.DTX2 {
		if state, err = PackedStateBytes(header, given); err != nil {
			return nil, err
		}
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
	dtx.PutLong(out, FormatAt+StrideAt, Stride(header, given))
	return out, nil
}

// Image gives the image for this table, from the code this build contains.
//
// The file defines which of the twenty-two it takes: the variant, the width
// every value takes, and under DTX2 the unit its data sets are packed at and
// whether they contain copies from the literal stream (R5.10). No word from
// a caller enters it, so no word can differ from the bytes.
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
// the state block, and under DTX2 the period, N, the unit and the copy code
// (doc/tools.md).
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
	period, state := 1, StateBytes()
	if variant == dtx.DTX2 {
		if given, err = ReadPacked(file, header); err != nil {
			return "", err
		}
		if period, err = Period(header, given); err != nil {
			return "", err
		}
		if state, err = PackedStateBytes(header, given); err != nil {
			return "", err
		}
	}
	var out strings.Builder
	fmt.Fprintf(&out, "; What org.dtx.Packager writes of one table, for"+
		" 68k/DTX%d.S to read.\n; DTX%d, R = %d, C = %d, W = %d, RR = %d\n"+
		"; Every instruction is the template's; nothing here is one.\n\n"+
		"; The state block, doc/abi.md 3.\n",
		variant, variant, header.Rows, header.Columns, header.Width,
		header.Repeat)
	out.WriteString(equ("DTX_TURN", Turn))
	out.WriteString(equ("DTX_PARK", Park))
	out.WriteString(equ("DTX_POINTER", Pointer))
	if variant != dtx.DTX2 {
		// Nothing parks a6 under the plain variants, so the payload
		// init was given stands in that long instead, which a jump
		// reaches (abi.md 3). Under DTX2 the template names its own.
		out.WriteString(equ("DTX_PAYLOAD", Park))
	}
	out.WriteString("\n; What the code takes at assembly time.\n")
	out.WriteString(equ("DTX_WIDTH", header.Width))
	if variant == dtx.DTX2 {
		out.WriteString(equ("ST4_UNIT", given.Unit))
	}
	out.WriteString("\n; The rest of what the table gives. A combine writes" +
		" these into the format\n; block and the code reads them from there" +
		" (doc/abi.md 1), so they stand\n; here for a caller reading the" +
		" figures rather than for the assembler.\n")
	out.WriteString(equ("DTX_ROWBYTES", header.RowBytes()))
	out.WriteString(equ("DTX_STATE", state))
	if variant == dtx.DTX2 {
		out.WriteString(equ("DTX_PERIOD", period))
		out.WriteString(equ("DTX_N", given.Ring))
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
		// Where rmac is not there its output is empty and the message would
		// end at "gave ", so the error from the run is wrapped into it.
		if trimmed := strings.TrimSpace(string(said)); trimmed != "" {
			return nil, fmt.Errorf("%s gave %s: %w", rmac, trimmed, err)
		}
		return nil, err
	}
	code, err := os.ReadFile(out)
	if err != nil {
		return nil, fmt.Errorf("%s did not write an image: %s", rmac, said)
	}
	return code, nil
}

// Blank zeroes the six fields a combine writes.
//
// Built code does not define a table. The assembler read one to build it, and
// what it read stands in the format block: zeroing those six makes the file
// a function of the template alone, and makes code shipped without a combine
// read a state block of zero bytes rather than some other table's.
func Blank(code []byte) {
	dtx.PutLong(code, FormatAt+StateAt, 0)
	dtx.PutLong(code, FormatAt+TableAt, 0)
	dtx.PutWord(code, FormatAt+RowBytesAt, 0)
	dtx.PutWord(code, FormatAt+PeriodAt, 0)
	dtx.PutWord(code, FormatAt+RingAt, 0)
	dtx.PutLong(code, FormatAt+StrideAt, 0)
}
