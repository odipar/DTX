# sources

One row a table of the kit: the text it was written from, the options
`dtx-write` took, what the file runs to, the first sixteen hex digits of
its sha256, and what reading it exercises. `ConformanceTest` writes every
table again from these and holds the file in `tables/` to it, so a row here
is one the writer gives.

The text is one of two. **numbers R C** is `R` rows of `C` columns where row
`r`, column `i` holds `r` times `i` plus one, modulo 251. **repeating** is
512 rows of two columns where row `r` holds `r` modulo 37 and seven times
that, so a pattern 37 rows long repeats, further back than a ring of 64
bytes reaches.

Beside every `NAME.dtx` stands `NAME.rows`: the rows the table holds, as
DTX0 lays them out, with nothing between the values. That file is what a
reader of the table gives back (TASK.md).

| table | text | options | bytes | sha256 | exercises |
|---|---|---|---|---|---|
| `dtx0-three-widths` | numbers 8 3 | `-v0 -w1,2,4` | 76 | 983aa5ff510c9766 | DTX0: a row of 1, 2 and 4 byte columns, the wide ones on odd offsets |
| `dtx0-one-column` | numbers 5 1 | `-v0 -w1` | 21 | ec7aec710189936c | DTX0: one column, one byte, five rows |
| `dtx0-repeat` | numbers 8 2 | `-v0 -w1,1 -r3` | 32 | f9a36ba7474ef56e | DTX0: a table that repeats at row 3 |
| `dtx1-three-widths` | numbers 8 3 | `-v1 -w1,2,4` | 76 | 2ae9fdad1be441f8 | DTX1: a byte column before a word one, so the word column begins on a pad byte |
| `dtx1-widest-first` | numbers 6 3 | `-v1 -w4,2,1` | 62 | 4aef7c91af19e6b4 | DTX1: the widest column first, so no pad byte at all |
| `dtx1-one-row` | numbers 1 2 | `-v1 -w1,4` | 22 | 24b29b22507bcd7e | DTX1: one row, R of 1 |
| `dtx1-repeat-at-0` | numbers 4 2 | `-v1 -w2,2 -r0` | 32 | b22519a345a76376 | DTX1: RR of 0, the table repeats from its first row |
| `dtx2-k1` | numbers 64 3 | `-v2 -w1,2,4 -k1 -m960` | 440 | a57c6a52d6f03d4f | DTX2 at k of 1: three widths, N of 960, P of 3 |
| `dtx2-k2` | numbers 64 2 | `-v2 -w2,2 -k2 -m960` | 348 | cb97416dc6b2a865 | DTX2 at k of 2: every column a whole number of units |
| `dtx2-k4` | numbers 64 2 | `-v2 -w4,4 -k4 -m960` | 604 | ea2373e6ec9dae7d | DTX2 at k of 4 |
| `dtx2-repeat` | numbers 64 2 | `-v2 -w1,1 -r16 -k1 -m960` | 220 | bc4fe5202238befb | DTX2: a table that repeats at row 16, a jump backward on a packed reader |
| `dtx2-rows-not-a-multiple-of-p` | numbers 50 3 | `-v2 -w1,1,1 -k1 -m960` | 288 | 51a5fda240d4d801 | DTX2: R of 50 at P of 3, so the last refill of a column is short |
| `dtx2-copies` | repeating | `-v2 -w1,2 -k1 -m64 -copies` | 272 | 867bf4e0ad703ef3 | DTX2 with copies from the literal stream, at a ring of 64 the pattern does not fit |
