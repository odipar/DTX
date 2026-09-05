// Package dtx reads the header every DTX variant shares.
//
// doc/SPEC.md section 1: DTX, the variant, R, C, RR, a width a column, and
// zero bytes up to the next long, so the payload begins on one. Every field
// of more than one byte is most significant byte first.
package dtx

import "fmt"

// The variant byte at offset 3.
const (
	DTX0 = 0 // laid out row by row
	DTX1 = 1 // laid out column by column
	DTX2 = 2 // laid out column by column and packed
)

// Magic is the three bytes a file opens with.
var Magic = []byte{'D', 'T', 'X'}

// Align gives at up to the next multiple of to.
func Align(at, to int) int {
	return (at + to - 1) / to * to
}

// HeaderLength gives what a header runs to: 14 plus C, up to the next long.
func HeaderLength(columns int) int {
	return Align(14+columns, 4)
}

// Header gives what a file's header defines. Length is the header's end, the
// payload's first byte.
type Header struct {
	Variant int
	Rows    int // R
	Repeat  int // RR
	Width   []int
	Length  int
}

// Columns gives C.
func (h Header) Columns() int {
	return len(h.Width)
}

// RowBytes gives a row's bytes, the sum of the widths.
func (h Header) RowBytes() int {
	out := 0
	for _, w := range h.Width {
		out += w
	}
	return out
}

// ReadHeader gives the header at the start of file, or an error where the
// file is short of one, does not open with DTX, or breaks a bound R6 sets.
func ReadHeader(file []byte) (Header, error) {
	if len(file) < 16 {
		return Header{}, fmt.Errorf(
			"a file of %d bytes does not contain a header", len(file))
	}
	for i, b := range Magic {
		if file[i] != b {
			return Header{}, fmt.Errorf("the file does not open with DTX")
		}
	}
	rows := GetLong(file, 4)
	columns := GetWord(file, 8)
	repeat := GetLong(file, 10)
	if rows < 1 {
		return Header{}, fmt.Errorf("R is 1 upward, not %d", rows)
	}
	if columns < 1 || columns > 256 {
		return Header{}, fmt.Errorf("C is 1 to 256, not %d", columns)
	}
	if repeat < 0 || repeat > rows {
		return Header{}, fmt.Errorf("RR is 0 to R, not %d", repeat)
	}
	length := HeaderLength(columns)
	if len(file) < length {
		return Header{}, fmt.Errorf(
			"a file of %d bytes is short of a header of %d", len(file), length)
	}
	width := make([]int, columns)
	for i := range width {
		width[i] = int(file[14+i])
		if width[i] != 1 && width[i] != 2 && width[i] != 4 {
			return Header{}, fmt.Errorf(
				"column %d is %d bytes wide, not 1, 2 or 4", i, width[i])
		}
	}
	return Header{int(file[3]), rows, repeat, width, length}, nil
}

// Offsets gives where each column begins in a DTX1 payload. A column of two
// or four bytes begins on an even offset, so a wide value is read whole.
func Offsets(rows int, width []int) []int {
	at := make([]int, len(width))
	next := 0
	for i, w := range width {
		next = Align(next, 2)
		at[i] = next
		next += rows * w
	}
	return at
}

// The four byte order helpers. A DTX file and a 68000 image are both most
// significant byte first.

// GetWord reads two bytes at at.
func GetWord(in []byte, at int) int {
	return int(in[at])<<8 | int(in[at+1])
}

// GetLong reads four bytes at at.
func GetLong(in []byte, at int) int {
	return int(in[at])<<24 | int(in[at+1])<<16 | int(in[at+2])<<8 | int(in[at+3])
}

// PutWord writes two bytes at at.
func PutWord(out []byte, at, value int) {
	out[at] = byte(value >> 8)
	out[at+1] = byte(value)
}

// PutLong writes four bytes at at.
func PutLong(out []byte, at, value int) {
	out[at] = byte(value >> 24)
	out[at+1] = byte(value >> 16)
	out[at+2] = byte(value >> 8)
	out[at+3] = byte(value)
}
