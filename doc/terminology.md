# terminology

The machine's terms, and no second word for a thing that has one
(requirements.md, R0.6 to R0.9). [glossary.md](glossary.md) lists every
term this repository uses and names where each is explained.

---

## Tables, rows and columns

A **table** yields rows: `R` of them, `C` columns wide, a column 1, 2 or 4
bytes, and a row `RR` it repeats to once the last row is done. A **row** is
one step of one: `C` values, and nothing in it about what any of them is
for. A **column** is one field of a row, the same width in every row.

**Yielding** is one row at a time and in order. A **clock** advances to a
next row, and has its own place in the table: the first advance gives
row 0, the next row 1, and the advance after row `R` minus one gives row
`RR`, or nothing where the table does not repeat. Two clocks on one table
have two places, and neither moves the other's.

`R` counts the rows in a table, not the rows it yields. One that repeats
yields them without end.

`R`, `C`, `RR` and the column widths are a table's **metadata**. They
describe it without containing any of it.

A **reader** takes rows out of a table, and a **writer** puts them in. A
variant is read by one and written by the other, and the table is the same
in both directions (requirements.md, R1.3).

---

## The variants

A **variant** is one way of laying a table's rows out in bytes. The table
is the same under all of them, and the variant is the whole of the
difference: DTX0 lays the rows out row by row, DTX1 column by column, and
DTX2 column by column with each column packed.

The three are for two things. DTX0 and DTX1 are for reading and writing
plainly: the bytes are the rows, so a reader finds a value by arithmetic
and takes it, and a writer puts a row down as it is. Row by row a whole
row is one run of bytes; column by column a column's values sit together,
so a reader takes one column without touching the others.

DTX1 costs a byte a column for one thing more: a column begins on a word,
so a value of two or four bytes sits where a 68000 reads it as one. In
DTX0 a value falls where the widths put it, and a reader takes it as bytes
where that is odd.

DTX2 is for size. Packing a column costs the plainness: a reader no longer
finds a value by arithmetic, and has a ring of `N` bytes on each column
rather than the column itself. The ring does not grow as the table does.
Packing is the table in fewer bytes, once the table has rows enough for
the packing to cost less than it saves. A short one packs to more than it
has, since what the packing costs does not grow with `R` (requirements.md,
R5.7).

---

## What a column contains

Nothing here. A column is so many bytes wide and no more, and what its
bytes are for belongs to the format built on this one that reads them.
That format states it in its own repository.
