package csv

import (
	"testing"

	"github.com/odipar/dtx/go/dtx"
)

// The text of the tests here: a comment, a blank line, three rows of three
// columns. 300 does not fit one byte, so every value of the table takes two.
const text = "# a comment, and the blank line below it\n\n" +
	"1, 300, -2\n2, 301, -1\n3, 302,  0\n"

// The narrowest width of 1, 2 and 4 that takes every value of the whole
// table, R6.3.
func TestTheTableTakesTheNarrowestWidthThatFitsEveryValue(t *testing.T) {
	for _, one := range []struct {
		text string
		want int
	}{
		{text, 2},
		{"1, -2\n3, 255\n", 1},
		{"1, 2\n3, 70000\n", 4},
		{"0,0,0\n255,256,-129\n-128,-32768,70000\n", 4},
	} {
		width, err := Width(one.text)
		if err != nil {
			t.Fatal(err)
		}
		if width != one.want {
			t.Fatalf("%q takes %d bytes, not %d", one.text, width, one.want)
		}
	}
}

// A value is stored most significant byte first, and a negative one in
// two's complement.
func TestAValueIsStoredMostSignificantByteFirst(t *testing.T) {
	table, err := TableAt(text, 2)
	if err != nil {
		t.Fatal(err)
	}
	if table.Rows() != 3 || table.Columns() != 3 || table.Width() != 2 {
		t.Fatalf("R=%d C=%d W=%d, not 3, 3 and 2",
			table.Rows(), table.Columns(), table.Width())
	}
	if table.Repeat() != 3 {
		t.Fatalf("RR is %d, not R where the table does not repeat",
			table.Repeat())
	}
	same(t, "column 0", table.Column(0), []byte{0, 1, 0, 2, 0, 3})
	same(t, "column 1", table.Column(1), []byte{1, 44, 1, 45, 1, 46})
	same(t, "column 2", table.Column(2), []byte{0xFF, 0xFE, 0xFF, 0xFF, 0, 0})
}

// A value is decimal, or hexadecimal where it opens with $, and negative
// where it opens with -. A blank line and a # line are not rows.
func TestWhatALineMayContain(t *testing.T) {
	table, err := TableAt("# a comment\n\n$10, -$1\n$FF, $7FFF\n", 2)
	if err != nil {
		t.Fatal(err)
	}
	if table.Rows() != 2 {
		t.Fatalf("%d rows, not 2", table.Rows())
	}
	same(t, "column 0", table.Column(0), []byte{0, 0x10, 0, 0xFF})
	same(t, "column 1", table.Column(1), []byte{0xFF, 0xFF, 0x7F, 0xFF})
}

// The width given is taken over the narrowest.
func TestTheWidthGivenIsTakenOverTheNarrowest(t *testing.T) {
	table, err := TableAt(text, 4)
	if err != nil {
		t.Fatal(err)
	}
	if table.Width() != 4 {
		t.Fatalf("W is %d, not the 4 given", table.Width())
	}
	same(t, "column 0", table.Column(0),
		[]byte{0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 3})
	repeating, err := Table(text, 2, 1)
	if err != nil {
		t.Fatal(err)
	}
	if repeating.Repeat() != 1 {
		t.Fatalf("RR is %d, not the 1 given", repeating.Repeat())
	}
}

// What the text does not give is reported.
func TestWhatIsRefused(t *testing.T) {
	for _, bad := range []struct{ name, said, text string }{
		{"a short line", "line 2 gives 2 values, not 3", "1,2,3\n4,5\n"},
		{"a value that is not a number",
			"line 1 column 1 gives \"x\", which is not a number", "1,x\n"},
		{"no row at all", "the text does not contain a row",
			"# nothing but a comment\n"},
		{"a value no width takes",
			"the text gives 4294967296, which no width of 1, 2 or 4 bytes" +
				" takes", "4294967296\n"},
	} {
		_, err := Width(bad.text)
		if err == nil {
			t.Fatalf("%s was taken", bad.name)
		}
		if err.Error() != bad.said {
			t.Fatalf("%s gave %q, not %q", bad.name, err, bad.said)
		}
	}
	if _, err := TableAt("300\n", 1); err == nil {
		t.Fatal("300 was taken in one byte")
	}
	if _, err := TableAt("1\n", 3); err == nil {
		t.Fatal("a width of 3 was taken")
	}
}

// A table written as text opens with its shape and its column names, then
// one row a line, each value the unsigned number its bytes give.
func TestATableWrittenAsTextOpensWithItsShapeAndItsColumnNames(t *testing.T) {
	table, err := TableAt(text, 2)
	if err != nil {
		t.Fatal(err)
	}
	want := "# 3 rows, 3 columns, width 2, RR 3\nc0,c1,c2\n" +
		"1,300,65534\n2,301,65535\n3,302,0\n"
	if got := Text(table); got != want {
		t.Fatalf("written as\n%s\nnot\n%s", got, want)
	}
}

// The text a table was written as reads back to that table: the same width
// and repeat, through the comment the text opens with.
func TestTheTextATableWasWrittenAsReadsBackToThatTable(t *testing.T) {
	table, err := Table(text, 4, 1)
	if err != nil {
		t.Fatal(err)
	}
	written := Text(table)
	back, err := read(t, written)
	if err != nil {
		t.Fatal(err)
	}
	if !back.Same(table) {
		t.Fatalf("read back to another table:\n%s", written)
	}
	if back.Width() != 4 || back.Repeat() != 1 {
		t.Fatalf("W is %d and RR is %d", back.Width(), back.Repeat())
	}
	for _, width := range []int{1, 2, 4} {
		one, err := Table("1, -2\n3, 4\n5, 6\n", width, 2)
		if err != nil {
			t.Fatal(err)
		}
		again, err := read(t, Text(one))
		if err != nil {
			t.Fatal(err)
		}
		if !again.Same(one) {
			t.Fatalf("a width of %d read back to another table", width)
		}
	}
}

// A line before the first row of numbers in which no cell is a number is not
// a row; one among the rows is an error.
func TestALineOfNamesBeforeTheRowsIsNotARow(t *testing.T) {
	named, err := TableAt("a table of two columns\nleft, right\n1, 2\n3, 4\n", 1)
	if err != nil {
		t.Fatal(err)
	}
	if named.Rows() != 2 {
		t.Fatalf("%d rows, not 2", named.Rows())
	}
	same(t, "column 0", named.Column(0), []byte{1, 3})
	if _, err := Width("1, 2\nleft, right\n"); err == nil {
		t.Fatal("a line of names among the rows was taken as a row")
	}
}

// The width and the repeat given outrank the comment, and the comment gives
// them where the caller does not.
func TestTheWidthAndRepeatGivenOutrankTheComment(t *testing.T) {
	given := "# 2 rows, 1 columns, width 4, RR 0\nc0\n1\n2\n"
	width, err := Width(given)
	if err != nil {
		t.Fatal(err)
	}
	if width != 4 {
		t.Fatalf("the comment's width read as %d", width)
	}
	repeat, err := Repeat(given)
	if err != nil {
		t.Fatal(err)
	}
	if repeat != 0 {
		t.Fatalf("the comment's RR read as %d", repeat)
	}
	from, err := read(t, given)
	if err != nil {
		t.Fatal(err)
	}
	if from.Width() != 4 || from.Repeat() != 0 {
		t.Fatalf("the comment gave W of %d and RR of %d",
			from.Width(), from.Repeat())
	}
	narrow, err := TableAt(given, 1)
	if err != nil {
		t.Fatal(err)
	}
	if narrow.Width() != 1 {
		t.Fatalf("W is %d, not the 1 given", narrow.Width())
	}
	repeating, err := Table(given, 1, 2)
	if err != nil {
		t.Fatal(err)
	}
	if repeating.Repeat() != 2 {
		t.Fatalf("RR is %d, not the 2 given", repeating.Repeat())
	}
}

// read gives the table of text at the width and repeat the text itself
// gives, as a caller who names neither takes it.
func read(t *testing.T, text string) (*dtx.Table, error) {
	t.Helper()
	width, err := Width(text)
	if err != nil {
		return nil, err
	}
	return TableAt(text, width)
}

// same fails where two columns are different bytes.
func same(t *testing.T, name string, got, want []byte) {
	t.Helper()
	if string(got) != string(want) {
		t.Fatalf("%s is %v, not %v", name, got, want)
	}
}
