# DTX

DTX is a data format: a table of `R` rows and `C` columns, where a column
is 1, 2 or 4 bytes wide and the rows repeat at a row `RR`.

The format is data. A compile step and a calling convention belong to a
reader and not to the format, so the specification states what the bytes
are and what a reader takes out of them, and no more than that.

The readers are here: Java is the source of truth, Go and C# follow it
byte for byte, and a 68000 one is under `68k/`.

DTX says nothing about what a column holds. That is a use's to define, and
[YMXR](https://github.com/odipar/YMXR) is one - a chiptune format for the
Atari ST, whose columns hold what a sound chip and its timers are set to.
YMXR is written against what this repository says, and records it under
its own R1.

[doc/requirements.md](doc/requirements.md) comes first. Nothing else is
written until it says what DTX has to do.

The shape follows YMXR, and the harnesses under `test/` hold the three
trees to each other.

| | |
|---|---|
| [doc/requirements.md](doc/requirements.md) | what the format has to do |
| [doc/SPEC.md](doc/SPEC.md) | the format specification |
| [doc/glossary.md](doc/glossary.md) | every term, one line each |
| [doc/terminology.md](doc/terminology.md) | the same terms explained |
| [doc/tools.md](doc/tools.md) | every tool's usage, flags and environment |
| [doc/performance.md](doc/performance.md) | what taking a row costs, in cycles |
| [doc/experiments.md](doc/experiments.md) | ideas measured, and what the measurements said |
| [doc/BINARIES.md](doc/BINARIES.md) | the prebuilt binaries, and how a tool combines them |
| [doc/RELEASES.md](doc/RELEASES.md) | what changed in each published set |
| [doc/conformance/](doc/conformance) | the kit an independent reader is written against |
