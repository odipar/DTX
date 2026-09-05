// Command dtx-rewrite turns a DTX0 or DTX1 file into a DTX2 one. The table
// is the same under every variant (R1.3), so what comes back holds the same
// rows, widths, R and RR as what went in. doc/tools.md states the tool.
//
//	dtx-rewrite in.dtx out.dtx [-kK] [-mN] [-pPACKER] [-copies[S]]
package main

import (
	"fmt"
	"os"
	"strconv"
	"strings"

	"dtx/internal/dtx"
	"dtx/internal/st4"
)

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func run(args []string) error {
	unit, ring := 1, 960
	packer, copies := "", ""
	var named []string
	for _, arg := range args {
		var err error
		switch {
		case strings.HasPrefix(arg, "-k"):
			unit, err = strconv.Atoi(arg[2:])
		case strings.HasPrefix(arg, "-m"):
			ring, err = strconv.Atoi(arg[2:])
		case strings.HasPrefix(arg, "-copies"):
			// the packer's own: a match beyond the ring copies from the
			// literal stream, and -copiesS searches S seconds for a better
			// parse. YMX spells it the same way.
			copies = "-c" + arg[7:]
		case strings.HasPrefix(arg, "-p"):
			packer = arg[2:]
		case strings.HasPrefix(arg, "-"):
			return fmt.Errorf("dtx-rewrite does not read %s", arg)
		default:
			named = append(named, arg)
		}
		if err != nil {
			return fmt.Errorf("%s does not give a number", arg)
		}
	}
	if len(named) != 2 {
		return fmt.Errorf(
			"dtx-rewrite in.dtx out.dtx [-kK] [-mN] [-pPACKER] [-copies[S]]")
	}
	in, err := os.ReadFile(named[0])
	if err != nil {
		return err
	}
	out, err := dtx.Dtx2From(in,
		packerFor(packer, copies), unit, ring)
	if err != nil {
		return err
	}
	if err := os.WriteFile(named[1], out, 0o644); err != nil {
		return err
	}
	header, err := dtx.ReadHeader(in)
	if err != nil {
		return err
	}
	fmt.Printf("DTX%d %d bytes -> DTX2 %d bytes, %d rows, %d columns,"+
		" k=%d, N=%d\n", header.Variant, len(in), len(out), header.Rows,
		header.Columns(), unit, ring)
	return nil
}

// packerFor gives what packs a column: the port this executable holds, or
// an ST4 executable beside it where -p names one.
func packerFor(named, copies string) dtx.Packer {
	if named != "" {
		return st4.Beside{Path: named, CopiesFlag: copies}
	}
	if copies == "" {
		return st4.Packer{}
	}
	return st4.Packer{CopiesFlag: true, Seconds: seconds(copies)}
}

// seconds gives what -copiesS searches for, or zero.
func seconds(copies string) float64 {
	if len(copies) <= 2 {
		return 0
	}
	out, err := strconv.ParseFloat(copies[2:], 64)
	if err != nil {
		return 0
	}
	return out
}
