// Package csv reads a table out of comma separated text, one row a line, one
// value a column, and writes one back out.
//
// The first row of numbers gives C. A line that is blank, or whose first
// character other than a space is #, is not a row, and neither is a line
// before the first row of numbers in which no cell is a number: a line of
// column names, or a line describing the table. A value is decimal, or
// hexadecimal where it opens with $, and negative where it opens with -.
//
// Every value of the table takes the same width W (R6.3). A value of W
// bytes is stored most significant byte first, as every field of the header
// is, and a negative one in two's complement. A value fits W bytes where it
// lies from -2^(8W-1) to 2^(8W)-1, so one width takes a signed column's
// values and an unsigned one's alike. DTX does not define more of a column
// than its width, so which of the two a column is, the caller defines
// elsewhere.
//
// A table is written out the other way as well: a comment that gives the
// shape, a line of column names, then one row a line, each value the
// unsigned number its bytes give. Read back, the comment gives the width
// and the repeat where the caller does not give them, and the names are
// passed over, so the text a table was written as reads back to that table.
package csv

import (
	"fmt"
	"strconv"
	"strings"

	"dtx/dtx"
)

// Table gives the rows of text at the given width, repeating at repeat.
func Table(text string, width, repeat int) (*dtx.Table, error) {
	row, err := rows(text)
	if err != nil {
		return nil, err
	}
	return table(row, width, repeat)
}

// TableAt gives the rows of text at the given width, repeating at the row
// the first comment gives or else at R.
func TableAt(text string, width int) (*dtx.Table, error) {
	row, err := rows(text)
	if err != nil {
		return nil, err
	}
	repeat, err := Repeat(text)
	if err != nil {
		return nil, err
	}
	if repeat < 0 {
		repeat = len(row)
	}
	return table(row, width, repeat)
}

// Width gives the width the first comment of text gives, or else the
// narrowest of 1, 2 and 4 that takes every value of it.
func Width(text string) (int, error) {
	given := comment(text, "width ")
	if given == "" {
		row, err := rows(text)
		if err != nil {
			return 0, err
		}
		return narrowest(row)
	}
	width, err := strconv.Atoi(given)
	if err != nil {
		return 0, fmt.Errorf(
			"the comment gives %q, which is not a width", given)
	}
	return width, nil
}

// Repeat gives the repeat the first comment of text gives, or -1 where it
// does not give one.
func Repeat(text string) (int, error) {
	given := comment(text, "RR ")
	if given == "" {
		return -1, nil
	}
	repeat, err := strconv.Atoi(given)
	if err != nil {
		return 0, fmt.Errorf(
			"the comment gives %q, which is not a repeat", given)
	}
	return repeat, nil
}

// Text gives t as text: a comment giving R, C, the width and RR; a line of
// column names, c0 onward; then one row a line, one value a column, each the
// unsigned number its bytes give. TableAt, at the width Width gives, reads
// it back to the same table.
func Text(t *dtx.Table) string {
	var out strings.Builder
	fmt.Fprintf(&out, "# %d rows, %d columns, width %d, RR %d\n",
		t.Rows(), t.Columns(), t.Width(), t.Repeat())
	column := make([][]byte, t.Columns())
	for i := range column {
		if i > 0 {
			out.WriteByte(',')
		}
		fmt.Fprintf(&out, "c%d", i)
		column[i] = t.Column(i)
	}
	out.WriteByte('\n')
	width := t.Width()
	for r := 0; r < t.Rows(); r++ {
		for i := range column {
			if i > 0 {
				out.WriteByte(',')
			}
			out.WriteString(
				strconv.FormatUint(get(column[i], r*width, width), 10))
		}
		out.WriteByte('\n')
	}
	return out.String()
}

// comment gives what follows key in the first comment of text, up to a comma
// followed by a space or the end of the line, or empty where the text does
// not open with a comment giving it. The comment is the one Text writes:
// # 3 rows, 3 columns, width 2, RR 3.
func comment(text, key string) string {
	for _, line := range strings.Split(text, "\n") {
		read := strings.TrimSpace(line)
		if read == "" {
			continue
		}
		if !strings.HasPrefix(read, "#") {
			return ""
		}
		at := strings.Index(read, key)
		if at < 0 {
			return ""
		}
		rest := read[at+len(key):]
		if end := strings.Index(rest, ", "); end >= 0 {
			rest = rest[:end]
		}
		return strings.TrimSpace(rest)
	}
	return ""
}

// get gives the unsigned number of width bytes at at.
func get(in []byte, at, width int) uint64 {
	var value uint64
	for i := 0; i < width; i++ {
		value = value<<8 | uint64(in[at+i])
	}
	return value
}

func table(row [][]int64, width, repeat int) (*dtx.Table, error) {
	if width != 1 && width != 2 && width != 4 {
		return nil, fmt.Errorf(
			"the width is 1, 2 or 4 bytes, not %d", width)
	}
	columns := len(row[0])
	column := make([][]byte, columns)
	for i := range column {
		column[i] = make([]byte, len(row)*width)
		for r := range row {
			value := row[r][i]
			if !fits(value, width) {
				return nil, fmt.Errorf(
					"row %d column %d gives %d, which %d bytes do not take",
					r, i, value, width)
			}
			put(column[i], r*width, value, width)
		}
	}
	return dtx.NewTable(len(row), repeat, width, column)
}

// rows gives every row of text, a value a column, in the order read.
func rows(text string) ([][]int64, error) {
	var out [][]int64
	columns := -1
	for at, line := range strings.Split(text, "\n") {
		read := strings.TrimSpace(line)
		if read == "" || strings.HasPrefix(read, "#") {
			continue
		}
		cell := strings.Split(read, ",")
		if columns < 0 && !aNumberAmong(cell) {
			// a line of column names, or one describing the table
			continue
		}
		if columns < 0 {
			columns = len(cell)
			if columns > 256 {
				return nil, fmt.Errorf("C is 1 to 256, not %d", columns)
			}
		} else if len(cell) != columns {
			return nil, fmt.Errorf("line %d gives %d values, not %d",
				at+1, len(cell), columns)
		}
		row := make([]int64, columns)
		for i := range row {
			var err error
			if row[i], err = value(strings.TrimSpace(cell[i]), at+1, i); err != nil {
				return nil, err
			}
		}
		out = append(out, row)
	}
	if len(out) == 0 {
		return nil, fmt.Errorf("the text does not contain a row")
	}
	return out, nil
}

// aNumberAmong gives whether a cell of the line is a number, as value reads
// one.
func aNumberAmong(cell []string) bool {
	for _, one := range cell {
		read := strings.TrimSpace(one)
		hex := strings.HasPrefix(read, "$") || strings.HasPrefix(read, "-$")
		digits := read
		switch {
		case strings.HasPrefix(read, "-$"):
			digits = read[2:]
		case hex || strings.HasPrefix(read, "-"):
			digits = read[1:]
		}
		if digits == "" {
			continue
		}
		number := true
		for _, c := range digits {
			if !digit(c, hex) {
				number = false
			}
		}
		if number {
			return true
		}
	}
	return false
}

// digit gives whether c is a decimal digit, or a hexadecimal one where hex.
func digit(c rune, hex bool) bool {
	return c >= '0' && c <= '9' ||
		hex && (c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F')
}

// narrowest gives the narrowest width that takes every value of every column.
func narrowest(row [][]int64) (int, error) {
	taken := 1
	for _, read := range row {
		for _, value := range read {
			for !fits(value, taken) {
				if taken == 4 {
					return 0, fmt.Errorf("the text gives %d, which no width"+
						" of 1, 2 or 4 bytes takes", value)
				}
				if taken == 1 {
					taken = 2
				} else {
					taken = 4
				}
			}
		}
	}
	return taken, nil
}

// value gives the number cell gives, or what it is that is not one.
func value(cell string, line, column int) (int64, error) {
	where := fmt.Sprintf("line %d column %d", line, column)
	notANumber := fmt.Errorf("%s gives %q, which is not a number", where, cell)
	switch {
	case strings.HasPrefix(cell, "$"):
		out, err := strconv.ParseInt(cell[1:], 16, 64)
		if err != nil {
			return 0, notANumber
		}
		return out, nil
	case strings.HasPrefix(cell, "-$"):
		out, err := strconv.ParseInt(cell[2:], 16, 64)
		if err != nil {
			return 0, notANumber
		}
		return -out, nil
	default:
		out, err := strconv.ParseInt(cell, 10, 64)
		if err != nil {
			return 0, notANumber
		}
		return out, nil
	}
}

// fits gives whether value lies from -2^(8W-1) to 2^(8W)-1.
func fits(value int64, width int) bool {
	return value >= -(int64(1)<<(8*width-1)) && value <= int64(1)<<(8*width)-1
}

// put writes value at at, most significant byte first.
func put(out []byte, at int, value int64, width int) {
	for i := 0; i < width; i++ {
		out[at+i] = byte(value >> (8 * (width - 1 - i)))
	}
}
