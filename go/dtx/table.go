package dtx

import "fmt"

// A Table in memory: R rows, C columns of one width, and a row RR it
// repeats to. Every variant is this same table (R1.3), so a variant reads
// into one and writes out of one.
//
// The values are stored column by column, R times the width bytes each.
// DTX1 and DTX2 lay them out that way, and DTX0 walks them a row at a time.
type Table struct {
	rows   int
	repeat int
	width  int
	column [][]byte
}

// NewTable gives a table of the given columns, each rows times width bytes.
// The slices are copied, so a later write to the caller's does not reach
// this table.
func NewTable(rows, repeat, width int, column [][]byte) (*Table, error) {
	if rows < 1 {
		return nil, fmt.Errorf("R is 1 upward, not %d", rows)
	}
	if len(column) < 1 || len(column) > 256 {
		return nil, fmt.Errorf("C is 1 to 256, not %d", len(column))
	}
	if width != 1 && width != 2 && width != 4 {
		return nil, fmt.Errorf(
			"the width is 1, 2 or 4 bytes, not %d", width)
	}
	if repeat < 0 || repeat > rows {
		return nil, fmt.Errorf("RR is 0 to R, not %d", repeat)
	}
	kept := make([][]byte, len(column))
	for i, bytes := range column {
		if len(bytes) != rows*width {
			return nil, fmt.Errorf("column %d is %d bytes, not %d",
				i, len(bytes), rows*width)
		}
		kept[i] = append([]byte(nil), bytes...)
	}
	return &Table{rows, repeat, width, kept}, nil
}

// Rows gives R.
func (t *Table) Rows() int { return t.rows }

// Columns gives C.
func (t *Table) Columns() int { return len(t.column) }

// Repeat gives RR, the row the table repeats to, or R where it does not.
func (t *Table) Repeat() int { return t.repeat }

// Width gives W, the bytes every value of the table takes.
func (t *Table) Width() int { return t.width }

// Column gives column i's R values, in row order.
func (t *Table) Column(i int) []byte {
	return append([]byte(nil), t.column[i]...)
}

// RowBytes gives a row's bytes: C values of W bytes.
func (t *Table) RowBytes() int {
	return len(t.column) * t.width
}

// Same gives whether two tables are the same rows, width, R and RR.
func (t *Table) Same(other *Table) bool {
	if t.rows != other.rows || t.repeat != other.repeat ||
		t.width != other.width || len(t.column) != len(other.column) {
		return false
	}
	for i := range t.column {
		if string(t.column[i]) != string(other.column[i]) {
			return false
		}
	}
	return true
}

// Header gives the header of table under variant, SPEC.md 1.
func (t *Table) Header(variant int) []byte {
	out := make([]byte, HeaderLength)
	copy(out, Magic)
	out[3] = byte(variant)
	PutLong(out, 4, t.rows)
	PutWord(out, 8, t.Columns())
	PutLong(out, 10, t.repeat)
	out[14] = byte(t.width)
	return out
}
