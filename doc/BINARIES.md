# binaries

The eight 68000 images a DTX table is packaged behind, and how a tool
combines one with a table.

## The eight

One variant is one code. `R`, `C`, `RR` and the widths reach the code at
run time, out of the table's own header and the column table behind the
code, so DTX0 and DTX1 assemble to one image each at any table. DTX2
assembles to one an ST4 build, and ST4 is built for a unit and with the
copy code or without: `k` of 1, 2 or 4, twice each, is six. Eight in all
(abi.md 5).

| image | contains |
|---|---|
| `DTX0` | the reader of a table laid out row by row |
| `DTX1` | the reader of a table laid out column by column |
| `DTX2-k1`, `DTX2-k2`, `DTX2-k4` | the packed reader, with a decoder at that unit |
| `DTX2-k1-copies`, `DTX2-k2-copies`, `DTX2-k4-copies` | the same, with the copy code built in |

An image alone states no table. The five fields of its format block that
come from a table - the state block's bytes, where the table's header
stands, the row's bytes, `P` and `N` - read zero until a tool writes them,
so an image shipped uncombined states no table rather than the one it was
built from.

## Where they stand

Each tree contains the eight: the Java jar on its classpath, the Go
executables through `go:embed`, the C# assembly as embedded resources. A
release puts them beside its zips as well, named by the release.
`bin/dtx-blobs`, `dtx-blobs` and `dtx dtx-blobs` build them, and are the one
step that needs rmac (tools.md, Build the images). No image is tracked in
the tree.

## Combining

A tool takes the image for the table - the variant from the header, and
under DTX2 the unit at payload byte 2 and the copies flag at byte 3 -
writes the five fields, and appends the column table and the table's bytes
behind the code. Nothing from beside the file enters it, so nothing can
differ from the bytes. abi.md 1 lays the image out, abi.md 5 states the
combine, and abi.md 4 states the rules a DTX2 table is checked against
first.
