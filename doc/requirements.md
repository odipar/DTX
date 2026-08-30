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

- **R1.1** A table of `R` rows and `C` columns, a column 1, 2 or 4 bytes
  wide, and a row `RR` it repeats to once the last row is done.
- **R1.2** A format, not an engine. The table is data; a compile step or a
  calling convention is a reader's, and a reader is written against the
  format rather than named by it.
- **R1.3** Nothing about what a column holds. A use states that in its own
  repository.

## R2. Not yet written

The rest. [doc/SPEC.md](SPEC.md) is empty, and the requirements above are
what stands.
