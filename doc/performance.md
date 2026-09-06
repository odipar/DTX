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
of three columns and one of twenty. Row `r`, column `i` is `r` times (`i`
plus one), modulo 251, so the first rows of the three column table are:

| row | c0 | c1 | c2 |
|---|---|---|---|
| 0 | 0 | 0 | 0 |
| 1 | 1 | 2 | 3 |
| 2 | 2 | 4 | 6 |

## What a call costs

### Three columns

| call | DTX0 | DTX1 | DTX2 k=1 | DTX2 k=2 | DTX2 k=4 |
|---|---|---|---|---|---|
| init | 350 | 278 | 6592 | 5140 | 5098 |
| advance | 126 | 122 | 1264-1374 | 1220-1330 | 478-1234 |
| jump to row 0 | 258 | 150 | 7510 | 6014 | 5986 |
| jump to row 63 | 258 | 150 | 75856 | 73272 | 58078 |
| code, bytes | 208 | 160 | 884 | 884 | 892 |

### Twenty columns

| call | DTX0 | DTX1 | DTX2 k=1 | DTX2 k=2 | DTX2 k=4 |
|---|---|---|---|---|---|
| init | 350 | 278 | 49278 | 35238 | 33798 |
| advance | 126 | 122 | 1700 | 1438 | 1394 |
| jump to row 0 | 258 | 150 | 50632 | 36330 | 34846 |
| jump to row 63 | 258 | 150 | 89860 | 79002 | 76846 |
| code, bytes | 208 | 160 | 884 | 884 | 892 |

There are four calls (abi.md 2), and none of them moves a value: an advance
gives the pointer at the row's first value and the caller reads from there.
So a read costs the image nothing, and what it costs the caller stands in
the table below.

An advance under DTX2 is a range where the columns differ in what their
refills decode, and one figure where they do not: a row refills one column
of `P` rows, and a turn past the last column does not refill.

**What is flat and what is not.** Under DTX0 and DTX1 every call is flat in
`R` and in `C`. Under DTX2 an advance is flat and takes one column's
refill, and a jump is not flat: it runs the advance's body once a row up to
the target, so a jump to row 63 costs the 63 rows. A backward jump seeds
every ring again first, so a jump to row 0 costs an init and one row's
step. A table that repeats costs that at every repeat, since the advance
from row `R` minus one to `RR` is a jump.

**Init under DTX2** is `C` decoder seeds and `C` refills of `P` rows, so it
grows with `C` and with `P`: the init rows of the two tables.

## What a value costs the caller

An advance leaves the pointer in `a1` and `DTX_metadata` gives the stride,
so column `i` of the row is one move at `i` strides off `a1`:

| width | the move | cycles |
|---|---|---|
| 1 | `move.b d(a1),d0` | 12 |
| 2 | `move.w d(a1),d0` | 12 |
| 4 | `move.l d(a1),d0` | 16 |

Those are the manual's figures for the move, which the rig reads out of the
same tables it counts a call with. A caller that reads every column of a
row makes `C` of them, and one that reads a single column makes one: DTX1
and DTX2 lay a column's values together, so taking one column of a wide
table never touches the others (R4.4).

## What the copy code costs

A column packed without copies, read by the decoder without the copy code
and by the one with it: the cycles of init and then 64 advances, on the
three column table at each unit.

| k | without | with | more |
|---|---|---|---|
| 1 | 88948 | 89152 | 204 |
| 2 | 84868 | 85024 | 156 |
| 4 | 69646 | 69802 | 156 |

The two decoders differ at init, where the one with the copy code writes
the ring's size into two of its own instructions, and not in a row.
