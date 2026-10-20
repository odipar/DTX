package dtx

import (
	"bytes"
	"testing"

	"github.com/odipar/dtx/go/st4"
)

// mixedTable returns tableAt's table with each column in constant one value,
// its number times 3 in every byte.
func mixedTable(t *testing.T, rows, repeat, columns, width int,
	constant map[int]bool) *Table {
	t.Helper()
	column := make([][]byte, columns)
	for i := range column {
		column[i] = make([]byte, rows*width)
		for at := range column[i] {
			if constant[i] {
				column[i][at] = byte(3 * i)
			} else {
				column[i][at] = byte(i*31 + at)
			}
		}
	}
	built, err := NewTable(rows, repeat, width, column)
	if err != nil {
		t.Fatal(err)
	}
	return built
}

func sameTable(t *testing.T, name string, want, got *Table) {
	t.Helper()
	if got.Rows() != want.Rows() || got.Repeat() != want.Repeat() ||
		got.Width() != want.Width() || got.Columns() != want.Columns() {
		t.Fatalf("%s: the shape differs", name)
	}
	for i := 0; i < want.Columns(); i++ {
		if !bytes.Equal(got.Column(i), want.Column(i)) {
			t.Fatalf("%s: column %d differs", name, i)
		}
	}
}

// SPEC.md 2.4: N, k, the flags, L, the map and the constant values.
func TestADtx3PayloadDefinesTheMapAndTheValues(t *testing.T) {
	want := mixedTable(t, 64, 64, 3, 2, map[int]bool{1: true})
	file, err := WriteDtx3(want, []Packer{st4.Packer{}}, 2, 960)
	if err != nil {
		t.Fatal(err)
	}
	p := HeaderLength
	if file[3] != DTX3 || GetWord(file, p) != 960 || file[p+2] != 2 ||
		file[p+3] != 0 || GetWord(file, p+4) != 2 || GetWord(file, p+6) != 0 {
		t.Fatalf("the payload opens % x", file[p:p+8])
	}
	if !bytes.Equal(file[p+8:p+11], []byte{0, Constant, 1}) {
		t.Fatalf("the map is % x", file[p+8:p+11])
	}
	if !bytes.Equal(file[p+11:p+13], []byte{3, 3}) {
		t.Fatalf("column 1's value is % x", file[p+11:p+13])
	}
	got, err := Read(file)
	if err != nil {
		t.Fatal(err)
	}
	sameTable(t, "DTX3", want, got)
}

// A table of constant columns alone has L of 0 and no stream record.
func TestEveryColumnConstantHasNoSlot(t *testing.T) {
	want := mixedTable(t, 6, 6, 2, 1, map[int]bool{0: true, 1: true})
	file, err := WriteDtx3(want, []Packer{st4.Packer{}}, 1, 960)
	if err != nil {
		t.Fatal(err)
	}
	if GetWord(file, HeaderLength+4) != 0 || len(file) != HeaderLength+12 {
		t.Fatalf("L is %d and the file %d bytes", GetWord(file, HeaderLength+4),
			len(file))
	}
	got, err := Read(file)
	if err != nil {
		t.Fatal(err)
	}
	sameTable(t, "every column constant", want, got)
}

// Every unit, a repeat, and a loop longer than the ring read back.
func TestADtx3FileReadsBackToTheTableItWasWrittenFrom(t *testing.T) {
	for _, one := range []struct {
		name                            string
		rows, repeat, width, unit, ring int
	}{
		{"k of 1", 64, 64, 1, 1, 960},
		{"k of 2", 64, 64, 2, 2, 960},
		{"k of 4", 64, 64, 4, 4, 960},
		{"a repeat", 64, 16, 1, 1, 960},
		{"a replayed loop", 512, 0, 1, 1, 64},
	} {
		want := mixedTable(t, one.rows, one.repeat, 4, one.width,
			map[int]bool{2: true})
		file, err := WriteDtx3(want, []Packer{st4.Packer{},
			st4.Packer{CopiesFlag: true}}, one.unit, one.ring)
		if err != nil {
			t.Fatalf("%s: %v", one.name, err)
		}
		got, err := Read(file)
		if err != nil {
			t.Fatalf("%s: %v", one.name, err)
		}
		sameTable(t, one.name, want, got)
		replayed := file[HeaderLength+3]&ReplayedDtx3 != 0
		if replayed != (one.name == "a replayed loop") {
			t.Fatalf("%s: the replayed bit is %v", one.name, replayed)
		}
	}
}

// A payload that breaks its rules is an error, naming the rule.
func TestADtx3PayloadThatBreaksItsRulesIsAnError(t *testing.T) {
	want := mixedTable(t, 64, 64, 3, 2, map[int]bool{1: true})
	file, err := WriteDtx3(want, []Packer{st4.Packer{}}, 2, 960)
	if err != nil {
		t.Fatal(err)
	}
	order := append([]byte{}, file...)
	order[HeaderLength+10] = 0
	if _, err := Read(order); err == nil || err.Error() != "column 2 maps to"+
		" slot 0, and the live columns' slots are 0 to L minus one in column"+
		" order" {
		t.Fatalf("a map out of order reads %v", err)
	}
	many := append([]byte{}, file...)
	PutWord(many, HeaderLength+4, 4)
	if _, err := Read(many); err == nil ||
		err.Error() != "L is 0 to C of 3, not 4" {
		t.Fatalf("an L above C reads %v", err)
	}
	if _, err := WriteDtx3(want, []Packer{st4.Packer{}}, 2, 961); err == nil ||
		err.Error() != "N is a multiple of k from 2 to 32512, not 961" {
		t.Fatalf("a ring off the unit reads %v", err)
	}
}
