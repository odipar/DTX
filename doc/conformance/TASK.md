# task

What to produce, the tables, and the rules.

## What to produce

For each `NAME.dtx` under `tables/`, the bytes of `NAME.rows`: every row in
the table, from row 0 to row `R` minus one, each row column 0 first, each
value at the table's width, most significant byte first, and nothing between
the values or the rows. That is the row as DTX0 lays it out (SPEC.md 2.1),
and it is what `DTX_read` gives on the 68000 (abi.md 2).

A reader that repeats the table gives row `RR` after row `R` minus one;
the file is one pass, rows 0 to `R` minus one, and nothing of the repeat.
Compare your reader's `RR` with the header's instead.

## The tables

SOURCES.md lists them. Each is complete: the header, and the payload of its
variant. A DTX2 table's data sets are ST4 version 7 (SPEC.md 2.3), packed
with the copy of ST4 in this repository, at the unit and ring the row
gives, and `dtx2-copies` was packed with copies from the literal stream,
which its payload's flag byte marks.

## The rules

- SPEC.md defines the format and requirements.md what it has to do. R6
  bounds what a table may contain, and a reader given a table that breaks
  R6 reports it and does not read further (R6.5). No table here breaks it.
- A reader takes `R`, `C`, `RR` and the width from the header, and under
  DTX2 `N`, `k` and the flags from the payload. Nothing outside the file
  enters a read.
- Under DTX2 a reader checks every data set's first long against
  `$53 $34 $07 k` (R5.2), and a decoder built without the copy code
  reads `dtx2-copies` wrongly: the flag byte marks the build (R5.10).
- The bytes a reader gives are compared with `NAME.rows` whole. A row that
  differs in one byte fails the table.

A reader that produces every `.rows` file from every `.dtx` file reads
DTX. Nothing here checks how fast, or how it is called.
