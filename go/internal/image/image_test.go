package image

import (
	"os"
	"path/filepath"
	"testing"
)

// A build with the images has all eight, and each is the bytes
// the Maven build wrote. A tree built without them contains none, which is
// the other whole state: half of them would be a build gone wrong.
func TestContainsEveryImageOrNone(t *testing.T) {
	embedded := Embedded()
	if embedded == 0 {
		t.Skip("this build does not contain images: run mvn process-classes")
	}
	if embedded != len(Builds()) {
		t.Fatalf("holds %d images, not the %d there are", embedded, len(Builds()))
	}
	for _, build := range Builds() {
		bytes := Read(build.Variant, build.Unit, build.Copies)
		name := Name(build.Variant, build.Unit, build.Copies)
		if len(bytes) < 24+24 {
			t.Fatalf("%s is %d bytes, too few to hold a format block",
				name, len(bytes))
		}
		// doc/abi.md 1: the format block stands at +24 and opens with the
		// variant this image reads.
		if string(bytes[24:27]) != "DTX" || int(bytes[27]) != build.Variant {
			t.Fatalf("%s opens %q at +24, not DTX%d",
				name, bytes[24:28], build.Variant)
		}
		if got := int(bytes[24+18]); got != build.Unit {
			t.Fatalf("%s decodes at a unit of %d, not %d", name, got, build.Unit)
		}
	}
}

// What the executable contains and what a release attaches are the same
// bytes: the Maven build writes both, and a release that shipped others
// would be two readers of one table.
func TestTheEmbeddedImagesAreWhatTheBuildReleases(t *testing.T) {
	if Embedded() == 0 {
		t.Skip("this build does not contain images: run mvn process-classes")
	}
	loose := filepath.Join("..", "..", "..", "build", "68k")
	if _, err := os.Stat(loose); err != nil {
		t.Skip("no build/68k: run mvn process-classes")
	}
	for _, build := range Builds() {
		name := Name(build.Variant, build.Unit, build.Copies)
		want, err := os.ReadFile(filepath.Join(loose, name))
		if err != nil {
			t.Fatalf("no %s in build/68k: %v", name, err)
		}
		got := Read(build.Variant, build.Unit, build.Copies)
		if string(got) != string(want) {
			t.Fatalf("%s is %d bytes carried and %d released",
				name, len(got), len(want))
		}
	}
}
