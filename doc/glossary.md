# glossary

Every term this repository defines, one line each. The document beside a
term explains it at length. A term that changes here changes there in the
same change (requirements.md, R0.6 to R0.9).

| term | what it is | explained in |
|---|---|---|
| `C` | The table's column count. | terminology.md, tables, rows and columns |
| column | One field of a row, `W` bytes wide. | terminology.md, tables, rows and columns |
| column map | The `C` bytes at +8 of a DTX3 payload, one a column: a live column's slot, or $FF for a constant column. | SPEC.md 2.4 |
| column table | What is behind a packaged reader's code and before its table: under DTX2 one stream record a column, four longs. DTX0, DTX1 and DTX3 do not have one. | abi.md 1 |
| constant column | A column whose `R` values are equal, which a DTX3 payload has as its value once, with neither ring nor decoder. | SPEC.md 2.4 |
| copy code | The part of an ST4 decoder that copies from a column's literal stream. A decoder is built with it or without, and the flags byte marks which a payload needs. | SPEC.md 2.3 |
| cursor | What advances to a next row and has its place in the table. | terminology.md, tables, rows and columns |
| data set | One column of a DTX2 payload, packed with ST4: its ST4 header, and the length of what it unpacks to. | SPEC.md 2.3 |
| decoder state | The eight longs a column's decoder is saved in between refills, with its ring's end, where its registers go at a loop, its budget, its phase and its mark: 48 bytes of a packaged DTX2 reader's state block, one a turn, and of a DTX3 reader's, one a slot and the idle state. | abi.md 3 |
| DTX | This format: a table of `R` rows and `C` columns. The table is data, and a reader of it is code. | README.md |
| format block | The 28 bytes at +16 of an image: the variant, the state block's bytes, where the table and the column table are, the row's bytes, `P`, `N`, `k`, `W` and the stride. | abi.md 1 |
| header | The 16 bytes before a payload: `DTX`, the variant, and the metadata. | SPEC.md 1 |
| idle state | The decoder state behind a packaged DTX3 reader's last slot's, with a budget of 0, which every turn past the last slot reads. | abi.md 3 |
| image | A table packaged for the 68000: the code, under DTX2 the column table, and the table's bytes in one file, read through four calls. | abi.md 1 |
| `k` | The third byte of a DTX2 or DTX3 payload: the unit its packed columns are packed at. | SPEC.md 2.3 and 2.4 |
| `L` | The live columns of a DTX3 table, 0 to `C`, a word at +4 of its payload. | SPEC.md 2.4 |
| literal stream | ST4's run of bytes packed as they are, which a match beyond the ring copies from. | SPEC.md 2.3 |
| live column | A column of a DTX3 table whose values differ, packed with ST4 and read through a ring. | SPEC.md 2.4 |
| long | Four bytes. An offset is on a long where it divides by 4. | SPEC.md |
| metadata | What describes a table without containing it: `R`, `C`, `RR` and `W`. | terminology.md, tables, rows and columns |
| `N` | The first two bytes of a DTX2 or DTX3 payload: how big a ring is. | SPEC.md 2.3 and 2.4 |
| payload | What follows the header: a table's rows in bytes, and in DTX2 the `N`, `k`, the flags and the offsets that reach them, and in DTX3 the `N`, `k`, the flags, `L`, the column map, the constant values and the stream records. | SPEC.md 2 |
| period | `P`, the rows between one column's refills in a packaged DTX2 or DTX3 reader. | abi.md 4 |
| pointer | The address, in a ring or in a payload, of the row a packaged reader's cursor is on. | abi.md 3 |
| `R` | The table's row count. | terminology.md, tables, rows and columns |
| reader | What reads rows out of a table. | terminology.md, tables, rows and columns |
| ring | The bytes of a column a reader has at a time, `N` of them, in place of the unpacked column. | SPEC.md 2.3 |
| row | One step of a table: `C` values, with nothing in it about what any of them is for. | terminology.md, tables, rows and columns |
| `RR` | The row a table repeats to once the last row is done. | terminology.md, tables, rows and columns |
| signature | The first long of an ST4 data set, `$53 $34 $07 k`. | SPEC.md 2.3 |
| slot | A live column's rank among a DTX3 table's live columns, the first being slot 0: its ring, decoder state and stream record are that many strides past the first. | SPEC.md 2.4 |
| ST4 | The packer a DTX2 column and a DTX3 live column are packed with, specified in its repository. | requirements.md R5 and R8, SPEC.md 2.3 and 2.4 |
| ST4 header | The twenty-eight bytes an ST4 data set opens with, its first long `$53 $34 $07 k`. | SPEC.md 2.3 |
| state block | What a caller of a packaged reader supplies, and passes back on every call but one. | abi.md 3 |
| stream record | Four longs, where a packed column's streams A, B, C and D begin from the payload's first byte: one a column in a DTX2 column table, and one a slot in a DTX3 payload. | abi.md 1, SPEC.md 2.4 |
| stride | The bytes from one column, ring or decoder state to the next. | abi.md 1 and 3, terminology.md, the variants |
| table | What yields rows: `R` of them, `C` columns wide, every value `W` bytes, repeating at `RR`. | terminology.md, tables, rows and columns |
| turn | The column a row refills in a packaged DTX2 reader, and the slot in a DTX3 reader: the row number modulo `P`. | abi.md 4 |
| unit | The width ST4 packs whole numbers of: 1, 2 or 4 bytes. | SPEC.md 2.3 |
| variant | One way of laying a table's rows out in bytes. DTX0 row by row, DTX1 column by column, DTX2 column by column and packed, DTX3 column by column with a constant column as its value and the others packed. | terminology.md, the variants |
| `W` | The bytes every value of the table is written in: 1, 2 or 4. | terminology.md, tables, rows and columns |
| word | Two bytes. An offset is on a word where it divides by 2. | SPEC.md |
| writer | What puts rows into a table. | terminology.md, tables, rows and columns |
| yielding | One row of a table at a time, in order. | terminology.md, tables, rows and columns |

