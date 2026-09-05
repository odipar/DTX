# DTX

DTX is a data format: a table of `R` rows and `C` columns, where a column
is 1, 2 or 4 bytes wide and the rows repeat at a row `RR`.

The format is data. A compile step and a calling convention belong to a
reader and not to the format, so the specification states what the bytes
are and what a reader takes out of them, and no more than that.

Three trees write it: Java under `src/main/java/`, Go under `go/` and C#
under `dotnet/`. The three write the same bytes, and a test runs every tool
in each of them over one corpus and holds the files to one another. Each
tree holds a copy of ST4, the packer a DTX2 column is packed with, so none
needs one beside it. `68k/` holds the reader a packaged table is read by on
a 68000 and a carried copy of the ST4 decoder it takes under DTX2; a rig
runs that reader under emulation and holds every row it gives to the text
the table came from.

DTX states nothing of what a column holds. A format built on DTX states
that, in its own repository and against what this one states.

[doc/requirements.md](doc/requirements.md) comes first. Nothing else is
written until it states what DTX has to do.

| | |
|---|---|
| [doc/requirements.md](doc/requirements.md) | what the format has to do |
| [doc/SPEC.md](doc/SPEC.md) | the format specification |
| [doc/glossary.md](doc/glossary.md) | every term, one line each |
| [doc/terminology.md](doc/terminology.md) | the same terms explained |
| [doc/tools.md](doc/tools.md) | every tool's usage, flags and environment, in three trees |
| [doc/abi.md](doc/abi.md) | the calls a packaged table is read by, on the 68000 |
| [doc/performance.md](doc/performance.md) | what each call costs, in instructions, measured |
| [doc/experiments.md](doc/experiments.md) | what was measured against real tables, and what came out |
| [doc/BINARIES.md](doc/BINARIES.md) | the eight 68000 images, and how a tool combines one with a table |
| [doc/RELEASES.md](doc/RELEASES.md) | what a release holds, and what changed in each |
| [doc/conformance/](doc/conformance) | the kit an independent reader is written against |
