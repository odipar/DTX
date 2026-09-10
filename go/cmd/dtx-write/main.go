// Command dtx-write writes a table: text or a DTX file of any variant in, a
// DTX file of any variant or text out. The table is the same under every
// variant (R1.3), so one tool writes text as DTX, rewrites a DTX file at
// another variant, unit or ring, and reads a DTX file out as text.
// doc/tools.md defines the tool.
//
//	dtx-write [-vV] [-wW] [-rRR] [-kK] [-mN] [-pPACKER] [-copies[S]] [-text]
package main

import (
	"bytes"
	"errors"
	"fmt"
	"io"
	"os"
	"strconv"
	"strings"

	"github.com/odipar/dtx/go/dtx"
	"github.com/odipar/dtx/go/internal/csv"
	"github.com/odipar/dtx/go/st4"
)

// What -help prints: the synopsis, a line a flag with the default in
// parentheses, examples, and the section of doc/tools.md that describes the
// tool. The Java and C# trees print the same text.
const help = `dtx-write [-vV] [-wW] [-rRR] [-kK] [-mN] [-pPACKER] [-copies[S]] [-text]

Writes the table on standard input to standard output. The input is a DTX
file of any variant, or comma separated text; the output is a DTX file of
variant V, or comma separated text under -text.

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
  -text        writes the table as comma separated text
  -help        this text

Examples

  dtx-write -v1 -w2 < t.csv > t.dtx
      text into a DTX1 file of two byte values
  dtx-write -v1 < t.csv > t.dtx
      the same, at the narrowest width every value of the text fits
  dtx-write -v2 -w1 -k1 -m960 < t.csv > t.dtx
      text into a DTX2 file of one byte values, at a unit of 1 and
      a ring of 960 bytes
  dtx-write -v0 -w4 -r32 < t.csv > t.dtx
      text into a DTX0 file of four byte values, repeating at row 32
  dtx-write -k2 -copies < t.dtx > again.dtx
      a DTX2 file repacked at a unit of 2, with copies from the
      literal stream. The width is the file's own
  dtx-write -text < t.dtx > t.csv
      a DTX file of any variant read out as text

doc/tools.md, Write.
`

// A misuse is a flag the tool does not read, or one that does not go with
// the files given: the message goes to standard error and the exit is 2, as
// the Java tree exits.
type misuse string

func (m misuse) Error() string { return string(m) }

func main() {
	if err := run(os.Args[1:], os.Stdin, os.Stdout); err != nil {
		fmt.Fprintln(os.Stderr, err)
		var m misuse
		if errors.As(err, &m) {
			os.Exit(2)
		}
		os.Exit(1)
	}
}

// run reads the table on in and writes it to out, its report going to
// standard error.
func run(args []string, in io.Reader, out io.Writer) error {
	for _, arg := range args {
		if arg == "-help" || arg == "-h" {
			fmt.Print(help)
			return nil
		}
	}
	variant, repeat, unit, ring := -1, -1, 1, 960
	width, packer, copies := "", "", ""
	toText := false
	for _, arg := range args {
		var err error
		switch {
		case arg == "-text":
			toText = true
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
	if toText && variant >= 0 {
		return misuse(fmt.Sprintf("-v%d names a DTX variant, and -text asks"+
			" for text", variant))
	}
	read, err := io.ReadAll(in)
	if err != nil {
		return err
	}
	var table *dtx.Table
	if bytes.HasPrefix(read, dtx.Magic) {
		if width != "" {
			return misuse(fmt.Sprintf("-w%s gives text its width, and the"+
				" input is a DTX file with its own", width))
		}
		header, err := dtx.ReadHeader(read)
		if err != nil {
			return err
		}
		if table, err = dtx.Read(read); err != nil {
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
		text := string(read)
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
	var written []byte
	if toText {
		written = []byte(csv.Text(table))
	} else {
		switch variant {
		case dtx.DTX0:
			written = dtx.WriteDtx0(table)
		case dtx.DTX1:
			written = dtx.WriteDtx1(table)
		case dtx.DTX2:
			with, taken := packerFor(packer, copies)
			if taken != nil {
				return taken
			}
			written, err = dtx.WriteDtx2(table, with, unit, ring)
		default:
			return fmt.Errorf("the variant is 0, 1 or 2, not %d", variant)
		}
		if err != nil {
			return err
		}
	}
	if _, err := out.Write(written); err != nil {
		return misuse("cannot write standard output")
	}
	kind, packing := "text", ""
	if !toText {
		kind = fmt.Sprintf("DTX%d", variant)
		if variant == dtx.DTX2 {
			packing = fmt.Sprintf(", k=%d, N=%d", unit, ring)
		}
	}
	fmt.Fprintf(os.Stderr, "%s %d bytes, %d rows, %d columns, width %d,"+
		" RR=%d%s\n", kind, len(written), table.Rows(),
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
func packerFor(named, copies string) (dtx.Packer, error) {
	if named != "" {
		return st4.Beside{Path: named, CopiesFlag: copies}, nil
	}
	if copies == "" {
		return st4.Packer{}, nil
	}
	search, err := seconds(copies)
	if err != nil {
		return nil, err
	}
	return st4.Packer{CopiesFlag: true, Seconds: search}, nil
}

// seconds gives what -copiesS searches for, zero where -copies stands on its
// own, and the tool's line for an S that is not a number.
func seconds(copies string) (float64, error) {
	if len(copies) <= 2 {
		return 0, nil
	}
	out, err := strconv.ParseFloat(copies[2:], 64)
	if err != nil {
		return 0, misuse("dtx-write does not read -copies" + copies[2:])
	}
	return out, nil
}
