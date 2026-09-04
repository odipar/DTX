# glossary

Every term this repository defines, one line each. The document beside a
term explains it at length. A term that changes here changes there in the
same change (requirements.md, R0.6 to R0.9).

| term | what it is | explained in |
|---|---|---|
| `C` | The table's column count. | terminology.md, tables, rows and columns |
| clock | What advances to a next row and holds its own place in the table. | terminology.md, tables, rows and columns |
| column | One field of a row, 1, 2 or 4 bytes wide, the same width in every row. | terminology.md, tables, rows and columns |
| data set | One column of a DTX2 payload, packed with ST4: its own ST4 header, and the length of what it unpacks to. | SPEC.md 2.3 |
| DTX | This format: a table of `R` rows and `C` columns. The table is data, and a reader of it is code. | README.md |
| header | What stands before a payload: `DTX`, the variant, and the metadata. | SPEC.md 1 |
| `k` | The third byte of a DTX2 payload: the unit its data sets are packed at. | SPEC.md 2.3 |
| long | Four bytes. An offset is on a long where it divides by 4. | SPEC.md |
| metadata | What describes a table without holding it: `R`, `C`, `RR` and each column's width. | terminology.md, tables, rows and columns |
| `N` | The first two bytes of a DTX2 payload: how big a ring is. | SPEC.md 2.3 |
| payload | What follows the header: a table's rows in bytes, and in DTX2 the `N`, `k` and offsets that reach them. | SPEC.md 2 |
| `R` | The table's row count, of the rows it holds. | terminology.md, tables, rows and columns |
| ring | The bytes of a column a reader holds at a time, `N` of them, in place of the unpacked column. | SPEC.md 2.3 |
| row | One step of a table: `C` values, with nothing in it about what any of them is for. | terminology.md, tables, rows and columns |
| `RR` | The row a table repeats to once the last row is done. | terminology.md, tables, rows and columns |
| ST4 | The packer a DTX2 column is packed with, specified in its own repository. | requirements.md R5, SPEC.md 2.3 |
| ST4 header | The twenty-eight bytes an ST4 data set opens with, its first long `$53 $34 $07 k`. | SPEC.md 2.3 |
| table | What yields rows: `R` of them, `C` columns wide, repeating at `RR`. | terminology.md, tables, rows and columns |
| unit | The width ST4 packs whole numbers of: 1, 2 or 4 bytes. | SPEC.md 2.3 |
| variant | One way of laying a table's rows out in bytes. DTX0 row by row, DTX1 column by column, DTX2 column by column and packed. | terminology.md, the variants |
| `W[i]` | Column `i`'s width, from the header. | SPEC.md 2 |
| word | Two bytes. An offset is on a word where it divides by 2. | SPEC.md |
| yielding | Giving one row of a table, in order. | terminology.md, tables, rows and columns |

## Named, not yet defined

These appear in the documents without an entry above. Each gets one as the
specification settles.

- **reader** and **writer** - what takes rows out of a table, and what puts
  them in.
