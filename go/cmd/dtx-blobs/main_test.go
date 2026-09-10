package main

import (
	"errors"
	"os"
	"testing"

	"github.com/odipar/dtx/go/dtx"
	"github.com/odipar/dtx/go/image"
	"github.com/odipar/dtx/go/pack"
)

// printed runs the tool over args and gives what it wrote to standard output
// and the error it gave. The tool prints a line a build, and a test reads
// them where a caller reads them.
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

// The table a build is assembled from is made rather than read, and every
// build takes one: a DTX0 or DTX1 file at the build's width, and a DTX2 file
// packed at its unit whose data sets carry the column itself.
func TestEveryBuildTakesASeedTableOfItsOwnShape(t *testing.T) {
	for _, build := range image.Builds() {
		file, err := seed(build)
		if err != nil {
			t.Fatalf("%s: %v", build.Name(), err)
		}
		header, err := dtx.ReadHeader(file)
		if err != nil {
			t.Fatalf("%s: %v", build.Name(), err)
		}
		if header.Variant != build.Variant {
			t.Fatalf("%s is seeded with a DTX%d file", build.Name(),
				header.Variant)
		}
		if header.Width != build.Width {
			t.Fatalf("%s is seeded at a width of %d", build.Name(),
				header.Width)
		}
		if build.Variant != dtx.DTX2 {
			continue
		}
		given, err := pack.ReadPacked(file, header)
		if err != nil {
			t.Fatalf("%s: %v", build.Name(), err)
		}
		if given.Unit != build.Unit || given.Copies != build.Copies {
			t.Fatalf("%s is seeded at a unit of %d with copies %v",
				build.Name(), given.Unit, given.Copies)
		}
	}
}

// A flag the tool does not read gives the tool's line and a misuse, which
// main exits 2 on, as the Java tree exits.
func TestAFlagTheToolDoesNotReadGivesTheToolsLine(t *testing.T) {
	_, err := printed(t, t.TempDir(), "-z")
	var m misuse
	if !errors.As(err, &m) {
		t.Fatalf("-z gave %v, not a misuse", err)
	}
	if want := "dtx-blobs does not read -z"; err.Error() != want {
		t.Fatalf("-z gave %q, not %q", err, want)
	}
}

// A run given no directory to write into gives the usage, which main prints
// to standard error and exits 2 on. -help prints the one text and nothing
// else.
func TestNoDirectoryToWriteIntoGivesTheUsageAndHelpPrintsTheOneText(
	t *testing.T) {
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
