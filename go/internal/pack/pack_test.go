package pack

import (
	"testing"

	"dtx/internal/dtx"
	"dtx/internal/image"
)

// A header of R rows and these widths, padded to a long as SPEC.md 1 states.
func header(variant, rows, repeat int, width []int) []byte {
	out := make([]byte, dtx.HeaderLength(len(width)))
	copy(out, dtx.Magic)
	out[3] = byte(variant)
	dtx.PutLong(out, 4, rows)
	dtx.PutWord(out, 8, len(width))
	dtx.PutLong(out, 10, repeat)
	for i, w := range width {
		out[14+i] = byte(w)
	}
	return out
}

// A plain file whose payload holds no values: the packager reads the header
// and the widths and never a value.
func plain(variant, rows int, width []int) []byte {
	head := header(variant, rows, rows, width)
	size := 0
	for _, w := range width {
		size += rows * w
	}
	return append(head, make([]byte, size)...)
}

// A DTX2 file at this ring and unit. The data sets are held rather than
// packed: the packager reads a set's four stream offsets and never a byte
// of a stream.
func packed(rows int, width []int, unit, ring int) []byte {
	return packedCopies(rows, width, unit, ring, false)
}

// The same, whose payload states that its columns hold copies, R5.10.
func packedCopies(rows int, width []int, unit, ring int, copies bool) []byte {
	head := header(dtx.DTX2, rows, rows, width)
	payload := make([]byte, 4+4*len(width))
	dtx.PutWord(payload, 0, ring)
	payload[2] = byte(unit)
	if copies {
		payload[3] = Copies
	}
	for i, w := range width {
		set := len(payload)
		dtx.PutLong(payload, 4+4*i, set)
		bytes := rows * w
		container := make([]byte, 28+bytes)
		container[0], container[1], container[2] = 'S', '4', 7
		container[3] = byte(unit)
		dtx.PutLong(container, 4, bytes/unit)
		dtx.PutLong(container, 8, 28)
		dtx.PutLong(container, 12, 28+bytes)
		dtx.PutLong(container, 16, 28+bytes)
		dtx.PutLong(container, 24, ring/unit)
		payload = append(payload, container...)
	}
	return append(head, payload...)
}

func held(t *testing.T) {
	t.Helper()
	if image.Held() == 0 {
		t.Skip("this build holds no images: run mvn process-classes")
	}
}

// The format block states back what the table settles, doc/abi.md 1.
func TestTheFormatBlockStatesWhatTheTableSettles(t *testing.T) {
	held(t)
	for _, one := range []struct {
		name    string
		file    []byte
		variant int
		rows    int
		row     int
	}{
		{"DTX0", plain(dtx.DTX0, 64, []int{1, 2, 4}), 0, 64, 7},
		{"DTX1", plain(dtx.DTX1, 64, []int{1, 2, 4}), 1, 64, 7},
		{"DTX1 one column", plain(dtx.DTX1, 1, []int{1}), 1, 1, 1},
		{"DTX2", packed(64, []int{1, 2}, 1, 960), 2, 64, 3},
		{"DTX2 k=4", packed(64, []int{4, 4}, 4, 960), 2, 64, 8},
	} {
		out, err := Image(one.file)
		if err != nil {
			t.Fatalf("%s: %v", one.name, err)
		}
		if string(out[FormatAt:FormatAt+3]) != "DTX" ||
			int(out[FormatAt+3]) != one.variant {
			t.Fatalf("%s: the block opens %q", one.name, out[FormatAt:FormatAt+4])
		}
		if got := dtx.GetWord(out, FormatAt+RowBytesAt); got != one.row {
			t.Fatalf("%s: the row's bytes are %d, not %d", one.name, got, one.row)
		}
		// The table stands where the block states, and states the same variant.
		at := dtx.GetLong(out, FormatAt+TableAt)
		if string(out[at:at+3]) != "DTX" || int(out[at+3]) != one.variant {
			t.Fatalf("%s: no header at %d", one.name, at)
		}
		// The column table stands between the code and the table, and DTX0
		// has none: its two offsets then meet.
		columns := dtx.GetLong(out, FormatAt+ColumnsAt)
		if columns > at {
			t.Fatalf("%s: the column table at %d stands past the table at %d",
				one.name, columns, at)
		}
		if one.variant == dtx.DTX0 && columns != at {
			t.Fatalf("%s: DTX0 holds %d bytes of column table", one.name,
				at-columns)
		}
		if one.variant != dtx.DTX0 && columns >= at {
			t.Fatalf("%s: DTX%d holds no column table", one.name, one.variant)
		}
		if got := len(out) - at; got != len(one.file) {
			t.Fatalf("%s: %d bytes of table, not %d", one.name, got,
				len(one.file))
		}
	}
}

// The five rules of doc/abi.md 4, on the image the packager writes.
func TestAPackedImageHoldsEveryRuleOfThePeriod(t *testing.T) {
	held(t)
	file := packed(64, []int{1, 2, 4}, 1, 960)
	out, err := Image(file)
	if err != nil {
		t.Fatal(err)
	}
	p := dtx.GetWord(out, FormatAt+PeriodAt)
	n := dtx.GetWord(out, FormatAt+RingAt)
	if p < 3 {
		t.Fatalf("P is %d, below C of 3", p)
	}
	for _, w := range []int{1, 2, 4} {
		if n%(p*w) != 0 {
			t.Fatalf("N of %d does not divide by P times %d", n, w)
		}
		if n < 2*p*w {
			t.Fatalf("N of %d is below twice P times %d", n, w)
		}
	}
}

// A table too wide for a 68000 displacement does not package.
func TestATableTooWideIsRefused(t *testing.T) {
	width := make([]int, 40)
	for i := range width {
		width[i] = 1
	}
	_, err := Image(packed(64, width, 1, 960))
	if err == nil {
		t.Fatal("40 columns at a ring of 960 reaches past 32767, and packaged")
	}
}

// A table packed at one unit does not package against another decoder:
// the image would read bytes no decoder wrote.
func TestAUnitTheDecoderDoesNotDecodeIsRefused(t *testing.T) {
	held(t)
	file := packed(64, []int{1, 2}, 1, 960)
	code, err := image.Code(dtx.DTX2, 2, false) // k of 2 against a table at 1
	if err != nil {
		t.Skip(err)
	}
	head, err := dtx.ReadHeader(file)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := Combine(code, file, head); err == nil {
		t.Fatal("a k of 2 decoder took a table packed at 1")
	}
}

// The payload states whether its columns hold copies, so the image a table
// takes is the file's to settle and no word from a caller enters it.
func TestThePayloadStatesWhetherItsColumnsHoldCopies(t *testing.T) {
	held(t)
	plain, err := Image(packed(64, []int{1, 2}, 1, 960))
	if err != nil {
		t.Fatal(err)
	}
	copies, err := Image(packedCopies(64, []int{1, 2}, 1, 960, true))
	if err != nil {
		t.Fatal(err)
	}
	if len(plain) == len(copies) {
		t.Fatalf("both images are %d bytes: the copy code is in neither or"+
			" in both", len(plain))
	}
}

// R5.2: the k a payload states and the k in every data set's own signature
// are the same, and the packager checks one against the other.
func TestADataSetThatDoesNotStateThePayloadsUnitIsRefused(t *testing.T) {
	file := packed(64, []int{1, 2}, 1, 960)
	head, err := dtx.ReadHeader(file)
	if err != nil {
		t.Fatal(err)
	}
	at := head.Length + dtx.GetLong(file, head.Length+4+4)
	file[at+3] = 2 // column 1 now states a unit of 2
	if _, err := ReadPacked(file, head); err == nil {
		t.Fatal("a payload took a data set stating another unit")
	}
	file[at+3] = 1
	file[at+2] = 6 // and the format version before this one
	if _, err := ReadPacked(file, head); err == nil {
		t.Fatal("a payload took a data set of another format version")
	}
}
