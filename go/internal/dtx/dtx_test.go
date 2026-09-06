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

// A packer that stands in for ST4: a twenty-eight byte header and the column
// behind it. What it packs to does not matter to DTX2's layout, only where
// the payload puts it.
type standin struct{}

func (standin) Copies() bool { return false }

func (standin) Pack(column []byte, unit, ring int) ([]byte, error) {
	set := make([]byte, 28+len(column))
	set[0], set[1], set[2], set[3] = 'S', '4', 7, byte(unit)
	copy(set[28:], column)
	return set, nil
}

// The offsets a DTX2 payload gives, one a column.
func offsets(file []byte, columns int) []int {
	at := make([]int, columns)
	for i := range at {
		at[i] = GetLong(file, HeaderLength+4+4*i)
	}
	return at
}

// SPEC.md 2.3: N, k and the flags once, then an offset a column. Three
// columns of three two byte values put the first data set at 16, past the
// four bytes and the three offsets.
func TestThePayloadDefinesTheRingAndTheUnitOnceAndThenAnOffsetAColumn(
	t *testing.T) {
	file, err := WriteDtx2(table(t, 3, 3, 2), standin{}, 1, 960)
	if err != nil {
		t.Fatal(err)
	}
	if file[3] != DTX2 {
		t.Fatalf("the variant is %d, not 2", file[3])
	}
	if got := GetWord(file, HeaderLength); got != 960 {
		t.Fatalf("N is %d, not 960", got)
	}
	if file[HeaderLength+2] != 1 {
		t.Fatalf("k is %d, not 1", file[HeaderLength+2])
	}
	if file[HeaderLength+3] != 0 {
		t.Fatalf("the flags are %d, not the zero of no copies",
			file[HeaderLength+3])
	}
	want := []int{16, 52, 88}
	for i, at := range offsets(file, 3) {
		if at != want[i] {
			t.Fatalf("column %d's data set begins at %d, not %d", i, at,
				want[i])
		}
	}
}

// R5.9: every data set begins on a long, and the pad to it is zero. Column 0
// packs to 34 bytes from offset 16, so two bytes stand before the next.
func TestEveryDataSetBeginsOnALongAndThePadBetweenIsZero(t *testing.T) {
	file, err := WriteDtx2(table(t, 3, 3, 2), standin{}, 1, 960)
	if err != nil {
		t.Fatal(err)
	}
	for i, at := range offsets(file, 3) {
		if at%4 != 0 {
			t.Fatalf("column %d's data set at %d is off a long", i, at)
		}
		if file[HeaderLength+at] != 'S' {
			t.Fatalf("column %d's data set at %d does not open with S", i, at)
		}
	}
	if pad := file[HeaderLength+50 : HeaderLength+52]; pad[0] != 0 ||
		pad[1] != 0 {
		t.Fatalf("the pad before the next data set is %v, not zero", pad)
	}
	if len(file) != HeaderLength+122 {
		t.Fatalf("the file is %d bytes, not the %d of three data sets and"+
			" the pad between them", len(file), HeaderLength+122)
	}
}

// The table is the same under every variant (R1.3), so the DTX2 file of a
// DTX0 file and of the DTX1 file of one table are the same bytes.
func TestADtx0FileAndADtx1FileOfOneTableGiveTheSameDtx2File(t *testing.T) {
	want := table(t, 3, 3, 2)
	fromZero, err := Dtx2From(WriteDtx0(want), standin{}, 1, 960)
	if err != nil {
		t.Fatal(err)
	}
	fromOne, err := Dtx2From(WriteDtx1(want), standin{}, 1, 960)
	if err != nil {
		t.Fatal(err)
	}
	if string(fromZero) != string(fromOne) {
		t.Fatal("a DTX0 file and a DTX1 file of one table gave two DTX2 files")
	}
	written, err := WriteDtx2(want, standin{}, 1, 960)
	if err != nil {
		t.Fatal(err)
	}
	if string(written) != string(fromZero) {
		t.Fatal("the table and its DTX0 file gave two DTX2 files")
	}
}

// R5.3, R5.5 and R5.6: k is 1, 2 or 4, N is 1 to 65535, and a column's bytes
// divide by k.
func TestAUnitOrRingOutsideItsBoundsIsRejected(t *testing.T) {
	built := table(t, 3, 3, 2)
	for _, one := range []struct {
		unit int
		ring int
		want string
	}{
		{3, 960, "k is 1, 2 or 4, not 3"},
		{1, 65536, "N is 1 to 65535, not 65536"},
		{1, 0, "N is 1 to 65535, not 0"},
		{4, 960, "a column is 3 times 2 bytes, which does not divide by" +
			" k of 4"},
	} {
		_, err := WriteDtx2(built, standin{}, one.unit, one.ring)
		if err == nil {
			t.Fatalf("k of %d at a ring of %d was taken", one.unit, one.ring)
		}
		if err.Error() != one.want {
			t.Fatalf("the error is %q, not %q", err, one.want)
		}
	}
}

// A payload short of what its header defines is an error on read, under DTX0
// and DTX1 alike: a reader steps to a row the file does not have.
func TestAFileShortOfWhatItsHeaderDefinesIsRejected(t *testing.T) {
	for _, one := range []struct {
		name  string
		write func(*Table) []byte
		read  func([]byte) (*Table, error)
	}{
		{"DTX0", WriteDtx0, ReadDtx0},
		{"DTX1", WriteDtx1, ReadDtx1},
	} {
		file := one.write(table(t, 3, 2, 1))
		if _, err := one.read(file[:len(file)-1]); err == nil {
			t.Fatalf("a %s file a byte short was taken", one.name)
		}
		if _, err := one.read(file[:HeaderLength]); err == nil {
			t.Fatalf("a %s file of a header alone was taken", one.name)
		}
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
