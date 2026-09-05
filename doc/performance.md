# performance

What each call of a packaged reader costs, in instructions, measured.

The figures are instruction counts on a 68000 under emulation, read out of
the rig `68k/test/emu/test_dtx.py`, which checks this document against what
it measures and fails where a figure here is not the one it counts. They
are not cycles: the emulator counts instructions and not the clocks each
one takes, so a figure here is compared with another figure here and with
nothing that is timed.

Every table is 64 rows at a ring of 960 bytes. The DTX0, DTX1 and DTX2 at
`k` of 1 columns are for three columns of widths 1, 2 and 4; the DTX2 at
`k` of 2 column is for three columns of width 2, and at `k` of 4 three of
width 4, since a column is a whole number of units.

| call | DTX0 | DTX1 | DTX2 k=1 | DTX2 k=2 | DTX2 k=4 |
|---|---|---|---|---|---|
| init | 57 | 75 | 778 | 557 | 569 |
| advance | 10 | 10 | 126-342 | 130-138 | 134-142 |
| read | 20 | 40 | 40 | 39 | 39 |
| take | 26 | 48 | 382 | 177 | 181 |
| jump to row 0 | 27 | 15 | 848 | 631 | 647 |
| jump to row 63 | 27 | 15 | 12061 | 8010 | 8258 |
| code, bytes | 408 | 716 | 1476 | 1480 | 1484 |

An advance under DTX2 is a range because a row refills one column of `P`
rows, and the columns differ: a four byte column's refill decodes more than
a one byte column's, and a turn past the last column refills nothing.

**What is flat and what is not.** Under DTX0 and DTX1 every call is flat
in `R` and a read is linear in `C`. Under DTX2 a read is flat and linear in
`C`, an advance is flat and takes one column's refill, and a jump is not
flat: it runs the advance's body once a row up to the target, so a jump to
row 63 costs the 63 rows. A backward jump seeds every ring again first, so
a jump to row 0 costs one init. A table that repeats costs that at every
repeat, since the advance from row `R` minus one to `RR` is a jump.

**Init under DTX2** is `C` decoder seeds and `C` refills of `P` rows, and
grows with `C`: 778 instructions at three columns, 1337 at eight one byte
columns.

**A read** is `C` moves and the tests around them: a wide column whose
place in the row is odd goes down as bytes, since a 68000 takes an address
error on a word at an odd address, and the test costs each wide column a
`btst` and a branch. A read of three columns went from 32 instructions to
40 for it.

**What the copy code costs** a column without copies: 14 instructions over
64 rows read, 0.1%, at each unit. experiments.md gives the bytes.
