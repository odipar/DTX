# tools

Each tool is written three times, once in each tree, and the three write
the same bytes. `ParityTest` runs all of them over a corpus and compares
the files.

In Java each is a script under `bin/`. A script builds first where a source
is newer than the last build, and then runs the tool out of
`target/classes`. Paths reach the tool as the caller gave them, so a
relative one is relative to the caller's directory and not to this
repository's.

In Go each is a command under `go/cmd/`, built with `go build ./cmd/NAME`
under `go/`. None of them takes a wrapper: Go builds an executable, so
nothing has to find a runtime or a classpath before one runs, and no
`dtx-run` is needed.

In C# the three are one assembly. `dotnet dtx.dll <tool>` names the tool in
the first argument, and an executable published under a tool's own name is
that tool, with every argument its own.

Every tool prints its synopsis, a line a flag with the default in
parentheses, examples, and the section of this document that describes it
on `-help`, and prints the same to standard error where it is given no
file to work on. The three trees print one text, which `ParityTest` compares.

| what it does | Java | Go | C# |
|---|---|---|---|
| a table, from text or a DTX file, into a DTX file or text | `bin/dtx-write` | `dtx-write` | `dtx dtx-write` |
| a DTX file into a 68000 image | `bin/dtx-package` | `dtx-package` | `dtx dtx-package` |
| the twenty-two images the packager combines from | `bin/dtx-blobs` | `dtx-blobs` | `dtx dtx-blobs` |
| find the classpath and run one of the above | `bin/dtx-run` | none needed | none needed |

## Write

A table, out of a DTX file of any variant or comma separated text, into a
DTX file of any variant or text: one tool writes text as DTX, rewrites a
DTX file at another variant, unit or ring, and reads a DTX file out as
text.

```
bin/dtx-write in.csv out.dtx -v2 -k1 -m960
bin/dtx-write in.dtx out.dtx -k2 -copies
bin/dtx-write in.dtx out.csv
```

The first file is read as a DTX file where it opens with `DTX`, and as text
otherwise. The second is written as text where its name ends in `.csv`, and
as a DTX file otherwise. The table is the same under every variant (R1.3),
so what comes out of a DTX file has the rows, the width, `R` and `RR` of
what went in, and a DTX2 file written from a DTX2 file is that table packed
at the unit and ring the flags give. A DTX2 file is unpacked with the copy
of ST4 in this repository.

| flag | gives |
|---|---|
| `-vV` | the variant to write: 0, 1 or 2. The default is the variant read, or 0 for text |
| `-wW` | the bytes every value of the table takes: 1, 2 or 4, for text. The default is what the text's first comment gives, or else the narrowest width that takes every value of the table. A DTX file gives its own width |
| `-rRR` | the row the table repeats to, 0 to `R`. The default is what the DTX file or the text's first comment gives, or else `R`, where the table does not repeat |
| `-kK` | the unit a DTX2 column is packed at, and `R` times the width divides by it (R5.6). The default is 1 |
| `-mN` | the ring a DTX2 column unpacks through, in bytes, 1 to 65535 (R5.4). The default is 960 |
| `-pPACKER` | an ST4 executable to pack with, instead of the copy in this repository. Nothing needs one: name it to pack with a build newer than the copy |
| `-copies[S]` | a match beyond the ring copies from the column's own literal stream, and `-copiesS` searches `S` seconds for a better parse. It reaches the packer as `-c`. YMX spells it the same way |

`-k`, `-m`, `-p` and `-copies` reach a DTX2 file alone: no other variant
packs. Every column is packed with `-l65535` as well, which meets
ST4_wrap's assumption 4: no operation longer than the 65535 units a 68000
decoder counts in a word.

### The text

One row a line, one value a column. The first row of numbers gives `C`,
and every row after it has that many. A line that is blank, or whose
first character other than a space is `#`, is not a row, and neither is a
line before the first row of numbers in which no cell is a number: a line
of column names, or a line describing the table.

A value is decimal, or hexadecimal where it opens with `$`, and negative
where it opens with `-`. Every value of the table takes the same width `W`
(R6.3). A value of `W` bytes is stored most significant byte first, as every
field of the header is, and a negative one in two's complement. A value fits
`W` bytes where it lies from -2^(8W-1) to 2^(8W)-1, so one width takes a
signed column's values and an unsigned one's alike. DTX does not define more
of a column than its width, so which of the two a column is is defined
elsewhere or not defined.

```
# a time, a note and a step
0, $0100, -2
1, $0101, -1
2, $0102,  0
```

That table takes a width of 2: the width is the narrowest that takes every
value of the table rather than of a column, and column 1 has values from 256
up.

Written out, a table is a comment giving its shape, a line of column
names, `c0` onward, and then one row a line, each value the unsigned number
its bytes give. The three columns above come out as:

```
# 3 rows, 3 columns, width 2, RR 3
c0,c1,c2
0,256,65534
1,257,65535
2,258,0
```

Read back, the comment gives the width and the repeat where `-w` and `-r`
do not, and the names are passed over, so the text a table was written as
reads back to that table. A negative value comes out as the unsigned
number of the same bytes, 65534 for -2 in two bytes, and reads back to the
same bytes.

`org.dtx.Csv` is the same reader and writer as a library, and `Table`,
`Dtx0`, `Dtx1` and `Dtx2` write a table a caller builds itself and read one
out of a file.

### Copies from the literal stream

With `-copies` a match beyond the ring copies from the column's own literal
stream, and that saves most at the small rings DTX2 reads through. Measured
on a table of 512 rows repeating a pattern 37 rows long, at a width of 2 and
`N` of 64, where the pattern runs to 74 bytes and reaches past the ring: the
file goes from 2140 bytes to 356, and its image from 3300 to 1548.

**The payload defines it**, at byte 3 of its flags (SPEC.md 2.3, R5.10), so
Write is the one tool that reads `-copies` and the packager takes the
decoder the file needs. No packager has a flag for it.

The flag is there because a decoder built without the copy code reads such
a column wrongly and no ST4 data set defines which kind it is. Measured on
the same table, a column packed with copies and read by a decoder without
the copy code reads wrong at the row the pattern first repeats past the
ring, and right at every row before it (experiments.md). The other way round
is safe: a decoder with the copy code reads a column without copies as the
plain one does, at a few cycles more over 64 rows (performance.md) and 32 to
36 bytes more code (experiments.md).

## Package

A DTX file of any variant into a standalone 68000 image: the code, then the
table's bytes, reached PC relative. [abi.md](abi.md) defines the four calls
into the image, and the state block a caller supplies. It combines rather
than assembles. The code does not move with `R`, `C` or `RR`, so it is built
ahead of time: one build a variant, one a width under DTX1 and DTX2, and under
DTX2 one a unit and the copy code as well. The tool takes the image for the
build the table needs, writes the five fields the table gives into the format
block, and appends the table's bytes. No assembler runs, and a caller who
takes a release does not install one.

```
bin/dtx-package in.dtx out.bin
```

The Go one contains the twenty-two images, so it needs neither this
repository nor a runtime beside it:

```
go build -o dtx-package ./cmd/dtx-package    # under go/
./dtx-package in.dtx out.bin
```

| flag | gives |
|---|---|
| `-aRMAC` | assemble the template with this rmac rather than take the carried code. The two give the same bytes, and a template edit is tried through this one |
| `-s` | write the figures rather than the image, for reading or for a build of your own |

The image contains one table and the code for that table's variant, and under
DTX1 and DTX2 for that table's width. `R`, `C` and `RR` reach the code at run
time, out of the table's own header, so one build is one code at any of the
three. The width is not one of them under DTX1 and DTX2: a read moves a value
in one instruction, and a table of another width takes another build. Under
DTX0 a read is one run of bytes, so one build reads every width.

Under DTX0 and DTX1 the table's bytes follow the code with nothing between
them. Every column is one width and one length, so a pointer and a stride
reach them all and the packager does not write an entry a column. Under DTX2
one stream record a column stands there, four longs each, giving where that
column's four streams begin (abi.md 1). The tool prints the image's bytes
and the state block's, and the format block defines the same figures for a
caller to read out of the file.

## Build the images

The twenty-two files the packager combines from. DTX0 reads a row as one run
of bytes, so its code does not move with the width and one file is every
DTX0 table's. DTX1 moves a value a column, so it has one a width: three.
DTX2 has one a width and a build of the decoder in it, a unit of 1, 2 or 4
with the copy code and without: eighteen.

The build makes them, so nothing here is run by hand. `mvn package` writes
each of them three times:

| into | read by |
|---|---|
| the classes the jar is made of | the Java packager, off the classpath |
| `build/68k` | a release, which attaches all of them, and the C# assembly, which embeds them from there |
| `go/internal/image/data` | `go:embed`, which reads only inside its own module |
They are plain files and nothing about them is Java's, so a port in another
language builds from the same twenty-two. A Go executable built after the
Maven build contains all of them and needs neither this repository nor an
assembler beside it; one built from a tree whose build had not run does not
contain one, and resolves an image through `DTX_68K` instead. The directory
under `go/` contains a README and a `.gitignore` of its own and is committed
empty of images, so the package compiles either way.

This is the one step rmac is needed for. `-Drmac=PATH` names one that is
not on the path, and a build without either fails at it and names which.
A caller who takes a release does not run an assembler: the code is built
where it is released, not where a table is packaged.

```
bin/dtx-blobs DIR [DIR..]
```

writes the same twenty-two into directories of your own.

| flag | gives |
|---|---|
| `-aRMAC` | the assembler to run. The default is `rmac` on the path |
| `-tTEMPLATES` | where `68k/` stands. The default is `$DTX_68K`, or `68k` beside the caller. An executable run from outside this repository does not have a directory to resolve a relative one against, so it names this |

The table each build is assembled from is made rather than read: at the
build's own width the code is the same for any table, and the five fields
one would give are zeroed, so an image does not define a table until a
package writes one.

A DTX2 image contains the decoder carried at
[68k/ST4_wrap.S](../68k/ST4_wrap.S), built at the unit the payload defines.
Init fills every ring before it returns, and one column is refilled a row
after that, so a read takes one value from each ring and never decodes.
Every ring is `N` bytes and every column one width, so column `i`'s ring
stands `i` rings past column 0's and one pointer arithmetic runs them all
(R5.5). The packager takes the period from the table and fails the package
where no period meets every rule abi.md 4 defines: what it gives names the
rule and the figures that break it.

A DTX2 image needs more of the caller than a plain one. Its state block
contains a decoder state and a ring a column, so it runs to `NC` bytes
and more; the
tool prints the figure and the format block defines it.

## Release

The three Go commands for six platforms, each containing the twenty-two
images, and the images themselves:

```
release/publish.sh [version]
TARGETS="linux-x64" release/publish.sh
```

**No Java runs.** The images come from the Go `dtx-blobs`, which assembles
`68k/` with rmac, so the only tool this needs beside Go is that assembler.
`go build` cross-compiles to any target from any host, so one machine
covers Windows, macOS and Linux on both architectures.

It writes `dist/release`: one zip a platform, one zip of the twenty-two
images, all named by the release, and `MANIFEST.txt`, written by
`release/manifest.sh`, which gives every file's size and sha256 beside what
identifies it - a variant, a width, a unit and copies for an image, what it
contains for a zip - so one release's file is told from another's without
opening it. It builds `dtx-blobs` first, from a tree with no image, since
that is the one command that makes them rather than containing them; it
fails where fewer than twenty-two come out; and it ends by writing and
packaging a table with the host's own executables, from a directory that is
not this repository, so an executable with no image fails there rather than
in a release.

Writing DTX2 needs an ST4 packer, and each tree contains one:
`src/main/java/org/st4` and `dotnet/nt4`, both taken from
odipar/ST4@498aa25, and `go/internal/st4`, taken from odipar/YMX@498aa25,
which is that same packer in Go. None is edited here beyond one comment
naming where it came from, and the three pack the same bytes, which
`ParityTest` checks. So a release does not need a packer beside it
either. `-pPACKER` runs another where a caller has a newer build.

## The rigs

```
python3 68k/test/emu/test_dtx.py
```

Three kinds of check.

**The calls.** Every table of a corpus is packaged at each variant, with
the code the build made, and run on a plain 68000 under emulation: every
row through advance and read, a jump to every row forward and backward,
the repeat, the end and a read before the first advance. It checks `d6`,
`d7` and `a6` across every call and a guard band past the row, and it
watches every access: a word or long at an odd address is a fault here as
it is on a 68000, which the emulator's own model does not take.

**The round trip.** The same text through Write, through Package and
through the 68000 at DTX0, DTX1 and DTX2, compared with the rows the text
defines and with one another (R1.3). What a row should be is worked out in
the rig
itself, from the text, by a reader that does not share code with the one under
test, so neither the writer nor the 68000 is checked against itself. Under
DTX2 it counts the decoder's calls as well, and compares that with the
calls ST4_wrap's assumption 5 allows: a stopping rule under which a column
runs one call past its end marker does not change a byte a reader gives, and
shows up only in the count.

**The figures.** performance.md records what each call costs in 68000
cycles and what a build's code runs to in bytes, and the rig measures both
again and checks every cell of its tables against what it read.

It needs `mvn compile`, [rmac](http://rmac.is-slick.com) on the path or at
`$RMAC`, and `pip install unicorn`, which brings the emulator it runs the
code on. `$ST4` names a packer to pack with instead of the carried one.

`ParityTest` and `StabilityTest` run under `mvn test` with the rest.

**The three trees.** Every tool run in each of them over a corpus, and the
files compared byte for byte: text written at each variant, width and unit,
a plain file rewritten, the twenty-two images built, and a table packaged
across the variants, the widths and the units. One input has one output in
every tree. It needs Go, the .NET SDK and rmac, and is skipped without one of
them.

**The code a build assembles to.** A corpus a variant at a time, every
image's code compared with the first one's byte for byte, so R, C and RR
move the table and not the reader. The width does move it: DTX0's code is
one at every width, since a row is one run of bytes, and DTX1's is one a
width, no two of the three one code. Under DTX2 the eighteen builds are
grouped, no two of them one code, and the copy code's size is read back out
and compared with what doc/abi.md 5 gives. It needs rmac.

## The style check

`org.dtx.style.HouseStyle` reads every document and every code comment
this repository writes against `STRUCK.md`, the list of constructs struck
under the rules of `AGENTS.md`. A hit names the file, the line, the text
matched and the rule. `mvn test` runs it; so does

    java -cp target/classes org.dtx.style.HouseStyle

from the root of the tree, which exits with 1 where there was a hit. A
construct is added to `STRUCK.md`, not to the code: an entry is a pattern
over lowered prose with samples it is in and samples it is not in, and
`HouseStyleTest` reads every sample back.

## Through Maven

Each tool has a Maven execution as well, which takes its arguments as one
string and builds every time:

```
mvn -q compile exec:exec@write -Dargs="in.csv out.dtx -v2"
mvn -q compile exec:exec@write -Dargs="in.dtx out.dtx -k2 -copies"
```
