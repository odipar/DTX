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
