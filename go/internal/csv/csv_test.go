package csv

import "testing"

// The narrowest width of 1, 2 and 4 that takes every value of a column, and
// what a value may be written as.
func TestTheNarrowestWidthTakesEveryValue(t *testing.T) {
	width, err := Narrowest("0,0,0\n255,256,-129\n-128,-32768,70000\n")
	if err != nil {
		t.Fatal(err)
	}
	for i, want := range []int{1, 2, 4} {
		if width[i] != want {
			t.Fatalf("column %d takes %d bytes, not %d", i, width[i], want)
		}
	}
}

// A value is decimal, or hexadecimal where it opens with $, and negative
// where it opens with -. A blank line and a # line are not rows.
func TestWhatALineMayContain(t *testing.T) {
	table, err := TableAt("# a comment\n\n1,$FF\n-2,-$10\n", []int{1, 2})
	if err != nil {
		t.Fatal(err)
	}
	if table.Rows() != 2 {
		t.Fatalf("%d rows, not 2", table.Rows())
	}
	if got := table.Column(0); got[0] != 1 || got[1] != 0xFE {
		t.Fatalf("column 0 holds %v", got)
	}
	// Most significant byte first, and a negative in two's complement.
	if got := table.Column(1); got[0] != 0 || got[1] != 0xFF ||
		got[2] != 0xFF || got[3] != 0xF0 {
		t.Fatalf("column 1 holds %v", got)
	}
}

func TestWhatIsRefused(t *testing.T) {
	for _, bad := range []struct{ name, text string }{
		{"no row at all", "\n# nothing\n"},
		{"a short line", "1,2\n3\n"},
		{"a value that is not a number", "1,two\n"},
	} {
		if _, err := Narrowest(bad.text); err == nil {
			t.Fatalf("%s was taken", bad.name)
		}
	}
	if _, err := TableAt("300\n", []int{1}); err == nil {
		t.Fatal("300 was taken in one byte")
	}
	if _, err := TableAt("1,2\n", []int{1}); err == nil {
		t.Fatal("one width was taken for two columns")
	}
}

// The text of the other tests here: a comment, a blank line, three rows of
// three columns taking widths 1, 2 and 1.
const text = "# a comment, and the blank line below it\n\n" +
	"1, 300, -2\n2, 301, -1\n3, 302,  0\n"

// A table written as text opens with its shape and its column names, then
// one row a line, each value the unsigned number its bytes give.
func TestATableWrittenAsTextOpensWithItsShapeAndItsColumnNames(t *testing.T) {
	width, err := Widths(text)
	if err != nil {
		t.Fatal(err)
	}
	table, err := TableAt(text, width)
	if err != nil {
		t.Fatal(err)
	}
	want := "# 3 rows, 3 columns, widths 1,2,1, RR 3\nc0,c1,c2\n" +
		"1,300,254\n2,301,255\n3,302,0\n"
	if got := Text(table); got != want {
		t.Fatalf("written as\n%s\nnot\n%s", got, want)
	}
}

// The text a table was written as reads back to that table: the same widths
// and repeat, through the comment the text opens with.
func TestTheTextATableWasWrittenAsReadsBackToThatTable(t *testing.T) {
	table, err := Table(text, []int{4, 2, 2}, 1)
	if err != nil {
		t.Fatal(err)
	}
	written := Text(table)
	width, err := Widths(written)
	if err != nil {
		t.Fatal(err)
	}
	back, err := TableAt(written, width)
	if err != nil {
		t.Fatal(err)
	}
	if !back.Same(table) {
		t.Fatalf("read back to another table:\n%s", written)
	}
	if back.Width(0) != 4 || back.Repeat() != 1 {
		t.Fatalf("column 0 is %d bytes wide and RR is %d",
			back.Width(0), back.Repeat())
	}
}

// A line before the first row of numbers in which no cell is a number is not
// a row; one among the rows is an error.
func TestALineOfNamesBeforeTheRowsIsNotARow(t *testing.T) {
	named, err := TableAt("a table of two columns\nleft, right\n1, 2\n3, 4\n",
		[]int{1, 1})
	if err != nil {
		t.Fatal(err)
	}
	if named.Rows() != 2 {
		t.Fatalf("%d rows, not 2", named.Rows())
	}
	if got := named.Column(0); got[0] != 1 || got[1] != 3 {
		t.Fatalf("column 0 is %v", got)
	}
	if _, err := Narrowest("1, 2\nleft, right\n"); err == nil {
		t.Fatal("a line of names among the rows was taken as a row")
	}
}

// The widths and the repeat given outrank the comment, and the comment gives
// them where the caller does not.
func TestTheWidthsAndRepeatGivenOutrankTheComment(t *testing.T) {
	text := "# 2 rows, 1 columns, widths 4, RR 0\nc0\n1\n2\n"
	width, err := Widths(text)
	if err != nil {
		t.Fatal(err)
	}
	if len(width) != 1 || width[0] != 4 {
		t.Fatalf("the comment's widths read as %v", width)
	}
	repeat, err := Repeat(text)
	if err != nil {
		t.Fatal(err)
	}
	if repeat != 0 {
		t.Fatalf("the comment's RR read as %d", repeat)
	}
	given, err := TableAt(text, width)
	if err != nil {
		t.Fatal(err)
	}
	if given.Repeat() != 0 {
		t.Fatalf("RR is %d, not the comment's 0", given.Repeat())
	}
	narrow, err := TableAt(text, []int{1})
	if err != nil {
		t.Fatal(err)
	}
	if narrow.Width(0) != 1 {
		t.Fatalf("column 0 is %d bytes wide, not the 1 given", narrow.Width(0))
	}
	repeating, err := Table(text, []int{1}, 2)
	if err != nil {
		t.Fatal(err)
	}
	if repeating.Repeat() != 2 {
		t.Fatalf("RR is %d, not the 2 given", repeating.Repeat())
	}
}
