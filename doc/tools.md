# tools

Each tool is written three times, once in each tree, and the three write
the same bytes. `ParityTest` runs all of them over a corpus and compares
the files.

`dtx-write` and `dtx-package` read standard input and write standard
output; their reports and their faults go to standard error, and
`dtx-blobs` writes its twenty-two images into the directories it is named.
So a conversion composes in a pipe:

```
bin/dtx-write -v2 -k1 < in.csv | bin/dtx-package > out.bin
```

The exits are three: 0 the tool completed, 1 the input is wrong, 2 the call
is wrong. Every tool prints on `-help` its synopsis, a line a flag with the
default in parentheses, examples, and the section of this document that
describes it.

| what it does | Java | Go | C# |
|---|---|---|---|
| a table, from text or a DTX file, into a DTX file or text | `bin/dtx-write` | `dtx-write` | `dtx dtx-write` |
| a DTX file into a 68000 image | `bin/dtx-package` | `dtx-package` | `dtx dtx-package` |
| the twenty-two images the packager combines from | `bin/dtx-blobs` | `dtx-blobs` | `dtx dtx-blobs` |
| find the classpath and run one of the above | `bin/dtx-run` | none needed | none needed |

In Java each is a script under `bin/` that builds where a source is newer
than the last build and then runs out of `target/classes`; paths reach the
tool as the caller wrote them. In Go each is a command under `go/cmd/`,
built with `go build ./cmd/NAME`, and Go builds an executable, so no
wrapper finds a runtime first. In C# the three are one assembly, and
`dotnet dtx.dll <tool>` names the tool in the first argument.

The Go `dtx-package` combines and does not assemble, so its help lists
neither `-a` nor `-s`; the other helps are one text in all three trees.

## Write

A table, out of a DTX file of any variant or comma separated text, into a
DTX file of any variant or text: one tool writes text as DTX, rewrites a
DTX file at another variant, unit or ring, and reads a DTX file out as
text.

```
bin/dtx-write -v2 -k1 -m960 < in.csv > out.dtx
bin/dtx-write -k2 -copies < in.dtx > out.dtx
bin/dtx-write -text < in.dtx > out.csv
```

The input is read as a DTX file where it opens with `DTX`, and as text
otherwise; the output is text under `-text` and a DTX file otherwise. The
table is the same under every variant (R1.3), so what comes out of a DTX
file has the rows, the width, `R` and `RR` of what went in. A DTX2 file is
unpacked with the copy of ST4 in this repository.

| flag | what it sets |
|---|---|
| `-vV` | the variant to write: 0, 1 or 2. The default is the variant read, or 0 for text |
| `-wW` | the bytes every value of the table is written in: 1, 2 or 4, for text. The default is the width the text's first comment declares, or else the narrowest width that fits every value of the table. A DTX file declares its width |
| `-rRR` | the row the table repeats to, 0 to `R`. The default is the row the DTX file or the text's first comment declares, or else `R`, where the table does not repeat |
| `-kK` | the unit a DTX2 column is packed at, and `R` times the width divides by it (R5.6). The default is 1 |
| `-mN` | the ring a DTX2 column unpacks through, in bytes, 1 to 65535 (R5.4). The default is 960 |
| `-pPACKER` | an ST4 executable to pack with, instead of the copy in this repository. Nothing needs one: name it to pack with a build newer than the copy |
| `-copies[S]` | a match beyond the ring copies from the column's literal stream, and `-copiesS` searches `S` seconds for a better parse. It reaches the packer as `-c`. YMX spells it the same way |

`-k`, `-m`, `-p` and `-copies` reach a DTX2 file alone: no other variant
packs. Every column is packed with `-l65535` as well (abi.md 5).

### The text

One row a line, one value a column. The first row of numbers defines `C`,
and every row after it has that many. A line that is blank, or whose first
character other than a space is `#`, is not a row, and neither is a line
before the first row of numbers in which no cell is a number: a line of
column names, or a line describing the table.

A value is decimal, or hexadecimal where it opens with `$`, and negative
where it opens with `-`. Every value of the table is the same width `W`
(R6.3), stored most significant byte first as every field of the header is,
and a negative one in two's complement. A value fits `W` bytes where it
lies from -2^(8W-1) to 2^(8W)-1, so one width fits a signed column's values
and an unsigned one's alike. DTX does not define more of a column than its
width, so which of the two a column is is defined elsewhere.

```
# a time, a note and a step
time, note, step
0, $0100, -2
1, $0101, -1
2, $0102,  0
```

The first two lines are not rows, and the column names a reader prints are
the reader's, a DTX file defining none. That table needs a width of 2: the
width fits every value of the table rather than of a column, and column 1
has values from 256 up.

Written out, a table is a comment declaring its shape, a line of names `c0`
onward, and one row a line, each value the unsigned number its bytes stand
for:

```
# 3 rows, 3 columns, width 2, RR 3
c0,c1,c2
0,256,65534
1,257,65535
2,258,0
```

Read back, the comment declares the width and the repeat where `-w` and
`-r` do not, and the names are passed over, so the text a table was written
as reads back to that table. A negative value comes out as the unsigned
number of the same bytes, 65534 for -2 in two bytes.

`org.dtx.Csv` is the same reader and writer as a library, and `Table`,
`Dtx0`, `Dtx1` and `Dtx2` write a table a caller builds itself.

### Copies from the literal stream

With `-copies` a match beyond the ring copies from the column's literal
stream, which saves most at the small rings DTX2 reads through. Measured on
a table of 512 rows repeating a pattern 37 rows long, at a width of 2 and
`N` of 64, where the pattern runs to 74 bytes and reaches past the ring:
the file goes from 2140 bytes to 356, and its image from 3616 to 1860.

**The payload defines it**, at byte 3 of its flags (SPEC.md 2.3, R5.10), so
Write is the one tool that reads `-copies` and the packager uses the
decoder the file needs. What the flag is for, and what a decoder without
the copy code reads instead, is abi.md 5; what it costs is 28 to 36 bytes
of code (experiments.md) and a few cycles over 64 rows (performance.md).

## Package

A DTX file of any variant into a standalone 68000 image: the code, then the
table's bytes, reached PC relative. [abi.md](abi.md) defines the four calls
and the state block a caller supplies.

```
bin/dtx-package < in.dtx > out.bin
bin/dtx-package a.dtx b.dtx > both.bin
```

It combines rather than assembles: it reads the image for the build the
table needs, writes the six fields the table defines into the format block,
and appends the table's bytes. No assembler runs, so a caller who unpacks a
release does not install one. The Go tool contains the twenty-two images
and so needs neither this repository nor a runtime beside it; it combines
only, and reads neither flag below.

| flag | what it does |
|---|---|
| `-aRMAC` | assemble the template with this rmac rather than read the image the build made. The templates are read from `68k` beside the caller, or from what `DTX_68K` names. The two assemble to the same bytes, and a template edit is tried through this one |
| `-s` | write the figures rather than the image, for reading or for a build you make |

Under DTX0 and DTX1 the table's bytes follow the code with nothing between
them, every column being one width and one length. Under DTX2 one stream
record a column stands there, four longs each (abi.md 1). The tool prints
the image's bytes and the state block's, which the format block also
defines for a caller reading the file.

A DTX2 image asks more of the caller: its state block contains a decoder
state a turn and a ring a column, so it runs to `NC` bytes and more. The
packager forms the period from the table and fails the package where none
meets every rule abi.md 4 defines, naming the rule and the figures that
break it.

## Build the images

The twenty-two the packager combines from. `R`, `C` and `RR` reach the code
at run time, so one build reads any shape; the width does move it, and
under DTX2 the unit and the copy code with it:

| variant | builds | why |
|---|---:|---|
| DTX0 | 1 | a row is one run of bytes, so the width does not move the code |
| DTX1 | 3 | one a width |
| DTX2 | 18 | one a width, a unit of 1, 2 or 4, with the copy code and without |

The build makes them, so nothing here is run by hand. `mvn package` writes
each three times:

| into | read by |
|---|---|
| the classes the jar is made of | the Java packager, off the classpath |
| `build/68k` | the C# assembly, which embeds them from there |
| `go/image/data` | `go:embed`, which reads only inside its module |

They are plain files and nothing about them is Java's, so a port in another
language builds from the same twenty-two. A Go executable built after the
Maven build contains all of them; one built from a tree whose build had not
run resolves an image through `DTX_68K` instead, and `go/image/data` is
committed empty so the package compiles either way.

This is the one step rmac is needed for. `-Drmac=PATH` names one that is
not on the path, and a build without either fails at it and names which.

```
bin/dtx-blobs DIR [DIR..]
```

writes the same twenty-two into directories you name.

| flag | what it sets |
|---|---|
| `-aRMAC` | the assembler to run. The default is `rmac` on the path |
| `-tTEMPLATES` | where `68k/` stands. The default is `$DTX_68K`, or `68k` beside the caller. An executable run from outside this repository does not have a directory to resolve a relative one against, so it names this |

The table each build is assembled from is made rather than read: at the
build's width the code is the same for any table, and the six fields a
package fills are zeroed, so an image does not define a table until a
package writes one. A DTX2 image contains the decoder carried at
[68k/ST4_wrap.S](../68k/ST4_wrap.S), built at the unit the payload defines.

## Release

The three Go commands for six platforms, each containing the twenty-two
images, and the images themselves:

```
release/publish.sh [version]
TARGETS="linux-x64" release/publish.sh
```

**No Java runs.** The images come from the Go `dtx-blobs`, which assembles
`68k/` with rmac, so the only tool this needs beside Go is that assembler,
and `go build` cross-compiles to any target from any host.

It writes `dist/release`: one zip a platform, one zip of the twenty-two
images, and `MANIFEST.txt` from `release/manifest.sh`, which lists every
file's size and sha256 beside what identifies it, so one release's file is
told from another's without opening it. It builds `dtx-blobs` first, from a
tree with no image, that being the one command that makes them rather than
containing them; it fails where fewer than twenty-two come out; and it ends
by writing and packaging a table with the host's executables from outside
this repository, so an executable with no image fails there rather than in
a release.

Writing DTX2 needs an ST4 packer. The Java and C# trees contain one,
`src/main/java/org/st4` and `dotnet/nt4`, both copied from
odipar/ST4@6341b8f and edited nowhere here. The Go tree requires
`github.com/odipar/st4/go` instead; `go/st4` has what belongs to DTX alone,
a `Packer` that packs in this process and a `Beside` that runs an ST4
executable. The three pack the same bytes, which `ParityTest` checks, so a
release does not need a packer beside it either.

## The rigs

`.github/workflows/test.yml` runs `mvn test` on a GitHub runner, with
Go, the .NET SDK, rmac 2.4.3 and ST4's packer on it so that no check
skips: the parity check of the three trees reads the first three, and
`St4Test` reads the copy of the packer here against the real one. No
push starts it: a caller starts it from the Actions tab or by `gh
workflow run test.yml`. The rig below runs by hand as well.

```
python3 68k/test/emu/test_dtx.py
```

It needs `mvn compile`, [rmac](http://rmac.is-slick.com) on the path or at
`$RMAC`, and `pip install unicorn`. `$ST4` names a packer to pack with
instead of the carried one. Three kinds of check:

**The calls.** Every table of a corpus packaged at each variant and run on
a plain 68000 under emulation: every row through advance and read, a jump
to every row forward and backward, the repeat, the end, and a read before
the first advance. It checks `d6`, `d7` and `a6` across every call and a
guard band past the row, and watches every access, a word or long at an odd
address being a fault here as it is on a 68000 and not in the emulator's
model.

**The round trip.** The same text through Write, through Package and
through the 68000 at DTX0, DTX1 and DTX2, compared with the rows the text
defines and with one another (R1.3). What a row should be is worked out in
the rig from the text, by a reader sharing no code with the one under test.
Under DTX2 it counts the decoder's calls against what ST4_wrap's assumption
5 allows.

**The figures.** performance.md records what each call costs in 68000
cycles and what a build's code runs to in bytes, and the rig measures both
again and checks every cell against what it read.

`ParityTest` and `StabilityTest` run under `mvn test`:

**The three trees.** Every tool run in each over a corpus and the files
compared byte for byte: text written at each variant, width and unit, a
plain file rewritten, the twenty-two images built, and a table packaged
across the variants, the widths and the units. It needs Go, the .NET SDK
and rmac, and is skipped without one of them. The C# tree is tested from
here alone, so this is its check; its corpus is one table of decimal values
under a comment, so no hexadecimal value, negative value, names line or
shape comment is read back in any tree, and no run with `-s`, `-a` or `-p`
is compared.

**The code a build assembles to.** A corpus a variant at a time, every
image's code compared with the first one's byte for byte, so `R`, `C` and
`RR` move the table and not the reader. The eighteen DTX2 builds are
grouped, no two of them one code, and the copy code's size is read back and
compared with what abi.md 5 records. It needs rmac.

## The style check

`org.dtx.style.HouseStyle` reads every document and every code comment this
repository writes against `STRUCK.md`, the constructs struck under the
rules of `AGENTS.md`. A hit names the file, the line, the text matched and
the rule. `mvn test` runs it; so does

    java -cp target/classes org.dtx.style.HouseStyle

from the root of the tree, which exits with 1 where there was a hit. A
construct is added to `STRUCK.md`, not to the code: an entry is a pattern
over lowered prose with samples it is in and samples it is not in, and
`HouseStyleTest` reads every sample back.

## Through Maven

Write and Blobs have a Maven execution as well. An execution reads its
arguments as one string and builds every time:

```
mvn -q compile exec:exec@write -Dargs="in.csv out.dtx -v2"
mvn -q compile exec:exec@write -Dargs="in.dtx out.dtx -k2 -copies"
```
