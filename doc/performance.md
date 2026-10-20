# performance

What each call of a packaged reader costs, in 68000 cycles, measured.

The figures are cycle counts read out of the rig `68k/test/emu/test_dtx.py`.
It runs each image under emulation and counts every instruction the machine
runs at the cycles the M68000 user's manual's tables list for it, out of the
opcode and, for a branch or a loop, out of where the machine went next; the
rig checks this document against its totals and fails where a figure here
differs from its count. Before it counts, it checks its tables against the
manual on a set of encodings. The cycles are the processor's, with no wait
state: a machine whose bus rounds an access up, as an Atari ST's does,
runs longer.

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
| init | 248 | 128 | 7700 | 6332 | 6330 |
| advance | 70 | 66 | 634-886 | 598-850 | 430-602 |
| jump to row 0 | 186 | 78 | 7834 | 6430 | 6432 |
| jump to row 63 | 186 | 78 | 55304 | 51796 | 42910 |
| code, bytes | 132 | 80 | 1400 | 1396 | 1404 |

### Twenty columns

| call | DTX0 | DTX1 | DTX2 k=1 | DTX2 k=2 | DTX2 k=4 |
|---|---|---|---|---|---|
| init | 248 | 128 | 54524 | 40964 | 39444 |
| advance | 70 | 66 | 1078 | 820 | 762 |
| jump to row 0 | 186 | 78 | 55102 | 41284 | 39706 |
| jump to row 63 | 186 | 78 | 116648 | 92272 | 87812 |
| code, bytes | 132 | 80 | 1400 | 1396 | 1404 |

There are four calls (abi.md 2), and none of them moves a value: an advance
leaves the pointer at the row's first value and the caller reads from there.
So a read costs the image nothing, and the table below has what it costs
the caller.

An advance under DTX2 is a range where the columns differ in what their
refills decode, and one figure where they do not: a row refills one column
of `P` rows, and a turn past the last column does not refill.

**What is flat and what is not.** Under DTX0 and DTX1 every call is flat in
`R` and in `C`. Under DTX2 an advance is flat and costs one column's
refill, and a jump is not flat: it runs the advance's body once a row up to
the target, so a jump to row 63 costs the 63 rows. A backward jump seeds
every ring again first, so a jump to row 0 costs an init and one row's
step. A table that repeats is advanced out of its last row as out of
any other, since its data sets loop.

**Init under DTX2** is `C` decoder seeds and `C` refills of `P` rows, so it
grows with `C` and with `P`: the init rows of the two tables.

## What a value costs the caller

An advance leaves the pointer in `a1` and `DTX_metadata` reports the stride,
so column `i` of the row is one move at `i` strides off `a1`:

| width | the move | cycles |
|---|---|---|
| 1 | `move.b d(a1),d0` | 12 |
| 2 | `move.w d(a1),d0` | 12 |
| 4 | `move.l d(a1),d0` | 16 |

Those are the manual's figures for the move, which the rig reads out of the
same tables it counts a call with. A caller that reads every column of a
row makes `C` of them, and one that reads a single column makes one: DTX1
and DTX2 lay a column's values together, so reading one column of a wide
table leaves the others unread (R4.4).

## What the copy code costs

A column packed without copies, read by the decoder without the copy code
and by the one with it: the cycles of init and then 64 advances, on the
three column table at each unit.

| k | without | with | more |
|---|---|---|---|
| 1 | 53410 | 53614 | 204 |
| 2 | 49902 | 50058 | 156 |
| 4 | 41016 | 41172 | 156 |

The two decoders differ at init, where the one with the copy code writes
the ring's size into two of its instructions, and not in a row.

## DTX3

The same two tables, every column of them live, and a third of thirty
columns of one byte whose columns 13 to 29 are constant: column `i` of row
`r` is `r` times (`i` plus one), modulo 251, below column 13, and 3 times
`i` from there. Each is at a ring of 960 bytes. An advance is counted over
the first eight rows, as under DTX2; an idle turn over 64 rows, on the
turns past the last slot that do not end a period.

### Three columns

| call | DTX3 k=1 | DTX3 k=2 | DTX3 k=4 |
|---|---|---|---|
| init | 7732 | 6364 | 6322 |
| advance | 638-890 | 602-854 | 406-606 |
| an idle turn | - | - | - |
| jump to row 0 | 7844 | 6440 | 6402 |
| jump to row 63 | 55554 | 52046 | 42608 |
| code, bytes | 1416 | 1412 | 1420 |

### Twenty columns

| call | DTX3 k=1 | DTX3 k=2 | DTX3 k=4 |
|---|---|---|---|
| init | 54556 | 40996 | 39476 |
| advance | 1082 | 824 | 766 |
| an idle turn | - | - | - |
| jump to row 0 | 55112 | 41294 | 39716 |
| jump to row 63 | 116814 | 92438 | 87978 |
| code, bytes | 1416 | 1412 | 1420 |

### Thirty columns, seventeen constant

| call | DTX3 k=1 | DTX3 k=2 | DTX3 k=4 |
|---|---|---|---|
| init | 28458 | 25728 | 25130 |
| advance | 944 | 746 | 794 |
| an idle turn | 154 | 154 | 154 |
| jump to row 0 | 28884 | 25956 | 25406 |
| jump to row 63 | 58898 | 52990 | 45934 |
| code, bytes | 1404 | 1416 | 1416 |

An advance that refills is DTX2's and 4 cycles more: the branch past the
refill and the step is a word branch, which costs 12 cycles where it falls
through against a short branch's 8. An **idle turn** is 154 cycles: it reads
the idle state's budget of 0, steps the turn and moves the pointer. The last
turn of a period also runs the period's end, as it does under DTX2, and
costs more.

**Init under DTX3** is `L` decoder seeds and `L` refills, so the table of
thirty columns, thirteen of them live, initialises in about half the cycles
of the twenty column table. The code is 16 bytes larger than DTX2's at a
width and a unit: init works out where the stream records are behind the
map and the constant values.
