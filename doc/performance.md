# performance

What each call of a packaged reader costs, in 68000 cycles, measured.

The figures are cycle counts read out of the rig `68k/test/emu/test_dtx.py`.
It runs each image under emulation and gives every instruction the machine
runs the cycles the M68000 user's manual's tables give it, out of the opcode
and, for a branch or a loop, out of where the machine went next; the rig
checks this document against its totals and fails where a figure here is
not the one it counts. Before it counts, it checks its tables against the
manual on a set of encodings. The cycles are the processor's own, with no
wait state: a machine whose bus rounds an access up, as an Atari ST's does,
takes longer.

## The example tables

Two tables, both of 64 rows of two byte values at a ring of 960 bytes: one
of three columns and one of twenty. Row `r`, column `i` is `r` times `i`
plus one, modulo 251, so the first rows of the three column table are:

| row | c0 | c1 | c2 |
|---|---|---|---|
| 0 | 0 | 0 | 0 |
| 1 | 1 | 2 | 3 |
| 2 | 2 | 4 | 6 |

Every value of a table is one width (R6.3), so a table is `R`, `C` and that
width and the code is built for it: the width table below is what it costs.

## What a call costs

### Three columns

| call | DTX0 | DTX1 | DTX2 k=1 | DTX2 k=2 | DTX2 k=4 |
|---|---|---|---|---|---|
| init | 580 | 458 | 7120 | 5722 | 5680 |
| advance | 130 | 106 | 1398-1508 | 1372-1482 | 594-1386 |
| read | 140 | 188 | 188 | 188 | 188 |
| take | 216 | 272 | 1698 | 1672 | 1576 |
| jump to row 0 | 292 | 166 | 8104 | 6680 | 6652 |
| jump to row 63 | 292 | 166 | 82424 | 80956 | 64796 |
| code, bytes | 392 | 316 | 1080 | 1084 | 1092 |

### Twenty columns

| call | DTX0 | DTX1 | DTX2 k=1 | DTX2 k=2 | DTX2 k=4 |
|---|---|---|---|---|---|
| init | 566 | 458 | 52390 | 38710 | 37270 |
| advance | 130 | 106 | 1834 | 1590 | 1546 |
| read | 374 | 834 | 834 | 834 | 834 |
| take | 450 | 918 | 2670 | 2426 | 2382 |
| jump to row 0 | 292 | 166 | 53810 | 39886 | 38402 |
| jump to row 63 | 292 | 166 | 96014 | 86218 | 84062 |
| code, bytes | 392 | 316 | 1080 | 1084 | 1092 |

An advance under DTX2 is a range where the columns differ in what their
refills decode, and one figure where they do not: a row refills one column
of `P` rows, and a turn past the last column does not refill.

**What is flat and what is not.** Under DTX0 and DTX1 every call is flat in
`R`, and a read is linear in `C`. Under DTX2 a read is flat and linear in
`C`, an advance is flat and takes one column's refill, and a jump is not
flat: it runs the advance's body once a row up to the target, so a jump to
row 63 costs the 63 rows. A backward jump seeds every ring again first, so
a jump to row 0 costs one init. A table that repeats costs that at every
repeat, since the advance from row `R` minus one to `RR` is a jump.

**Init under DTX2** is `C` decoder seeds and `C` refills of `P` rows, so it
grows with `C` and with `P`: the init rows of the two tables.

**A read** under DTX0 is one run of bytes, at the widest move the row's
bytes take. Under DTX1 and DTX2 it is `C` moves and a stride each: one
value from each column, and the columns lie at one stride because every
column is the same length.

## What the width costs

A read of the twenty column table, at each width:

| width | DTX0 | DTX1 | DTX2 k=1 |
|---|---|---|---|
| 1 | 224 | 834 | 834 |
| 2 | 374 | 834 | 834 |
| 4 | 674 | 994 | 994 |

A wider value moves more bytes and the row is longer, so a read grows with
the width. It does not grow twice over: the loop is the same `C` turns at
every width, and only the move inside it changes.

## What the copy code costs

A column packed without copies, read by the decoder without the copy code
and by the one with it: the cycles of init and then 64 rows read and
advanced, on the three column table at each unit.

| k | without | with | more |
|---|---|---|---|
| 1 | 109806 | 110010 | 204 |
| 2 | 106914 | 107070 | 156 |
| 4 | 90726 | 90882 | 156 |

The two decoders differ at init, where the one with the copy code writes
the ring's size into two of its own instructions, and not in a row.
