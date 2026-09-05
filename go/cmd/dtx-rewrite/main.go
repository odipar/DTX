// Command dtx-rewrite turns a DTX0 or DTX1 file into a DTX2 one. The table
// is the same under every variant (R1.3), so what comes back has the same
// rows, widths, R and RR as what went in. doc/tools.md defines the tool.
//
//	dtx-rewrite in.dtx out.dtx [-kK] [-mN] [-pPACKER] [-copies[S]]
package main

import (
	"errors"
	"fmt"
	"os"
	"strconv"
	"strings"

	"dtx/internal/dtx"
	"dtx/internal/st4"
)

// What -help prints: the synopsis, a line a flag with the default in
// parentheses, and the section of doc/tools.md that describes the tool. The
// Java and C# trees print the same text.
const help = `dtx-rewrite in.dtx out.dtx [-kK] [-mN] [-pPACKER] [-copies[S]]

Rewrites a DTX0 or DTX1 file as DTX2: the same rows, widths, R and RR,
packed.

  -kK          the unit, 1, 2 or 4 (1)
  -mN          the ring, in bytes (960)
  -pPACKER     an ST4 executable to pack with (the copy carried here)
  -copies[S]   copies from the literal stream, with S seconds of search for
               a better parse
  -help        this text

doc/tools.md, Rewrite.
`

// errUsage is the run given no file to work on, which prints help to
// standard error and exits with 2.
var errUsage = errors.New("usage")

func main() {
	if err := run(os.Args[1:]); err != nil {
		if errors.Is(err, errUsage) {
			fmt.Fprint(os.Stderr, help)
			os.Exit(2)
		}
		fmt.Fprintln(os.Stderr, err)
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
		return errUsage
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

// packerFor gives what packs a column: the port in this executable, or
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
