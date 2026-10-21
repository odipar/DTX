# binaries

The forty 68000 images a DTX table is packaged behind, and how a tool
combines one with a table.

## The forty

One variant is one code. `R`, `C` and `RR` reach the code at run time, out
of the table's header, so a variant assembles to one code at any number of
rows or columns. The width reaches the assembler instead: every value of a
table is one width (R6.3), so a read moves a value in one instruction and no
call reads a width a column. That costs one build a width.

DTX0 reads a row as one run of bytes and forms the run from the row's
bytes, so its code is the same at every width and one file is every DTX0
table's. DTX1 has one file a width. DTX2 and DTX3 each have one a width and
an ST4 build, and ST4 is built for a unit and with the copy code or
without: three widths by three units, twice each, is eighteen each. Forty
in all (abi.md 5).

| image | contains |
|---|---|
| `DTX0.bin` | the reader of a table laid out row by row, at any width |
| `DTX1-wW.bin` | the reader of a table laid out column by column, at width `W` |
| `DTX2-wW-kK.bin` | the packed reader at width `W`, with a decoder at unit `K` |
| `DTX2-wW-kK-copies.bin` | the same, with the copy code built in |
| `DTX3-wW-kK.bin` | the reader of a table with constant columns as values and the others packed, at width `W`, with a decoder at unit `K` |
| `DTX3-wW-kK-copies.bin` | the same, with the copy code built in |

`W` is 1, 2 or 4, and `K` the same three. Each file is the code with its
four slots and format block in front, and no table behind it: 176 bytes
for DTX0, 124 for a DTX1 image, 1436 to 1484 for a DTX2 image and 1448 to
1500 for a DTX3 image.
`bin/dtx-blobs` prints each figure as it writes the file.

The six fields of an image's format block that come from a table - the state
block's bytes, where the table's header is, the row's bytes, `P`, `N` and
the stride - read zero until a tool writes them, so an image shipped
uncombined does not define a table, rather than the one it was built from.

## Where they are

Each tree contains the forty: the Java jar on its classpath, the Go
executables through `go:embed`, the C# assembly as embedded resources. A
release packs them in one zip beside the tools' zips, named by the release.
`bin/dtx-blobs`, `dtx-blobs` and `dtx dtx-blobs` build them, and are the one
step that needs rmac (tools.md, Build the images). No image is tracked in
the tree.

## Combining

A tool reads the image for the table - the variant from the header, the
width at header byte 14, and under DTX2 and DTX3 the unit at payload byte 2
and the copies flag at byte 3 - writes the six fields, and appends the
table's bytes behind the code. Under DTX2 a column table is between the
two, one stream record a column and nothing else; DTX0, DTX1 and DTX3 do
not have one, so there the table's header is the first byte behind the
code. An image of several DTX3 tables is the build with the copy code
where any of them sets the flag.

What a combine does not write it checks: the variant, the width under DTX1,
DTX2 and DTX3, and under DTX2 and DTX3 the unit the decoder built into the
code decodes at. Where the code was built for another, it fails the package
rather than writing an image that reads wrong. The combine reads the image
and the table alone, so the result is their bytes. abi.md 1 lays the image
out, abi.md 5 defines the combine, and abi.md 4 defines the rules a DTX2 or
DTX3 table is checked against first.