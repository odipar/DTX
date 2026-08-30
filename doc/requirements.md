# What DTX has to do

## R0. The house style and the terms

The specification is what this repository produces. How it is written comes
before what it describes, and what things are called comes before both.

- **R0.1** `AGENTS.md` gives the rules, for every document, code comment
  and commit message.
- **R0.2** A test reads every document against a list of phrases struck in
  review, and names the file and line of each hit.
- **R0.3** The test walks the tree for documents. A document is held because
  it is there, not because someone listed it.
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
  has no glossary entry.
- **R0.10** A test reads the documents for what can be recomputed or
  followed: the figures, the citations, the links, the glossary's order,
  the one wrap width.

## R1. What DTX is

- **R1.1** A table of `R` rows and `C` columns, a column of a fixed width,
  and a row `RR` it repeats to once the last row is done. R6 bounds all
  four.
- **R1.2** A format, not an engine. The table is data; a compile step or a
  calling convention is a reader's, and a reader is written against the
  format rather than named by it.
- **R1.3** How the rows are laid out is a variant's (R2). The table does
  not change with the variant: the same `R`, `C` and `RR`, the same column
  widths, and the same rows in the same order.
- **R1.4** Nothing about what a column holds. A format built on this one
  states that in its own repository.

## R2. The variants

- **R2.1** A **variant** is one way of laying the rows out. It is the only
  thing that differs between them: the table is the same under all, the
  same `R`, `C` and `RR`, the same widths, and the same rows in the same
  order.
- **R2.2** **DTX0**, **DTX1** and **DTX2** are the variants this
  specification defines, in R3, R4 and R5. A variant it does not define
  takes the next number, and **DTXN** names one of those.
- **R2.3** A table states which variant it is, and a reader which variants
  it reads. Where either states it is SPEC.md's.
- **R2.4** A variant's number holds once assigned, and a later
  specification assigns a number this one leaves free rather than
  redefining one.

## R3. DTX0

- **R3.1** The rows laid out row by row.
- **R3.2** The rows as they stand. A reader takes a row by finding it,
  unpacking nothing and keeping nothing between one row and the next, and
  a writer puts a row down the same way.
- **R3.3** Finding a row, or a column within one, is arithmetic on `R`,
  `C` and the widths. There is no index to walk.
- **R3.4** Nothing padded inside the payload. A value falls where the
  widths put it, so a two or four byte column may fall on an odd offset,
  where a 68000 takes it as bytes.
- **R3.5** A whole row in one run of bytes. That is what DTX0 is for.

## R4. DTX1

- **R4.1** The rows laid out column by column.
- **R4.2** The rows as they stand and found by arithmetic, as R3.2 and
  R3.3 have DTX0's.
- **R4.3** A column begins on a word, so every value of a two or four byte
  column sits where a 68000 reads it as one. This is what DTX1 has over
  DTX0, at a byte a column.
- **R4.4** A column's values together, so a reader takes one column
  without touching the others. That is what DTX1 is for.

## R5. DTX2

- **R5.1** The rows laid out column by column, each column packed with
  ST4.
- **R5.2** One unit `k` for a payload. The `k` the payload states and the
  `k` in every data set's own signature are the same, and a reader may
  check one against the other.
- **R5.3** A reader holds one ST4 decoder, built for that `k`, and takes
  every column of the payload through it. ST4 code is built for a unit,
  and one unit a payload is what lets one build serve every column.
- **R5.4** One ring size `N` for a payload. No data set in it reaches back
  further than `N`, so a ring of `N` bytes serves any of them, and every
  data set was packed for the `N` the payload states.
- **R5.5** A reader holds its rings at that one size, so they stand at a
  fixed stride from one another and one cursor arithmetic serves every
  column.
- **R5.6** `R` divides by `k`. ST4 packs whole units, so a column that is
  not a whole number of them unpacks to more bytes than it holds.
- **R5.7** A table in fewer bytes than DTX1 holds the same one. That is
  what DTX2 is for.
- **R5.8** Read back through a ring that does not grow with `R`. A reader
  holds a window on a column, not the column.
- **R5.9** An ST4 data set begins on a long, which is what ST4 asks of one
  of its containers.

## R6. The constraints

What a table may hold, and what a reader does where it holds otherwise.

- **R6.1** `R` is 1 upward.
- **R6.2** `C` is 1 to 256.
- **R6.3** A column's width is 1, 2 or 4 bytes, and no other.
- **R6.4** `RR` names a row of the table, 0 to `R` minus one, or is `R`
  itself where the table does not repeat.
- **R6.5** A reader given a table that breaks any of these, or R5.6,
  reports it and reads no further. What it reports is SPEC.md's.

## R7. Not yet required

What R1 to R6 do not yet say. Each is open, and none of it is settled by
[doc/SPEC.md](SPEC.md), which states the format R1 to R6 require.

- Whether a table states its own length, and whether a reader needs one to
  read it.
- What a reader reports of a table it will not read. R6.5 has it report and
  read no further, and leaves what it reports to SPEC.md, which has not
  written it.
- Whether a variant may hold columns of more than one kind, some packed and
  some plain.
- What holds this repository's readers to one another. None is written yet;
  when they are, Java is the source of truth and Go, C# and a 68000 one
  follow it, and nothing here requires that they agree or says how that is
  shown.
