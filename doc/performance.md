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

Two tables, both of 64 rows at a ring of 960 bytes. Row `r`, column `i` is
`r` times `i` plus one, modulo 251, so every value fits one byte, and the
first rows of the three column table are:

| row | c0 | c1 | c2 |
|---|---|---|---|
| 0 | 0 | 0 | 0 |
| 1 | 1 | 2 | 3 |
| 2 | 2 | 4 | 6 |

The widths are what the tables differ in. Under DTX0, DTX1 and DTX2 at `k`
of 1 a column is 1, 2 or 4 bytes wide in turn; under DTX2 at `k` of 2 and
4 every column is as wide as the unit, since a column is a whole number of
units.

### Three columns

| column | DTX0, DTX1, DTX2 k=1 | DTX2 k=2 | DTX2 k=4 |
|---|---|---|---|
| c0 | 1 | 2 | 4 |
| c1 | 2 | 2 | 4 |
| c2 | 4 | 2 | 4 |

### Twenty columns

| column | DTX0, DTX1, DTX2 k=1 | DTX2 k=2 | DTX2 k=4 |
|---|---|---|---|
| c0 | 1 | 2 | 4 |
| c1 | 2 | 2 | 4 |
| c2 | 4 | 2 | 4 |
| c3 | 1 | 2 | 4 |
| c4 | 2 | 2 | 4 |
| c5 | 4 | 2 | 4 |
| c6 | 1 | 2 | 4 |
| c7 | 2 | 2 | 4 |
| c8 | 4 | 2 | 4 |
| c9 | 1 | 2 | 4 |
| c10 | 2 | 2 | 4 |
| c11 | 4 | 2 | 4 |
| c12 | 1 | 2 | 4 |
| c13 | 2 | 2 | 4 |
| c14 | 4 | 2 | 4 |
| c15 | 1 | 2 | 4 |
| c16 | 2 | 2 | 4 |
| c17 | 4 | 2 | 4 |
| c18 | 1 | 2 | 4 |
| c19 | 2 | 2 | 4 |

## What a call costs

### Three columns

| call | DTX0 | DTX1 | DTX2 k=1 | DTX2 k=2 | DTX2 k=4 |
|---|---|---|---|---|---|
| init | 592 | 824 | 6688 | 5296 | 5488 |
| advance | 130 | 154 | 1650-3086 | 1688-1798 | 1750-1860 |
| read | 228 | 536 | 536 | 460 | 484 |
| take | 304 | 664 | 3618 | 2256 | 2342 |
| jump to row 0 | 338 | 176 | 7652 | 6298 | 6552 |
| jump to row 63 | 338 | 176 | 126334 | 100832 | 104628 |
| code, bytes | 408 | 716 | 1476 | 1480 | 1484 |

### Twenty columns

| call | DTX0 | DTX1 | DTX2 k=1 | DTX2 k=2 | DTX2 k=4 |
|---|---|---|---|---|---|
| init | 592 | 824 | 91392 | 33116 | 37116 |
| advance | 130 | 154 | 1868-11208 | 1906 | 2104 |
| read | 1064 | 1844 | 1844 | 1616 | 1776 |
| take | 1140 | 1972 | 13050 | 3520 | 3878 |
| jump to row 0 | 342 | 176 | 92574 | 34336 | 38534 |
| jump to row 63 | 342 | 176 | 229970 | 105998 | 115120 |
| code, bytes | 408 | 716 | 1476 | 1480 | 1484 |

An advance under DTX2 is a range because a row refills one column of `P`
rows, and the columns differ: a four byte column's refill decodes more
than a one byte column's, and a turn past the last column does not refill.
Under DTX2 at `k` of 2 and 4 in the twenty column table every column is
the same width, so every advance that refills costs the same.

**What is flat and what is not.** Under DTX0 and DTX1 every call is flat
in `R` and a read is linear in `C`. Under DTX2 a read is flat and linear in
`C`, an advance is flat and takes one column's refill, and a jump is not
flat: it runs the advance's body once a row up to the target, so a jump to
row 63 costs the 63 rows. A backward jump seeds every ring again first, so
a jump to row 0 costs one init. A table that repeats costs that at every
repeat, since the advance from row `R` minus one to `RR` is a jump.

**Init under DTX2** is `C` decoder seeds and `C` refills of `P` rows, so
it grows with `C` and with `P`: the init rows of the two tables.

**A read** is `C` moves and the tests around them: a wide column whose
place in the row is odd goes down as bytes, since a 68000 takes an address
error on a word at an odd address, and the test costs each wide column a
`btst` and a branch.

## What the copy code costs

A column packed without copies, read by the decoder without the copy code
and by the one with it: the cycles of init and then 64 rows read and
advanced, on the three column table at each unit.

| k | without | with | more |
|---|---|---|---|
| 1 | 137252 | 137408 | 156 |
| 2 | 143816 | 143972 | 156 |
| 4 | 149378 | 149534 | 156 |

The two decoders differ at init, where the one with the copy code writes
the ring's size into two of its own instructions, and not in a row.
