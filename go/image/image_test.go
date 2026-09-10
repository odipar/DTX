package image

import (
	"os"
	"path/filepath"
	"testing"
)

// A build with the images contains all twenty-two, and each is the bytes the
// Maven build wrote. A tree built without them does not contain one, which is
// the other whole state: half of them would be a build gone wrong.
func TestContainsEveryImageOrNone(t *testing.T) {
	embedded := Embedded()
	if embedded == 0 {
		t.Skip("this build does not contain images: run mvn process-classes")
	}
	if embedded != len(Builds()) {
		t.Fatalf("%d images, not the %d there are", embedded, len(Builds()))
	}
	name := map[string]bool{}
	for _, build := range Builds() {
		bytes := Read(build.Variant, build.Width, build.Unit, build.Copies)
		if name[build.Name()] {
			t.Fatalf("two builds name %s", build.Name())
		}
		name[build.Name()] = true
		if len(bytes) < 16+28 {
			t.Fatalf("%s is %d bytes, too few for a format block",
				build.Name(), len(bytes))
		}
		// doc/abi.md 1: the format block stands at +16, behind the four
		// slots, and opens with the variant this image reads.
		if string(bytes[16:19]) != "DTX" || int(bytes[19]) != build.Variant {
			t.Fatalf("%s opens %q at +16, not DTX%d",
				build.Name(), bytes[16:20], build.Variant)
		}
		if got := int(bytes[16+18]); got != build.Unit {
			t.Fatalf("%s decodes at a unit of %d, not %d",
				build.Name(), got, build.Unit)
		}
		// DTX0 reads a row as one run of bytes, so its code does not move
		// with the width and its format block gives a width of 0.
		want := build.Width
		if build.Variant == 0 {
			want = 0
		}
		if got := int(bytes[16+19]); got != want {
			t.Fatalf("%s reads values of %d bytes, not %d",
				build.Name(), got, want)
		}
	}
	if len(name) != 22 {
		t.Fatalf("%d builds, not the DTX0, three DTX1 and eighteen DTX2 there"+
			" are", len(name))
	}
}

// No two of the twenty-two are one bytes. A build is a variant, a width and
// a decoder, and each of the three moves the code: two builds that assembled
// to the same bytes would be one build named twice.
func TestNoTwoImagesAreTheSameBytes(t *testing.T) {
	if Embedded() == 0 {
		t.Skip("this build does not contain images: run mvn process-classes")
	}
	builds := Builds()
	for i, one := range builds {
		first := Read(one.Variant, one.Width, one.Unit, one.Copies)
		for _, other := range builds[i+1:] {
			second := Read(other.Variant, other.Width, other.Unit, other.Copies)
			if string(first) == string(second) {
				t.Fatalf("%s and %s are the same %d bytes",
					one.Name(), other.Name(), len(first))
			}
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
		want, err := os.ReadFile(filepath.Join(loose, build.Name()))
		if err != nil {
			t.Fatalf("no %s in build/68k: %v", build.Name(), err)
		}
		got := Read(build.Variant, build.Width, build.Unit, build.Copies)
		if string(got) != string(want) {
			t.Fatalf("%s is %d bytes carried and %d released",
				build.Name(), len(got), len(want))
		}
	}
}
