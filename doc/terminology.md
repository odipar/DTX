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

## What a column holds

Nothing here. A column is so many bytes wide and no more, and what its
bytes mean belongs to the use that reads them. A use states that in its own
repository.

That is the whole of the boundary. A table is a shape, and a use is what
gives the shape a meaning.
