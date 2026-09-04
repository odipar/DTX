// Command dtx-package turns a DTX file into a standalone 68000 image.
//
// It combines rather than assembles: the code does not move with the table,
// so this executable holds the eight images built once and takes the one the
// table asks for. No assembler runs. doc/tools.md states the tool and
// doc/abi.md the six calls the image answers.
//
//	dtx-package in.dtx out.bin [-copies]
package main

import (
	"fmt"
	"os"

	"dtx/internal/dtx"
	"dtx/internal/pack"
)

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func run(args []string) error {
	var named []string
	copies := false
	for _, arg := range args {
		switch {
		case arg == "-copies":
			copies = true
		case len(arg) > 0 && arg[0] == '-':
			return fmt.Errorf("dtx-package does not read %s", arg)
		default:
			named = append(named, arg)
		}
	}
	if len(named) != 2 {
		return fmt.Errorf("dtx-package in.dtx out.bin [-copies]")
	}
	file, err := os.ReadFile(named[0])
	if err != nil {
		return err
	}
	header, err := dtx.ReadHeader(file)
	if err != nil {
		return err
	}
	out, err := pack.Image(file, copies)
	if err != nil {
		return err
	}
	if err := os.WriteFile(named[1], out, 0o644); err != nil {
		return err
	}
	state := pack.StateBytes(header)
	if header.Variant == dtx.DTX2 {
		state = pack.PackedStateBytes(header, pack.ReadPacked(file, header))
	}
	fmt.Printf("%s -> DTX%d image %d bytes, table %d bytes, %d rows,"+
		" %d columns, state block %d bytes\n",
		named[0], header.Variant, len(out), len(file), header.Rows,
		header.Columns(), state)
	return nil
}
