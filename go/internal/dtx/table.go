package dtx

import "fmt"

// A Table in memory: R rows, C columns, and a row RR it repeats to. Every
// variant is this same table (R1.3), so a variant reads into one and
// writes out of one.
//
// The values are stored column by column, R times a column's width bytes each.
// DTX1 and DTX2 lay them out that way, and DTX0 walks them a row at a time.
type Table struct {
	rows   int
	repeat int
	width  []int
	column [][]byte
}

// NewTable gives a table of the given columns, each rows times its width
// bytes. The slices are copied, so a later write to the caller's does not
// reach this table.
func NewTable(rows, repeat int, width []int, column [][]byte) (*Table, error) {
	if rows < 1 {
		return nil, fmt.Errorf("R is 1 upward, not %d", rows)
	}
	if len(width) < 1 || len(width) > 256 {
		return nil, fmt.Errorf("C is 1 to 256, not %d", len(width))
	}
	if len(width) != len(column) {
		return nil, fmt.Errorf("%d widths for %d columns",
			len(width), len(column))
	}
	if repeat < 0 || repeat > rows {
		return nil, fmt.Errorf("RR is 0 to R, not %d", repeat)
	}
	held := make([][]byte, len(column))
	for i, bytes := range column {
		if width[i] != 1 && width[i] != 2 && width[i] != 4 {
			return nil, fmt.Errorf("column %d is %d bytes wide, not 1, 2 or 4",
				i, width[i])
		}
		if len(bytes) != rows*width[i] {
			return nil, fmt.Errorf("column %d holds %d bytes, not %d",
				i, len(bytes), rows*width[i])
		}
		held[i] = append([]byte(nil), bytes...)
	}
	return &Table{rows, repeat, append([]int(nil), width...), held}, nil
}

// Rows gives R.
func (t *Table) Rows() int { return t.rows }

// Columns gives C.
func (t *Table) Columns() int { return len(t.width) }

// Repeat gives RR, the row the table repeats to, or R where it does not.
func (t *Table) Repeat() int { return t.repeat }

// Width gives column i's width in bytes.
func (t *Table) Width(i int) int { return t.width[i] }

// Widths gives every column's width.
func (t *Table) Widths() []int { return append([]int(nil), t.width...) }

// Column gives column i's R values, in row order.
func (t *Table) Column(i int) []byte {
	return append([]byte(nil), t.column[i]...)
}

// RowBytes gives a row's bytes: column 0 through column C minus one.
func (t *Table) RowBytes() int {
	out := 0
	for _, w := range t.width {
		out += w
	}
	return out
}

// Same states whether two tables are the same rows, widths, R and RR.
func (t *Table) Same(other *Table) bool {
	if t.rows != other.rows || t.repeat != other.repeat ||
		len(t.width) != len(other.width) {
		return false
	}
	for i := range t.width {
		if t.width[i] != other.width[i] ||
			string(t.column[i]) != string(other.column[i]) {
			return false
		}
	}
	return true
}

// Header gives the header of table under variant, SPEC.md 1.
func (t *Table) Header(variant int) []byte {
	out := make([]byte, HeaderLength(t.Columns()))
	copy(out, Magic)
	out[3] = byte(variant)
	PutLong(out, 4, t.rows)
	PutWord(out, 8, t.Columns())
	PutLong(out, 10, t.repeat)
	for i, w := range t.width {
		out[14+i] = byte(w)
	}
	return out
}
