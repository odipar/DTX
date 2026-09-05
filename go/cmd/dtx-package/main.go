// Command dtx-package turns a DTX file into a standalone 68000 image.
//
// It combines rather than assembles: the code does not move with the table,
// so this executable contains the eight images built once and takes the one the
// table needs. No assembler runs. doc/tools.md states the tool and
// doc/abi.md the six calls into the image.
//
//	dtx-package in.dtx out.bin
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
	for _, arg := range args {
		if len(arg) > 0 && arg[0] == '-' {
			return fmt.Errorf("dtx-package does not read %s", arg)
		}
		named = append(named, arg)
	}
	if len(named) != 2 {
		return fmt.Errorf("dtx-package in.dtx out.bin")
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
	state := pack.StateBytes(header)
	if header.Variant == dtx.DTX2 {
		given, err := pack.ReadPacked(file, header)
		if err != nil {
			return err
		}
		state = pack.PackedStateBytes(header, given)
	}
	fmt.Printf("%s -> DTX%d image %d bytes, table %d bytes, %d rows,"+
		" %d columns, state block %d bytes\n",
		named[0], header.Variant, len(out), len(file), header.Rows,
		header.Columns(), state)
	return nil
}
