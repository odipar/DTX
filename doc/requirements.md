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
  and a row `RR` it repeats to once the last row is done. R3 bounds all
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

A **variant** is one way of laying the rows out. It is the only thing that
differs between them, and R1.3 is what they hold in common.

- **R2.1** **DTX0** lays the rows out row by row.
- **R2.2** **DTX1** lays them out column by column.
- **R2.3** DTX0 and DTX1 hold their rows as they stand. A reader takes a
  row by finding it, unpacking nothing and keeping nothing between one row
  and the next, and a writer puts a row down the same way.
- **R2.4** Finding a row in DTX0, or a column's value in DTX1, is
  arithmetic on `R`, `C` and the widths. There is no index to walk.
- **R2.5** DTX1 begins each column on a word, so every value of a two or
  four byte column sits where a 68000 reads it as one. DTX0 does not: a
  value falls where the widths put it, and a reader takes it as bytes
  where that is odd. This is what DTX1 has over DTX0, at a byte a column.
- **R2.6** **DTX2** lays them out column by column, each column packed with
  ST4 at one unit `k` (R3.5).
- **R2.7** DTX2 holds a table in fewer bytes than DTX1 holds the same one.
- **R2.8** DTX2 is read back through a buffer that does not grow with `R`.
  A reader holds a window on a column, not the column.
- **R2.9** A variant this specification does not define takes the next
  number. **DTXN** names one of those.
- **R2.10** A table states which variant it is, and a reader which variants
  it reads. Where either states it is SPEC.md's.
- **R2.11** A variant's number holds once assigned, and a later
  specification assigns a number this one leaves free rather than
  redefining one.

## R3. The constraints

What a table may hold, and what a reader does where it holds otherwise.

- **R3.1** `R` is 1 upward.
- **R3.2** `C` is 1 to 256.
- **R3.3** A column's width is 1, 2 or 4 bytes, and no other.
- **R3.4** `RR` names a row of the table, 0 to `R` minus one, or is `R`
  itself where the table does not repeat.
- **R3.5** Under DTX2, `R` divides by the unit `k` the payload is packed
  at, so a column is a whole number of units and unpacks to the bytes it
  holds and no more.
- **R3.6** A reader given a table that breaks any of these reports it and
  reads no further. What it reports is SPEC.md's.

## R4. Not yet written

The rest. [doc/SPEC.md](SPEC.md) is empty, and the requirements above are
what stands.
