package dtx

import (
	"fmt"
	"testing"

	"dtx/internal/st4"
)

func table(t *testing.T, rows int, width []int) *Table {
	t.Helper()
	return tableAt(t, rows, rows, width)
}

// tableAt gives a table of rows rows repeating at repeat, with a byte pattern
// a column.
func tableAt(t *testing.T, rows, repeat int, width []int) *Table {
	t.Helper()
	column := make([][]byte, len(width))
	for i, w := range width {
		column[i] = make([]byte, rows*w)
		for at := range column[i] {
			column[i][at] = byte(i*31 + at)
		}
	}
	held, err := NewTable(rows, repeat, width, column)
	if err != nil {
		t.Fatal(err)
	}
	return held
}

// Every variant is the same table (R1.3): what goes out comes back.
func TestAPlainVariantWritesWhatItReads(t *testing.T) {
	for _, width := range [][]int{{1}, {1, 2, 4}, {4, 2, 1}, {2, 2}} {
		for _, rows := range []int{1, 3, 64} {
			want := table(t, rows, width)
			for _, one := range []struct {
				name  string
				write func(*Table) []byte
				read  func([]byte) (*Table, error)
			}{
				{"DTX0", WriteDtx0, ReadDtx0},
				{"DTX1", WriteDtx1, ReadDtx1},
			} {
				got, err := one.read(one.write(want))
				if err != nil {
					t.Fatalf("%s at R=%d C=%d: %v", one.name, rows, len(width), err)
				}
				if !got.Same(want) {
					t.Fatalf("%s at R=%d C=%d gave another table",
						one.name, rows, len(width))
				}
			}
		}
	}
}

// A DTX1 column of two or four bytes begins on a word, so a wide value is
// read whole (R4.2).
func TestEveryWideColumnBeginsOnAWord(t *testing.T) {
	at := Offsets(3, []int{1, 4, 2})
	for i, w := range []int{1, 4, 2} {
		if w > 1 && at[i]%2 != 0 {
			t.Fatalf("column %d of %d bytes begins at %d", i, w, at[i])
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
		width  []int
	}{
		{"R below 1", 0, 0, []int{1}},
		{"RR above R", 1, 2, []int{1}},
		{"a width of 3", 1, 1, []int{3}},
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
}

// A DTX2 file written with the carried copy of ST4 reads back to the table
// it was written from: at a unit of 1, 2 and 4, through Read as well, with
// copies from the literal stream, and from a DTX1 or a DTX2 file repacked.
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
		wide := tableAt(t, 64, 16, []int{unit, 4})
		file, err := WriteDtx2(wide, st4.Packer{}, unit, 960)
		if err != nil {
			t.Fatal(err)
		}
		same(fmt.Sprintf("k of %d", unit), file, wide)
		got, err := Read(file)
		if err != nil {
			t.Fatalf("k of %d through Read: %v", unit, err)
		}
		if !got.Same(wide) {
			t.Fatalf("k of %d through Read gave another table", unit)
		}
	}
	want := tableAt(t, 64, 16, []int{1, 2, 4})
	copies, err := WriteDtx2(want, st4.Packer{CopiesFlag: true}, 1, 64)
	if err != nil {
		t.Fatal(err)
	}
	same("packed with copies at a ring of 64", copies, want)
	again, err := Dtx2From(WriteDtx1(want), st4.Packer{}, 1, 960)
	if err != nil {
		t.Fatal(err)
	}
	same("DTX1 to DTX2 and back", again, want)
	again, err = Dtx2From(copies, st4.Packer{}, 2, 960)
	if err != nil {
		t.Fatal(err)
	}
	same("DTX2 to DTX2 at another unit and back", again, want)
}

// A data set that opens with another unit than the payload defines is an
// error on read (R5.2), as is a payload cut short.
func TestADataSetOpeningWithAnotherUnitIsAnErrorOnRead(t *testing.T) {
	file, err := WriteDtx2(table(t, 8, []int{2}), st4.Packer{}, 2, 960)
	if err != nil {
		t.Fatal(err)
	}
	cut := file[:HeaderLength(1)+6]
	if _, err := ReadDtx2(cut); err == nil {
		t.Fatal("a payload cut short was taken")
	}
	file[HeaderLength(1)+2] = 1
	if _, err := ReadDtx2(file); err == nil {
		t.Fatal("a data set of another unit was taken")
	}
}
