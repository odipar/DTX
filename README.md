# DTX - a table format, and a 68000 reader for it

DTX is a data format: a table of `R` rows and `C` columns, where every value
takes one width, 1, 2 or 4 bytes, and the rows repeat at a row `RR`.

## Read this first

**AI wrote most of DTX.** Claude (Anthropic's Claude Code) wrote the three
tool trees, the 68000 reader, the tests, the emulation rig and most of what
is written here, under Robbert van Dalen's direction. The attribution
section below says who did what. If you would rather not use software
written that way, this is not the repository for you, and nothing here is
meant to talk you out of that.

What it is built on is not new. ST4, which a DTX2 column is packed with, is
derived from Einar Saukas's ZX1 through ST1, and its 68000 decoder is
carried here rather than rewritten. The 68000 reader is measured against
the timings in Motorola's own manual.

## What the format is

The format is data. A compile step and a calling convention belong to a
reader and not to the format, so the specification defines what the bytes
are and what a reader takes out of them, and no more than that.

DTX does not define what a column contains. A format built on DTX defines
that, in its own repository and against what this one defines.

Three variants lay one table out three ways, and a file names which:

| variant | the payload | for |
|---|---|---|
| DTX0 | row by row | a reader that takes whole rows |
| DTX1 | column by column | a reader that takes one column of many |
| DTX2 | column by column, each packed as an ST4 data set | a table too large to keep unpacked |

Every one is the same table (R1.3), so a file converts between them without
loss. [doc/SPEC.md](doc/SPEC.md) defines the bytes.

## Usage

The tools come as executables for Windows, macOS and Linux, on x64 and
arm64, from the [releases](https://github.com/odipar/DTX/releases). Each
one contains the twenty-two 68000 images, so nothing is installed beside
it: no assembler, no packer, no runtime.

Write a table from comma separated text:

```bash
dtx-write -v1 -w2 < table.csv > table.dtx
```

Package it as a standalone 68000 image, the code and the table in one file:

```bash
dtx-package < table.dtx > table.bin
```

Read it back out as text, which is how a DTX file is inspected:

```bash
dtx-write -text < table.dtx > back.csv
```

Convert between variants, or pack one at a unit and ring of your own:

```bash
dtx-write -v2 -k2 -m960 < table.dtx > packed.dtx
```

There are three tools, and [doc/tools.md](doc/tools.md) gives every flag of
each. `-help` on any of them prints its own usage and examples.

| tool | what it does |
|---|---|
| `dtx-write` | text or a DTX file in, a DTX file or text out |
| `dtx-package` | a DTX file into a standalone 68000 image |
| `dtx-blobs` | the twenty-two images, one file a build |

## Reading a table on a 68000

A packaged image is the code for the table's variant, the table's bytes,
and four calls that reach them PC relative. No relocation, no operating
system, nothing allocated while it runs.

```
        bsr     DTX_advance     ; onto the next row
        move.w  (a1),d0                 ; column 0
        move.w  DTX_STRIDE(a1),d1       ; column 1
        move.w  DTX_STRIDE*2(a1),d2     ; column 2
```

An advance gives the address of the row's first value, and the stride
`DTX_metadata` gives reaches the next column's. No call copies a value: the
image finds a row and the caller reads it. On 64 rows of three two byte
columns an advance costs 70 cycles under DTX0 and 66 under DTX1.
[doc/abi.md](doc/abi.md) is the calling convention and
[doc/performance.md](doc/performance.md) has what every call costs,
measured.

One width a table, so the code is built for the width and no call tests it:
twenty-two builds, one for DTX0, which reads a row as one run of bytes at
any width, three for DTX1, and eighteen for DTX2, three widths by the three
units its columns are packed at, with the copy code and without.

## What's here

| | |
|---|---|
| `doc/` | the specification, the requirements it is written against, and the rest |
| `src/main/java/org/dtx/` | the Java tools |
| `go/`, `dotnet/` | the same tools in Go and C# |
| `68k/` | the 68000 reader, one template a variant |
| `68k/test/emu/` | the emulation rig |
| `bin/` | the Java tools, run out of a build |
| `release/` | what builds a release |

Each tree contains a copy of ST4, the packer a DTX2 column is packed with,
so none needs one beside it. `68k/` contains the ST4 decoder the same way.

## Tests

The three trees write the same bytes. A test runs every tool in each of them
over one corpus and compares the files byte for byte, so a difference
between trees fails a build rather than reaching a release.

| what runs | what it checks |
|---|---|
| 101 Java tests | the format, the tools, the packager, and every figure the documents give |
| the Go and C# suites | each tree against itself |
| `68k/test/emu/test_dtx.py` | the 68000 reader under emulation, every row against the text the table came from |
| the conformance kit | 19 tables an independent reader is written against |
| the style check | the house style AGENTS.md defines, over every document and comment |

The rig counts 68000 cycles as Motorola's manual counts them, and reads
every figure in [doc/performance.md](doc/performance.md) back out of the
document, so a stale figure fails.

```bash
mvn test -Drmac=/usr/local/bin/rmac
```

## The documents

[doc/requirements.md](doc/requirements.md) comes first. Nothing else is
written until it defines what DTX has to do.

| | |
|---|---|
| [doc/requirements.md](doc/requirements.md) | what the format has to do |
| [doc/SPEC.md](doc/SPEC.md) | the format specification |
| [doc/glossary.md](doc/glossary.md) | every term, one line each |
| [doc/terminology.md](doc/terminology.md) | the same terms explained |
| [doc/tools.md](doc/tools.md) | every tool's usage, flags and environment, in three trees |
| [doc/abi.md](doc/abi.md) | the calls a packaged table is read by, on the 68000 |
| [doc/performance.md](doc/performance.md) | what each call costs, in 68000 cycles, measured |
| [doc/experiments.md](doc/experiments.md) | what was measured against real tables, and what came out |
| [doc/BINARIES.md](doc/BINARIES.md) | the twenty-two 68000 images, and how a tool combines one with a table |
| [doc/RELEASES.md](doc/RELEASES.md) | what a release contains, and what changed in each |
| [doc/conformance/](doc/conformance) | the kit an independent reader is written against |

[AGENTS.md](AGENTS.md) is the house style every document and comment
follows, and [STRUCK.md](STRUCK.md) lists what it strikes.

## License and attribution

The format may be implemented freely. `doc/SPEC.md` is the contract, and an
independent reader or writer owes only the acknowledgement.

The readers, writers and tests under `src/`, `go/`, `dotnet/` and `68k/`
can be used freely within your own programs, for any platform, including
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
