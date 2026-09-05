// Command dtx-write writes a table: text or a DTX file of any variant in, a
// DTX file of any variant or text out. The table is the same under every
// variant (R1.3), so one tool writes text as DTX, rewrites a DTX file at
// another variant, unit or ring, and reads a DTX file out as text.
// doc/tools.md defines the tool.
//
//	dtx-write in out [-vV] [-wW] [-rRR] [-kK] [-mN] [-pPACKER] [-copies[S]]
package main

import (
	"bytes"
	"errors"
	"fmt"
	"os"
	"strconv"
	"strings"

	"dtx/internal/csv"
	"dtx/internal/dtx"
	"dtx/internal/st4"
)

// What -help prints: the synopsis, a line a flag with the default in
// parentheses, examples, and the section of doc/tools.md that describes the
// tool. The Java and C# trees print the same text.
const help = `dtx-write in out [-vV] [-wW] [-rRR] [-kK] [-mN] [-pPACKER] [-copies[S]]

Writes the table in the first file to the second. The first is a DTX file
of any variant, or comma separated text; the second is a DTX file of
variant V, or text where its name ends in .csv.

  -vV          the variant to write, 0, 1 or 2 (the one read, or 0 for
               text)
  -wW          text: the bytes every value takes, 1, 2 or 4 (what the
               text's first comment gives, or else the narrowest that
               fits every value)
  -rRR         the repeat: the row an advance past the last steps to, R for
               none (what the file or the text's first comment gives, or R)
  -kK          DTX2: the unit, 1, 2 or 4 (1)
  -mN          DTX2: the ring, in bytes (960)
  -pPACKER     DTX2: an ST4 executable to pack with (the copy carried here)
  -copies[S]   DTX2: copies from the literal stream, with S seconds of
               search for a better parse
  -help        this text

Examples

  dtx-write t.csv t.dtx -v1 -w2
      text into a DTX1 file of two byte values
  dtx-write t.csv t.dtx -v1
      the same, at the narrowest width every value of the text fits
  dtx-write t.csv t.dtx -v2 -w1 -k1 -m960
      text into a DTX2 file of one byte values, at a unit of 1 and
      a ring of 960 bytes
  dtx-write t.csv t.dtx -v0 -w4 -r32
      text into a DTX0 file of four byte values, repeating at row 32
  dtx-write t.dtx again.dtx -k2 -copies
      a DTX2 file repacked at a unit of 2, with copies from the
      literal stream. The width is the file's own
  dtx-write t.dtx t.csv
      a DTX file of any variant read out as text

doc/tools.md, Write.
`

// errUsage is the run given no file to work on, which prints help to
// standard error and exits with 2.
var errUsage = errors.New("usage")

// A misuse is a flag the tool does not read, or one that does not go with
// the files given: the message goes to standard error and the exit is 2, as
// the Java tree exits.
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
	if len(args) < 2 {
		return errUsage
	}
	named := args[:2]
	variant, repeat, unit, ring := -1, -1, 1, 960
	width, packer, copies := "", "", ""
	for _, arg := range args[2:] {
		var err error
		switch {
		case strings.HasPrefix(arg, "-v"):
			variant, err = strconv.Atoi(arg[2:])
		case strings.HasPrefix(arg, "-w"):
			width = arg[2:]
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
		default:
			return misuse("dtx-write does not read " + arg)
		}
		if err != nil {
			return fmt.Errorf("%s does not give a number", arg)
		}
	}
	toText := strings.HasSuffix(named[1], ".csv")
	if toText && variant >= 0 {
		return misuse(fmt.Sprintf("-v%d names a DTX variant, and %s is text",
			variant, named[1]))
	}
	in, err := os.ReadFile(named[0])
	if err != nil {
		return err
	}
	var table *dtx.Table
	if bytes.HasPrefix(in, dtx.Magic) {
		if width != "" {
			return misuse(fmt.Sprintf("-w%s gives text its width, and %s is"+
				" a DTX file with its own", width, named[0]))
		}
		header, err := dtx.ReadHeader(in)
		if err != nil {
			return err
		}
		if table, err = dtx.Read(in); err != nil {
			return err
		}
		if repeat >= 0 {
			if table, err = repeating(table, repeat); err != nil {
				return err
			}
		}
		if variant < 0 {
			variant = header.Variant
		}
	} else {
		text := string(in)
		taken, err := readWidth(width, text)
		if err != nil {
			return err
		}
		given := repeat
		if given < 0 {
			if given, err = csv.Repeat(text); err != nil {
				return err
			}
		}
		if given < 0 {
			table, err = csv.TableAt(text, taken)
		} else {
			table, err = csv.Table(text, taken, given)
		}
		if err != nil {
			return err
		}
		if variant < 0 {
			variant = dtx.DTX0
		}
	}
	var out []byte
	if toText {
		out = []byte(csv.Text(table))
	} else {
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
	}
	if err := os.WriteFile(named[1], out, 0o644); err != nil {
		return err
	}
	kind, packing := "text", ""
	if !toText {
		kind = fmt.Sprintf("DTX%d", variant)
		if variant == dtx.DTX2 {
			packing = fmt.Sprintf(", k=%d, N=%d", unit, ring)
		}
	}
	fmt.Printf("%s -> %s %d bytes, %d rows, %d columns, width %d,"+
		" RR=%d%s\n", named[0], kind, len(out), table.Rows(),
		table.Columns(), table.Width(), table.Repeat(), packing)
	return nil
}

// repeating gives t repeating at repeat, the rest as it is.
func repeating(t *dtx.Table, repeat int) (*dtx.Table, error) {
	column := make([][]byte, t.Columns())
	for i := range column {
		column[i] = t.Column(i)
	}
	return dtx.NewTable(t.Rows(), repeat, t.Width(), column)
}

// readWidth gives the width -w defines, or else the width the text's first
// comment gives, or else the narrowest the text takes.
func readWidth(given, text string) (int, error) {
	if given == "" {
		return csv.Width(text)
	}
	width, err := strconv.Atoi(strings.TrimSpace(given))
	if err != nil {
		return 0, fmt.Errorf("-w gives %q, which is not a width", given)
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
