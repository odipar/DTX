// Package pack combines a DTX table with the 68000 image that reads it.
//
// One variant is one code, so nothing here assembles: it takes the image for
// the build the table asks for, writes the five fields the table settles
// into the format block, and appends the column table and the table's bytes.
// doc/abi.md states the image, the format block and the column table.
package pack

import (
	"fmt"

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
	ColumnsAt  = 20
)

// The column table: its header, one read entry a column, and under DTX2 one
// stream record a column.
const (
	Entry      = 4
	Entries    = 32
	Stream     = 32
	PackedHead = 80
)

// Copies is the flags bit at payload byte 3 that says every column was
// packed with copies from its own literal stream, R5.10.
const Copies = 1

// Packed is what a DTX2 payload states: the ring, the unit, whether its
// columns hold copies from the literal stream, and where each column's data
// set begins in the payload.
type Packed struct {
	Ring   int // N
	Unit   int // k
	Copies bool
	At     []int
}

// ReadPacked reads those out of a DTX2 payload, SPEC.md 2.3.
//
// It checks the data sets against it. Every set opens with $53 $34 $07 k, so
// one compare against the payload's own k holds ST4's signature, its format
// version and R5.2 at once.
func ReadPacked(file []byte, header dtx.Header) (Packed, error) {
	payload := header.Length
	unit := int(file[payload+2])
	out := Packed{
		Ring:   dtx.GetWord(file, payload),
		Unit:   unit,
		Copies: file[payload+3]&Copies != 0,
		At:     make([]int, header.Columns()),
	}
	signature := 0x53340700 + unit
	for i := range out.At {
		out.At[i] = dtx.GetLong(file, payload+4+4*i)
		said := dtx.GetLong(file, payload+out.At[i])
		if said != signature {
			return Packed{}, fmt.Errorf("column %d's data set opens %08X and"+
				" the payload states %08X: an ST4 data set opens with S4, the"+
				" format version 7 and the payload's own k",
				i, said, signature)
		}
	}
	return out, nil
}

// Classes gives the widths a table of these holds, in the order 1, 2, 4.
func Classes(width []int) []int {
	var out []int
	for _, w := range []int{1, 2, 4} {
		for _, held := range width {
			if held == w {
				out = append(out, w)
				break
			}
		}
	}
	return out
}

// Counts gives how many columns each width class holds.
func Counts(width []int) [3]int {
	var out [3]int
	for _, w := range width {
		switch w {
		case 1:
			out[0]++
		case 2:
			out[1]++
		default:
			out[2]++
		}
	}
	return out
}

// first gives the column of w bytes that stands first, or -1.
func first(width []int, w int) int {
	for i, held := range width {
		if held == w {
			return i
		}
	}
	return -1
}

// Bases gives where each width class's first column begins in the payload.
func Bases(header dtx.Header) [3]int {
	at := dtx.Offsets(header.Rows, header.Width)
	var out [3]int
	for c, w := range [3]int{1, 2, 4} {
		if i := first(header.Width, w); i >= 0 {
			out[c] = at[i]
		}
	}
	return out
}

// Slot gives where the slots stand in the state block, doc/abi.md 3.
func Slot(header dtx.Header) int {
	return PackedHead
}

// Ring gives where the rings stand in the state block.
func Ring(header dtx.Header) int {
	return Slot(header) + 32*header.Columns()
}

// RingOf gives where a width class's ring begins in the state block.
func RingOf(header dtx.Header, c, n int) int {
	w := [3]int{1, 2, 4}[c]
	if i := first(header.Width, w); i >= 0 {
		return Ring(header) + i*n
	}
	return Ring(header)
}

// StateBytes gives the state block a plain reader of this table takes.
func StateBytes(header dtx.Header) int {
	if header.Variant == dtx.DTX0 {
		return Cursor + 4
	}
	// DTX1 holds three cursors and the three places their classes begin,
	// whatever widths the table states, so its block does not move with C.
	if header.Variant == dtx.DTX1 {
		return 48
	}
	return Cursor + 4*len(Classes(header.Width))
}

// PackedStateBytes gives the state block a packaged DTX2 reader takes.
func PackedStateBytes(header dtx.Header, given Packed) int {
	return Ring(header) + given.Ring*header.Columns()
}

// Period gives the period a table of these takes, the smallest that meets
// every rule of doc/abi.md 4, or an error naming the rule none meets.
func Period(header dtx.Header, given Packed) (int, error) {
	width := header.Width
	rows := header.Rows
	n := given.Ring
	k := given.Unit
	columns := len(width)
	widest := 0
	for _, w := range width {
		if w > widest {
			widest = w
		}
	}
	if (columns-1)*n > 32767 {
		return 0, fmt.Errorf("a read reaches column %d at %d, past the 32767"+
			" a 68000 displacement holds: C is at most %d at N of %d",
			columns-1, (columns-1)*n, 32767/n+1, n)
	}
	if k == 0 || rows%k != 0 {
		return 0, fmt.Errorf("R is %d, which does not divide by k of %d",
			rows, k)
	}
	for p := columns; p <= rows; p++ {
		if n < 2*p*widest {
			break
		}
		holds := true
		for _, w := range width {
			budget := p * w / k
			holds = holds && n%(p*w) == 0 && (p*w)%k == 0 &&
				budget >= 1 && budget <= 65535
		}
		if holds {
			return p, nil
		}
	}
	return 0, fmt.Errorf("no period from C of %d to R of %d holds N of %d and"+
		" k of %d: N divides by P times every width, is at least twice that,"+
		" and every budget is a whole number of units", columns, rows, n, k)
}

// shift gives log2 of of where it is a power of two, or -1.
func shift(of int) int {
	for s := 0; s < 32; s++ {
		if 1<<s == of {
			return s
		}
	}
	return -1
}

// ColumnTable gives the table the image holds behind its code: a header, one
// read entry a column grouped by width so each of a read's three loops walks
// a run of them, and under DTX2 one stream record a column.
//
// DTX0 has none: a DTX0 row is one run of bytes, so no loop walks a column.
func ColumnTable(file []byte, header dtx.Header) ([]byte, error) {
	if header.Variant == dtx.DTX0 {
		return nil, nil
	}
	width := header.Width
	at := dtx.Offsets(header.Rows, width)
	count := Counts(width)
	base := Bases(header)
	packed := header.Variant == dtx.DTX2
	var given Packed
	period := 1
	if packed {
		var err error
		if given, err = ReadPacked(file, header); err != nil {
			return nil, err
		}
		if period, err = Period(header, given); err != nil {
			return nil, err
		}
	}
	n := given.Ring
	records := Entries + Entry*len(width)
	size := records
	if packed {
		size += Stream * len(width)
	}
	out := make([]byte, size)
	for c := 0; c < 3; c++ {
		dtx.PutWord(out, 2*c, count[c])
		// Under DTX2 a class begins at its first column's ring in the state
		// block, not at its column's place in the payload.
		if packed {
			dtx.PutLong(out, 8+4*c, RingOf(header, c, n))
		} else {
			dtx.PutLong(out, 8+4*c, base[c])
		}
	}
	dtx.PutWord(out, 6, header.RowBytes())
	dtx.PutWord(out, 20, len(width))
	dtx.PutWord(out, 22, period)
	dtx.PutLong(out, 24, records)
	dtx.PutLong(out, 28, n)
	wrote := Entries
	for _, w := range [3]int{1, 2, 4} {
		begins := first(width, w)
		row := 0
		for i, held := range width {
			if held == w {
				if packed {
					dtx.PutWord(out, wrote, (i-begins)*n)
				} else {
					dtx.PutWord(out, wrote, at[i]-base[classOf(w)])
				}
				dtx.PutWord(out, wrote+2, row)
				wrote += Entry
			}
			row += held
		}
	}
	if packed {
		payload := header.Length
		kshift := shift(given.Unit)
		for i, w := range width {
			rec := records + Stream*i
			set := given.At[i]
			dtx.PutLong(out, rec, set+28)
			dtx.PutLong(out, rec+4, set+dtx.GetLong(file, payload+set+8))
			dtx.PutLong(out, rec+8, set+dtx.GetLong(file, payload+set+12))
			dtx.PutLong(out, rec+12, set+dtx.GetLong(file, payload+set+16))
			dtx.PutLong(out, rec+16, Ring(header)+i*n)
			dtx.PutLong(out, rec+20, Slot(header)+32*i)
			dtx.PutWord(out, rec+24, shift(w))
			dtx.PutWord(out, rec+26, kshift)
		}
	}
	return out, nil
}

// classOf gives where a width stands among the three classes.
func classOf(w int) int {
	switch w {
	case 1:
		return 0
	case 2:
		return 1
	default:
		return 2
	}
}

// Combine gives one image: this code, the column table, the table's bytes,
// and the format block written to state the three.
//
// The code is the same bytes whatever table follows it, so what a combine
// writes is the five fields the table settles. It checks the two it cannot
// write: the variant, and under DTX2 the unit the decoder built into the
// code decodes at.
func Combine(code, file []byte, header dtx.Header) ([]byte, error) {
	if len(code) < FormatAt+Format {
		return nil, fmt.Errorf("code of %d bytes holds no format block",
			len(code))
	}
	if string(code[FormatAt:FormatAt+3]) != "DTX" {
		return nil, fmt.Errorf("the code opens with no format block")
	}
	if int(code[FormatAt+3]) != header.Variant {
		return nil, fmt.Errorf("the code reads DTX%d and the table is DTX%d",
			code[FormatAt+3], header.Variant)
	}
	// The code ends where the format block says the column table begins: the
	// two agree, or the image reads its own last instruction as a column.
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
	// The table stands behind both, and only the packager holds the figure:
	// the column table's size moves with C, so the assembler could not have
	// worked it out.
	dtx.PutLong(out, FormatAt+TableAt, len(code)+len(entries))
	dtx.PutWord(out, FormatAt+RowBytesAt, header.RowBytes())
	dtx.PutWord(out, FormatAt+PeriodAt, period)
	dtx.PutWord(out, FormatAt+RingAt, given.Ring)
	return out, nil
}

// Image gives the image for this table, from the code this build holds.
//
// Which of the eight it takes is the file's to state: the variant, and under
// DTX2 the unit its data sets are packed at and whether they hold copies
// from the literal stream (R5.10). No word from a caller enters it, so no
// word can disagree with the bytes.
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
	code, err := image.Code(header.Variant, unit, copies)
	if err != nil {
		return nil, err
	}
	return Combine(code, file, header)
}
