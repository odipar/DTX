# conformance

The kit an independent reader is written against: nineteen tables under
`tables/`, one file each and the rows in it beside it, and TASK.md, which
defines what a reader produces from each and the rules it is checked
against.

Every table is written by this repository's own writer from the text and
options SOURCES.md lists, and `ConformanceTest` writes each again under
`mvn test` and compares the file with it byte for byte. So the kit is what
the writer gives, and a change to the writer that moved a byte of it fails
here.

The tables reach every variant at every width, a repeat and a repeat at row 0,
one row, a DTX2 at each unit above and below the width, twenty columns, a
table whose rows are not a multiple of its period, and columns packed with
copies at a ring too short for their pattern. The Java reader reads every
table here back to its rows in `ConformanceTest`; the Go and C# trees write
the same bytes as the Java tree in `ParityTest`; and the 68000 reads tables
of the same shapes under emulation in `68k/test/emu/test_dtx.py`. A reader
written against the kit is checked by nothing here yet (requirements.md,
R7).
