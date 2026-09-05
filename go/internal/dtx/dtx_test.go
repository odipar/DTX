package dtx

import (
	"fmt"
	"testing"

	"dtx/internal/st4"
)

func table(t *testing.T, rows, columns, width int) *Table {
	t.Helper()
	return tableAt(t, rows, rows, columns, width)
}

// tableAt gives a table of rows rows repeating at repeat, with a byte pattern
// a column.
func tableAt(t *testing.T, rows, repeat, columns, width int) *Table {
	t.Helper()
	column := make([][]byte, columns)
	for i := range column {
		column[i] = make([]byte, rows*width)
		for at := range column[i] {
			column[i][at] = byte(i*31 + at)
		}
	}
	built, err := NewTable(rows, repeat, width, column)
	if err != nil {
		t.Fatal(err)
	}
	return built
}

// Every variant is the same table (R1.3): what goes out comes back.
func TestAPlainVariantWritesWhatItReads(t *testing.T) {
	for _, width := range []int{1, 2, 4} {
		for _, columns := range []int{1, 2, 3} {
			for _, rows := range []int{1, 3, 64} {
				want := table(t, rows, columns, width)
				for _, one := range []struct {
					name  string
					write func(*Table) []byte
					read  func([]byte) (*Table, error)
				}{
					{"DTX0", WriteDtx0, ReadDtx0},
					{"DTX1", WriteDtx1, ReadDtx1},
				} {
					at := fmt.Sprintf("%s at R=%d C=%d W=%d",
						one.name, rows, columns, width)
					got, err := one.read(one.write(want))
					if err != nil {
						t.Fatalf("%s: %v", at, err)
					}
					if !got.Same(want) {
						t.Fatalf("%s gave another table", at)
					}
				}
			}
		}
	}
}

// A DTX1 column begins on a word, so at a width of 1 and an odd R a zero
// byte stands between one column and the next (R4.3). At a width of 2 or 4
// a column is a whole number of words already, and DTX1 runs to what DTX0
// runs to.
func TestAColumnBeginsOnAWord(t *testing.T) {
	if got := StrideDtx1(3, 1); got != 4 {
		t.Fatalf("the stride at R=3 and a width of 1 is %d, not 4", got)
	}
	if got := PayloadLengthDtx1(3, 2, 1); got != 7 {
		t.Fatalf("two columns of three bytes run to %d, not 7", got)
	}
	odd, err := NewTable(3, 3, 1, [][]byte{{1, 2, 3}, {4, 5, 6}})
	if err != nil {
		t.Fatal(err)
	}
	file := WriteDtx1(odd)
	want := []byte{1, 2, 3, 0, 4, 5, 6}
	if got := file[HeaderLength:]; string(got) != string(want) {
		t.Fatalf("the payload is %v, not %v", got, want)
	}
	for _, width := range []int{2, 4} {
		even := table(t, 3, 3, width)
		if len(WriteDtx1(even)) != len(WriteDtx0(even)) {
			t.Fatalf("at a width of %d DTX1 is %d bytes and DTX0 %d",
				width, len(WriteDtx1(even)), len(WriteDtx0(even)))
		}
	}
}

// R6's bounds, and what a reader does where a table breaks one.
func TestTheBoundsAreChecked(t *testing.T) {
	one := []byte{0}
	for _, bad := range []struct {
		name   string
		rows   int
		repeat int
		width  int
	}{
		{"R below 1", 0, 0, 1},
		{"RR above R", 1, 2, 1},
		{"a width of 3", 1, 1, 3},
	} {
		if _, err := NewTable(bad.rows, bad.repeat, bad.width,
			[][]byte{one}); err == nil {
			t.Fatalf("%s was taken", bad.name)
		}
	}
	if _, err := ReadHeader([]byte("DT")); err == nil {
		t.Fatal("two bytes read as a header")
	}
	if _, err := ReadHeader(make([]byte, 20)); err == nil {
		t.Fatal("zero bytes read as a header")
	}
	head := WriteDtx0(table(t, 3, 2, 2))[:HeaderLength]
	head[14] = 3
	if _, err := ReadHeader(head); err == nil {
		t.Fatal("a width of 3 read as a header")
	}
}

// A DTX2 file written with the carried copy of ST4 reads back to the table
// it was written from: at a unit of 1, 2 and 4, at every width, through Read
// as well, with copies from the literal stream, and from a DTX1 or a DTX2
// file repacked.
func TestADtx2FileReadsBackToTheTableItWasWrittenFrom(t *testing.T) {
	same := func(name string, file []byte, want *Table) {
		t.Helper()
		got, err := ReadDtx2(file)
		if err != nil {
			t.Fatalf("%s: %v", name, err)
		}
		if !got.Same(want) {
			t.Fatalf("%s gave another table", name)
		}
	}
	for _, unit := range []int{1, 2, 4} {
		for _, width := range []int{1, 2, 4} {
			wide := tableAt(t, 64, 16, 2, width)
			file, err := WriteDtx2(wide, st4.Packer{}, unit, 960)
			if err != nil {
				t.Fatal(err)
			}
			at := fmt.Sprintf("k of %d at a width of %d", unit, width)
			same(at, file, wide)
			got, err := Read(file)
			if err != nil {
				t.Fatalf("%s through Read: %v", at, err)
			}
			if !got.Same(wide) {
				t.Fatalf("%s through Read gave another table", at)
			}
		}
	}
	for _, width := range []int{1, 2, 4} {
		at := fmt.Sprintf("a width of %d", width)
		want := tableAt(t, 64, 16, 3, width)
		copies, err := WriteDtx2(want, st4.Packer{CopiesFlag: true}, 1, 64)
		if err != nil {
			t.Fatal(err)
		}
		same("copies at a ring of 64, "+at, copies, want)
		again, err := Dtx2From(WriteDtx1(want), st4.Packer{}, 1, 960)
		if err != nil {
			t.Fatal(err)
		}
		same("DTX1 to DTX2 and back, "+at, again, want)
		again, err = Dtx2From(copies, st4.Packer{}, 2, 960)
		if err != nil {
			t.Fatal(err)
		}
		same("DTX2 to DTX2 at another unit and back, "+at, again, want)
	}
}

// A column whose bytes do not divide by k does not pack, R5.6.
func TestAColumnTheUnitDoesNotDivideIsRefused(t *testing.T) {
	if _, err := WriteDtx2(table(t, 3, 3, 2), st4.Packer{}, 4, 960); err == nil {
		t.Fatal("three rows of two bytes were packed at a k of 4")
	}
}

// A data set that opens with another unit than the payload defines is an
// error on read (R5.2), as is a payload cut short.
func TestADataSetOpeningWithAnotherUnitIsAnErrorOnRead(t *testing.T) {
	file, err := WriteDtx2(table(t, 8, 1, 2), st4.Packer{}, 2, 960)
	if err != nil {
		t.Fatal(err)
	}
	cut := file[:HeaderLength+6]
	if _, err := ReadDtx2(cut); err == nil {
		t.Fatal("a payload cut short was taken")
	}
	file[HeaderLength+2] = 1
	if _, err := ReadDtx2(file); err == nil {
		t.Fatal("a data set of another unit was taken")
	}
}
