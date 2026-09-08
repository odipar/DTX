// Command dtx-package turns a DTX file into a standalone 68000 image.
//
// It combines rather than assembles: the code does not move with the table's
// shape, so this executable contains the twenty-two images built once and
// takes the one the table needs. No assembler runs. doc/tools.md defines the
// tool and doc/abi.md the four calls into the image.
//
//	dtx-package in.dtx... out.bin
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
const help = `dtx-package in.dtx... out.bin

Packages one DTX file or several as a 68000 image: the code for their
variant and, under DTX1 and DTX2, their width, then a column table and a
file for each. doc/abi.md gives the four calls into the image.

  -help        this text

Examples

  dtx-package t.dtx t.bin
      the image of a table, from the code the build made

  dtx-package a.dtx b.dtx both.bin
      one image of two tables, the code in it once, with a line
      saying where each table stands: a caller hands that to
      DTX_init

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
	if len(named) < 2 {
		return errUsage
	}
	// Every name but the last is a table, in the order the image lays them
	// out; the last is what the image is written to.
	out_ := named[len(named)-1]
	named = named[:len(named)-1]
	var files [][]byte
	for _, name := range named {
		file, err := os.ReadFile(name)
		if err != nil {
			return err
		}
		files = append(files, file)
	}
	file := files[0]
	header, err := dtx.ReadHeader(file)
	if err != nil {
		return err
	}
	out, at, err := pack.Images(files)
	if err != nil {
		return err
	}
	if err := os.WriteFile(out_, out, 0o644); err != nil {
		return err
	}
	state, err := stateOf(file, header)
	if err != nil {
		return err
	}
	fmt.Printf("%s -> DTX%d image %d bytes, table %d bytes, %d rows,"+
		" %d columns, state block %d bytes\n",
		named[0], header.Variant, len(out), len(file), header.Rows,
		header.Columns, state)
	// A caller hands init the header of the table to read (abi.md 2), so
	// the image says where each one stands.
	for i := 1; i < len(named); i++ {
		its, err := dtx.ReadHeader(files[i])
		if err != nil {
			return err
		}
		mine, err := stateOf(files[i], its)
		if err != nil {
			return err
		}
		fmt.Printf("%s -> table %d at image+%d, %d bytes, %d rows,"+
			" %d columns, state block %d bytes\n",
			named[i], i+1, at[i], len(files[i]), its.Rows, its.Columns, mine)
	}
	if len(named) > 1 {
		fmt.Printf("table 1 stands at image+%d\n", at[0])
	}
	return nil
}

// stateOf gives the state block a table's reader takes, in bytes.
func stateOf(file []byte, header dtx.Header) (int, error) {
	if header.Variant != dtx.DTX2 {
		return pack.StateBytes(), nil
	}
	given, err := pack.ReadPacked(file, header)
	if err != nil {
		return 0, err
	}
	return pack.PackedStateBytes(header, given)
}
