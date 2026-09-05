package dtx

import "fmt"

// WriteDtx0 gives table as a DTX0 file: R rows, each column 0
// through column C minus one in order, with nothing between them.
//
// A value falls where the widths put it, so a two or four byte column can
// fall on an odd offset and a reader takes it as bytes (R3.4).
func WriteDtx0(t *Table) []byte {
	head := t.Header(DTX0)
	out := make([]byte, len(head)+t.Rows()*t.RowBytes())
	copy(out, head)
	at := len(head)
	for n := 0; n < t.Rows(); n++ {
		for i := 0; i < t.Columns(); i++ {
			w := t.Width(i)
			copy(out[at:at+w], t.column[i][n*w:])
			at += w
		}
	}
	return out
}

// ReadDtx0 gives the table in a DTX0 file.
func ReadDtx0(file []byte) (*Table, error) {
	header, err := ReadHeader(file)
	if err != nil {
		return nil, err
	}
	if header.Variant != DTX0 {
		return nil, fmt.Errorf("variant %d is not DTX0", header.Variant)
	}
	payload := header.Rows * header.RowBytes()
	if len(file)-header.Length < payload {
		return nil, fmt.Errorf("a payload of %d bytes is short of %d",
			len(file)-header.Length, payload)
	}
	column := make([][]byte, header.Columns())
	for i := range column {
		column[i] = make([]byte, header.Rows*header.Width[i])
	}
	at := header.Length
	for n := 0; n < header.Rows; n++ {
		for i := 0; i < header.Columns(); i++ {
			w := header.Width[i]
			copy(column[i][n*w:], file[at:at+w])
			at += w
		}
	}
	return NewTable(header.Rows, header.Repeat, header.Width, column)
}

// PayloadLengthDtx1 gives what a DTX1 payload runs to.
func PayloadLengthDtx1(rows int, width []int) int {
	at := Offsets(rows, width)
	last := len(width) - 1
	return at[last] + rows*width[last]
}

// WriteDtx1 gives table as a DTX1 file: column by column, each column
// beginning on a word so a wide value is read whole.
func WriteDtx1(t *Table) []byte {
	head := t.Header(DTX1)
	width := t.Widths()
	at := Offsets(t.Rows(), width)
	out := make([]byte, len(head)+PayloadLengthDtx1(t.Rows(), width))
	copy(out, head)
	for i := range width {
		copy(out[len(head)+at[i]:], t.column[i])
	}
	return out
}

// ReadDtx1 gives the table in a DTX1 file.
func ReadDtx1(file []byte) (*Table, error) {
	header, err := ReadHeader(file)
	if err != nil {
		return nil, err
	}
	if header.Variant != DTX1 {
		return nil, fmt.Errorf("variant %d is not DTX1", header.Variant)
	}
	payload := PayloadLengthDtx1(header.Rows, header.Width)
	if len(file)-header.Length < payload {
		return nil, fmt.Errorf("a payload of %d bytes is short of %d",
			len(file)-header.Length, payload)
	}
	at := Offsets(header.Rows, header.Width)
	column := make([][]byte, header.Columns())
	for i := range column {
		column[i] = make([]byte, header.Rows*header.Width[i])
		copy(column[i], file[header.Length+at[i]:])
	}
	return NewTable(header.Rows, header.Repeat, header.Width, column)
}

// Read gives the table in a file, under either plain variant.
func Read(file []byte) (*Table, error) {
	header, err := ReadHeader(file)
	if err != nil {
		return nil, err
	}
	switch header.Variant {
	case DTX0:
		return ReadDtx0(file)
	case DTX1:
		return ReadDtx1(file)
	default:
		return nil, fmt.Errorf("variant %d is not read here", header.Variant)
	}
}

// MaxRing is the largest ring a payload can define, in bytes: N is two bytes.
const MaxRing = 65535

// CopiesFlag is the flags bit at payload byte 3 that marks every column was
// packed with copies from its own literal stream, R5.10.
const CopiesFlag = 1

// A Packer makes one ST4 data set of one column.
//
// DTX2 defines a column as an ST4 data set (R5.1) and does not define how
// ST4 packs: the copy of ST4 carried in internal/st4 packs, or a packer
// beside it that -p names.
type Packer interface {
	// Pack gives column as one complete ST4 data set: its own header, and
	// the length of what it unpacks to.
	Pack(column []byte, unit, ring int) ([]byte, error)

	// Copies gives whether a match beyond the ring copies from the
	// column's own literal stream, which ST4 packs with -c. The packer
	// defines it: a flag carried beside a file could differ from the
	// bytes in it, and one the packer wrote cannot (R5.10).
	Copies() bool
}

// WriteDtx2 gives table as a DTX2 file, every column packed at one unit.
func WriteDtx2(t *Table, packer Packer, unit, ring int) ([]byte, error) {
	if unit != 1 && unit != 2 && unit != 4 {
		return nil, fmt.Errorf("k is 1, 2 or 4, not %d", unit)
	}
	if ring < 1 || ring > MaxRing {
		return nil, fmt.Errorf("N is 1 to %d, not %d", MaxRing, ring)
	}
	if t.Rows()%unit != 0 {
		return nil, fmt.Errorf("R is %d, which does not divide by k of %d",
			t.Rows(), unit)
	}
	set := make([][]byte, t.Columns())
	for i := range set {
		var err error
		if set[i], err = packer.Pack(t.Column(i), unit, ring); err != nil {
			return nil, err
		}
	}

	// 2.3: N, k, the flags, then an offset a column
	at := make([]int, t.Columns())
	next := 4 + 4*t.Columns()
	for i := range set {
		next = Align(next, 4)
		at[i] = next
		next += len(set[i])
	}
	head := t.Header(DTX2)
	out := make([]byte, len(head)+next)
	copy(out, head)
	PutWord(out, len(head), ring)
	out[len(head)+2] = byte(unit)
	if packer.Copies() {
		out[len(head)+3] = CopiesFlag
	}
	for i := range set {
		PutLong(out, len(head)+4+4*i, at[i])
		copy(out[len(head)+at[i]:], set[i])
	}
	return out, nil
}

// Dtx2From gives the DTX2 file of the table in a DTX0 or DTX1 file. The
// table is the same under every variant (R1.3), so what comes back has the
// same rows, widths, R and RR as what went in.
func Dtx2From(plain []byte, packer Packer, unit, ring int) ([]byte, error) {
	t, err := Read(plain)
	if err != nil {
		return nil, err
	}
	return WriteDtx2(t, packer, unit, ring)
}
