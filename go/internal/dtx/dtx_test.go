package dtx

import "testing"

func table(t *testing.T, rows int, width []int) *Table {
	t.Helper()
	column := make([][]byte, len(width))
	for i, w := range width {
		column[i] = make([]byte, rows*w)
		for at := range column[i] {
			column[i][at] = byte(i*31 + at)
		}
	}
	held, err := NewTable(rows, rows, width, column)
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
