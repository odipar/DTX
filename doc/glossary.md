# glossary

Every term this repository defines, one line each. The document beside a
term explains it at length. A term that changes here changes there in the
same change (requirements.md, R0.6 to R0.9).

| term | what it is | explained in |
|---|---|---|
| `C` | The table's column count. | terminology.md, tables, rows and columns |
| column | One field of a row, 1, 2 or 4 bytes wide, the same width in every row. | terminology.md, tables, rows and columns |
| DTX | This format: a table of `R` rows and `C` columns. The table is data, and a reader of it is code. | README.md |
| metadata | What describes a table without holding it: `R`, `C`, `RR` and each column's width. | terminology.md, tables, rows and columns |
| `R` | The table's row count, of the rows it holds. | terminology.md, tables, rows and columns |
| row | One step of a table: `C` values, with nothing in it about what any of them is for. | terminology.md, tables, rows and columns |
| `RR` | The row a table repeats to once the last row is done. | terminology.md, tables, rows and columns |
| table | What yields rows: `R` of them, `C` columns wide, repeating at `RR`. | terminology.md, tables, rows and columns |
| yielding | Giving one row of a table, in order. A clock advances to a next row and holds its own place in the table. | terminology.md, tables, rows and columns |

## Named, not yet defined

These appear in the documents without an entry above. Each gets one as the
specification settles.

- **reader** and **writer** - what takes rows out of a table, and what puts
  them in.
- **use** - a format built on this one, which says what a column holds.
