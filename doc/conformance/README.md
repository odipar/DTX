# conformance

The kit an independent reader is written against: thirteen tables under
`tables/`, one file each and the rows it holds beside it, and TASK.md,
which states what a reader produces from each and the rules it is held to.

Every table is written by this repository's own writer from the text and
options SOURCES.md lists, and `ConformanceTest` writes each again under
`mvn test` and holds the file to it byte for byte. So the kit is what the
writer gives, and a change to the writer that moved a byte of it fails
here.

The tables reach every variant, every width, a repeat and a repeat at row
0, one row, a DTX2 at each unit, a table whose rows are not a multiple of
its period, and columns packed with copies at a ring their pattern does
not fit. The three readers this repository holds pass every one: the
Java, Go and C# trees through `ParityTest`, and the 68000 under emulation
through `68k/test/emu/test_dtx.py`, which reads the same shapes.
