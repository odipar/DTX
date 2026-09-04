# tools

Each tool is a script under `bin/`. A script builds first where a source is
newer than the last build, and then runs the tool out of `target/classes`.
Paths reach the tool as the caller gave them, so a relative one is relative
to the caller's directory and not to this repository's.

## Write

A DTX file of any variant out of comma separated text.

```
bin/dtx-write in.csv out.dtx -v2 -k1 -m960 -pst4
```

| flag | gives |
|---|---|
| `-vV` | the variant: 0, 1 or 2. The default is 0 |
| `-wW,W,..` | one width a column: 1, 2 or 4 each. The default is the narrowest width that holds every value of the column |
| `-rRR` | the row the table repeats to, 0 to `R`. The default is `R`, where the table does not repeat |
| `-kK` | the unit a DTX2 column is packed at, and `R` divides by it (R5.6). The default is 1 |
| `-mN` | the ring a DTX2 column unpacks through, in bytes, 1 to 65535 (R5.4). The default is 960 |
| `-pPACKER` | the ST4 executable a DTX2 file is packed by. The default is `st4` on the path |

`-k`, `-m` and `-p` reach a DTX2 file alone: no other variant packs.

### The text

One row a line, one value a column. The first row that holds values gives
`C`, and every row after it holds that many. A line that is blank, or whose
first character other than a space is `#`, is not a row.

A value is decimal, or hexadecimal where it opens with `$`, and negative
where it opens with `-`. A value of `W` bytes is stored most significant
byte first, as every field of the header is, and a negative one in two's
complement. A value fits `W` bytes where it lies from -2^(8W-1) to
2^(8W)-1, so one width takes what a signed column holds and what an
unsigned one holds alike. DTX states no more of a column than its width
(R6.3), so which of the two a column holds is stated elsewhere or not at
all.

```
# a clock, a note and a step
0, $0100, -2
1, $0101, -1
2, $0102,  0
```

Those three columns take widths 1, 2 and 1: column 1 holds 256 upward, and
column 2 holds a negative value.

`org.dtx.Csv` is the same reader as a library, and `Table`, `Dtx0`,
`Dtx1` and `Dtx2` write a table a caller builds itself.

## Package

A DTX file of any variant into a standalone 68000 image: the code, then
the table's bytes, reached PC relative. [abi.md](abi.md) states the five
calls the image answers, and the state block a caller supplies.

```
bin/dtx-package in.dtx out.bin
```

| flag | gives |
|---|---|
| `-aRMAC` | the assembler to run. The default is `rmac` on the path |
| `-s` | write the assembly rather than the image, for reading or for a build of your own |

The image holds one table and the code for that table's variant. What the
table settles is folded into the code: `R`, `RR`, the row's bytes, each
width as the size of a move, and every column's displacement off its class
cursor. The tool prints the image's bytes and the state block's, and the
format block states the same figures for a caller to read out of the file.

A DTX2 image holds the decoder carried at
[68k/ST4_wrap.S](../68k/ST4_wrap.S), built at the unit the payload states.
Init fills every ring before it returns, and one column is refilled a row
after that, so a read takes one value from each ring and never decodes.
The packager takes the period from the table and fails the package where
no period holds every rule abi.md 4 states: what it says names the rule
and the figures that break it.

A DTX2 image asks for more of the caller than a plain one. Its state block
holds a slot and a ring a column, so it runs to `NC` bytes and more; the
tool prints the figure and the format block states it.

## Rewrite

A DTX0 or DTX1 file into a DTX2 one. The table is the same under every
variant (R1.3), so what comes out holds the same rows, widths, `R` and `RR`
as what went in.

```
bin/dtx-rewrite in.dtx out.dtx -k1 -m960 -pst4
```

| flag | gives |
|---|---|
| `-kK` | the unit every column is packed at: 1, 2 or 4, and `R` divides by it (R5.6). The default is 1 |
| `-mN` | the ring in bytes, 1 to 65535 (R5.4). The default is 960 |
| `-pPACKER` | the ST4 executable to run. The default is `st4` on the path |

Rewrite keeps no packer of its own. `-p` names the one ST4's own repository
builds, and a column reaches it as a file.

## The rigs

```
python3 68k/test/emu/test_dtx.py
```

Packages every table of a corpus at each variant, assembles it with rmac,
and runs the image on a plain 68000 under emulation: every row through
advance and read, a jump to every row forward and backward, the repeat, the
end and a read before the first advance. It holds `d6`, `d7` and `a6`
across every call and a guard band past the row.

What a row should hold is read out of the `.dtx` file by a reader written
in the rig itself, which shares no code with the one under test. A DTX2
image is held to the plain file of the same table, which every variant
holds alike (R1.3).

It needs `mvn compile`, [rmac](http://rmac.is-slick.com) on the path or at
`$RMAC`, `pip install unicorn`, and an ST4 packer at `$ST4` for the packed
tables.

## Through Maven

Each tool has a Maven execution as well, which takes its arguments as one
string and builds every time:

```
mvn -q compile exec:exec@write -Dargs="in.csv out.dtx -v2"
mvn -q compile exec:exec@rewrite -Dargs="in.dtx out.dtx -k1"
```
