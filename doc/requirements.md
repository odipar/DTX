# What DTX has to do

## R0. The house style and the terms

The specification is what this repository produces. How it is written comes
before what it describes, and what things are called comes before both.

- **R0.1** `AGENTS.md` gives the rules, for every document, code comment
  and commit message.
- **R0.2** A test reads every document, and every code comment this
  repository writes, against a list of phrases struck in review, and names
  the file and line of each hit.
- **R0.3** The test walks the tree for documents. A document is checked
  because it is there, not because someone listed it.
- **R0.4** Striking a phrase adds it to the list, in the same change.
- **R0.5** Using a struck phrase again removes it from the list, in the same
  change.
- **R0.6** [glossary.md](glossary.md) lists every term and names the
  document that explains it. The terms are this repository's ubiquitous
  language.
- **R0.7** Every document, comment and name in this repository uses those
  terms, and no second word for a thing that has one.
- **R0.8** A term that changes in the glossary changes everywhere in the
  same change.
- **R0.9** A test reads terminology.md and fails when a term it explains
  does not have a glossary entry.
- **R0.10** A test reads the documents for what can be recomputed or
  followed: the figures, the citations, the links, the glossary's order,
  the one wrap width.

## R1. What DTX is

- **R1.1** A table of `R` rows and `C` columns, every value of one fixed
  width, and a row `RR` it repeats to once the last row is done. R6 bounds
  all four.
- **R1.2** A format, not an engine. The table is data; a compile step or a
  calling convention is a reader's, and a reader is written against the
  format rather than named by it.
- **R1.3** How the rows are laid out is a variant's (R2). The table does
  not change with the variant: the same `R`, `C` and `RR`, the same width,
  and the same rows in the same order.
- **R1.4** Nothing about what a column contains. A format built on this one
  defines that in its own repository.

## R2. The variants

- **R2.1** A **variant** is one way of laying the rows out, and the only
  thing two layouts of one table differ in (R1.3).
- **R2.2** **DTX0**, **DTX1** and **DTX2** are the variants this
  specification defines, in R3, R4 and R5. A variant it does not define
  takes the next number, and **DTXN** names one of those.
- **R2.3** A table defines which variant it is, and a reader which variants
  it reads. Where either defines it is SPEC.md's.
- **R2.4** A variant's number is fixed once assigned, and a later
  specification assigns a number this one leaves free rather than
  redefining one.

## R3. DTX0

- **R3.1** The rows laid out row by row.
- **R3.2** The rows as they stand. A reader takes a row by finding it,
  unpacking nothing and keeping nothing between one row and the next, and
  a writer puts a row down the same way.
- **R3.3** Finding a row, or a column within one, is arithmetic on `R`,
  `C` and the width. An index is absent.
- **R3.4** Nothing padded inside the payload. A value falls where the width
  puts it, so at a width of 1 and an odd `C` a row may fall on an odd
  offset, where a 68000 takes its values as bytes.
- **R3.5** A whole row in one run of bytes. That is what DTX0 is for.

## R4. DTX1

- **R4.1** The rows laid out column by column.
- **R4.2** The rows as they stand and found by arithmetic, as R3.2 and
  R3.3 have DTX0's.
- **R4.3** A column begins on a word, so every value sits where a 68000
  reads it as one. DTX1 has this over DTX0, at a byte a column where the
  width is 1 and `R` odd, and at nothing where the width is 2 or 4.
- **R4.4** A column's values together, so a reader takes one column
  without touching the others. That is what DTX1 is for.

## R5. DTX2

- **R5.1** The rows laid out column by column, each column packed with
  ST4.
- **R5.2** One unit `k` for a payload. The `k` the payload defines and the
  `k` in every data set's own signature are the same, and a reader checks
  one against the other.
- **R5.3** A reader has one ST4 decoder, built for that `k`, and takes
  every column of the payload through it. ST4 code is built for a unit,
  and with one unit a payload one build reads every column.
- **R5.4** One ring size `N` for a payload. No data set in it reaches back
  further than `N`, so a ring of `N` bytes is enough for any of them, and
  every data set was packed for the `N` the payload defines.
- **R5.5** A reader's rings are all that one size, so they stand at a
  fixed stride from one another and one pointer arithmetic runs every
  column. Every column is one width (R6.3), so one pointer does: column
  `i`'s value for a row stands `i` rings past column 0's.
- **R5.6** `R` times the width divides by `k`. ST4 packs whole units, so a
  column that is not a whole number of them unpacks to more bytes than it
  has.
- **R5.7** The same table in fewer bytes than DTX1, once it has rows
  enough for the packing to cost less than it saves. That is what DTX2 is
  for. What the packing costs does not grow with `R`, where what it saves
  does, so a short table packs to more than it has.
- **R5.8** Read back through a ring that does not grow with `R`. A reader
  has `N` bytes of a column at a time, not the column.
- **R5.9** An ST4 data set begins on a long.
- **R5.10** The payload defines whether its columns contain copies from their
  own literal streams. A decoder built without the copy code reads such a
  column wrongly, and no data set defines which kind it is, so a reader that
  took it from anywhere but the file could be given one that differs from the
  bytes.

## R6. The constraints

What a table may contain, and what a reader does where it contains
otherwise.

- **R6.1** `R` is 1 upward.
- **R6.2** `C` is 1 to 256.
- **R6.3** One width a table: every value of it takes 1, 2 or 4 bytes, and
  a column of another width is another table. A reader built for one width
  reads a table of that width.
- **R6.4** `RR` names a row of the table, 0 to `R` minus one, or is `R`
  itself where the table does not repeat.
- **R6.5** A reader given a table that breaks any of these, or R5.6, or a
  payload whose data sets do not give its own `k` and ST4's format
  version 7, reports it and does not read further. What it reports is
  SPEC.md's.

## R7. Not yet required

What R1 to R6 do not yet define. Each is open, and none of it is fixed by
[doc/SPEC.md](SPEC.md), which defines the format R1 to R6 require.

- Whether a table defines its own length, and whether a reader needs one to
  read it.
- What a reader reports of a table it will not read. R6.5 has it report and
  not read further, and leaves what it reports to SPEC.md, which has not
  written it.
- Whether a variant may contain columns of more than one kind, some packed
  and some plain, or of more than one width.
- What checks a reader written elsewhere against this repository's. The
  Java, Go and C# trees write the same bytes and a test compares them, and
  the 68000 reader is compared with the text a table came from under
  emulation; a reader written against the kit under doc/conformance is
  checked by nothing here yet.
