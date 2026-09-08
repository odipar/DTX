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
| init | 248 | 128 | 7844 | 6392 | 6390 |
| advance | 70 | 66 | 640-892 | 604-856 | 430-608 |
| jump to row 0 | 186 | 78 | 7984 | 6496 | 6498 |
| jump to row 63 | 186 | 78 | 55826 | 52234 | 43240 |
| code, bytes | 132 | 80 | 1412 | 1408 | 1420 |

### Twenty columns

| call | DTX0 | DTX1 | DTX2 k=1 | DTX2 k=2 | DTX2 k=4 |
|---|---|---|---|---|---|
| init | 248 | 128 | 55324 | 41284 | 39844 |
| advance | 70 | 66 | 1076 | 822 | 768 |
| jump to row 0 | 186 | 78 | 55900 | 41606 | 40112 |
| jump to row 63 | 186 | 78 | 117488 | 92792 | 88572 |
| code, bytes | 132 | 80 | 1412 | 1408 | 1420 |

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
step. A table that repeats is advanced out of its last row as out of
any other, since its data sets loop.

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
| 1 | 53932 | 54136 | 204 |
| 2 | 50340 | 50496 | 156 |
| 4 | 41346 | 41502 | 156 |

The two decoders differ at init, where the one with the copy code writes
the ring's size into two of its own instructions, and not in a row.
