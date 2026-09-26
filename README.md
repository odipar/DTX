# DTX - a table format, and a 68000 reader for it

## Read this first

**AI wrote most of DTX.** Claude (Anthropic's Claude Code) wrote the three
tool trees, the 68000 reader, the tests, the emulation rig and most of what is
written here, under Robbert van Dalen's direction: he requested, read and
merged every change. [LICENSE](LICENSE) sets the terms, and its attribution
records who did what. This section informs the reader's decision to use
software written this way.

DTX builds on older work. [ST4](https://github.com/odipar/ST4),
which a DTX2 column is packed with, derives from Einar Saukas's
[ZX1](https://github.com/einar-saukas/ZX1) through
[ST1](https://github.com/odipar/ST1), and its 68000 decoder is carried here
rather than rewritten. The 68000 reader is measured against the timings in
Motorola's manual.

## What DTX is

DTX is a table format for programs on the Motorola 68000, the processor
of the Atari ST: a table of `R` rows and `C` columns, where every value is
one width, 1, 2 or 4 bytes, and the rows repeat at a row `RR`. The table is
data, and a reader of it is code.

A table is written on a PC from comma separated text, and packaged with a
68000 reader into one file, an image. A 68000 program includes the image,
steps through the rows with one call a row, and reads the values at the
address each call leaves.

DTX defines what the bytes are and what a reader reads out of them. What a
column means is left to the program, or to a format built on DTX.

## Getting started

The tools come as executables for Windows, macOS and Linux, on x64 and
arm64, from the [releases](https://github.com/odipar/DTX/releases). Each
one contains the twenty-two 68000 images, and runs with no assembler,
packer or runtime beside it.

A table starts as text: one row a line and one value a column. A value is
decimal, or hexadecimal where it opens with `$`, and negative where it
opens with `-`. A reader skips a blank line, a line that opens with `#`,
and a line of column names before the first row of numbers:

```
# a time, a note and a step
time, note, step
0, $0100, -2
1, $0101, -1
2, $0102,  0
```

Write it as a DTX file, then package that as an image:

```bash
dtx-write -v1 -w2 < table.csv > table.dtx
dtx-package < table.dtx > table.bin
```

Read a DTX file back out as text, which is how one is inspected:

```bash
dtx-write -text < table.dtx > back.csv
```

Convert between variants, or pack one at a unit and ring you set:

```bash
dtx-write -v2 -k2 -m960 < table.dtx > packed.dtx
```

There are three tools, and [tools.md](doc/tools.md) lists every flag of
each. `-help` on any of them prints its usage and examples.

| tool | what it does |
|---|---|
| `dtx-write` | text or a DTX file in, a DTX file or text out |
| `dtx-package` | a DTX file into a standalone 68000 image |
| `dtx-blobs` | the twenty-two images, one file a build |

## Reading a table on a 68000

An image is the reader code for the table's variant, the table's bytes, and
four calls. The calls reach the table by addresses relative to the program
counter, so an image runs wherever it is loaded, with or without an
operating system.

```
        bsr     DTX_advance     ; onto the next row
        move.w  (a1),d0                 ; column 0
        move.w  DTX_STRIDE(a1),d1       ; column 1
        move.w  DTX_STRIDE*2(a1),d2     ; column 2
```

An advance leaves the address of the row's first value, and the stride
`DTX_metadata` reports reaches the next column's. The image finds a row and
the caller reads the values in place. On 64 rows of three two byte columns
an advance costs 70 cycles under DTX0 and 66 under DTX1, counted with no
wait state, so an Atari ST runs longer. [abi.md](doc/abi.md) is the calling
convention and [performance.md](doc/performance.md) has what every call
costs, measured.

One width a table, so the code is built for the width and no call tests it:
twenty-two builds, one for DTX0, which reads a row as one run of bytes at
any width, three for DTX1, and eighteen for DTX2, three widths by the three
units its columns are packed at, with the copy code and without.

## The three variants

A file declares which of three variants lays its table out:

| variant | the payload | for |
|---|---|---|
| DTX0 | row by row | a reader that reads whole rows |
| DTX1 | column by column | a reader that reads one column of many |
| DTX2 | column by column, each packed as an ST4 data set | a table too large to keep unpacked |

Every one is the same table ([requirements.md](doc/requirements.md) R1.3),
so a file converts between them without loss. [SPEC.md](doc/SPEC.md)
defines the bytes. DTX2 packs each column with
[ST4](https://github.com/odipar/ST4), and a reader of it unpacks a column a
part at a time, through a ring of `N` bytes.

## Words used here

| word | definition |
|---|---|
| table | `R` rows, `C` columns wide, every value `W` bytes, repeating at `RR` |
| row | one step of a table: `C` values |
| column | one field of a row, `W` bytes wide |
| `RR` | the row a table repeats to once the last row is done; `RR` equal to `R` marks a table that does not repeat |
| variant | one way of laying a table's rows out in bytes |
| image | a table packaged for the 68000: the code and the table's bytes in one file, read through four calls |
| stride | the bytes from one column, ring or decoder state to the next |
| ring | the bytes of a column a reader has at a time, `N` of them, in place of the unpacked column |
| unit | the width ST4 packs whole numbers of: 1, 2 or 4 bytes |

## What's here

| source | contents |
|---|---|
| [`doc/`](doc) | the specification, the requirements it is written against, and the rest |
| [`src/main/java/org/dtx/`](src/main/java/org/dtx) | the three tools in Java, the reference |
| [`go/`](go), [`dotnet/`](dotnet) | the same three in Go and C#, the executables a release ships |
| [`68k/`](68k) | the 68000 reader, one template a variant ([abi.md](doc/abi.md)) |
| [`68k/test/emu/`](68k/test/emu) | the emulation rig, which counts cycles as Motorola's manual counts them |
| [`bin/`](bin) | the Java tools, run out of a build |
| [`release/`](release) | the scripts that build and list a release ([RELEASES.md](doc/RELEASES.md)) |

The Java and C# trees contain a copy of ST4, the packer a DTX2 column is
packed with, so neither needs one beside it, and [`68k/`](68k) contains the
ST4 decoder the same way. The Go tree reads the library from
[ST4](https://github.com/odipar/ST4) as a module instead, since that
repository publishes one.

## Tests

The three trees write the same bytes. A test runs every tool in each of them
over one corpus and compares the files byte for byte, so a difference
between trees fails a build rather than reaching a release.

| what runs | what it checks |
|---|---|
| the Java tests | the format, the tools and the packager |
| the Go and C# suites | each tree against itself |
| [`68k/test/emu/test_dtx.py`](68k/test/emu/test_dtx.py) | the 68000 reader under emulation, every row against the text the table came from |
| [the conformance kit](doc/conformance) | 19 tables an independent reader is written against |
| the style check | the house style [AGENTS.md](AGENTS.md) defines, over every document and comment |

The rig counts 68000 cycles as Motorola's manual counts them, and reads
every figure in [performance.md](doc/performance.md) back out of the
document, so a stale figure fails.

```bash
mvn test -Drmac=/usr/local/bin/rmac
```

## The documents

[requirements.md](doc/requirements.md) comes first. Nothing else is
written until it defines what DTX has to do.

| | |
|---|---|
| [requirements.md](doc/requirements.md) | what the format has to do |
| [SPEC.md](doc/SPEC.md) | the format specification |
| [glossary.md](doc/glossary.md) | every term, one line each |
| [terminology.md](doc/terminology.md) | the same terms explained |
| [tools.md](doc/tools.md) | every tool's usage, flags and environment, in three trees |
| [abi.md](doc/abi.md) | the calls a packaged table is read by, on the 68000 |
| [performance.md](doc/performance.md) | what each call costs, in 68000 cycles, measured |
| [experiments.md](doc/experiments.md) | what was measured against real tables, and what came out |
| [BINARIES.md](doc/BINARIES.md) | the twenty-two 68000 images, and how a tool combines one with a table |
| [RELEASES.md](doc/RELEASES.md) | what a release contains, and what changed in each |
| [doc/conformance/](doc/conformance) | the kit an independent reader is written against |

[AGENTS.md](AGENTS.md) is the house style every document and comment
follows, and [STRUCK.md](STRUCK.md) lists what it strikes.

## Related repositories

[ST4](https://github.com/odipar/ST4) is the compression a DTX2 column is
packed with. [YMXR](https://github.com/odipar/YMXR) is a format built on
DTX, for music on the Atari ST. [YMX](https://github.com/odipar/YMX) is the
family this repository belongs to: a design document defining how YMXS,
YMXR, DTX and ST4 fit together.

## License and attribution

The format may be implemented freely. [SPEC.md](doc/SPEC.md) is the
contract, and an independent reader or writer owes only the
acknowledgement.

The readers, writers and tests under `src/`, `go/`, `dotnet/` and `68k/`
can be used freely within your programs, for any platform, including
commercial releases, on the one condition that your documentation indicates
somewhere that you have used DTX. See [LICENSE](LICENSE) for the whole of
it, and for the BSD 3-Clause License that covers the carried ST4 compressor.

DTX, its specification and its tests are © 2026 Robbert van Dalen, written
by Claude (Anthropic's Claude Code) under Robbert's direction.

[ST4](https://github.com/odipar/ST4) is a separate format, © 2026 Robbert
van Dalen, and its compressor is derived from
[ZX1](https://github.com/einar-saukas/ZX1) by Einar Saukas through
[ST1](https://github.com/odipar/ST1). Reading a DTX2 file means using ZX1
through ST4, which your documentation indicates in the same way.
