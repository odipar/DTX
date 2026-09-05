# tools

Each tool is written twice, once in each tree, and the two write the same
bytes. `test/test_parity.py` runs both over a corpus and holds them to it.

In Java each is a script under `bin/`. A script builds first where a source
is newer than the last build, and then runs the tool out of
`target/classes`. Paths reach the tool as the caller gave them, so a
relative one is relative to the caller's directory and not to this
repository's.

In Go each is a command under `go/cmd/`, built with `go build ./cmd/NAME`
under `go/`. None of them takes a wrapper: Go builds an executable, so
nothing has to find a runtime or a classpath before one runs, and there is
no `dtx-run` to write.

| what it does | Java | Go |
|---|---|---|
| text into a DTX file | `bin/dtx-write` | `dtx-write` |
| a plain file into a DTX2 one | `bin/dtx-rewrite` | `dtx-rewrite` |
| a DTX file into a 68000 image | `bin/dtx-package` | `dtx-package` |
| the eight images the packager combines from | `bin/dtx-blobs` | `dtx-blobs` |
| find the classpath and run one of the above | `bin/dtx-run` | none needed |

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
| `-pPACKER` | an ST4 executable to pack with, instead of the copy held here. Nothing needs one: name it to pack with a build newer than the copy |
| `-copies[S]` | a match beyond the ring copies from the column's own literal stream, and `-copiesS` searches `S` seconds for a better parse. It reaches the packer as `-c`. YMX spells it the same way |

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
the table's bytes, reached PC relative. [abi.md](abi.md) states the six
calls the image answers, and the state block a caller supplies.

It combines rather than assembles. The code does not move with the table,
so it is built once and the tool takes the image for the build the table
asks for, writes the five fields the table settles into the format block,
and appends the column table and the table's bytes. No assembler runs,
and a caller who takes a release installs none.

```
bin/dtx-package in.dtx out.bin
```

The Go one holds the eight images inside it, so it needs neither this
repository nor a runtime beside it:

```
go build -o dtx-package ./cmd/dtx-package    # under go/
./dtx-package in.dtx out.bin
```

| flag | gives |
|---|---|
| `-aRMAC` | assemble the template with this rmac rather than take the carried code. The two give the same bytes, and a template edit is tried through this one |
| `-s` | write the figures rather than the image, for reading or for a build of your own |

The image holds one table and the code for that table's variant. What the
table settles reaches the code at run time, out of the table's own header
and the column table behind it, so one variant is one code at any `R`, `C`
or `RR`. The tool prints the image's bytes and the state block's, and the
format block states the same figures for a caller to read out of the file.

## Build the images

The eight files the packager combines from: DTX0, DTX1, and one a build of
the decoder built into DTX2, which is a unit of 1, 2 or 4 with the copy
code and without.

The build makes them, so nothing here is run by hand. `mvn package` writes
each of them three times:

| into | read by |
|---|---|
| the classes the jar is made of | the Java packager, off the classpath |
| `build/68k` | a release, which attaches the eight |
| `go/internal/image/data` | `go:embed`, which reads only inside its own module |

They are plain files and nothing about them is Java's, so a port in another
language builds from the same eight. A Go executable built after the Maven
build holds all eight and needs neither this repository nor an assembler
beside it; one built from a tree whose build had not run holds none, and
resolves an image through `DTX_68K` instead. The directory under `go/` holds
a README and a `.gitignore` of its own and is committed empty of images, so
the package compiles either way.

This is the one step rmac is needed for. `-Drmac=PATH` names one that is
not on the path, and a build without either fails at it, saying so. A
caller who takes a release runs no assembler at all, which is the whole
of the arrangement: the code is built where it is released, not where a
table is packaged.

```
bin/dtx-blobs DIR [DIR..]
```

writes the same eight into directories of your own.

| flag | gives |
|---|---|
| `-aRMAC` | the assembler to run. The default is `rmac` on the path |
| `-tTEMPLATES` | where `68k/` stands. The default is `$DTX_68K`, or `68k` beside the caller. An executable run from outside this repository has no directory to resolve a relative one against, so it names this |

The table each build is assembled from is made rather than read: the code
does not move with a table, and the five fields one would settle are
zeroed, so an image states no table at all until a package writes one.

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
| `-pPACKER` | as Write reads it |
| `-copies[S]` | as Write reads it |

Rewrite keeps no packer of its own. `-p` names the one ST4's own repository
builds, and a column reaches it as a file.

Every column is packed with `-l65535` as well, which holds ST4_wrap's
assumption 4: no operation longer than the 65535 units a 68000 decoder
counts in a word.

### Copies from the literal stream

`-copies` lets a match beyond the ring copy from the column's own literal
stream, and it pays at the small rings DTX2 reads through. Measured
on a table of 512 rows repeating a pattern 37 rows long, at `N` of 64: the
file goes from 1164 bytes to 272, and its image from 2196 to 1336.

**The payload states it**, at byte 3 of its flags (SPEC.md 2.3, R5.10), so
Write is the one tool that reads `-copies` and the packager takes the
decoder the file asks for. Neither packager has a flag for it.

The flag is there because a decoder built without the copy code reads such
a column wrongly and no ST4 data set says which kind it is. Measured on the
same table, a column packed with copies and read by a decoder without the
copy code gives row 37 wrong, where the pattern first repeats past the
ring. The other way round is safe: a decoder with the copy code reads a
column without copies correctly, at 2.0 to 4.0% more cycles and 32 bytes
more code.

## Release

The four Go commands for six platforms, each holding the eight images, and
the images themselves:

```
release/publish.sh [version]
TARGETS="linux-x64" release/publish.sh
```

**No Java runs.** The images come from the Go `dtx-blobs`, which assembles
`68k/` with rmac, so the only tool this needs beside Go is that assembler.
`go build` cross-compiles to any target from any host, which is why one
machine covers Windows, macOS and Linux on both architectures.

It writes `dist/release`: one zip a platform and the eight images, both
named by the release. It builds `dtx-blobs` first, from a tree holding no
image, since that is the one command that makes them rather than holding
them; it fails where fewer than eight come out; and it ends by writing and
packaging a table with the host's own executables, from a directory that is
not this repository, so an executable holding no image fails there rather
than in a release.

Writing DTX2 asks for an ST4 packer, and both trees hold a copy of one:
`src/main/java/org/st4`, taken from odipar/ST4@498aa25 and not edited here.
So a release needs no packer beside it either. `-pPACKER` runs another
where a caller has a newer build.

## The rigs

```
python3 68k/test/emu/test_dtx.py
```

Two kinds of check.

**The calls.** Every table of a corpus is packaged at each variant,
assembled with rmac, and run on a plain 68000 under emulation: every row
through advance and read, a jump to every row forward and backward, the
repeat, the end and a read before the first advance. It holds `d6`, `d7`
and `a6` across every call and a guard band past the row.

**The round trip.** The same text through Write, through Package and
through the 68000 at DTX0, DTX1 and DTX2, held to the rows the text states
and to one another (R1.3). What a row should hold is worked out in the rig
itself, from the text, by a reader that shares no code with the one under
test, so neither the writer nor the 68000 is checked against itself. Under
DTX2 it counts what the decoder is asked for as well, and holds that to the
calls ST4_wrap's assumption 5 allows: a stopping rule that lets a column
run one call past its end marker changes no byte a reader gives, and shows
up only in the count.

It needs `mvn compile`, [rmac](http://rmac.is-slick.com) on the path or at
`$RMAC`, `pip install unicorn`, and an ST4 packer at `$ST4` for the packed
tables.

```
python3 test/test_parity.py
```

**The two trees.** Every tool run both ways over a corpus, and the files
held to the same bytes: text written at each variant and each unit, a plain
file rewritten, the eight images built, and fourteen tables packaged, which
reach every image the packager picks from. One input has one output,
whichever tree a caller took.

It needs `mvn package`, Go on the path, rmac on it or at `$RMAC`, and an
ST4 packer at `$ST4`.

## Through Maven

Each tool has a Maven execution as well, which takes its arguments as one
string and builds every time:

```
mvn -q compile exec:exec@write -Dargs="in.csv out.dtx -v2"
mvn -q compile exec:exec@rewrite -Dargs="in.dtx out.dtx -k1"
```
