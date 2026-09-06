// Command dtx-blobs builds the 68000 images the packager combines from, one
// file a build. doc/tools.md defines the tool.
//
// Twenty-two of them. DTX0 reads a row as one run of bytes, so its code does
// not move with the width and one file is every DTX0 table's. DTX1 moves a
// value a column, so it has one a width. DTX2 has one a width and a build of
// the decoder built into it: a unit of 1, 2 or 4, with the copy code and
// without. This is the one step an assembler is needed for.
//
// The table each is assembled from is made here rather than read: the code
// does not move with a table's shape, and pack.Blank zeroes the six fields
// the one used did give, so what comes out is a function of the template
// alone.
//
//	dtx-blobs DIR [DIR..] [-aRMAC] [-tTEMPLATES]
package main

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"dtx/internal/dtx"
	"dtx/internal/image"
	"dtx/internal/pack"
	"dtx/internal/st4"
)

// What -help prints: the synopsis, a line a flag with the default in
// parentheses, examples, and the section of doc/tools.md that describes the
// tool. The Java and C# trees print the same text.
const help = `dtx-blobs DIR [DIR..] [-aRMAC] [-tTEMPLATES]

Builds the twenty-two images from the 68k/ templates with rmac and writes
them into each DIR: DTX0, DTX1 at each width, and DTX2 at each width and a
unit of 1, 2 and 4, with the copy code and without.

  -aRMAC       the assembler (rmac, on the path)
  -tTEMPLATES  the directory the templates are read from (68k, or what
               DTX_68K names)
  -help        this text

Examples

  dtx-blobs build/68k
      the twenty-two images into build/68k, with the rmac on the path
  dtx-blobs build/68k go/internal/image/data -a/usr/local/bin/rmac
      into two directories, with that rmac

doc/tools.md, Build the images.
`

// errUsage is the run given no file to work on, which prints help to
// standard error and exits with 2.
var errUsage = errors.New("usage")

// A misuse is a flag the tool does not read: the message goes to standard
// error and the exit is 2, as the Java tree exits.
type misuse string

func (m misuse) Error() string { return string(m) }

func main() {
	if err := run(os.Args[1:]); err != nil {
		if errors.Is(err, errUsage) {
			fmt.Fprint(os.Stderr, help)
			os.Exit(2)
		}
		fmt.Fprintln(os.Stderr, err)
		var m misuse
		if errors.As(err, &m) {
			os.Exit(2)
		}
		os.Exit(1)
	}
}

// plain does not pack: it hands the column back inside an ST4 data set and
// defines the build's own copies flag.
//
// The assembler reads only a data set's four stream offsets, not the streams,
// so a data set whose streams are the column itself fixes every figure the
// build takes. Nothing decodes it, and nothing here runs it: the packer that
// writes a table a caller reads is ST4's own.
type plain struct{ copies bool }

func (h plain) Copies() bool { return h.copies }

func (h plain) Pack(column []byte, unit, ring, loop int) ([]byte, error) {
	set := make([]byte, 28+len(column))
	set[0], set[1], set[2], set[3] = 'S', '4', 7, byte(unit)
	dtx.PutLong(set, 4, len(column))
	dtx.PutLong(set, 8, 28)
	dtx.PutLong(set, 12, 28+len(column))
	dtx.PutLong(set, 16, 28+len(column))
	// Nothing decodes this set, so it does not loop where the table does:
	// the end marker loops it and no pass is replayed.
	dtx.PutLong(set, 20, st4.NoRewind)
	dtx.PutLong(set, 24, ring/unit)
	copy(set[28:], column)
	return set, nil
}

// seed gives a table that fixes the figures one build's assembly reads.
// Any table of the kind does, since the code does not move with it: this
// one is 64 rows of two columns at the build's width, at a ring of 960 where
// the build is packed.
func seed(build image.Build) ([]byte, error) {
	const rows, columns = 64, 2
	column := make([][]byte, columns)
	for i := range column {
		column[i] = make([]byte, rows*build.Width)
	}
	table, err := dtx.NewTable(rows, rows, build.Width, column)
	if err != nil {
		return nil, err
	}
	switch build.Variant {
	case dtx.DTX0:
		return dtx.WriteDtx0(table), nil
	case dtx.DTX1:
		return dtx.WriteDtx1(table), nil
	default:
		return dtx.WriteDtx2(table, plain{build.Copies}, build.Unit, 960)
	}
}

func run(args []string) error {
	for _, arg := range args {
		if arg == "-help" || arg == "-h" {
			fmt.Print(help)
			return nil
		}
	}
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
			return misuse("dtx-blobs does not read " + arg)
		default:
			into = append(into, arg)
		}
	}
	if len(into) == 0 {
		return errUsage
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
				build.Name(), rmac, templates, err)
		}
		pack.Blank(code)
		name := build.Name()
		for _, at := range into {
			if err := os.WriteFile(filepath.Join(at, name), code, 0o644); err != nil {
				return err
			}
		}
		fmt.Printf("%-20s %5d bytes\n", name, len(code))
	}
	return nil
}
