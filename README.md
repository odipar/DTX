# DTX

DTX is a data format: a table of `R` rows and `C` columns, where a column
is 1, 2 or 4 bytes wide and the rows repeat at a row `RR`.

The format is data. A compile step and a calling convention belong to a
reader and not to the format, so the specification states what the bytes
are and what a reader takes out of them, and no more than that.

No reader is written yet. The trees are `src/main/java/`, `go/`,
`dotnet/` and `68k/`, and `test/` is where the harnesses go. Nothing yet
holds one reader to another, and R7 leaves that open.

DTX says nothing about what a column holds. A format built on DTX says
that, in its own repository and against what this one says.

[doc/requirements.md](doc/requirements.md) comes first. Nothing else is
written until it says what DTX has to do.

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
