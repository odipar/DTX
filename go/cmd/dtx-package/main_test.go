package main

import (
	"bytes"
	"errors"
	"os"
	"strings"
	"testing"

	"dtx/dtx"
	"dtx/image"
	"dtx/pack"
)

// ran runs the tool over args with in on standard input, and gives the image
// it wrote to standard output, what it reported on standard error, and the
// error it gave. A caller reads the three the same way.
func ran(t *testing.T, in []byte, args ...string) ([]byte, string, error) {
	t.Helper()
	file, err := os.CreateTemp(t.TempDir(), "said")
	if err != nil {
		t.Fatal(err)
	}
	var out bytes.Buffer
	was := os.Stderr
	os.Stderr = file
	err = run(args, bytes.NewReader(in), &out)
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
// run that writes a text rather than an image.
func printed(t *testing.T, args ...string) (string, error) {
	t.Helper()
	file, err := os.CreateTemp(t.TempDir(), "said")
	if err != nil {
		t.Fatal(err)
	}
	was := os.Stdout
	os.Stdout = file
	gave := run(args, bytes.NewReader(nil), os.Stdout)
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

// A DTX1 file of eight rows and two columns at a width of two.
func table(t *testing.T) []byte {
	t.Helper()
	column := make([][]byte, 2)
	for i := range column {
		column[i] = make([]byte, 8*2)
	}
	built, err := dtx.NewTable(8, 8, 2, column)
	if err != nil {
		t.Fatal(err)
	}
	return dtx.WriteDtx1(built)
}

// A table is packaged as an image that opens with the four slots and the
// format block, and the report gives the figures a caller reads back.
func TestATableIsPackagedAndTheReportGivesItsFigures(t *testing.T) {
	if image.Embedded() == 0 {
		t.Skip("this build does not contain images: run mvn process-classes")
	}
	built, said, err := ran(t, table(t))
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(said, "DTX1 image") ||
		!strings.Contains(said, "8 rows, 2 columns, state block 12 bytes") {
		t.Fatalf("the report is %q", said)
	}
	// doc/abi.md 1: the format block stands at +16, behind the four slots,
	// and opens with the variant the code reads.
	if string(built[pack.FormatAt:pack.FormatAt+3]) != "DTX" ||
		int(built[pack.FormatAt+3]) != dtx.DTX1 {
		t.Fatalf("the image opens %q at +16, not DTX1",
			built[pack.FormatAt:pack.FormatAt+4])
	}
	at := dtx.GetLong(built, pack.FormatAt+pack.TableAt)
	if string(built[at:at+3]) != "DTX" {
		t.Fatalf("no table at %d, where the format block puts it", at)
	}
}

// A flag the tool does not read gives the tool's line and a misuse, which
// main exits 2 on, as the Java tree exits. This tool combines and does not
// assemble, so -a and -s are two of those.
func TestAFlagTheToolDoesNotReadGivesTheToolsLine(t *testing.T) {
	for _, flag := range []string{"-z", "-a/usr/local/bin/rmac", "-s"} {
		_, _, err := ran(t, table(t), flag)
		var m misuse
		if !errors.As(err, &m) {
			t.Fatalf("%s gave %v, not a misuse", flag, err)
		}
		if want := "dtx-package does not read " + flag; err.Error() != want {
			t.Fatalf("%s gave %q, not %q", flag, err, want)
		}
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
