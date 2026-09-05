# experiments

What was measured against real tables, and what came out. Every figure
here is one a test reads back: `ExperimentsTest` checks the two tables of
bytes against the writer and the packager, `StabilityTest` checks the
decoder's bytes against rmac, and the rig `68k/test/emu/test_dtx.py`
counts the cycles.

The numbers table is the rig's: row `r`, column `i` is `r` times `i` plus
one, modulo 251, at the width given. Every value of a table takes one
width (R6.3), so the width is a figure of the table like `R` and `C`.

## Where DTX2 becomes the smaller of the two

R5.7: a packed table is smaller than a plain one once it has rows enough
for the packing to cost less than it saves. The numbers table of three
columns at a width of 1, packed at `k` of 1 and `N` of 960, DTX1 against
DTX2:

| R | DTX1 bytes | DTX2 bytes | DTX2 over DTX1 |
|---|---|---|---|
| 3 | 27 | 140 | 5.19 |
| 6 | 34 | 152 | 4.47 |
| 12 | 52 | 164 | 3.15 |
| 24 | 88 | 200 | 2.27 |
| 48 | 160 | 272 | 1.70 |
| 64 | 208 | 320 | 1.54 |
| 96 | 304 | 416 | 1.37 |
| 128 | 400 | 512 | 1.28 |
| 256 | 784 | 896 | 1.14 |
| 512 | 1552 | 908 | 0.59 |

DTX2 is the larger up to 256 rows on this table and the smaller at 512,
where it is under three fifths of DTX1. What packing costs does not shrink
with `R`: the 28 byte ST4 header a column, and the payload's offsets. What
it saves does, and on this table it overtakes between 256 and 512 rows,
where the values begin to repeat: they are taken modulo 251.

## Copies from the literal stream, at a small ring

A 512 row table of two columns at a width of 2, repeating a pattern 37
rows long, so a column's pattern is 74 bytes and a match reaches further
back than a ring of 64:

| written as | file bytes | image bytes |
|---|---|---|
| DTX1 | 2064 | 2284 |
| DTX2, N=64 | 2140 | 3140 |
| DTX2, N=64, copies | 356 | 1388 |
| DTX2, N=128, copies | 252 | 1284 |

Without copies the ring is too short for the pattern and DTX2 packs to
more than DTX1. With them a match beyond the ring copies from the column's
own literal stream, and the file is under a fifth of DTX1's. The image
moves less than the file, since the code inside it does not move.

## What the copy code costs

ST4_wrap.S assembled alone, without the copy code and with it:

| k | without | with | more |
|---|---|---|---|
| 1 | 324 | 354 | 30 |
| 2 | 328 | 360 | 32 |
| 4 | 330 | 366 | 36 |

In an image the difference is 32, 32 and 36 bytes: the decoder stands on a
long, and the 30 rounds up to one. In cycles, on a column without copies,
the copy code costs what performance.md's last table gives, 0.1 percent,
at every `k`: the two decoders differ at init, where the one with the copy
code writes the ring's size into two of its own instructions, and not in a
row.

## ST4_wrap against ST4_ring

The decoder a packaged reader takes is ST4_wrap, which decodes a fixed
budget a call and leaves the ring's wrap to the caller; ST4_ring checks the
ring end itself. Assembled alone at each unit, ST4_wrap is 324, 328 and 330
bytes and ST4_ring 386, 394 and 396, measured from odipar/ST4's `68k/` at
498aa25, of which this repository contains the first and not the second.
The reader wraps the write pointer with one compare after each refill, so
it takes the smaller decoder (abi.md 8).

## A word at an odd address

A 68000 takes an address error on a word or long at an odd address, and
Unicorn's model of it does not: it reads and writes the bytes. The rig ran
under that model, so a wide column whose place in the row was odd, a two
byte column after a one byte one, was read with one word move on every
table it passed, and would have faulted on the hardware. The rig watches
every access now and fails a misaligned one as the 68000 does. The read
then tested each wide value and moved bytes where its offset was odd,
which cost a `btst` and a branch a column and took DTX1's code from 592
bytes to 716 and DTX2's from 1352 to 1476.

One width a table (R6.3) took the test out again, and the ABI took the
move with it: an advance gives the pointer at the row's first value and
the caller reads where the values stand (abi.md 2), so nothing in an image
moves one. DTX1's code is 176 bytes now and DTX2's 924, and the rig's
alignment hook passes every table it runs.

## A column packed with copies, read without them

Before the payload defined whether its columns contain copies (R5.10), the
packager took that from a flag beside the file, and the flag could be
wrong. Measured on the 512 row table above, packaged without it: row 37
read wrong, where the pattern first repeats past the ring, and every row
before it read right. SPEC.md 2.3 puts the flag in the payload, so the
packager reads it out of the table and no word from beside the file enters.
