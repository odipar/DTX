// Package csv reads a table out of comma separated text: one row a line, one
// value a column.
//
// The first row that holds values gives C. A line that is blank, or whose
// first character other than a space is #, is not a row. A value is decimal,
// or hexadecimal where it opens with $, and negative where it opens with -.
//
// A value of W bytes is stored most significant byte first, as every field
// of the header is, and a negative one in two's complement. A value fits W
// bytes where it lies from -2^(8W-1) to 2^(8W)-1, so one width takes what a
// signed column holds and what an unsigned one holds alike. DTX states no
// more of a column than its width, so which of the two a column holds is the
// caller's to state elsewhere.
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

// TableAt gives the rows of text at the given widths, repeating at R.
func TableAt(text string, width []int) (*dtx.Table, error) {
	row, err := rows(text)
	if err != nil {
		return nil, err
	}
	return table(row, width, len(row))
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
		return nil, fmt.Errorf("the text holds no row")
	}
	return out, nil
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

// fits states whether value lies from -2^(8W-1) to 2^(8W)-1.
func fits(value int64, width int) bool {
	return value >= -(int64(1)<<(8*width-1)) && value <= int64(1)<<(8*width)-1
}

// put writes value at at, most significant byte first.
func put(out []byte, at int, value int64, width int) {
	for i := 0; i < width; i++ {
		out[at+i] = byte(value >> (8 * (width - 1 - i)))
	}
}
