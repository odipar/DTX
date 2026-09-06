// Command dtx-package turns a DTX file into a standalone 68000 image.
//
// It combines rather than assembles: the code does not move with the table's
// shape, so this executable contains the twenty-two images built once and
// takes the one the table needs. No assembler runs. doc/tools.md defines the
// tool and doc/abi.md the four calls into the image.
//
//	dtx-package in.dtx out.bin
package main

import (
	"errors"
	"fmt"
	"os"

	"dtx/internal/dtx"
	"dtx/internal/pack"
)

// What -help prints: the synopsis, the one flag, an example, and the section
// of doc/tools.md that describes the tool. The Java and C# trees also take
// -aRMAC and -s, which this tool does not read.
const help = `dtx-package in.dtx out.bin

Packages a DTX file as a 68000 image: the code for its variant and,
under DTX1 and DTX2, its width, the column table and the file.
doc/abi.md gives the four calls into the image.

  -help        this text

Examples

  dtx-package t.dtx t.bin
      the image of a table, from the code the build made

doc/tools.md, Package.
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

func run(args []string) error {
	for _, arg := range args {
		if arg == "-help" || arg == "-h" {
			fmt.Print(help)
			return nil
		}
	}
	var named []string
	for _, arg := range args {
		if len(arg) > 0 && arg[0] == '-' {
			return misuse("dtx-package does not read " + arg)
		}
		named = append(named, arg)
	}
	if len(named) != 2 {
		return errUsage
	}
	file, err := os.ReadFile(named[0])
	if err != nil {
		return err
	}
	header, err := dtx.ReadHeader(file)
	if err != nil {
		return err
	}
	out, err := pack.Image(file)
	if err != nil {
		return err
	}
	if err := os.WriteFile(named[1], out, 0o644); err != nil {
		return err
	}
	state := pack.StateBytes()
	if header.Variant == dtx.DTX2 {
		given, err := pack.ReadPacked(file, header)
		if err != nil {
			return err
		}
		if state, err = pack.PackedStateBytes(header, given); err != nil {
			return err
		}
	}
	fmt.Printf("%s -> DTX%d image %d bytes, table %d bytes, %d rows,"+
		" %d columns, state block %d bytes\n",
		named[0], header.Variant, len(out), len(file), header.Rows,
		header.Columns, state)
	return nil
}
