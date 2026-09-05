# The DTX68 calling convention

A DTX table packaged as a standalone 68000 binary: the code first, the
table's bytes after it, and four calls that reach those bytes PC relative.
No relocation, no operating system, nothing allocated while it runs.

**No call copies a value.** An advance gives the caller the address of the
row's first value, and `DTX_metadata` gives the stride from one column's
value to the next, so a caller reads the columns it needs where they
stand. A packaged reader finds a row; the caller reads it.

One image contains one table, so it contains the code for that table's
variant. The variant is resolved at package time behind four slots: a
caller has one contract and one state block, and the code the packager
emits behind the slots changes with the variant.

The code behind those slots does not move with the table's shape. `R`,
`C` and `RR` reach it at run time, out of the table's own header, and init
writes the few of them a loop counts with into the instructions that take
them. So one build assembles to one image, byte for byte, at any number of
rows or columns.

What does move the code is the width and the decoder. Every value of a
table takes one width (R6.3), and that width is an assembly-time constant:
a read moves a value in one instruction, and a table of another width
takes another build. Under DTX2 `k` and the copy code are ST4's own build
parameters and move it the same way. The cost is one build a width, and
where the image stands: the code writes to itself at init, so it stands in
RAM and not in ROM, and a 68030 caller flushes the instruction cache after
`DTX_init`.

Under DTX2 the reader follows YMX's shape, which plays twenty-five packed
streams a frame at a time on the same hardware: every column decodes
through its own ring, init fills every ring once, and after that one
column is refilled a row, in turn. So a read takes one value from each
ring and no read ever decodes.

Terms are the glossary's. This document adds four, and the glossary takes
them in the change that adds this file (R0.8): **state block**, what a
caller supplies and passes back; **pointer**, the address in a ring of the
row the cursor stands on; **turn**, the column a row refills; **period**,
`P`, the rows between one column's refills.

---

## 1. What the image contains

In this order, from the image's first byte:

| at | bytes | contains |
|---|---|---|
| +0 | 16 | four `bra.w` slots, one a call |
| +16 | 28 | the format block, on a long |
| +44 | .. | the bodies for the variant |
| .. | 324, 328 or 330 | under DTX2, ST4's wrap decoder at `k` |
| .. | .. | under DTX2 the column table, on a long. The plain variants do not have one |
| .. | .. | the table's bytes, header and payload, on a long |

The four slots stand at +0, +4, +8 and +12, in the order the calls are
numbered below, following ST4's own precedent. The dispatch is the whole
of it: no call tests the variant, because the packager emitted the bodies
for it.

**The format block**, 28 bytes on a long:

| at | bytes | gives |
|---|---|---|
| +0 | 4 | `DTX` and the variant this image reads |
| +4 | 4 | the state block's bytes |
| +8 | 4 | the table's header, from the image's first byte |
| +12 | 2 | the row's bytes, `C` times the width |
| +14 | 2 | `P`, the period in rows. 1 under DTX0 and DTX1 |
| +16 | 2 | `N`, a ring's bytes. Zero under DTX0 and DTX1 |
| +18 | 1 | `k`. Zero under DTX0 and DTX1 |
| +19 | 1 | the width this image reads. Zero under DTX0, whose code does not move with it |
| +20 | 4 | the column table, from the image's first byte |
| +24 | 4 | the stride from one column's value to the next |

`R`, `C` and `RR` are not here. They stand in the table's own header, at
the offsets SPEC.md 1 gives, and the field at +8 reaches it. The variant
at +3 of that header and the width at +14 are the two bytes this block
repeats, and the packager checks both rather than writing them: an image
built for another variant or another width does not read this table.

The block does not define a cost figure. What a call costs is measured on the
emitted code under emulation and recorded in performance.md, not in the
image.

The two offsets are fields rather than displacements the assembler works
out because both move with `C`, and the code does not.

**The column table**, which DTX2 has and the plain variants do not. Under DTX0
and DTX1 a read is arithmetic on `R`, `C` and the width, and the packager
writes down none of it: every column is the same length and they lie at one
stride. Under DTX2 the table is one stream record a column, 16 bytes at a
stride of 16, and nothing else:

| at | bytes | gives |
|---|---|---|
| +0 | 4 | stream A, from the payload |
| +4 | 4 | stream B |
| +8 | 4 | stream C |
| +12 | 4 | stream D |

The ring and the decoder state a record used to give are strides as well:
every ring is `N` bytes and every decoder state 32, so column `i`'s stand
`i` strides past column 0's. One fill loop and one refill body reach every
column: the fill counts the columns up and the refill indexes them by the
turn, so the decoder ST4 contributes stands in the image once.

---

## 2. The four calls

Under every variant `d6`, `d7`, `a6` and the stack beyond the return
address stand, as they stand across an ST4 call. No call builds a stack
frame.

| call | in | out | clobbered |
|---|---|---|---|
| `DTX_init` | `a0` the state block | nothing | d0-d5, a0-a5 |
| `DTX_metadata` | nothing | `a0` format block, `a1` the table's header, `d0.l` `R`, `d1.w` `C`, `d2.l` `RR`, `d3.l` the stride | d0-d3, a0-a1 |
| `DTX_jump` | `a0`, `d0.l` the row | `d0.l` that row, `a1` its first value | d0-d5, a0-a5 |
| `DTX_advance` | `a0` | `d0.l` the row, or $FFFFFFFF, `a1` the row's first value | d0-d5, a0-a5 |

### 0. `DTX_init`, image+0
Seeds the block. The cursor does not stand on a row, so the first advance
gives row 0 (terminology.md).

Under DTX0 it writes one pointer, the payload minus the row's bytes. Under
DTX1 it writes one pointer, the payload minus the width. With the pointer a
row below row 0, the advance from before row 0 to row 0 steps like any
other advance.

**Under DTX2 it fills every ring before it returns.** For each column it
forms the five pointers ST4_init takes, calls it, and then makes one
`ST4_resume` of `P` rows into that column's ring, so every ring contains
`P` rows before any row is read. It stores the decoder's eight longs in
the column's decoder state, sets the turn to 0, and leaves the pointer a
row below row 0 of column 0's ring.

After that preload a read alternates between the streams and does not touch a
decoder: from row 0 onward every value a read takes is already in a ring.
Init writes only the block and its own instructions. The image is code in RAM
under every variant: the sites section 5 lists are written on every init, and
under DTX2 with copies ST4's own init writes the reach into two more. A 68030
caller flushes the instruction cache after `DTX_init`, and after a jump that
runs from row 0, which seeds the decoders again.

Two readers of one image run at once, a block each, and the values the
two inits write are the same: one image contains one table. A call is not
re-entrant on one block, and an interrupt that reads uses a block of its
own.

Init may be called again on a block at any time. `DTX_metadata` is the
one call that may be made before it.

### 1. `DTX_metadata`, image+4

Both blocks are bytes in the image, reached with one `lea` each, so this
call does not need state and may be made before init. Its fields stand at
fixed offsets, so a caller that builds against a given image reads them
out of the file at build time and never makes the call. The packager
prints them as it writes the image, the state block's bytes among them.

### 2. `DTX_jump`, image+8

The cursor stands on the row given, so a read gives it and the next
advance gives the row after it.

Under DTX0 the pointer is the payload plus the row times the row's bytes.
Under DTX1 it is the payload plus the row times the width, a shift and an
add. Under DTX2 a jump forward runs the whole of the advance's body
once a row up to the target: the pointer step and its wrap, the refill of the
row's turn, the turn step and the rows decoded. It leaves every ring, every
write pointer and the turn where advancing there would leave them. Where the
target is at or below the row standing, or the cursor does not stand on a row,
it seeds and fills every ring afresh, as init does, and runs forward from row
0. No checkpoints, so a backward jump costs the target row and not the
distance.

Under DTX2 the pointer stands in column 0's ring, at the row modulo the
rows a ring contains, times the width. Under DTX1 it stands in the
payload, and a ring is absent.

### 3. `DTX_advance`, image+12

Steps the cursor. The move that leaves `d0` sets N and Z, so `bmi` after
the call.

It adds the row's bytes to the pointer under DTX0, and the width to it
under DTX1 and DTX2. A pointer that reaches the ring end goes back to the
ring start; under DTX0 and DTX1 a ring is absent and nothing wraps.
**Under DTX2 it then refills the column whose turn it is**, one column
a row: column `j` on the row where the row number modulo `P` is `j`. A turn
past `C` minus one does not have a column and does not refill, so a `P` above
`C` is free for those rows.

The refill is one `ST4_resume` into that column's ring. Its budget is `P`
times the width divided by `k` units, which the width and `k` being
assembly-time constants makes a shift, except for the call that would reach
past the data set: the block contains the rows decoded, and where fewer than
`P` rows are left the budget is the rows left, and where none are left the
refill does not happen. Without that rule every column takes one call
past its end marker, which ST4_wrap's assumption 5 forbids.

After the call the write pointer is compared with the column's ring end
and taken back to the ring start where the two are equal. `N` divides by
`P` times the width, so a full refill lands exactly on the end or short of
it, and the compare is exact. No call is counted and the block does not
contain a counter; YMX wraps the same way.

The advance from row `R` minus one where `RR` is below `R` is a jump to
`RR`, since it is one. Where `RR` equals `R` it gives $FFFFFFFF, leaves
the cursor on row `R` minus one and does not move the pointer, and a
further advance gives $FFFFFFFF again: the end is sticky, and `a1` still
gives row `R` minus one's values.

### The row a caller reads

`a1` comes back from an advance or a jump at the row's first value, which
is column 0's, and the stride `DTX_metadata` gives reaches the next
column's. So column `i` of that row is one move at `i` strides:

        bsr     DTX_advance     ; onto row 0
    .row:
        move.w  (a1),d0                 ; column 0
        move.w  DTX_STRIDE(a1),d1       ; column 1
        move.w  DTX_STRIDE*2(a1),d2     ; column 2
        bsr     DTX_advance
        bpl.s   .row

The stride is the width under DTX0, where a row's values stand one after
another; a column's length under DTX1, `R` times the width rounded up to a
word, where the columns lie at one stride; and `N` under DTX2, where every
column has a ring of that size. None of the three moves with the column,
so one displacement a column reaches the whole row and a caller reading
one column of it makes one move.

A caller that needs the row laid out as DTX0 lays it (SPEC.md 2.1) makes
those `C` moves into its own memory. The image does not, so the two calls
that did are gone from it, and with them every rule about where a row may
go.

**Nothing tests an address.** A value is the table's width, the payload
and every ring stand on a long, and the stride is a whole number of
widths, so at a width of 2 or 4 the pointer and every column off it stand
on that width's boundary and a 68000 takes the move whole. At a width of 1
no boundary applies.

**The pointer stands until the next advance.** Under DTX0 and DTX1 it
points into the table's own bytes, which nothing writes. Under DTX2 it
points into a ring, and the refill that would write over it is `P`
advances away (section 4), so the row it was given stands until its next
advance.

---

## 3. The state block

The caller supplies the block and passes it back in `a0` on every call
but `DTX_metadata`. Its size is a build time constant the format block
defines at +4, which the packager prints and a caller reads out of the
image. The block stands on a long.

| at | bytes | contains |
|---|---|---|
| +0 | 4 | the row the cursor stands on, or $FFFFFFFF |
| +4 | 2 | the turn, 0 to `P` minus one. Zero under DTX0 and DTX1 |
| +6 | 2 | zero |
| +8 | 4 | the rows decoded. Zero under DTX0 and DTX1 |
| +12 | 12 | the caller's `a6`, `d6` and `d7`, parked for a DTX2 refill |
| +24 | 4 | the pointer, under every variant |

One pointer, because every column is one width: under DTX1 and DTX2 the
columns lie at one stride and a read walks them from it. Under DTX2 the
block contains more, because the code contains less: `R`, `C`, `RR`, `P`
and `N` reach a refill out of it rather than as immediates, and so does
the column a fill stands at. ST4 leaves `d6`, `d7` and `a6` alone and
clobbers the rest, so a loop that calls it keeps its state in the block
that `a6` reaches:

| at | bytes | contains |
|---|---|---|
| +28 | 4 | the payload's first byte |
| +32 | 2 | the column a fill or a refill stands at |
| +34 | 2 | `C` |
| +36 | 4 | where the rings begin, from the block's first byte |
| +40 | 4 | `R` |
| +44 | 4 | `RR` |
| +48 | 4 | `P` |
| +52 | 4 | `N` |

The rows decoded is init's `P` and grows by each refill's rows. It
shortens the last refill of a column and stops the one after it, and it is
the only counter in the block: the write pointer's wrap is a compare,
not a count.

Then, under DTX2 only, at +56, one **decoder state** a column: the eight
longs a column's decoder is saved in between refills, 32 bytes at a stride
of 32, in `movem`'s own order, so that `movem.l (a3)+,d0-d2/a0-a2/a4-a5`
loads one whole:

| at in the decoder state | contains |
|---|---|
| +0 | `d0`, the bit queue in the low word |
| +4 | `d1`, the remaining count with the negated ring start above it |
| +8 | `d2`, the byte offset with the ring's bytes above it |
| +12 | `a0`, the position in stream A |
| +16 | `a1`, the write pointer |
| +20 | `a2`, the position in stream B |
| +24 | `a4`, the position in stream C |
| +28 | `a5`, the position in stream D |

`d1` and `d2` are stored and put back as longs: ST4's ring decoders have
the ring's bounds in their high words, so a decoder state of the low words
alone would decode into another column's ring.

The rings follow, `N` bytes a column at a stride of `N`, which is R5.5's
one size and one stride. Column `i`'s ring is the ring area plus `i` times
`N`, and its value for the row the cursor stands on is the pointer plus the
same, so one pointer reaches every column.

Sizes: DTX0 and DTX1 28 bytes, at every width and every `C`. DTX2 56 plus
32`C` plus `NC`: a decoder state and a ring a column.

---

## 4. DTX2: the turn

A DTX2 payload contains `C` data sets. A reader that drove `C` decoders a
few bytes a row would swap `C` register sets a row. This one refills
**one column a row**, so the work a row is one `ST4_resume` and the read
is never in the decoder. Over `P` rows every column is refilled once, and
the `C` refills produce in that time what `P` rows take.

**The invariant.** Init leaves `P` rows in every ring. Column `j` is refilled
on rows `j`, `j+P`, `j+2P` and so on, `P` rows each time. At the row before
its next refill, column `j` has had `P` rows written for every `P` a read has
taken since init, and its ring contains at least one row that has not been
read. A ring of 2`P` rows a column is therefore enough, and a ring of `P` rows
is not: with `N` equal to `P` times the width, the refill on row `j` writes
over the row the read on that row is about to take.

**The four rules the packager checks, and fails the package on.**

- `P` is at least `C`, so one column a row refills every column in time,
  and at most `R`, since no refill takes more rows than the table has
- `N` divides by `P` times the width, so a full refill lands on the ring
  end or short of it and never straddles it
- `N` is at least 2`P` times the width, so a ring contains two periods and
  a refill never writes over a row not yet read
- `P` times the width divided by `k` is a whole number, 1 to 65535: the
  budget range, and the reason `k` divides `P` times the width. `k` then
  divides `N` as well, which is ST4_wrap's assumption 1

A read walks the rings by adding a stride rather than by a displacement
off a pointer, so no rule bounds `C` beyond R6.2's 256: a table of 256
columns at any `N` the format takes packages.

**Why ST4_wrap and not ST4_ring.** ST4_wrap decodes a fixed budget a call and
leaves the wrap to the caller, which fits exactly: every refill of a column is
the same budget, `N` divides by what a refill writes, so the reader resets
that column's write pointer after `N` divided by `P` times the width calls and
the decoder never checks a ring end. It is 324, 328 or 330 bytes at `k` of 1,
2 and 4, against ST4_ring's 386, 394 and 396. It does not have a done state,
which is free here: the caller takes rows 0 to `R` minus one and no decoder is
driven past its data set.

`P` is the lever. `P` equal to `C` gives the smallest rings and the
flattest cost; a larger `P` uses more ring and leaves `P` minus `C`
rows a period with no refill. The packager's default is the
smallest `P` at least `C` that meets every rule above, and it fails the
package where no such `P` stands below `R`.

**The repeat.** An advance from row `R` minus one to an `RR` below it is a
jump backward, so it seeds every ring again and runs forward from row 0: a
table that repeats costs `RR` rows of decoding once a pass. ST4 can pack a
data set to loop where the table does, with `st4 -r`, and YMX packs its
streams that way so that a repeat costs one pointer reload; the packager does
not yet, and under it a repeat is a jump.

---

## 5. What the packager resolves

It combines rather than assembles. One variant is one code, so the code
is built once and kept, and packaging a table takes the file for the
build the table needs, writes the five fields the table gives into the
format block, and appends the column table and the table's
bytes. rmac runs where the code is built, not where a table is packaged.

The five: the state block's bytes at +4, the table's header at +8, the
row's bytes at +12, `P` at +14 and `N` at +16. A kept file reads zero for
each of them, so code shipped without a combine does not define a table,
rather than the one it happened to be built from. What it does define is
the variant at +3, `k` at +18, the width at +19 and the column table's
place at +20, and a combine checks the first three against the table
rather than writing them.

It folds only the state block's offsets into the bodies. Every figure the
table gives it writes into the format block and the column table instead,
and the code reads them at run time. The few a loop counts with, which no
68000 addressing mode takes from memory, init writes into the instructions
that take them: `R` and `RR` for the compares an advance takes, and the
row's bytes a DTX0 advance steps by. A site is `lea`d PC relative into an
address register and written through it, since a 68000 reaches PC relative
for a source and never for a destination.

Under DTX2 the carried decoder is ST4's wrap decoder built at `ST4_UNIT`
equal to `k`. The packer's options are `-f -k<k> -m<N/k> -l65535`, and the
last meets ST4_wrap's assumption 4: no operation longer than the 65535
units a 68000 decoder counts in a word.

A column may be packed with `-c` as well, so that a match beyond the ring
copies from the column's own literal stream. That packs a small ring
far smaller, and it asks one thing of the package: the decoder is built
with its copy code, which measures 32 bytes more at `k` of 1
and 2 and 36 at `k` of 4.

The payload defines which kind its columns are, in the flags byte SPEC.md
2.3 gives, so the packager reads the decoder a table needs out of the
table itself. Nothing carried beside the file enters, and nothing can
differ from the bytes. The flag is there because no ST4 data set defines
it and a column packed with copies read by a decoder without the copy code
gives wrong bytes; the other way round is safe, since a decoder with the
copy code reads a column without copies as the plain one does, at a few
cycles more over 64 rows (performance.md).

**How many images there are.** DTX0 assembles to one at every width: a
row is one run of bytes, and the move that run takes comes from the row's
bytes. DTX1 assembles to one a width, three in all. DTX2 assembles to one
a width a build of the decoder built into it, `k` of 1, 2 or 4 with the
copy code and without: eighteen. Twenty-two in all, and `StabilityTest`
builds every one of them and checks that no two are the same bytes.

Which of them a table takes is its width, in the header at +14, and under
DTX2 `k` and the copies flag, both in the payload (SPEC.md 2.3). The image
repeats the width at +19 of its format block and `k` at +18, and a combine
checks both against the table.

The writer packs the columns for the `N` it is given, and the packager
takes the smallest `P` that meets every rule of section 4 at that `N`, or
fails the package naming the rule.

At package time the packager checks what no call checks: the header against
R6, so an `R` at or above 2147483648, which reads as a negative, fails R6.1
and no row number reads as the $FFFFFFFF an advance ends on; under DTX2 that a
column's bytes divide by `k`, that every data set opens with `$53 $34 $07 k`
for the payload's own `k`, and the four rules of section 4; and that the image
it combines with reads the table's variant, width and unit and puts the column
table where its code ends. It fails the package rather than emitting an image
that reads wrong.

---

## 6. What each call costs

performance.md records it, in 68000 cycles counted under emulation from
the manual's tables, and the rig that counts them checks that document
against its count.

The shape, which the figures bear out: under DTX0 and DTX1 every call is
flat in `R` and in `C`, since no call walks the columns. Under DTX2 an
advance is flat and takes one column's refill of at most `P` rows, and a
jump costs the rows it runs the turn through. What reading the columns
costs is the caller's, one move a column.

Two calls are not flat. A backward jump costs the target row of decoding,
and so does the advance from row `R` minus one of a table that repeats,
since under this reader it is a jump (section 4).

---

## 7. The assumptions, unchecked

1. The state block stands on a long and is at least the bytes the format
   block defines at +4.
2. The block passed to a call was seeded by init on this image, and one
   reader uses it. A call is not re-entrant on one block, and an
   interrupt that calls into the image uses a block of its own.
3. A jump's row in `d0.l` is 0 to `R` minus one.
4. A caller reads the row `a1` points at before its next advance, and
   reads `C` values at the stride and no more.
5. The table's bytes are the ones the packager checked. No call checks any of
   it, as ST4 does not check its own: a wrong value reads or writes arbitrary
   memory.
6. Nothing but the image's own init writes the image, and every
   reference into it is PC relative. The bytes init writes are the
   operands of instructions in it, and the image stands in writable
   memory for that.
7. DTX2: the four rules of section 4 are met: `P` from `C` to `R`, `N` a
   multiple of `P` times the width and at least twice that, and the budget
   a whole number of units from 1 to 65535.
8. DTX2: `R` times the width divides by `k` (R5.6).
9. DTX2: the caller takes rows 0 to `R` minus one. The rows decoded
   in the block shorten the last refill of a column and stop the one
   after it, so a column takes the `ceil(R/P)` calls ST4_wrap's
   assumption 5 allows and no more. ST4_wrap does not have a done state and
   none is read.
10. DTX2: ST4_wrap's own assumptions are met, its 1 to 7, which the packer
    options and the packager's checks see to.

---

## 8. The trade-offs

**Init fills every ring.** A read then alternates between the streams and
does not touch a decoder, and the first row is ready when init returns. Given
up: init is the one burst, `C` calls of `P` rows each, and that cost
comes before the first row rather than during it.

**One column a row, not `C` columns every `P` rows.** The same total
decoding, spread flat. A row takes one `ST4_resume` of one column rather
than `C` of them on one row in `P`. Given up: the rings are 2`P` rows a
column rather than one group, and `P` is at least `C`.

**The block is the caller's, not the image's.** Two cursors on one table
have two places, and what a reader keeps of a row stays out of the code.
Given up: `a0` is the block on every call.

**The code does not move with the table.** One variant assembles to one
image at any `R`, `C` or `RR`, so what an emulation check reads is the
code every table of that variant runs, not one table's. Given up: the
image is code in RAM rather than ROM, and a read costs a memory read for
each of the three counts and displacements it once had as immediates.

**ST4_wrap over ST4_ring.** No ring end check in the decoder, and 62 to
66 bytes less. Given up: the reader takes the write pointer back to the
ring start itself, which is one compare against the ring end after each
refill and no counter anywhere. The budget is fixed except on the last
refill of a column, and the rows decoded shortens that one.

**One pointer, and a stride.** Every column is one width, so column `i`
stands at the pointer plus `i` strides and a caller reaches the row off one
address register. Given up: a table of columns of different widths, which
took three pointers before.

**`movem` of the whole decoder state.** Reaching it through a
pointer would turn every register read into a memory read and fork ST4's
code. Given up: two `movem`s a refill, and one refill a row.

**No checkpoints in DTX2.** A backward jump costs the target row and not
the distance, and the repeat at `R` to `RR` is one. Given up: a bounded
backward jump.

**The image finds a row and the caller reads it.** An advance gives an
address and a stride reaches the rest, so no call copies a value and
reading one column of a wide table is one move. Given up: a caller that
needs the row packed as DTX0 lays it out makes those `C` moves itself.

**One variant an image.** A caller that reads two variants has two
images, and the four calls read the same in both.
