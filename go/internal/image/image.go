// Package image contains the 68000 images a DTX table is packaged behind.
//
// One build is one code, so the twenty-two are built once and a program that
// packages a table contains them rather than assembling one. doc/tools.md
// defines how they are built and doc/abi.md what each of them does.
//
// The files are build output. The Maven build writes them into data/, which
// go:embed reads only inside its own module. A tree built without them does
// not contain one: Read gives nil back and the caller resolves an image as it
// otherwise would.
package image

import (
	"embed"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
)

// The directory rather than the files in it: a tree whose build has not run
// does not have a .bin here, and a pattern that did not match a file would
// not compile.
//
//go:embed data
var data embed.FS

// Name gives the file one build stands in: a variant, the width its values
// take, and under DTX2 the unit its decoder decodes at and whether that
// decoder has the copy code.
//
// DTX0 reads a row as one run of bytes, and the move that run takes comes
// from the row's bytes: its code does not move with the width, so one file
// is every DTX0 table's.
func Name(variant, width, unit int, copies bool) string {
	if variant == 0 {
		return "DTX0.bin"
	}
	if variant != 2 {
		return fmt.Sprintf("DTX1-w%d.bin", width)
	}
	if copies {
		return fmt.Sprintf("DTX2-w%d-k%d-copies.bin", width, unit)
	}
	return fmt.Sprintf("DTX2-w%d-k%d.bin", width, unit)
}

// Read gives one image's bytes, or nil where the build does not contain one.
func Read(variant, width, unit int, copies bool) []byte {
	bytes, err := fs.ReadFile(data, "data/"+Name(variant, width, unit, copies))
	if err != nil {
		return nil
	}
	return bytes
}

// Code gives one image's bytes, from what this build contains or, where it
// does not contain one, from the directory DTX_68K names.
func Code(variant, width, unit int, copies bool) ([]byte, error) {
	name := Name(variant, width, unit, copies)
	if bytes := Read(variant, width, unit, copies); bytes != nil {
		return bytes, nil
	}
	at := os.Getenv("DTX_68K")
	if at == "" {
		return nil, fmt.Errorf("this build does not contain %s, and DTX_68K"+
			" does not name a directory with one", name)
	}
	return os.ReadFile(filepath.Join(at, name))
}

// Build is one build of the code: a variant, the width its values take, and
// under DTX2 a decoder.
type Build struct {
	Variant int
	Width   int
	Unit    int
	Copies  bool
}

// Name gives the file this build stands in.
func (b Build) Name() string {
	return Name(b.Variant, b.Width, b.Unit, b.Copies)
}

// Builds gives every build, in the order the Maven build writes them.
//
// Twenty-two: one DTX0, one DTX1 a width, and one DTX2 a width and a build
// of the decoder built into it, which is a unit of 1, 2 or 4 with the copy
// code and without.
func Builds() []Build {
	out := []Build{{0, 1, 0, false}}
	for _, width := range []int{1, 2, 4} {
		out = append(out, Build{1, width, 0, false})
	}
	for _, width := range []int{1, 2, 4} {
		for _, unit := range []int{1, 2, 4} {
			out = append(out, Build{2, width, unit, false},
				Build{2, width, unit, true})
		}
	}
	return out
}

// Embedded gives how many of the builds this one contains: all of them, or
// none.
func Embedded() int {
	count := 0
	for _, build := range Builds() {
		if Read(build.Variant, build.Width, build.Unit, build.Copies) != nil {
			count++
		}
	}
	return count
}
