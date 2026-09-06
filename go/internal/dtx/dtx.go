// Package dtx reads the header every DTX variant shares.
//
// doc/SPEC.md section 1: DTX, the variant, R, C, RR, the width every value
// takes, and one zero byte, so the payload begins on a long. Every field of
// more than one byte is most significant byte first.
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

// HeaderLength is what a header runs to, under every variant and every C.
const HeaderLength = 16

// MaxRows is the largest R a header defines, R6.1: the field is four bytes,
// and a value past what a signed long gives is out of bounds, so the error
// gives the count the header defines rather than a negative one. RR is 0 to
// R, so the one bound covers both.
const MaxRows = 2147483647

// Align gives at up to the next multiple of to.
func Align(at, to int) int {
	return (at + to - 1) / to * to
}

// Header gives what a file's header defines.
type Header struct {
	Variant int
	Rows    int // R
	Columns int // C
	Repeat  int // RR
	Width   int // W, the bytes every value takes
}

// Length gives what the header runs to, the payload's first byte.
func (h Header) Length() int {
	return HeaderLength
}

// RowBytes gives a row's bytes: C values of W bytes.
func (h Header) RowBytes() int {
	return h.Columns * h.Width
}

// ReadHeader gives the header at the start of file, or an error where the
// file is short of one, does not open with DTX, or breaks a bound R6 sets.
func ReadHeader(file []byte) (Header, error) {
	if len(file) < HeaderLength {
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
	width := int(file[14])
	if rows < 1 || rows > MaxRows {
		return Header{}, fmt.Errorf("R is 1 to %d, not %d", MaxRows, rows)
	}
	if columns < 1 || columns > 256 {
		return Header{}, fmt.Errorf("C is 1 to 256, not %d", columns)
	}
	if repeat < 0 || repeat > rows {
		return Header{}, fmt.Errorf("RR is 0 to R, not %d", repeat)
	}
	if width != 1 && width != 2 && width != 4 {
		return Header{}, fmt.Errorf(
			"the width is 1, 2 or 4 bytes, not %d", width)
	}
	return Header{int(file[3]), rows, columns, repeat, width}, nil
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
