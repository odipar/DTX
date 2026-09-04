// Package image holds the 68000 images a DTX table is packaged behind.
//
// One variant is one code, and under DTX2 one a build of the decoder built
// into it, so the eight are built once and a program that packages a table
// holds them rather than assembling one. doc/tools.md states how they are
// built and doc/abi.md what each of them answers.
//
// The files are build output. The Maven build writes them into data/, which
// go:embed reads only inside its own module. A tree built without them
// holds none: Read gives nothing back and the caller resolves an image as
// it otherwise would.
package image

import (
	"embed"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
)

// The directory rather than the files in it: a tree whose build has not run
// holds no .bin here, and a pattern that matched none would not compile.
//
//go:embed data
var data embed.FS

// Name gives the file one build stands in: a variant, and under DTX2 the
// unit its decoder decodes at and whether that decoder holds the copy code.
func Name(variant, unit int, copies bool) string {
	if variant != 2 {
		return fmt.Sprintf("DTX%d.bin", variant)
	}
	if copies {
		return fmt.Sprintf("DTX2-k%d-copies.bin", unit)
	}
	return fmt.Sprintf("DTX2-k%d.bin", unit)
}

// Read gives one image's bytes, or nil where this build holds none.
func Read(variant, unit int, copies bool) []byte {
	bytes, err := fs.ReadFile(data, "data/"+Name(variant, unit, copies))
	if err != nil {
		return nil
	}
	return bytes
}

// Code gives one image's bytes, from what this build holds or, where it
// holds none, from the directory DTX_68K names.
func Code(variant, unit int, copies bool) ([]byte, error) {
	name := Name(variant, unit, copies)
	if bytes := Read(variant, unit, copies); bytes != nil {
		return bytes, nil
	}
	at := os.Getenv("DTX_68K")
	if at == "" {
		return nil, fmt.Errorf("this build holds no %s, and DTX_68K names"+
			" no directory holding one", name)
	}
	return os.ReadFile(filepath.Join(at, name))
}

// Build is one build of the code.
type Build struct {
	Variant int
	Unit    int
	Copies  bool
}

// Builds gives every build, in the order the Maven build writes them.
func Builds() []Build {
	out := []Build{{0, 0, false}, {1, 0, false}}
	for _, unit := range []int{1, 2, 4} {
		out = append(out, Build{2, unit, false}, Build{2, unit, true})
	}
	return out
}

// Held says how many of the builds this one holds: eight, or none.
func Held() int {
	held := 0
	for _, build := range Builds() {
		if Read(build.Variant, build.Unit, build.Copies) != nil {
			held++
		}
	}
	return held
}
