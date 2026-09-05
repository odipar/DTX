# terminology

The machine's terms, and no second word for a thing that has one
(requirements.md, R0.6 to R0.9). [glossary.md](glossary.md) lists every
term this repository uses and names where each is explained.

---

## Tables, rows and columns

A **table** yields rows: `R` of them, `C` columns wide, every value `W`
bytes, and a row `RR` it repeats to once the last row is done. A **row** is
one step of one: `C` values, and nothing in it about what any of them is
for. A **column** is one field of a row, `W` bytes wide.

`W` is the table's and not a column's. Every value takes 1, 2 or 4 bytes
and one table takes one of the three (requirements.md, R6.3), so a column
of another width is another table, and a reader built for a width reads
the tables of that width.

**Yielding** is one row at a time and in order. A **clock** advances to a
next row, and has its own place in the table: the first advance gives
row 0, the next row 1, and the advance after row `R` minus one gives row
`RR`, or nothing where the table does not repeat. Two clocks on one table
have two places, and neither moves the other's.

`R` counts the rows in a table, not the rows it yields. One that repeats
yields them without end.

`R`, `C`, `RR` and `W` are a table's **metadata**. They describe it without
containing any of it.

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

DTX1 pads for one thing more: a column begins on a word, so every value
stands where a 68000 reads it as one. That costs a byte a column at a
width of 1 and an odd `R`, and nothing at a width of 2 or 4, where a
column is a whole number of words already. In DTX0 a row begins where the
row before it ends, so at a width of 1 and an odd `C` a row falls on an
odd offset and a reader takes its values as bytes.

Every column is the same length, so under DTX1 they lie at one **stride**:
`R` times `W`, up to a word. A reader steps from one column of a row to the
next by adding it.

DTX2 is for size. Packing a column costs the plainness: a reader no longer
finds a value by arithmetic, and has a ring of `N` bytes on each column
rather than the column itself. The ring does not grow as the table does.
Packing is the table in fewer bytes, once the table has rows enough for
the packing to cost less than it saves. A short one packs to more than it
has, since what the packing costs does not grow with `R` (requirements.md,
R5.7).

---

## What a column contains

Nothing here. A column is `W` bytes wide and no more, and what its bytes
are for belongs to the format built on this one that reads them. That
format defines it in its own repository.
