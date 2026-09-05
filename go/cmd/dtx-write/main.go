// Command dtx-write turns comma separated text into a DTX file of any
// variant. doc/tools.md defines the tool.
//
//	dtx-write in.csv out.dtx [-vV] [-wW,W,..] [-rRR] [-kK] [-mN] [-pPACKER] [-copies[S]]
package main

import (
	"fmt"
	"os"
	"strconv"
	"strings"

	"dtx/internal/csv"
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
	variant, repeat, unit, ring := dtx.DTX0, -1, 1, 960
	widths, packer, copies := "", "", ""
	var named []string
	for _, arg := range args {
		var err error
		switch {
		case strings.HasPrefix(arg, "-v"):
			variant, err = strconv.Atoi(arg[2:])
		case strings.HasPrefix(arg, "-w"):
			widths = arg[2:]
		case strings.HasPrefix(arg, "-r"):
			repeat, err = strconv.Atoi(arg[2:])
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
			return fmt.Errorf("dtx-write does not read %s", arg)
		default:
			named = append(named, arg)
		}
		if err != nil {
			return fmt.Errorf("%s does not give a number", arg)
		}
	}
	if len(named) != 2 {
		return fmt.Errorf("dtx-write in.csv out.dtx [-vV] [-wW,W,..] [-rRR]" +
			" [-kK] [-mN] [-pPACKER] [-copies[S]]")
	}
	text, err := os.ReadFile(named[0])
	if err != nil {
		return err
	}
	width, err := readWidths(widths, string(text))
	if err != nil {
		return err
	}
	var table *dtx.Table
	if repeat < 0 {
		table, err = csv.TableAt(string(text), width)
	} else {
		table, err = csv.Table(string(text), width, repeat)
	}
	if err != nil {
		return err
	}
	var out []byte
	switch variant {
	case dtx.DTX0:
		out = dtx.WriteDtx0(table)
	case dtx.DTX1:
		out = dtx.WriteDtx1(table)
	case dtx.DTX2:
		out, err = dtx.WriteDtx2(table,
			packerFor(packer, copies), unit, ring)
	default:
		return fmt.Errorf("the variant is 0, 1 or 2, not %d", variant)
	}
	if err != nil {
		return err
	}
	if err := os.WriteFile(named[1], out, 0o644); err != nil {
		return err
	}
	drawn := make([]string, table.Columns())
	for i := range drawn {
		drawn[i] = strconv.Itoa(table.Width(i))
	}
	packing := ""
	if variant == dtx.DTX2 {
		packing = fmt.Sprintf(", k=%d, N=%d", unit, ring)
	}
	fmt.Printf("%s -> DTX%d %d bytes, %d rows, %d columns, widths %s,"+
		" RR=%d%s\n", named[0], variant, len(out), table.Rows(),
		table.Columns(), strings.Join(drawn, ","), table.Repeat(), packing)
	return nil
}

// readWidths gives the widths -w defines, or the narrowest the text takes.
func readWidths(given, text string) ([]int, error) {
	if given == "" {
		return csv.Narrowest(text)
	}
	cell := strings.Split(given, ",")
	width := make([]int, len(cell))
	for i, one := range cell {
		var err error
		if width[i], err = strconv.Atoi(strings.TrimSpace(one)); err != nil {
			return nil, fmt.Errorf("-w gives %q, which is not a width", one)
		}
	}
	return width, nil
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
