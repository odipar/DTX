package pack

import (
	"strings"
	"testing"

	"dtx/dtx"
	"dtx/image"
)

// A header of R rows and C columns at this width, SPEC.md 1.
func header(variant, rows, repeat, columns, width int) []byte {
	out := make([]byte, dtx.HeaderLength)
	copy(out, dtx.Magic)
	out[3] = byte(variant)
	dtx.PutLong(out, 4, rows)
	dtx.PutWord(out, 8, columns)
	dtx.PutLong(out, 10, repeat)
	out[14] = byte(width)
	return out
}

// A plain file whose payload does not have values: the packager reads the
// header and never a value.
func plain(variant, rows, columns, width int) []byte {
	head := header(variant, rows, rows, columns, width)
	size := rows * columns * width
	if variant == dtx.DTX1 {
		size = dtx.PayloadLengthDtx1(rows, columns, width)
	}
	return append(head, make([]byte, size)...)
}

// A DTX2 file at this ring and unit. The data sets are stored rather than
// packed: the packager reads a set's four stream offsets and never a byte
// of a stream.
func packed(rows, columns, width, unit, ring int) []byte {
	return packedCopies(rows, columns, width, unit, ring, false)
}

// The same, whose payload defines that its columns contain copies, R5.10.
func packedCopies(rows, columns, width, unit, ring int, copies bool) []byte {
	return sets(rows, rows, columns, width, unit, ring, copies, -1)
}

// The same at this repeat, whose sets record rewind as their loop: a rewind
// of -1 loops a set by its end marker and any other replays its pass.
func sets(rows, repeat, columns, width, unit, ring int, copies bool,
	rewind int) []byte {
	head := header(dtx.DTX2, rows, repeat, columns, width)
	payload := make([]byte, 4+4*columns)
	dtx.PutWord(payload, 0, ring)
	payload[2] = byte(unit)
	if copies {
		payload[3] = dtx.CopiesFlag
	}
	bytes := rows * width
	for i := 0; i < columns; i++ {
		at := len(payload)
		dtx.PutLong(payload, 4+4*i, at)
		set := make([]byte, 28+bytes)
		set[0], set[1], set[2] = 'S', '4', 7
		set[3] = byte(unit)
		dtx.PutLong(set, 4, bytes/unit)
		// Three offsets apart, so a record that carried B where C stands
		// fails rather than passing on equal values.
		dtx.PutLong(set, 8, 28+bytes/4)
		dtx.PutLong(set, 12, 28+bytes/2)
		dtx.PutLong(set, 16, 28+bytes)
		dtx.PutLong(set, 20, rewind)
		dtx.PutLong(set, 24, ring/unit)
		payload = append(payload, set...)
	}
	return append(head, payload...)
}

func needsImages(t *testing.T) {
	t.Helper()
	if image.Embedded() == 0 {
		t.Skip("this build does not contain images: run mvn process-classes")
	}
}

// The format block defines what the table gives, doc/abi.md 1.
func TestTheFormatBlockDefinesWhatTheTableFixes(t *testing.T) {
	needsImages(t)
	for _, one := range []struct {
		name    string
		file    []byte
		variant int
		row     int
	}{
		{"DTX0", plain(dtx.DTX0, 64, 3, 2), 0, 6},
		{"DTX0 at a width of 1", plain(dtx.DTX0, 64, 3, 1), 0, 3},
		{"DTX1", plain(dtx.DTX1, 64, 3, 2), 1, 6},
		{"DTX1 one column at a width of 4", plain(dtx.DTX1, 1, 1, 4), 1, 4},
		{"DTX2", packed(64, 2, 1, 1, 960), 2, 2},
		{"DTX2 k=4", packed(64, 2, 4, 4, 960), 2, 8},
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
		// The table stands where the block puts it, and defines the same variant.
		at := dtx.GetLong(out, FormatAt+TableAt)
		if string(out[at:at+3]) != "DTX" || int(out[at+3]) != one.variant {
			t.Fatalf("%s: no header at %d", one.name, at)
		}
		// The column table stands between the code and the table, and a
		// plain variant does not have one: its two offsets then meet.
		columns := dtx.GetLong(out, FormatAt+ColumnsAt)
		if columns > at {
			t.Fatalf("%s: the column table at %d stands past the table at %d",
				one.name, columns, at)
		}
		if one.variant != dtx.DTX2 && columns != at {
			t.Fatalf("%s: DTX%d is %d bytes of column table", one.name,
				one.variant, at-columns)
		}
		if one.variant == dtx.DTX2 && columns >= at {
			t.Fatalf("%s: DTX2 does not contain a column table", one.name)
		}
		if got := len(out) - at; got != len(one.file) {
			t.Fatalf("%s: %d bytes of table, not %d", one.name, got,
				len(one.file))
		}
	}
}

// The stride a caller adds to reach the next column's value, which
// DTX_metadata gives out of the format block at +24.
func TestTheStrideReachesTheNextColumnsValue(t *testing.T) {
	needsImages(t)
	for _, one := range []struct {
		name   string
		file   []byte
		stride int
	}{
		// DTX0 lays a row's values one after another, so the next column's
		// value stands one width on.
		{"DTX0 strides by the width", plain(dtx.DTX0, 64, 3, 2), 2},
		{"DTX0 at a width of 4", plain(dtx.DTX0, 64, 3, 4), 4},
		// DTX1 lays a column's values together, so the next column stands a
		// column's length on: R times the width, up to a word.
		{"DTX1 strides by a column's length", plain(dtx.DTX1, 64, 3, 2), 128},
		{"DTX1 at an odd R of one byte values", plain(dtx.DTX1, 3, 3, 1), 4},
		// DTX2 unpacks each column through its own ring of N bytes.
		{"DTX2 strides by N", packed(64, 2, 2, 1, 960), 960},
	} {
		head, err := dtx.ReadHeader(one.file)
		if err != nil {
			t.Fatal(err)
		}
		var given Packed
		if head.Variant == dtx.DTX2 {
			if given, err = ReadPacked(one.file, head); err != nil {
				t.Fatal(err)
			}
		}
		if got := Stride(head, given); got != one.stride {
			t.Fatalf("%s: the stride is %d, not %d", one.name, got, one.stride)
		}
		out, err := Image(one.file)
		if err != nil {
			t.Fatalf("%s: %v", one.name, err)
		}
		if got := dtx.GetLong(out, FormatAt+StrideAt); got != one.stride {
			t.Fatalf("%s: the block gives a stride of %d, not %d",
				one.name, got, one.stride)
		}
	}
}

// The six fields a combine writes, as Blank and the tests below read them.
var fields = []struct {
	name string
	at   int
	long bool
}{
	{"the state block's bytes", StateAt, true},
	{"the table", TableAt, true},
	{"the row's bytes", RowBytesAt, false},
	{"P", PeriodAt, false},
	{"N", RingAt, false},
	{"the stride", StrideAt, true},
}

// field gives what one of the six reads in code.
func field(code []byte, at int, long bool) int {
	if long {
		return dtx.GetLong(code, FormatAt+at)
	}
	return dtx.GetWord(code, FormatAt+at)
}

// Blank zeroes the six fields a combine writes. A package writes all six, so
// blanking one of those is the run where a field left out would show.
func TestBlankZeroesEveryFieldACombineWrites(t *testing.T) {
	needsImages(t)
	code, err := Image(packed(64, 2, 2, 1, 960))
	if err != nil {
		t.Fatal(err)
	}
	for _, one := range fields {
		if field(code, one.at, one.long) == 0 {
			t.Fatalf("%s is zero before the blank, so a blank that left it"+
				" alone would pass", one.name)
		}
	}
	Blank(code)
	for _, one := range fields {
		if got := field(code, one.at, one.long); got != 0 {
			t.Fatalf("%s is %d, not zero", one.name, got)
		}
	}
}

// The carried code was blanked where it was built, so the six read zero in
// the file itself: code shipped without a combine reads a state block of
// zero bytes rather than some other table's.
func TestTheCarriedCodeReadsZeroForEveryFieldACombineWrites(t *testing.T) {
	needsImages(t)
	code := image.Read(dtx.DTX2, 2, 1, false)
	if code == nil {
		t.Skip("this build does not contain DTX2-w2-k1.bin")
	}
	for _, one := range fields {
		if got := field(code, one.at, one.long); got != 0 {
			t.Fatalf("%s is %d, not zero", one.name, got)
		}
	}
}

// The state block is the head and one pointer at every width and every C,
// under DTX0 and DTX1 alike: one width covers the whole table, so one pointer
// walks every column of it. The figure is read back out of the image, where
// a caller reads it.
func TestTheStateBlockIsTheSameAtEveryWidth(t *testing.T) {
	if got := StateBytes(); got != 12 {
		t.Fatalf("the head and one pointer are %d bytes, not 12", got)
	}
	needsImages(t)
	for _, width := range []int{1, 2, 4} {
		for _, variant := range []int{dtx.DTX0, dtx.DTX1} {
			for _, columns := range []int{1, 3} {
				out, err := Image(plain(variant, 2, columns, width))
				if err != nil {
					t.Fatal(err)
				}
				if got := dtx.GetLong(out, FormatAt+StateAt); got != 12 {
					t.Fatalf("DTX%d of %d columns at a width of %d takes %d"+
						" bytes, not 12", variant, columns, width, got)
				}
			}
		}
	}
}

// The figures define what the image cannot read back: R, C and RR reach the
// code out of the table's own header, and what is left is the width, the
// row's bytes and the state block, and under DTX2 the period, N, the unit
// and the copy code.
func TestTheFiguresDefineOnlyWhatTheImageCannotReadBack(t *testing.T) {
	for _, width := range []int{1, 2, 4} {
		for _, file := range [][]byte{
			plain(dtx.DTX0, 4, 3, width),
			plain(dtx.DTX1, 4, 3, width),
			packed(64, 3, width, 1, 960),
		} {
			out, err := Figures(file)
			if err != nil {
				t.Fatal(err)
			}
			for _, want := range []string{
				equ("DTX_WIDTH", width),
				equ("DTX_ROWBYTES", 3*width),
			} {
				if !strings.Contains(out, want) {
					t.Fatalf("the figures do not define %q:\n%s", want, out)
				}
			}
			if !strings.Contains(out, "DTX_STATE\tequ\t") {
				t.Fatalf("the figures do not define the state block:\n%s", out)
			}
			for _, gone := range []string{"DTX_ROWS", "DTX_COLUMNS",
				"DTX_REPEAT", "DTX_VARIANT", "DTX_HEADER"} {
				if strings.Contains(out, gone+"\tequ\t") {
					t.Fatalf("the figures define %s, so the code moves with"+
						" the table:\n%s", gone, out)
				}
			}
		}
	}
}

// The packed figures define the period, the ring and the unit.
func TestThePackedFiguresDefineThePeriodTheRingAndTheUnit(t *testing.T) {
	out, err := Figures(packed(64, 2, 2, 1, 960))
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{"DTX_PERIOD\tequ\t2", "DTX_N\t\tequ\t960",
		"ST4_UNIT\tequ\t1"} {
		if !strings.Contains(out, want) {
			t.Fatalf("the figures do not define %q:\n%s", want, out)
		}
	}
}

// A packed column table is one stream record a column and nothing else.
func TestAPackedColumnTableContainsAStreamRecordAColumn(t *testing.T) {
	file := packed(64, 2, 2, 1, 960)
	head, err := dtx.ReadHeader(file)
	if err != nil {
		t.Fatal(err)
	}
	table, err := ColumnTable(file, head)
	if err != nil {
		t.Fatal(err)
	}
	if len(table) != 4*4*head.Columns {
		t.Fatalf("the column table is %d bytes, not the four longs a column"+
			" that are the whole of it", len(table))
	}
	payload := head.Length()
	for i := 0; i < head.Columns; i++ {
		at := Stream * i
		set := dtx.GetLong(file, payload+4+4*i)
		if got := dtx.GetLong(table, at); got != set+28 {
			t.Fatalf("column %d's stream A is %d, not the %d past the data"+
				" set's header", i, got, set+28)
		}
		for stream := 1; stream < 4; stream++ {
			want := set + dtx.GetLong(file, payload+set+4+4*stream)
			if got := dtx.GetLong(table, at+4*stream); got != want {
				t.Fatalf("column %d's stream %c is %d, not %d",
					i, 'A'+stream, got, want)
			}
		}
	}
}

// A plain column table is empty: every column is one width, so a pointer and
// a stride reach them all.
func TestAPlainColumnTableIsEmpty(t *testing.T) {
	for _, variant := range []int{dtx.DTX0, dtx.DTX1} {
		for _, width := range []int{1, 2, 4} {
			file := plain(variant, 4, 3, width)
			head, err := dtx.ReadHeader(file)
			if err != nil {
				t.Fatal(err)
			}
			table, err := ColumnTable(file, head)
			if err != nil {
				t.Fatal(err)
			}
			if len(table) != 0 {
				t.Fatalf("DTX%d at a width of %d is %d bytes of column table",
					variant, width, len(table))
			}
		}
	}
}

// The period is the smallest that meets every rule of doc/abi.md 4.
func TestThePeriodIsTheSmallestThatMeetsEveryRule(t *testing.T) {
	for _, one := range []struct {
		name string
		file []byte
		want int
	}{
		{"P is C where C meets the rules", packed(64, 2, 1, 1, 960), 2},
		{"P is C at three columns of four bytes", packed(48, 3, 4, 1, 960), 3},
		// N divides by P times the width, so a C that does not divide N
		// takes the first period above C that does: 960 by 7 leaves 1.
		{"the first period above C of 7 that divides N",
			packed(64, 7, 1, 1, 960), 8},
		// A column's ring stands its own number times N from the first, so
		// no displacement bounds C: forty columns package.
		{"P is C at forty columns", packed(64, 40, 1, 1, 960), 40},
	} {
		head, err := dtx.ReadHeader(one.file)
		if err != nil {
			t.Fatal(err)
		}
		given, err := ReadPacked(one.file, head)
		if err != nil {
			t.Fatal(err)
		}
		period, err := Period(head, given)
		if err != nil {
			t.Fatalf("%s: %v", one.name, err)
		}
		if period != one.want {
			t.Fatalf("%s: P is %d, not %d", one.name, period, one.want)
		}
	}
}

// A ring no period divides does not package. A period needs a ring of 2P
// times the width, so at a width of four the smallest period needs eight
// bytes, and P is at least C of two.
func TestARingTheWidthDoesNotDivideIsRefused(t *testing.T) {
	file := packed(64, 2, 4, 1, 6)
	head, err := dtx.ReadHeader(file)
	if err != nil {
		t.Fatal(err)
	}
	given, err := ReadPacked(file, head)
	if err != nil {
		t.Fatal(err)
	}
	_, err = Period(head, given)
	if err == nil {
		t.Fatal("a ring of six bytes took a period")
	}
	if !strings.HasPrefix(err.Error(), "no period from C of 2 up") {
		t.Fatalf("the error is %q", err)
	}
}

// A packed state block is the head, a decoder state a column and a ring a
// column, doc/abi.md 3.
func TestAPackedStateBlockContainsADecoderStateAndARingAColumn(t *testing.T) {
	file := packed(64, 2, 2, 1, 960)
	head, err := dtx.ReadHeader(file)
	if err != nil {
		t.Fatal(err)
	}
	given, err := ReadPacked(file, head)
	if err != nil {
		t.Fatal(err)
	}
	if got := Decoders(); got != 72 {
		t.Fatalf("the decoder states stand at %d, not the 72 that 68k/DTX2.S"+
			" puts them at", got)
	}
	if got, err := Ring(head, given); err != nil || got != 72+48*2 {
		t.Fatalf("the rings stand at %d, not behind two decoder states of 48"+
			" bytes, one a turn (%v)", got, err)
	}
	if got, err := PackedStateBytes(head, given); err != nil || got != 72+48*2+2*960 {
		t.Fatalf("the block is %d bytes, not a ring a column (%v)", got, err)
	}
}

// A payload whose sets record a loop is replayed, and its block takes a
// second decoder state a column, where the reader puts the registers away at
// the row the loop begins, doc/abi.md 3.
func TestAReplayedPayloadTakesASecondDecoderStateAColumn(t *testing.T) {
	file := sets(64, 0, 2, 2, 1, 960, false, 0)
	head, err := dtx.ReadHeader(file)
	if err != nil {
		t.Fatal(err)
	}
	given, err := ReadPacked(file, head)
	if err != nil {
		t.Fatal(err)
	}
	if !given.Replayed {
		t.Fatal("a payload whose sets record a loop was not replayed")
	}
	if got, err := PackedStateBytes(head, given); err != nil || got != 72+48*2+2*960+32*2 {
		t.Fatalf("the block is %d bytes, not a ring, a decoder state and a"+
			" copy of the registers a column (%v)", got, err)
	}
}

// The reader puts a replayed set's registers away and takes them back at
// the exact row, splitting the refill the row falls inside, so the period
// is the same for every RR and R, doc/abi.md 4.
func TestAReplayedPayloadTakesThePeriodForEveryRepeat(t *testing.T) {
	for _, one := range []struct {
		name   string
		file   []byte
		period int
	}{
		{"P is C where the sets loop by their end marker",
			sets(64, 16, 3, 2, 1, 960, false, -1), 3},
		{"P is C where the pass is replayed from a row it does not divide",
			sets(64, 16, 3, 2, 1, 960, false, 32), 3},
		{"P is C where the pass is replayed from row 3 of 64",
			sets(64, 3, 2, 2, 1, 960, false, 6), 2},
	} {
		head, err := dtx.ReadHeader(one.file)
		if err != nil {
			t.Fatal(err)
		}
		given, err := ReadPacked(one.file, head)
		if err != nil {
			t.Fatal(err)
		}
		period, err := Period(head, given)
		if err != nil {
			t.Fatalf("%s: %v", one.name, err)
		}
		if period != one.period {
			t.Fatalf("%s: P is %d, not %d", one.name, period, one.period)
		}
	}
}

// A refill meets one mark at most, so a replayed loop is a period long at
// least, doc/abi.md 4: thirty columns give P of 30, and a loop of 20 rows
// from row 20 of 40 fails the package.
func TestAReplayedLoopUnderThePeriodIsRefused(t *testing.T) {
	file := sets(40, 20, 30, 1, 1, 960, false, 20)
	head, err := dtx.ReadHeader(file)
	if err != nil {
		t.Fatal(err)
	}
	given, err := ReadPacked(file, head)
	if err != nil {
		t.Fatal(err)
	}
	if _, err = Period(head, given); err == nil {
		t.Fatal("a replayed loop of 20 rows under a period of 30 took a period")
	}
	if !strings.HasPrefix(err.Error(), "a replayed loop of 20 rows is under the period of 30") {
		t.Fatalf("the error is %q", err)
	}
}

// A table packed at one unit does not package against another decoder:
// the image would read bytes that no decoder wrote.
func TestAUnitTheDecoderDoesNotDecodeIsRefused(t *testing.T) {
	needsImages(t)
	file := packed(64, 2, 2, 1, 960)
	code, err := image.Code(dtx.DTX2, 2, 2, false) // k of 2 against a table at 1
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

// A table of one width does not package against code built for another: a
// read moves a value in one instruction, and that instruction is the code's.
func TestAWidthTheCodeDoesNotReadIsRefused(t *testing.T) {
	needsImages(t)
	file := plain(dtx.DTX1, 64, 2, 2)
	code, err := image.Code(dtx.DTX1, 4, 0, false)
	if err != nil {
		t.Skip(err)
	}
	head, err := dtx.ReadHeader(file)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := Combine(code, file, head); err == nil {
		t.Fatal("code of four byte values took a table of two byte ones")
	}
}

// The payload defines whether its columns contain copies, so the file fixes
// the image a table takes and no word from a caller enters it.
func TestThePayloadDefinesWhetherItsColumnsContainCopies(t *testing.T) {
	needsImages(t)
	plain, err := Image(packed(64, 2, 2, 1, 960))
	if err != nil {
		t.Fatal(err)
	}
	copies, err := Image(packedCopies(64, 2, 2, 1, 960, true))
	if err != nil {
		t.Fatal(err)
	}
	if len(plain) == len(copies) {
		t.Fatalf("both images are %d bytes: the copy code is in neither or"+
			" in both", len(plain))
	}
}

// R5.2: the k a payload defines and the k in every data set's own signature
// are the same, and the packager checks one against the other.
func TestADataSetThatDoesNotDefineThePayloadsUnitIsRefused(t *testing.T) {
	file := packed(64, 2, 2, 1, 960)
	head, err := dtx.ReadHeader(file)
	if err != nil {
		t.Fatal(err)
	}
	at := head.Length() + dtx.GetLong(file, head.Length()+4+4)
	file[at+3] = 2 // column 1 now defines a unit of 2
	if _, err := ReadPacked(file, head); err == nil {
		t.Fatal("a payload took a data set defining another unit")
	}
	file[at+3] = 1
	file[at+2] = 6 // and the format version before this one
	if _, err := ReadPacked(file, head); err == nil {
		t.Fatal("a payload took a data set of another format version")
	}
}
