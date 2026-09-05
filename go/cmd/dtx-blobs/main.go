// Command dtx-blobs builds the 68000 images the packager combines from, one
// file a build. doc/tools.md states the tool.
//
// Eight of them: DTX0, DTX1, and one a build of the decoder built into DTX2,
// which is a unit of 1, 2 or 4 with the copy code and without. This is the
// one step an assembler is needed for.
//
// The table each is assembled from is made here rather than read: the code
// does not move with a table, and pack.Blank zeroes the five fields the one
// used did settle, so what comes out is a function of the template alone.
//
//	dtx-blobs DIR [DIR..] [-aRMAC] [-tTEMPLATES]
package main

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"dtx/internal/dtx"
	"dtx/internal/image"
	"dtx/internal/pack"
)

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

// held packs nothing: it hands the column back inside an ST4 data set and
// states the build's own copies flag.
//
// The assembler reads a data set's four stream offsets and nothing in the
// streams, so a data set whose streams are the column itself settles every
// figure the build takes. Nothing decodes it, and nothing here runs it: the
// packer that writes a table a caller reads is ST4's own.
type held struct{ copies bool }

func (h held) Copies() bool { return h.copies }

func (h held) Pack(column []byte, unit, ring int) ([]byte, error) {
	set := make([]byte, 28+len(column))
	set[0], set[1], set[2], set[3] = 'S', '4', 7, byte(unit)
	dtx.PutLong(set, 4, len(column)/unit)
	dtx.PutLong(set, 8, 28)
	dtx.PutLong(set, 12, 28+len(column))
	dtx.PutLong(set, 16, 28+len(column))
	dtx.PutLong(set, 24, ring/unit)
	copy(set[28:], column)
	return set, nil
}

// seed gives a table that settles the figures one build's assembly reads.
// Any table of the kind serves, since the code does not move with it: this
// one is 64 rows of two columns, at a ring of 960 where the build is packed.
func seed(build image.Build) ([]byte, error) {
	width := []int{1, 2, 4}
	if build.Variant == dtx.DTX2 {
		width = []int{build.Unit, build.Unit}
	}
	const rows = 64
	column := make([][]byte, len(width))
	for i, w := range width {
		column[i] = make([]byte, rows*w)
	}
	table, err := dtx.NewTable(rows, rows, width, column)
	if err != nil {
		return nil, err
	}
	switch build.Variant {
	case dtx.DTX0:
		return dtx.WriteDtx0(table), nil
	case dtx.DTX1:
		return dtx.WriteDtx1(table), nil
	default:
		return dtx.WriteDtx2(table, held{build.Copies}, build.Unit, 960)
	}
}

func run(args []string) error {
	rmac := "rmac"
	templates := pack.Templates()
	var into []string
	for _, arg := range args {
		switch {
		case strings.HasPrefix(arg, "-a"):
			rmac = arg[2:]
		case strings.HasPrefix(arg, "-t"):
			templates = arg[2:]
		case strings.HasPrefix(arg, "-"):
			return fmt.Errorf("dtx-blobs does not read %s", arg)
		default:
			into = append(into, arg)
		}
	}
	if len(into) == 0 {
		return fmt.Errorf("dtx-blobs DIR [DIR..] [-aRMAC] [-tTEMPLATES]")
	}
	for _, at := range into {
		if err := os.MkdirAll(at, 0o755); err != nil {
			return err
		}
	}
	for _, build := range image.Builds() {
		file, err := seed(build)
		if err != nil {
			return err
		}
		code, err := pack.Code(file, rmac, templates)
		if err != nil {
			return fmt.Errorf("no code built for %s with an assembler at %s"+
				" and templates at %s: %w",
				image.Name(build.Variant, build.Unit, build.Copies),
				rmac, templates, err)
		}
		pack.Blank(code)
		name := image.Name(build.Variant, build.Unit, build.Copies)
		for _, at := range into {
			if err := os.WriteFile(filepath.Join(at, name), code, 0o644); err != nil {
				return err
			}
		}
		fmt.Printf("%-20s %5d bytes\n", name, len(code))
	}
	return nil
}
