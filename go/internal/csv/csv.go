// Package csv reads a table out of comma separated text, one row a line, one
// value a column, and writes one back out.
//
// The first row of numbers gives C. A line that is blank, or whose first
// character other than a space is #, is not a row, and neither is a line
// before the first row of numbers in which no cell is a number: a line of
// column names, or a line describing the table. A value is decimal, or
// hexadecimal where it opens with $, and negative where it opens with -.
//
// A value of W bytes is stored most significant byte first, as every field
// of the header is, and a negative one in two's complement. A value fits W
// bytes where it lies from -2^(8W-1) to 2^(8W)-1, so one width takes what a
// signed column's values and an unsigned one's alike. DTX does not define
// more of a column than its width, so which of the two a column is, the
// caller defines elsewhere.
//
// A table is written out the other way as well: a comment that gives the
// shape, a line of column names, then one row a line, each value the
// unsigned number its bytes give. Read back, the comment gives the widths
// and the repeat where the caller does not give them, and the names are
// passed over, so the text a table was written as reads back to that table.
package csv

import (
	"fmt"
	"strconv"
	"strings"

	"dtx/internal/dtx"
)

// Table gives the rows of text at the given widths, repeating at repeat.
func Table(text string, width []int, repeat int) (*dtx.Table, error) {
	row, err := rows(text)
	if err != nil {
		return nil, err
	}
	return table(row, width, repeat)
}

// TableAt gives the rows of text at the given widths, repeating at the row
// the first comment gives or else at R.
func TableAt(text string, width []int) (*dtx.Table, error) {
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

// Widths gives the widths the first comment of text gives, or else the
// narrowest width of 1, 2 and 4 that takes every value of each column.
func Widths(text string) ([]int, error) {
	given := comment(text, "widths ")
	if given == "" {
		return Narrowest(text)
	}
	cell := strings.Split(given, ",")
	for len(cell) > 1 && cell[len(cell)-1] == "" {
		// a trailing comma is passed over, as the Java tree passes it over
		cell = cell[:len(cell)-1]
	}
	width := make([]int, len(cell))
	for i, one := range cell {
		var err error
		if width[i], err = strconv.Atoi(strings.TrimSpace(one)); err != nil {
			return nil, fmt.Errorf(
				"the comment gives %q, which is not a width", one)
		}
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

// Narrowest gives the narrowest width of 1, 2 and 4 that takes every value
// of each column of text.
func Narrowest(text string) ([]int, error) {
	row, err := rows(text)
	if err != nil {
		return nil, err
	}
	return narrowest(row)
}

// Text gives t as text: a comment giving R, C, the widths and RR; a line of
// column names, c0 onward; then one row a line, one value a column, each the
// unsigned number its bytes give. TableAt, at the widths Widths gives, reads
// it back to the same table.
func Text(t *dtx.Table) string {
	var out strings.Builder
	fmt.Fprintf(&out, "# %d rows, %d columns, widths ", t.Rows(), t.Columns())
	column := make([][]byte, t.Columns())
	for i := range column {
		if i > 0 {
			out.WriteByte(',')
		}
		out.WriteString(strconv.Itoa(t.Width(i)))
		column[i] = t.Column(i)
	}
	fmt.Fprintf(&out, ", RR %d\n", t.Repeat())
	for i := range column {
		if i > 0 {
			out.WriteByte(',')
		}
		fmt.Fprintf(&out, "c%d", i)
	}
	out.WriteByte('\n')
	for r := 0; r < t.Rows(); r++ {
		for i := range column {
			if i > 0 {
				out.WriteByte(',')
			}
			w := t.Width(i)
			out.WriteString(strconv.FormatUint(get(column[i], r*w, w), 10))
		}
		out.WriteByte('\n')
	}
	return out.String()
}

// comment gives what follows key in the first comment of text, up to a comma
// followed by a space or the end of the line, or empty where the text does
// not open with a comment giving it. The comment is the one Text writes:
// # 3 rows, 3 columns, widths 1,2,1, RR 3.
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

func table(row [][]int64, width []int, repeat int) (*dtx.Table, error) {
	for _, w := range width {
		if w != 1 && w != 2 && w != 4 {
			return nil, fmt.Errorf(
				"a column is 1, 2 or 4 bytes wide, not %d", w)
		}
	}
	if len(row[0]) != len(width) {
		return nil, fmt.Errorf("%d widths for %d columns",
			len(width), len(row[0]))
	}
	column := make([][]byte, len(width))
	for i, w := range width {
		column[i] = make([]byte, len(row)*w)
		for r := range row {
			value := row[r][i]
			if !fits(value, w) {
				return nil, fmt.Errorf(
					"row %d column %d gives %d, which %d bytes do not take",
					r, i, value, w)
			}
			put(column[i], r*w, value, w)
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
			return nil, fmt.Errorf("line %d holds %d values, not %d",
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

// narrowest gives the narrowest width each column of row takes.
func narrowest(row [][]int64) ([]int, error) {
	width := make([]int, len(row[0]))
	for i := range width {
		taken := 1
		for _, read := range row {
			for !fits(read[i], taken) {
				if taken == 4 {
					return nil, fmt.Errorf("column %d gives %d, which no"+
						" width of 1, 2 or 4 bytes takes", i, read[i])
				}
				if taken == 1 {
					taken = 2
				} else {
					taken = 4
				}
			}
		}
		width[i] = taken
	}
	return width, nil
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
