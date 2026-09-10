package main

import (
	"bytes"
	"errors"
	"os"
	"strconv"
	"strings"
	"testing"

	"github.com/odipar/dtx/go/dtx"
)

// numbers gives comma separated text of rows rows and columns columns, the
// value at row r and column i being r times i.
func numbers(rows, columns int) string {
	var out strings.Builder
	for r := 0; r < rows; r++ {
		for i := 0; i < columns; i++ {
			if i > 0 {
				out.WriteByte(',')
			}
			out.WriteString(strconv.Itoa(r * i))
		}
		out.WriteByte('\n')
	}
	return out.String()
}

// ran runs the tool over args with in on standard input, and gives what it
// wrote to standard output, what it reported on standard error, and the
// error it gave. A caller reads the three the same way.
func ran(t *testing.T, in string, args ...string) ([]byte, string, error) {
	t.Helper()
	file, err := os.CreateTemp(t.TempDir(), "said")
	if err != nil {
		t.Fatal(err)
	}
	var out bytes.Buffer
	was := os.Stderr
	os.Stderr = file
	err = run(args, strings.NewReader(in), &out)
	os.Stderr = was
	if err := file.Close(); err != nil {
		t.Fatal(err)
	}
	said, read := os.ReadFile(file.Name())
	if read != nil {
		t.Fatal(read)
	}
	return out.Bytes(), string(said), err
}

// printed runs the tool and gives what it printed to standard output, for a
// run that writes a text rather than a table.
func printed(t *testing.T, args ...string) (string, error) {
	t.Helper()
	file, err := os.CreateTemp(t.TempDir(), "said")
	if err != nil {
		t.Fatal(err)
	}
	was := os.Stdout
	os.Stdout = file
	gave := run(args, strings.NewReader(""), os.Stdout)
	os.Stdout = was
	if err := file.Close(); err != nil {
		t.Fatal(err)
	}
	said, read := os.ReadFile(file.Name())
	if read != nil {
		t.Fatal(read)
	}
	return string(said), gave
}

// Text is written as a DTX file of the variant and width given, and the
// report gives the figures of the table written.
func TestATextIsWrittenAndTheReportGivesItsFigures(t *testing.T) {
	file, said, err := ran(t, numbers(8, 2), "-v1", "-w2")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(said, "DTX1") ||
		!strings.Contains(said, "8 rows, 2 columns, width 2, RR=8") {
		t.Fatalf("the report is %q", said)
	}
	header, err := dtx.ReadHeader(file)
	if err != nil {
		t.Fatal(err)
	}
	if header.Variant != dtx.DTX1 || header.Width != 2 ||
		header.Rows != 8 || header.Columns != 2 {
		t.Fatalf("the file is DTX%d of %d rows and %d columns at a width"+
			" of %d", header.Variant, header.Rows, header.Columns,
			header.Width)
	}
}

// A DTX file is written out as text, with the comment Text writes, and that
// text reads back to the table it came from.
func TestADtxFileIsWrittenOutAsTextAndReadsBack(t *testing.T) {
	for _, width := range []int{1, 2, 4} {
		file, _, err := ran(t, "# a note\n"+numbers(8, 2), "-v1",
			"-w"+strconv.Itoa(width), "-r3")
		if err != nil {
			t.Fatal(err)
		}
		text, _, err := ran(t, string(file), "-text")
		if err != nil {
			t.Fatal(err)
		}
		want := "# 8 rows, 2 columns, width " + strconv.Itoa(width) +
			", RR 3\nc0,c1\n0,0\n0,1\n"
		if !strings.HasPrefix(string(text), want) {
			t.Fatalf("at a width of %d the text opens %q", width, text)
		}
		again, _, err := ran(t, string(text), "-v1")
		if err != nil {
			t.Fatal(err)
		}
		if !bytes.Equal(file, again) {
			t.Fatalf("at a width of %d the table did not come back through"+
				" text, with the width and repeat the comment gives", width)
		}
	}
}

// The repeat of a DTX file is changed and the variant it was read at kept.
func TestTheRepeatOfADtxFileIsChangedAndItsVariantKept(t *testing.T) {
	plain, _, err := ran(t, numbers(8, 2), "-v1")
	if err != nil {
		t.Fatal(err)
	}
	file, _, err := ran(t, string(plain), "-r2")
	if err != nil {
		t.Fatal(err)
	}
	header, err := dtx.ReadHeader(file)
	if err != nil {
		t.Fatal(err)
	}
	if header.Variant != dtx.DTX1 || header.Repeat != 2 {
		t.Fatalf("the file is DTX%d repeating at %d, not DTX1 at 2",
			header.Variant, header.Repeat)
	}
}

// A flag the tool does not read gives the tool's line and a misuse, which
// main exits 2 on, as the Java tree exits. -copiesS with letters behind it
// is one of those: a search of no seconds would pack another file.
func TestAFlagTheToolDoesNotReadGivesTheToolsLine(t *testing.T) {
	for _, one := range []struct {
		flags []string
		want  string
	}{
		{[]string{"-z"}, "dtx-write does not read -z"},
		{[]string{"-v2", "-copiesx"}, "dtx-write does not read -copiesx"},
	} {
		_, _, err := ran(t, numbers(4, 2), one.flags...)
		var m misuse
		if !errors.As(err, &m) {
			t.Fatalf("%v gave %v, not a misuse", one.flags, err)
		}
		if err.Error() != one.want {
			t.Fatalf("%v gave %q, not %q", one.flags, err, one.want)
		}
	}
}

// -text and -v together name two forms for one output, which is a misuse.
func TestTextAndAVariantTogetherAreAMisuse(t *testing.T) {
	_, _, err := ran(t, numbers(4, 2), "-v1", "-text")
	var m misuse
	if !errors.As(err, &m) {
		t.Fatalf("-v1 -text gave %v, not a misuse", err)
	}
}

// -help prints the one text and nothing else.
func TestHelpPrintsTheOneText(t *testing.T) {
	said, err := printed(t, "-help")
	if err != nil {
		t.Fatal(err)
	}
	if said != help {
		t.Fatalf("-help printed %q", said)
	}
}
