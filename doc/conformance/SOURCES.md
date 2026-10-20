# sources

One row a table of the kit: the text it was written from, the options
`dtx-write` ran with, what the file runs to, the first sixteen hex digits of
its sha256, and what reading it exercises. `ConformanceTest` writes every
table again from these and compares the file in `tables/` with it, so a
row here is one the writer wrote.

The text is one of three. **numbers R C** is `R` rows of `C` columns where
row `r`, column `i` is `r` times (`i` plus one), modulo 251. **mixed R C**
is the same, with each column it names 3 times its number in every row, so
those columns are constant. **repeating** is 512 rows of two columns where
row `r` is `r` modulo 37 and seven times that, so a pattern 37 rows long
repeats, further back than a ring of 64 bytes reaches.

Beside every `NAME.dtx` is `NAME.rows`: the rows in the table, as DTX0
lays them out, with nothing between the values. A reader of the table
reads that file back (TASK.md).

| table | text | options | bytes | sha256 | exercises |
|---|---|---|---|---|---|
| `dtx0-w1` | numbers 8 3 | `-v0 -w1` | 40 | 36d25f5d2b148a11 | DTX0 at a width of 1: an odd row, so a row begins on an odd offset |
| `dtx0-w2` | numbers 8 3 | `-v0 -w2` | 64 | a0159dfb4a880fa5 | DTX0 at a width of 2 |
| `dtx0-w4` | numbers 6 3 | `-v0 -w4` | 88 | b00e5ace6866c63d | DTX0 at a width of 4 |
| `dtx0-one-column` | numbers 5 1 | `-v0 -w1` | 21 | ec7aec710189936c | DTX0: one column, one byte, five rows |
| `dtx0-repeat` | numbers 8 2 | `-v0 -w1 -r3` | 32 | 6965f829b5459d2a | DTX0: a table that repeats at row 3 |
| `dtx1-w1-odd-rows` | numbers 7 3 | `-v1 -w1` | 39 | e7977b28533dcb4d | DTX1 at a width of 1 and an odd R, so a pad byte is between columns |
| `dtx1-w2` | numbers 8 3 | `-v1 -w2` | 64 | 98f9da272a4e65cb | DTX1 at a width of 2, where a column is a whole number of words |
| `dtx1-w4` | numbers 6 3 | `-v1 -w4` | 88 | 793f8d82d9ac7b53 | DTX1 at a width of 4 |
| `dtx1-one-row` | numbers 1 2 | `-v1 -w4` | 24 | 980824999c012be3 | DTX1: one row, R of 1 |
| `dtx1-repeat-at-0` | numbers 4 2 | `-v1 -w2 -r0` | 32 | cb4e4db6209408bb | DTX1: RR of 0, the table repeats from its first row |
| `dtx2-w1-k1` | numbers 64 3 | `-v2 -w1 -k1 -m960` | 320 | 98a50698e6348630 | DTX2 at a width of 1 and k of 1: N of 960, P of 3 |
| `dtx2-w2-k2` | numbers 64 2 | `-v2 -w2 -k2 -m960` | 348 | e92a23fd8cd8bed6 | DTX2 at a width of 2 and k of 2, a unit a value |
| `dtx2-w4-k4` | numbers 64 2 | `-v2 -w4 -k4 -m960` | 604 | 5fb2e661a148978c | DTX2 at a width of 4 and k of 4 |
| `dtx2-w4-k1` | numbers 64 2 | `-v2 -w4 -k1 -m960` | 324 | 475d57982f70276b | DTX2 at a width of 4 and k of 1, a unit below the width |
| `dtx2-w1-k4` | numbers 64 2 | `-v2 -w1 -k4 -m960` | 220 | 53c80b69f773a637 | DTX2 at a width of 1 and k of 4, a unit above the width |
| `dtx2-repeat` | numbers 64 2 | `-v2 -w1 -r16 -k1 -m960` | 226 | 7db50b78c9c39e15 | DTX2: a table that repeats at row 16, so its data sets loop there |
| `dtx2-rows-not-a-multiple-of-p` | numbers 50 3 | `-v2 -w1 -k1 -m960` | 284 | 76c705b1e5fc0347 | DTX2: R of 50 at P of 3, so the last refill of a column is short |
| `dtx2-twenty-columns` | numbers 64 20 | `-v2 -w2 -k1 -m960` | 3300 | 34c8bd7f7fc53dd6 | DTX2: C of 20, so P is 20 and a read walks twenty rings |
| `dtx2-copies` | repeating | `-v2 -w2 -k1 -m64 -copies` | 356 | 4764b1c120676c1a | DTX2 with copies from the literal stream, at a ring of 64 the pattern does not fit |
| `dtx3-w1-k1` | mixed 64 6, columns 0, 2 and 5 | `-v3 -w1 -k1 -m960` | 288 | 622c5fb704173997 | DTX3: three constant columns among six, a map with gaps and three values |
| `dtx3-w2-k2` | numbers 64 2 | `-v3 -w2 -k2 -m960` | 324 | 5c85aa3052ab4eac | DTX3 at a width of 2 and k of 2, every column live |
| `dtx3-w4-k1` | mixed 64 3, column 1 | `-v3 -w4 -k1 -m960` | 304 | 4bf05d33c8b16bc9 | DTX3 at a width of 4 and k of 1, a constant value of four bytes |
| `dtx3-every-column-constant` | mixed 8 3, columns 0 to 2 | `-v3 -w2 -k1 -m960` | 36 | 62d2e607f0c163c2 | DTX3: L of 0, every column a value and no stream record |
| `dtx3-replayed` | mixed 512 3, column 1 | `-v3 -w1 -r0 -k1 -m64` | 1092 | 9ea895758ada64d9 | DTX3: a loop of 512 rows against a ring of 64, which bit 1 marks replayed |
| `dtx3-copies` | repeating | `-v3 -w2 -k1 -m64 -copies` | 332 | 7721acd1cc29b053 | DTX3 with copies where they pack a column smaller, at a ring of 64 |
| `dtx3-thirty-columns` | mixed 120 30, columns 13 to 29 | `-v3 -w1 -k1 -m60` | 1892 | dc371e11d6342bbd | DTX3: thirteen live columns of thirty at a ring of 60, P of 30 |
