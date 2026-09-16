# The DTX68 calling convention

One or more DTX tables packaged as a standalone 68000 binary: the code
first, a column table and a table's bytes for each after it, and four calls
that reach those bytes PC relative. No relocation, no operating system,
nothing allocated while it runs.

**No call copies a value.** An advance leaves the caller the address of the
row's first value, and `DTX_metadata` reports the stride from one column's
value to the next, so a caller reads the columns it needs where they stand.
A packaged reader finds a row; the caller reads it.

One image is one variant, resolved at package time behind four slots, so
the caller has one contract and one state block under each of the
three. `R`, `C`
and `RR` reach the code at run time out of the table's header, so one build
assembles to one code at any shape. The width does move it, and under DTX2
`k` and the copy code with it, so there is one build a width and a decoder
(section 5).

Under DTX0 and DTX1, and under DTX2 with copies, init writes into the code,
so the image stands in RAM rather than ROM and a 68030 caller flushes the
instruction cache after `DTX_init`.

Under DTX2 the reader follows YMX's shape, which plays twenty-five packed
streams a frame on the same hardware: every column decodes through a ring,
init fills every ring once, and one column is refilled a row thereafter. So
a read finds a value in a ring and never decodes.

Terms are the glossary's. This document adds four: **state block**, what a
caller supplies and passes back; **pointer**, the address, in a ring or in
the payload, of the row the cursor stands on; **turn**, the column a row
refills; **period**, `P`, the rows between one column's refills.

---

## 1. What the image contains

In this order, from the image's first byte:

| at | bytes | contains |
|---|---|---|
| +0 | 16 | four `bra.w` slots, one a call |
| +16 | 28 | the format block, on a long |
| +44 | .. | the bodies for the variant |
| .. | 324, 328 or 330, and 354, 360 or 366 with the copy code | under DTX2, ST4's wrap decoder at `k` |
| .. | .. | the tables, the pair below repeated one a table |

One table, in this order:

| at | bytes | contains |
|---|---|---|
| .. | 16`C` | under DTX2 that table's column table, on a long. The plain variants do not have one |
| .. | .. | that table's bytes, header and payload, on a long |

A table's column table stands immediately before that table's header, so
init reaches it at the header less 16 times `C`. The first pair stands
where the code ends. An image does not define where its tables end: a
caller reads each table's place from what the packager prints, and the
format block names the first.

The four slots stand at +0, +4, +8 and +12, in the order the calls are
numbered below, following ST4's precedent. The dispatch is the whole of it:
no call tests the variant, the packager having emitted the bodies for it.

**The format block**, 28 bytes on a long:

| at | bytes | contains |
|---|---|---|
| +0 | 4 | `DTX` and the variant this image reads |
| +4 | 4 | the first table's state block bytes, and the largest of the image's |
| +8 | 4 | the first table's header, from the image's first byte |
| +12 | 2 | the first table's row bytes, `C` times the width |
| +14 | 2 | `P`, the period in rows, which every table shares. 1 under DTX0 and DTX1 |
| +16 | 2 | `N`, a ring's bytes, which every table shares. Zero under DTX0 and DTX1 |
| +18 | 1 | `k`, which every table shares. Zero under DTX0 and DTX1 |
| +19 | 1 | the width this image reads. Zero under DTX0, whose code does not move with it |
| +20 | 4 | the first table's column table, from the image's first byte |
| +24 | 4 | the first table's stride from one column's value to the next |

The four fields naming *the first table's* are there for a caller that has
only the image; a caller of another table reads that table's figures out of
its header and out of what the packager printed. `P`, `N`, `k` and the width
are the image's, one figure for every table in it.

`R`, `C` and `RR` are not here. They stand in the table's header at the
offsets SPEC.md 1 defines, which the field at +8 reaches. The variant at +3
of that header and the width at +14 are the two the block repeats, and the
packager checks them rather than writing them: an image built for another
variant or width does not read this table.

**The column table**, which DTX2 has and the plain variants do not. Under
DTX0 and DTX1 a read is arithmetic on `R`, `C` and the width. Under DTX2 it
is one stream record a column, 16 bytes at a stride of 16:

| at | bytes | contains |
|---|---|---|
| +0 | 4 | stream A, from the payload |
| +4 | 4 | stream B |
| +8 | 4 | stream C |
| +12 | 4 | stream D |

The ring and the decoder state are strides rather than fields: every ring
is `N` bytes and every decoder state 48, so column `i`'s stand `i` strides
past column 0's. One seed loop and one refill body reach every column, and
the decoder ST4 contributes stands in the image once.

---

## 2. The four calls

`d6`, `d7`, `a6` and the stack beyond the return address stand across every
call, as they stand across an ST4 call. No call builds a stack frame. The
state block is in `a6`, where ST4 leaves it alone and a reader reads it
without parking anything.

| call | in | out | clobbered |
|---|---|---|---|
| `DTX_init` | `a6` the state block, `a1` the header of the table to read | nothing | d0-d5, a0-a5 |
| `DTX_metadata` | nothing | `a0` format block, `a1` the table's header, `d0.l` `R`, `d1.w` `C`, `d2.l` `RR`, `d3.l` the stride | d0-d3, a0-a1 |
| `DTX_jump` | `a6`, `d0.l` the row | `a1` that row's first value | d0-d5, a0-a5 |
| `DTX_advance` | `a6` | `a1` the row's first value | d0-d5, a0-a5 |

**No call keeps a row number.** An advance steps one row on and leaves the
address; a jump reads a row number and nothing else. A caller counts its
rows against the `R` and `RR` of `DTX_metadata`. So an advance under DTX0
and DTX1 is three instructions.

### 0. `DTX_init`, image+0

Seeds the block on the table whose header stands in `a1`, so a block seeded
here is a block on that table and every later call reads it. A caller with
one table passes the image plus the format block's +8. The cursor does not
stand on a row, so the first advance reads row 0 (terminology.md).

Under DTX0 it writes one pointer, the payload minus the row's bytes; under
DTX1, the payload minus the width. A pointer a row below row 0 makes the
advance into row 0 like any other.

**Under DTX2 it fills every ring before it returns.** For each column it
forms the five pointers `ST4_init` reads, calls it, and makes one
`ST4_resume` of `P` rows into that column's ring. It stores the decoder's
eight longs in the column's decoder state with the ring's end and the
budget beside them, puts the first turn at column 0's state, and leaves the
pointer a row below row 0 of column 0's ring.

Init writes the block and the instructions of the image: under DTX0 the
sites section 5 lists, and under DTX2 with copies ST4's init writes the ring's
size into two of the decoder's. A DTX1 image, and a DTX2 image without
copies, is not written and may stand in ROM.

Two readers of one image run at once, a block each, where both blocks were
seeded on one table. Seeded on two tables, only DTX1 runs as two images
would; under DTX0 and under DTX2 with copies the two inits write different
values into the code, so one table is read at a time and a switch is an
init. A call is not re-entrant on one block, and an interrupt that reads
uses a separate block.

Init may be called again on a block at any time. `DTX_metadata` is the one
call that may be made before it.

### 1. `DTX_metadata`, image+4

Both blocks are bytes in the image, reached with one `lea` each, so this
call stands apart from the block and may be made before init. Its fields
stand at fixed offsets, so a caller that builds against one image reads
them out of the file and never makes the call.

### 2. `DTX_jump`, image+8

The cursor stands on the row the call names, so a read finds that row and
the next advance steps past it.

Under DTX0 the pointer is the payload plus the row times the row's bytes;
under DTX1, plus the row times the width. Under DTX2 a jump seeds and fills
every ring afresh and then runs the advance's body once a row up to the
target, leaving every ring, write pointer and turn where advancing there
would. Nothing in the block records where the cursor stood, so every jump
runs from row 0 and costs the target row, forward or back.

### 3. `DTX_advance`, image+12

Steps the cursor one row on and leaves the pointer. Under DTX0 and DTX1 it
is a load, an add and a store. It adds the row's bytes under DTX0 and the
width under DTX1 and DTX2. A pointer that reaches the ring end goes back to
the ring start; under DTX0 and DTX1 nothing wraps.

**Under DTX2 it refills the column whose turn it is** before the pointer
moves: column `j` on the row where the row number modulo `P` is `j`. A turn
past `C` minus one does not have a column and does not refill, so a `P`
above `C` is free for those rows.

The refill is one `ST4_resume` into that column's ring, or two where a mark
splits it (section 3). Its budget is `P` times the width divided by `k`
units, a shift at assembly time, and it stands in the column's decoder
state. Where the data sets loop (SPEC.md 2.3, R5.11) every budget is that
one; where they end, the last period's budget is short and the one after it
is 0, so no refill runs a decoder past its end marker.

After the call the write pointer is compared with the ring end and set back
to the ring start where the two are equal. `N` divides by `P` times the
width, so a full refill lands on the end or short of it and the compare is
exact. The wrap follows from the compare alone, as YMX wraps.

**The repeat is not a call's to make.** Under DTX2 the rows come round
because the data sets do: a set whose table repeats loops at `RR`, so the
advance out of row `R` minus one is the advance into row `RR`. A set the
ring reaches loops in it, and one it does not is replayed (section 4).
Under DTX0 and DTX1 nothing loops, so a caller whose table repeats counts
to `R` and jumps to `RR`, which costs 246 cycles under DTX0 and 138 under
DTX1.

Past row `R` minus one of a table that does not repeat, what an advance
reads is not defined. A caller stops at `R`, which `DTX_metadata` reports.

### The row a caller reads

`a1` comes back at the row's first value, which is column 0's, and the
stride `DTX_metadata` reports reaches the next column's. So column `i` of
that row is one move at `i` strides:

        bsr     DTX_advance     ; onto row 0
    .row:
        move.w  (a1),d0                 ; column 0
        move.w  DTX_STRIDE(a1),d1       ; column 1
        move.w  DTX_STRIDE*2(a1),d2     ; column 2
        bsr     DTX_advance
        bpl.s   .row

The stride is the width under DTX0, a column's length under DTX1 (`R` times
the width rounded up to a word) and `N` under DTX2. None of the three moves
with the column. A `d16(An)` displacement reaches the columns within 32767
bytes, and a caller adds the stride to reach one past that. A caller that
needs the row laid out as DTX0 lays it (SPEC.md 2.1) makes those `C` moves
itself.

**Nothing tests an address.** A value is the table's width, the payload and
every ring stand on a long, and the stride is a whole number of widths, so
at a width of 2 or 4 the pointer and every column off it stand on that
width's boundary. At a width of 1 no boundary applies. A 68000 faults on a
misaligned word or long and Unicorn's model does not, so
`68k/test/emu/test_dtx.py` watches every access and reports one as the
hardware would, through a ring that wraps, a loop the ring does not fit, an
odd `R`, a unit of 2 and 4, a copy from the literal stream, and two tables
in one image.

**The pointer stands until the next advance.** Under DTX0 and DTX1 it
points into the table's bytes, which nothing writes. Under DTX2 it points
into a ring, and the refill that would write over it does not come before
the next advance (section 4).

---

## 3. The state block

The caller supplies the block and passes it back in `a6` on every call but
`DTX_metadata`. Its size is the table's: the format block defines it at +4,
the packager prints it, and a caller reads it out of the image. The block
stands on a long.

| at | bytes | contains |
|---|---|---|
| +0 | 2 | the turns left in the period, `P` down to 1. Zero under DTX0 and DTX1 |
| +2 | 2 | unused |
| +4 | 4 | under DTX0 and DTX1 the payload of the table init was seeded on; unused under DTX2 |
| +8 | 4 | the pointer, under every variant |

One pointer reaches every column, since every column is one width and they
lie at one stride. Under DTX2 the block contains more because the code
contains less: `R`, `C`, `RR`, `P`, `N` and the column a fill stands at
reach a refill out of it rather than as immediates, and ST4 clobbers every
register but `d6`, `d7` and `a6`.

| at | bytes | contains |
|---|---|---|
| +12 | 4 | the payload's first byte |
| +16 | 4 | where the stream records stand |
| +20 | 2 | the column a seed stands at |
| +22 | 2 | `C` |
| +24 | 4 | where the rings begin, from the block's first byte |
| +28 | 4 | `P` |
| +32 | 4 | `N` |
| +36 | 4 | the rows every column has produced |
| +40 | 4 | what a period's budget counts those rows against: `R` where the sets end, $7FFFFFFF where they loop |
| +44 | 4 | the units a column decodes before the row its loop begins at, where a pass is replayed |
| +48 | 4 | the units of the loop, `RR` to `R`, where a pass is replayed |
| +52 | 4 | where the decoders' registers go at the loop's row, or 0 where none go anywhere |
| +56 | 4 | the decoder state whose turn the next row is |
| +60 | 4 | column 0's ring, the pointer ring |
| +64 | 4 | one past its last byte |
| +68 | 4 | unused |

The rows produced grow at each period's end, by `P` or by what is left to
`R`, once a period rather than once a row. Where a back reference reaches
the loop's first unit the set loops by its end marker and +52 reads zero.
Where the pass is replayed, each column's decoder state has a mark: the
units it has left before the row its registers are put away at, or put back
at, and the refill that mark falls inside is split there. The first mark
puts them away at `RR` and every mark after puts them back at `R`; the
registers of a loop beginning at row 0 are put away at the seed. So `RR`
and `R` fall on any row, the loop being a period long at least (section 4).

Then, under DTX2 only, at +72, one **decoder state** a turn, 48 bytes at a
stride of 48: the eight longs a column's decoder is saved in, in `movem`'s
order so that `movem.l (a3)+,d0-d2/a0-a2/a4-a5` loads them whole, and five
fields the refill reads:

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
| +32 | one past the ring's last byte |
| +36 | where the registers go at the loop's row |
| +40 | the budget, a word: the refill's units, 0 on an idle turn or where the set has ended |
| +42 | the phase, a word: 0 where the next mark puts the registers away, 1 where it puts them back |
| +44 | the mark, a long: the units left before that row, 0 where no such row is ahead |

A turn past `C` minus one has a state whose budget is 0, and nothing else
in it is read. `d1` and `d2` are stored and put back as longs: ST4's ring
decoders keep the ring's bounds in their high words, so the low words alone
would decode into another column's ring.

The rings follow, `N` bytes a column at a stride of `N`, which is R5.5's
one size and one stride. Column `i`'s ring is the ring area plus `i` times
`N`, and its value for the current row is the pointer plus the same.

Sizes: DTX0 and DTX1 12 bytes, at every width and every `C`. DTX2 72 plus
48`P` plus `NC`, and 32`C` more where a pass is replayed, for a copy of the
registers a column behind the rings.

---

## 4. DTX2: the turn

A DTX2 payload contains `C` data sets, and this reader refills **one column
a row**, so the work a row is one `ST4_resume` and a read is never in a
decoder. Over `P` rows every column is refilled once, and the `C` refills
produce what `P` rows read.

**The invariant.** Init leaves `P` rows in every ring. Column `j` is
refilled on rows `j`, `j+P`, `j+2P` and so on, `P` rows each time, so at
the row before its next refill it has had `P` rows written for every `P`
read since init and its ring contains a row not yet read. A ring of 2`P`
rows a column is therefore enough, and `P` rows is not: at `N` equal to `P`
times the width, the refill on row `j` writes over the row that row reads.

**The five rules the packager checks, and fails the package on.**

- `P` is at least `C`, so one column a row refills every column in time. A
  table of fewer rows than `P` whose sets end seeds them all and its first
  period's budget is 0; one that repeats seeds `P` rows round its loop
- `N` divides by `P` times the width, so a full refill lands on the ring
  end or short of it and never straddles it
- `N` is at least 2`P` times the width, the invariant above
- `P` times the width divided by `k` is a whole number, 1 to 65535: the
  budget range. `k` then divides `N` as well, which is ST4_wrap's
  assumption 1
- a replayed loop, `RR` to `R`, is `P` rows or more, so a refill meets one
  mark at most. ST4 replays a loop longer than a back reference reaches,
  `N` or 32512 bytes, so the rule binds only a table whose period is above
  32512 bytes

No rule bounds `C` beyond R6.2's 256: a caller past 32767 bytes from the
pointer reaches a column by adding a stride.

`P` is the lever. `P` equal to `C` makes the smallest rings and the
flattest cost; a larger `P` uses more ring and leaves `P` minus `C` rows a
period with no refill. The packager writes the smallest `P` at least `C`
that meets every rule, and fails the package where none does.

**The repeat.** A data set of a table that repeats loops at `RR` (R5.11),
so the advance out of row `R` minus one is the advance into row `RR`. A
loop longer than a back reference reaches is replayed instead: the set
records the unit its loop begins at, and the reader puts every column's
registers but the write pointer away at the loop's row and back at the
pass's end, each column at its refill, splitting the refill the row falls
inside (section 3). The rows from `RR` to `R` minus one decode again each
pass.

---

## 5. What the packager resolves

It combines rather than assembles. One variant is one code, built once and
kept, so packaging a table reads the file for the build it needs, writes
the six fields the table defines into the format block, and appends the
column table and the table's bytes. rmac runs where the code is built, not
where a table is packaged.

The six: the state block's bytes at +4, the table's header at +8, the row's
bytes at +12, `P` at +14, `N` at +16 and the stride at +24. A kept file
reads zero for each, so code shipped without a combine does not define a
table. What it does define is the variant at +3, `k` at +18, the width at
+19 and the column table's place at +20, which a combine checks against the
table rather than writing.

It folds the state block's offsets into the bodies, the width into DTX1's
and DTX2's, and `k` and the copy code into DTX2's. One figure a loop counts
with is written into the instructions that read it instead: the row's
bytes, which a DTX0 advance steps by and a DTX0 jump multiplies by twice.
Init forms it from the header in `a1`, so it is the table this block was
seeded on. A site is `lea`d PC relative into an address register and
written through it, a 68000 reaching PC relative for a source and never for
a destination.

Under DTX2 the carried decoder is ST4's wrap decoder built at `ST4_UNIT`
equal to `k`, packed with `-f -k<k> -m<N/k> -l65535`. The last meets
ST4_wrap's assumption 4: no operation longer than the 65535 units a 68000
decoder counts in a word.

A column may be packed with `-c` as well, so that a match beyond the ring
copies from the column's literal stream. That packs a small ring far
smaller, and the package uses the decoder built with its copy code,
which measures 32 bytes more at `k` of 1 and 2 and 36 at `k` of 4.

The payload defines which kind its columns are, in the flags byte SPEC.md
2.3 defines, so the packager reads the decoder a table needs out of the
table itself. The flag is there because no ST4 data set defines it and a
column packed with copies read by a decoder without the copy code decodes
to wrong bytes; the other way round is safe, at a few cycles more over 64
rows (performance.md).

**How many images there are.** DTX0 assembles to one at every width, a row
being one run of bytes. DTX1 assembles to one a width, three in all. DTX2
assembles to one a width a decoder, `k` of 1, 2 or 4 with the copy code and
without: eighteen. Twenty-two in all, and `StabilityTest` builds every one
and checks that no two are the same bytes.

Which a table needs is its width, in the header at +14, and under DTX2 `k`
and the copies flag, both in the payload (SPEC.md 2.3).

At package time the packager checks what no call checks: the header against
R6, so an `R` at or above 2147483648 fails R6.1 and no row number reads as
the $FFFFFFFF an advance ends on; under DTX2 that a column's bytes divide
by `k`, that every data set opens with `$53 $34 $07 k` for the payload's
`k`, and the rules of section 4; and that the image it combines with reads
the table's variant, width and unit and puts the column table where its
code ends. It fails the package rather than emitting an image that reads
wrong.

---

## 6. What each call costs

performance.md records it, in 68000 cycles counted under emulation from the
manual's tables, and the rig that counts them checks that document against
its count.

Under DTX0 and DTX1 every call is flat in `R` and in `C`, no call walking
the columns. Under DTX2 an advance is flat and costs one column's refill of
at most `P` rows. Two calls are not flat: a jump costs the rows it runs the
turn through, forward or back. What reading the columns costs is the
caller's, one move a column.

---

## 7. The assumptions, unchecked

1. The state block stands on a long and is at least the bytes the format
   block defines at +4.
2. The block passed to a call was seeded by init on this image, and one
   reader uses it. A call is not re-entrant on one block, and an interrupt
   that calls into the image uses a separate block.
3. A jump's row in `d0.l` is 0 to `R` minus one.
4. A caller reads the row `a1` points at before its next advance, and reads
   `C` values at the stride and no more.
5. The table's bytes are the ones the packager checked. No call checks any
   of it, as ST4 does not check it: a wrong value reads or writes arbitrary
   memory.
6. Nothing but the image's init writes the image, and every reference into
   it is PC relative. The image stands in writable memory for that.
7. DTX2: the five rules of section 4 are met.
8. DTX2: `R` times the width divides by `k` (R5.6).
9. DTX2: every data set loops (R5.11), so no refill reaches an end marker
   and ST4_wrap's assumption 5 is met however long a caller advances.
10. DTX2: ST4_wrap's own assumptions 1 to 7 are met, which the packer
    options and the packager's checks see to.

---

## 8. The trade-offs

Each choice above, and what it costs:

| the choice | the gain | the cost |
|---|---|---|
| init fills every ring | a read never touches a decoder, and the first row is ready when init returns | one burst before the first row: `C` calls of `P` rows |
| one column a row, not `C` every `P` rows | the same decoding spread flat, one `ST4_resume` a row | rings of 2`P` rows a column, and `P` at least `C` |
| the block is the caller's | two cursors on one table, and what a reader keeps stays out of the code | `a6` is the block on every call |
| the code does not move with the table | one code a variant at any `R`, `C` or `RR`, so a check reads what every table runs | the image stands in RAM, and three counts are memory reads rather than immediates |
| ST4_wrap over ST4_ring | no ring end check in the decoder, and 62 to 66 bytes less | the reader sets the write pointer back itself, one compare a refill |
| one pointer and a stride | column `i` is one move at `i` strides off one address register | a table of columns of differing widths, which needed three pointers |
| `movem` of the whole decoder state | every register stays a register, and ST4's code is not forked | two `movem`s a refill |
| no checkpoints in DTX2 | a jump costs the target row, not the distance | a forward jump begins where the cursor is, and a backward jump is bounded |
| the image finds a row, the caller reads it | no call copies a value, and one column of a wide table is one move | a caller that needs DTX0's layout makes those `C` moves |
| one variant an image | the four calls read the same under every variant | a caller of two variants has two images |
