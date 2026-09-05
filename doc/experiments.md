# experiments

What was measured against real tables, and what came out. Every figure
here is one a test reads back: `ExperimentsTest` checks the two tables of
bytes against the writer and the packager, `StabilityTest` checks the
decoder's bytes against rmac, and the rig `68k/test/emu/test_dtx.py`
counts the instructions.

The numbers table is the rig's: row `r`, column `i` is `r` times `i` plus
one, modulo 251, at the widths given.

## Where DTX2 becomes the smaller of the two

R5.7: a packed table is smaller than a plain one once it has rows enough
for the packing to cost less than it saves. The numbers table at widths 1,
2 and 4, packed at `k` of 1 and `N` of 960, DTX1 against DTX2:

| R | DTX1 bytes | DTX2 bytes | DTX2 over DTX1 |
|---|---|---|---|
| 3 | 42 | 144 | 3.43 |
| 6 | 62 | 168 | 2.71 |
| 12 | 104 | 192 | 1.85 |
| 24 | 188 | 248 | 1.32 |
| 48 | 356 | 364 | 1.02 |
| 64 | 468 | 440 | 0.94 |
| 96 | 692 | 592 | 0.86 |
| 128 | 916 | 744 | 0.81 |
| 256 | 1812 | 1348 | 0.74 |
| 512 | 3604 | 1800 | 0.50 |

DTX2 is the smaller from between 48 and 64 rows on this table, and at 512
rows it is half of DTX1. Below 48 it is the larger: the 28 byte ST4 header
a column and the payload's offsets do not shrink with `R`.

## Copies from the literal stream, at a small ring

A 512 row table of two columns, widths 1 and 2, repeating a pattern 37 rows
long, so a match reaches further back than a ring of 64 bytes:

| written as | file bytes | image bytes |
|---|---|---|
| DTX1 | 1552 | 2356 |
| DTX2, N=64 | 1164 | 2792 |
| DTX2, N=64, copies | 272 | 1932 |
| DTX2, N=128, copies | 220 | 1880 |

Without copies the ring is too short for the pattern and DTX2 packs to
three quarters of DTX1. With them a match beyond the ring copies from the
column's own literal stream, and the file is under a fifth of DTX1's. The
image moves less than the file, since the code inside it does not move.

## What the copy code costs

ST4_wrap.S assembled alone, without the copy code and with it:

| k | without | with | more |
|---|---|---|---|
| 1 | 324 | 354 | 30 |
| 2 | 328 | 360 | 32 |
| 4 | 330 | 366 | 36 |

In an image the difference is 32, 32 and 36 bytes: the decoder stands on a
long, and the 30 rounds up to one. In instructions, on a column without
copies, the copy code costs 14 over 64 rows read, 0.1 percent, at every
`k`: the two decoders differ at init, where the one with the copy code
writes the ring's size into two of its own instructions, and not in a row.

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
under that model, so a wide column whose place in the row is odd, a two
byte column after a one byte one, was read with one word move on every
table it passed, and would have faulted on the hardware. The rig watches
every access now and fails a misaligned one as the 68000 does, and the read
tests each wide entry and moves bytes where its offset is odd. What that
cost: a read of three columns went from 32 instructions to 40, DTX1's code
from 592 bytes to 716 and DTX2's from 1352 to 1476.

## A column packed with copies, read without them

Before the payload stated whether its columns contain copies (R5.10), the
packager took that from a flag beside the file, and the flag could be
wrong. Measured on the 512 row table above, packaged without it: row 37
read wrong, where the pattern first repeats past the ring, and every row
before it read right. SPEC.md 2.3 puts the flag in the payload, so the
packager reads it out of the table and no word from beside the file enters.
