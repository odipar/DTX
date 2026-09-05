# glossary

Every term this repository defines, one line each. The document beside a
term explains it at length. A term that changes here changes there in the
same change (requirements.md, R0.6 to R0.9).

| term | what it is | explained in |
|---|---|---|
| `C` | The table's column count. | terminology.md, tables, rows and columns |
| clock | What advances to a next row and has its own place in the table. | terminology.md, tables, rows and columns |
| column | One field of a row, `W` bytes wide. | terminology.md, tables, rows and columns |
| column table | What stands behind a packaged reader's code and before its table: under DTX2 one stream record a column, four longs. DTX0 and DTX1 do not have one. | abi.md 1 |
| cursor | The address, in a ring or in a payload, of the row a packaged reader's clock stands on. | abi.md 3 |
| data set | One column of a DTX2 payload, packed with ST4: its own ST4 header, and the length of what it unpacks to. | SPEC.md 2.3 |
| decoder state | The eight longs a column's decoder is saved in between refills, 32 bytes of a packaged DTX2 reader's state block. | abi.md 3 |
| DTX | This format: a table of `R` rows and `C` columns. The table is data, and a reader of it is code. | README.md |
| format block | The 24 bytes at +24 of an image: the variant, the state block's bytes, where the table and the column table stand, the row's bytes, `P`, `N`, `k` and `W`. | abi.md 1 |
| header | The 16 bytes before a payload: `DTX`, the variant, and the metadata. | SPEC.md 1 |
| image | A table packaged for the 68000: the code, under DTX2 the column table, and the table's bytes in one file, read through six calls. | abi.md 1 |
| `k` | The third byte of a DTX2 payload: the unit its data sets are packed at. | SPEC.md 2.3 |
| long | Four bytes. An offset is on a long where it divides by 4. | SPEC.md |
| metadata | What describes a table without containing it: `R`, `C`, `RR` and `W`. | terminology.md, tables, rows and columns |
| `N` | The first two bytes of a DTX2 payload: how big a ring is. | SPEC.md 2.3 |
| payload | What follows the header: a table's rows in bytes, and in DTX2 the `N`, `k` and offsets that reach them. | SPEC.md 2 |
| period | `P`, the rows between one column's refills in a packaged DTX2 reader. | abi.md 4 |
| `R` | The table's row count, of the rows in it. | terminology.md, tables, rows and columns |
| reader | What takes rows out of a table. | terminology.md, tables, rows and columns |
| ring | The bytes of a column a reader has at a time, `N` of them, in place of the unpacked column. | SPEC.md 2.3 |
| row | One step of a table: `C` values, with nothing in it about what any of them is for. | terminology.md, tables, rows and columns |
| `RR` | The row a table repeats to once the last row is done. | terminology.md, tables, rows and columns |
| ST4 | The packer a DTX2 column is packed with, specified in its own repository. | requirements.md R5, SPEC.md 2.3 |
| ST4 header | The twenty-eight bytes an ST4 data set opens with, its first long `$53 $34 $07 k`. | SPEC.md 2.3 |
| state block | What a caller of a packaged reader supplies, and passes back on every call but one. | abi.md 3 |
| stride | The bytes from one column, ring or decoder state to the next. | terminology.md, the variants |
| table | What yields rows: `R` of them, `C` columns wide, every value `W` bytes, repeating at `RR`. | terminology.md, tables, rows and columns |
| turn | The column a row refills in a packaged DTX2 reader: the row number modulo `P`. | abi.md 4 |
| unit | The width ST4 packs whole numbers of: 1, 2 or 4 bytes. | SPEC.md 2.3 |
| variant | One way of laying a table's rows out in bytes. DTX0 row by row, DTX1 column by column, DTX2 column by column and packed. | terminology.md, the variants |
| `W` | The bytes every value of the table takes: 1, 2 or 4. | terminology.md, tables, rows and columns |
| word | Two bytes. An offset is on a word where it divides by 2. | SPEC.md |
| writer | What puts rows into a table. | terminology.md, tables, rows and columns |
| yielding | Giving one row of a table, in order. | terminology.md, tables, rows and columns |

