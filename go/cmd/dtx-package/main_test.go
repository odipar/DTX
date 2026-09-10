package main

import (
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"dtx/dtx"
	"dtx/image"
	"dtx/pack"
)

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

// A DTX1 file of eight rows and two columns at a width of two, written into
// work.
func table(t *testing.T, work string) string {
	t.Helper()
	column := make([][]byte, 2)
	for i := range column {
		column[i] = make([]byte, 8*2)
	}
	built, err := dtx.NewTable(8, 8, 2, column)
	if err != nil {
		t.Fatal(err)
	}
	at := filepath.Join(work, "t.dtx")
	if err := os.WriteFile(at, dtx.WriteDtx1(built), 0o644); err != nil {
		t.Fatal(err)
	}
	return at
}

// A table is packaged as an image that opens with the four slots and the
// format block, and the line gives the figures a caller reads back.
func TestATableIsPackagedAndTheLineGivesItsFigures(t *testing.T) {
	if image.Embedded() == 0 {
		t.Skip("this build does not contain images: run mvn process-classes")
	}
	work := t.TempDir()
	in := table(t, work)
	out := filepath.Join(work, "t.bin")
	said, err := printed(t, in, out)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(said, "DTX1 image") ||
		!strings.Contains(said, "8 rows, 2 columns, state block 12 bytes") {
		t.Fatalf("the line is %q", said)
	}
	built, err := os.ReadFile(out)
	if err != nil {
		t.Fatal(err)
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
	work := t.TempDir()
	in := table(t, work)
	out := filepath.Join(work, "t.bin")
	for _, flag := range []string{"-z", "-a/usr/local/bin/rmac", "-s"} {
		_, err := printed(t, in, out, flag)
		var m misuse
		if !errors.As(err, &m) {
			t.Fatalf("%s gave %v, not a misuse", flag, err)
		}
		if want := "dtx-package does not read " + flag; err.Error() != want {
			t.Fatalf("%s gave %q, not %q", flag, err, want)
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
