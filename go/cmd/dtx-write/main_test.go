package main

import (
	"errors"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"

	"dtx/dtx"
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

// printed runs the tool over args and gives what it wrote to standard output
// and the error it gave. The tool prints one line a run, and a test reads it
// where a caller reads it.
func printed(t *testing.T, args ...string) (string, error) {
	t.Helper()
	file, err := os.CreateTemp(t.TempDir(), "said")
	if err != nil {
		t.Fatal(err)
	}
	was := os.Stdout
	os.Stdout = file
	ran := run(args)
	os.Stdout = was
	if err := file.Close(); err != nil {
		t.Fatal(err)
	}
	said, err := os.ReadFile(file.Name())
	if err != nil {
		t.Fatal(err)
	}
	return string(said), ran
}

// A text file is written as a DTX file of the variant and width given, and
// the line gives the figures of the table written.
func TestATextFileIsWrittenAndTheLineGivesItsFigures(t *testing.T) {
	work := t.TempDir()
	text := filepath.Join(work, "t.csv")
	if err := os.WriteFile(text, []byte(numbers(8, 2)), 0o644); err != nil {
		t.Fatal(err)
	}
	out := filepath.Join(work, "t.dtx")
	said, err := printed(t, text, out, "-v1", "-w2")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(said, "DTX1") ||
		!strings.Contains(said, "8 rows, 2 columns, width 2, RR=8") {
		t.Fatalf("the line is %q", said)
	}
	file, err := os.ReadFile(out)
	if err != nil {
		t.Fatal(err)
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
	work := t.TempDir()
	text := filepath.Join(work, "t.csv")
	if err := os.WriteFile(text, []byte("# a note\n"+numbers(8, 2)),
		0o644); err != nil {
		t.Fatal(err)
	}
	for _, width := range []int{1, 2, 4} {
		at := "w" + strconv.Itoa(width)
		file := filepath.Join(work, "p-"+at+".dtx")
		if _, err := printed(t, text, file, "-v1", "-w"+strconv.Itoa(width),
			"-r3"); err != nil {
			t.Fatal(err)
		}
		out := filepath.Join(work, "out-"+at+".csv")
		if _, err := printed(t, file, out); err != nil {
			t.Fatal(err)
		}
		said, err := os.ReadFile(out)
		if err != nil {
			t.Fatal(err)
		}
		want := "# 8 rows, 2 columns, width " + strconv.Itoa(width) +
			", RR 3\nc0,c1\n0,0\n0,1\n"
		if !strings.HasPrefix(string(said), want) {
			t.Fatalf("at a width of %d the text opens %q", width, said)
		}
		back := filepath.Join(work, "back-"+at+".dtx")
		if _, err := printed(t, out, back, "-v1"); err != nil {
			t.Fatal(err)
		}
		first, err := os.ReadFile(file)
		if err != nil {
			t.Fatal(err)
		}
		again, err := os.ReadFile(back)
		if err != nil {
			t.Fatal(err)
		}
		if string(first) != string(again) {
			t.Fatalf("at a width of %d the table did not come back through"+
				" text, with the width and repeat the comment gives", width)
		}
	}
}

// The repeat of a DTX file is changed and the variant it was read at kept.
func TestTheRepeatOfADtxFileIsChangedAndItsVariantKept(t *testing.T) {
	work := t.TempDir()
	text := filepath.Join(work, "t.csv")
	if err := os.WriteFile(text, []byte(numbers(8, 2)), 0o644); err != nil {
		t.Fatal(err)
	}
	plain := filepath.Join(work, "p.dtx")
	if _, err := printed(t, text, plain, "-v1"); err != nil {
		t.Fatal(err)
	}
	repeating := filepath.Join(work, "r.dtx")
	if _, err := printed(t, plain, repeating, "-r2"); err != nil {
		t.Fatal(err)
	}
	file, err := os.ReadFile(repeating)
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
	work := t.TempDir()
	text := filepath.Join(work, "t.csv")
	if err := os.WriteFile(text, []byte(numbers(4, 2)), 0o644); err != nil {
		t.Fatal(err)
	}
	out := filepath.Join(work, "t.dtx")
	for _, one := range []struct {
		flags []string
		want  string
	}{
		{[]string{"-z"}, "dtx-write does not read -z"},
		{[]string{"-v2", "-copiesx"}, "dtx-write does not read -copiesx"},
	} {
		args := append([]string{text, out}, one.flags...)
		_, err := printed(t, args...)
		var m misuse
		if !errors.As(err, &m) {
			t.Fatalf("%v gave %v, not a misuse", one.flags, err)
		}
		if err.Error() != one.want {
			t.Fatalf("%v gave %q, not %q", one.flags, err, one.want)
		}
	}
}

// A run given no file to work on gives the usage, which main prints to
// standard error and exits 2 on. -help prints the one text and nothing else.
func TestNoFileToWorkOnGivesTheUsageAndHelpPrintsTheOneText(t *testing.T) {
	if _, err := printed(t); !errors.Is(err, errUsage) {
		t.Fatalf("no argument gave %v, not the usage", err)
	}
	said, err := printed(t, "-help")
	if err != nil {
		t.Fatal(err)
	}
	if said != help {
		t.Fatalf("-help printed %q", said)
	}
}
