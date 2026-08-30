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

**Yielding** is one row at a time and in order. A clock advances to a next
row, and holds its own place in the table: the first advance gives row 0,
the next row 1, and the advance after row `R` minus one gives row `RR`, or
nothing where the table does not repeat. Two clocks on one table hold two
places, and neither moves the other's.

`R` counts the rows a table holds, not the rows it yields. One that repeats
yields them without end.

`R`, `C`, `RR` and the column widths are a table's **metadata**. They
describe it without holding any of it.

---

## The variants

A **variant** is one way of laying a table's rows out in bytes. The table
is the same under all of them, and the variant is the whole of the
difference: DTX0 lays the rows out row by row, DTX1 column by column, and
DTX2 column by column with each column packed.

The three answer two goals. DTX0 and DTX1 are for reading and writing
plainly: the bytes are the rows, so a reader finds a value by arithmetic
and takes it, and a writer puts a row down as it is. Row by row a whole
row is one run of bytes; column by column a column's values sit together,
which is what lets a reader take one column without touching the others.

DTX1 pays a byte a column for one thing more: a column begins on a word,
so a value of two or four bytes sits where a 68000 reads it as one. In
DTX0 a value falls where the widths put it, and a reader takes it as bytes
where that is odd.

DTX2 is for size. Packing a column costs the plainness: a reader no longer
finds a value by arithmetic, and holds a window on each column instead of
the column. What it buys is a table small enough to keep, and a buffer
that does not grow as the table does.

---

## What a column holds

Nothing here. A column is so many bytes wide and no more, and what its
bytes mean belongs to the format built on this one that reads them. That
format states it in its own repository.

That is the whole of the boundary: a table is a shape, and a format built
on it gives the shape a meaning.
