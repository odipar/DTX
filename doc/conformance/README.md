# conformance

The kit an independent reader is written against: nineteen tables under
`tables/`, one file each and the rows in it beside it, and TASK.md, which
defines what a reader produces from each and the rules it is checked
against.

Every table is written by this repository's writer from the text and
options SOURCES.md lists, and `ConformanceTest` writes each again under
`mvn test` and compares the file with it byte for byte. So the writer
emits the kit itself, and a change that moved a byte of it fails
here.

The tables reach every variant at every width, a repeat and a repeat at row 0,
one row, a DTX2 at each unit above and below the width, twenty columns, a
table whose rows are not a multiple of its period, and columns packed with
copies at a ring too short for their pattern. The Java reader reads every
table here back to its rows in `ConformanceTest`; the Go and C# trees write
the same bytes as the Java tree in `ParityTest`; and the 68000 reads tables
of the same shapes under emulation in `68k/test/emu/test_dtx.py`.

## The runs

An implementer reads SPEC.md, requirements.md and TASK.md, and ST4's
SPEC.md for the packed payloads, writes a reader from those alone, and
produces the rows of every table. The `.rows` files and SOURCES.md stand
outside the run, since either has the rows of a table in it. A run passes
where every table's bytes equal the kit's and every reading the notes
record is one the documents decide.

**The first run**, 2026-09-19, against the kit at nineteen tables. The
implementer wrote a reader of 332 lines from the four documents alone and
produced all nineteen tables byte for byte, `dtx2-copies` and the
twenty-column table among them. Its notes had 23 entries with 7 marked
*decides output*, six of them in ST4's document and one here, and four
clauses changed for them.

- ST4's 3.4 reaches a block at the last offset with no value for that
  offset before a block sets one. It is 1 unit, and 22 columns of this kit
  open on a block that reads it: ST4 names it now.
- ST4's 3.8 reads that a block is an even number of bits, where the first
  block stands without a flag and is odd; 2.3 runs each stream to the next,
  where stream D stands last; and 3.5 left the order a block reads its
  offset stream in.
- SPEC.md 2.3 read that "nothing in an ST4 data set defines which kind it
  is", where a copy is an offset above `M` (ST4, SPEC.md 4.4): what the
  flags byte names is the build a reader needs, which R5.10 reads the same
  way now.
- SPEC.md 2.3 reads `M` against `N`, `N` divided by `k`, which every data
  set of this kit has and no clause had; and 2.2 reads that the payload is
  `C` minus one strides and the last column's bytes, which the reader read
  off the payload's length.

`TASK.md` cited SOURCES.md as though a run could read it; it reads that
the file stands outside a run now.

**The third run**, the same day, against the kit with the clauses of the
first two in it and a third implementer. The reader was 423 lines and
produced all nineteen tables byte for byte; its notes had 20 entries with
9 marked *decides output*, and two places changed here.

- SPEC.md 2.3's Note read that a data set of a payload is packed without
  a loop, where R5.11 has every data set of a repeating table loop at row
  `RR` and both sets of `dtx2-repeat` carry the loop word `$FFD0`. A data
  set of such a table loops at the form of ST4, SPEC.md 6.2, and the loop
  point of that stream is this format's `RR`.
- 2.3 read that the bits packing a data set "end on a marker", where ST4
  calls it the end code and a marker bit is a gamma's (ST4, SPEC.md 3.3,
  3.6). R0.7 forbids the second word.

Three more changed in ST4's document, which the same reader read for the
payloads: its 3.1 lists three kinds of block and its 3.4 three cases of
the flag, a copy standing among neither, so a reader reads a copy as a
match block, which governs every byte of `dtx2-copies` after its second
block.
