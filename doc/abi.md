# The DTX68 calling convention

A DTX table packaged as a standalone 68000 binary: the code first, the
table's bytes after it, and five calls that reach those bytes PC relative.
No relocation, no operating system, nothing allocated while it runs.

One image holds one table, so it holds the code for that table's variant.
The variant is resolved at package time behind five slots: a caller holds
one contract and one state block, and what changes with the variant is the
code the packager emits behind the slots.

Under DTX2 the reader follows YMX's shape, which plays twenty-five packed
streams a frame at a time on the same hardware: every column decodes
through its own ring, init fills every ring once, and after that one
column is refilled a row, in turn. So a read takes one value from each
ring and no read ever decodes.

Terms are the glossary's. This document adds four, and the glossary takes
them in the change that adds this file (R0.8): **state block**, what a
caller supplies and passes back; **cursor**, the address in a ring of the
row the clock stands on; **turn**, the column a row refills; **period**,
`P`, the rows between one column's refills.

---

## 1. What the image holds

In this order, from the image's first byte:

| at | bytes | holds |
|---|---|---|
| +0 | 20 | five `bra.w` slots, one a call |
| +20 | 20 | the format block, on a long |
| +40 | .. | the bodies the variant asks for |
| .. | 324, 328 or 330 | under DTX2, ST4's wrap decoder at `k` |
| .. | .. | the table's bytes, header and payload, on a long |

The five slots stand at +0, +4, +8, +12 and +16, in the order the calls
are numbered below, following ST4's own precedent. The dispatch is the
whole of it: no call tests the variant, because the variant chose which
bodies the packager emitted.

**The format block**, 20 bytes on a long:

| at | bytes | gives |
|---|---|---|
| +0 | 4 | `DTX` and the variant this image serves |
| +4 | 4 | the state block's bytes |
| +8 | 4 | the table's header, from the image's first byte |
| +12 | 2 | the row's bytes, the sum of the widths |
| +14 | 2 | `P`, the period in rows. 1 under DTX0 and DTX1 |
| +16 | 2 | `N`, a ring's bytes. Zero under DTX0 and DTX1 |
| +18 | 1 | `k`. Zero under DTX0 and DTX1 |
| +19 | 1 | zero |

`R`, `C`, `RR` and the widths are not here. They stand in the table's own
header, at the offsets SPEC.md 1 gives, and the field at +8 reaches it:
the widths at that plus 14. The variant at +3 of that header is the one
byte this block repeats, and the packager writes it from there and fails
the package where the two differ.

The block states no cycle figure. What a call costs is measured on the
emitted code under emulation and stated in this document, not in the
image.

---

## 2. The five calls

Under every variant `d6`, `d7`, `a6` and the stack beyond the return
address stand, as they stand across an ST4 call. No call builds a stack
frame.

| call | in | out | clobbered |
|---|---|---|---|
| `DTX_init` | `a0` the state block | nothing | d0-d5, a0-a5 |
| `DTX_metadata` | nothing | `a0` format block, `a1` the table's header, `d0.l` `R`, `d1.w` `C`, `d2.l` `RR` | d0-d2, a0-a1 |
| `DTX_jump` | `a0`, `d0.l` the row | `d0.l` that row | d0-d5, a0-a5 |
| `DTX_advance` | `a0` | `d0.l` the row, or $FFFFFFFF | d0-d5, a0-a5 |
| `DTX_read` | `a0`, `a1` where the row goes | `a1` one past the last byte | d0-d1, a0-a3 |

### 0. `DTX_init`, image+0

Seeds the block. The clock stands on no row, so the first advance gives
row 0 (terminology.md).

Under DTX0 it writes one cursor holding the payload minus the row's
bytes. Under DTX1 it writes one cursor a width class, each holding that
class's base minus its width. The cursor a row below row 0 is why the
advance from no row to row 0 steps like any other advance.

**Under DTX2 it fills every ring before it returns.** For each column it
forms the five pointers ST4_init takes, calls it, and then makes one
`ST4_resume` of `P` rows into that column's ring, so every ring holds `P`
rows before any row is read. It stores the eight longs of decoder state
in the column's slot, sets the turn to 0, and leaves the class cursors a
row below row 0.

That preload lets a read alternate between the streams and touch no
decoder: from row 0 onward every value a read takes is already in a
ring.

Init writes nothing outside the block. The image is read after packaging
and never written, so a 68030 caller flushes no instruction cache: the
carried decoder is built without the copy code, and its init writes no
instruction. Two readers of one image run at once, a block each. A call
is not re-entrant on one block, and an interrupt that reads uses a block
of its own.

Init may be called again on a block at any time. `DTX_metadata` is the
one call that may be made before it.

### 1. `DTX_metadata`, image+4

Both blocks are bytes in the image, reached with one `lea` each, so this
call needs no state and may be made before init. Its fields stand at
fixed offsets, so a caller that builds against a given image reads them
out of the file at build time and never makes the call. The packager
prints them as it writes the image, the state block's bytes among them.

### 2. `DTX_jump`, image+8

The clock stands on the row given, so a read gives it and the next
advance gives the row after it.

Under DTX0 the cursor is the payload plus the row times the row's bytes.
Under DTX1 a class cursor is that class's base plus the row times its
width, a shift and an add.

Under DTX2 a jump forward runs the whole of the advance's body once a
row up to the target: the cursor step and its wrap, the refill of the
row's turn, the turn step and the rows decoded. It leaves every ring,
every write pointer and the turn where advancing there would leave them.
Where the target is at or below the row standing, or the clock stands on
no row, it seeds and fills every ring afresh, as init does, and runs
forward from row 0. No checkpoints, so a backward jump costs the target
row and not the distance.

Under DTX2 a class cursor is that class's ring base plus the row modulo
`N` divided by the width, times the width. Under DTX1 it is the class
base plus the row times the width, and there is no ring to wrap in.

### 3. `DTX_advance`, image+12

Steps the clock. The move that leaves `d0` sets N and Z, so `bmi` after
the call.

It adds the row's bytes to the one cursor under DTX0, and its width to
each class cursor under DTX1 and DTX2. A class cursor that reaches the
ring end goes back to the ring start; under DTX0 and DTX1 there is no
ring and no wrap.

**Under DTX2 it then refills the column whose turn it is**, one column a
row: column `j` on the row where the row number modulo `P` is `j`. A turn
past `C` minus one has no column and does nothing, which is how a `P`
above `C` costs those rows nothing.

The refill is one `ST4_resume` into that column's ring. Its budget is `P`
times `W[j]` divided by `k` units, except for the call that would reach
past the data set: the block holds the rows decoded, and where fewer than
`P` rows are left the budget is what is left, and where none are left the
refill does not happen. Without that rule every column takes one call
past its end marker, which ST4_wrap's assumption 5 forbids.

After the call the write pointer is compared with the column's ring end
and taken back to the ring start where the two are equal. `N` divides by
`P` times `W[j]`, so a full refill lands exactly on the end or short of
it, and the compare is exact. No call is counted and the block holds no
counter, which is how YMX wraps.

The advance from row `R` minus one where `RR` is below `R` is a jump to
`RR`, since it is one. Where `RR` equals `R` it gives $FFFFFFFF, leaves
the clock on row `R` minus one and moves no cursor, and a further advance
gives $FFFFFFFF again: the end is sticky, and a read after it still gives
row `R` minus one.

### 4. `DTX_read`, image+16

Writes the row the clock stands on, column 0 first, each value at its own
width and nothing between them: the row as DTX0 lays it out (SPEC.md
2.1). Where the clock stands on no row it writes nothing and gives back
`a1` as it came.

Read moves no cursor, refills nothing and decodes nothing, so a second
read of one row writes the same bytes.

Under DTX0 it is one run of bytes from the cursor. Under DTX1 and DTX2 it
takes `C` values, alternating between the columns: column `i` of a width
class stands at that class's cursor plus a build time displacement, so
the whole read is `C` moves off at most three cursors. The displacement
is `i` times `N` under DTX2, where every column has a ring of `N` bytes,
and the distance between the two column bases under DTX1, where a column
is `R` times `W[i]` bytes in the payload.

A displacement off an address register is a signed word, so the packager
holds the furthest of them to 32767 and fails the package otherwise.
Where a wide value falls on an odd offset of the row, the packager emits
two byte moves for it, or four for a four byte value, since it holds
every offset at build time.

---

## 3. The state block

The caller supplies the block and passes it back in `a0` on every call
but `DTX_metadata`. Its size is a build time constant the format block
states at +4, which the packager prints and a caller reads out of the
image. The block stands on a long.

| at | bytes | holds |
|---|---|---|
| +0 | 4 | the row the clock stands on, or $FFFFFFFF |
| +4 | 2 | the turn, 0 to `P` minus one. Zero under DTX0 and DTX1 |
| +6 | 2 | zero |
| +8 | 4 | the rows decoded. Zero under DTX0 and DTX1 |
| +12 | 12 | the caller's `a6`, `d6` and `d7`, parked for a DTX2 refill |
| +24 | 4 or 12 | one cursor under DTX0; one a width class present otherwise |

The rows decoded is init's `P` and grows by what each refill asks for. It
is what shortens the last refill of a column and stops the one after it,
and it is the only counter the block holds: the write pointer's wrap is a
compare, not a count.

Then, under DTX2 only, one slot a column, 32 bytes at a stride of 32, the
eight longs in `movem`'s own order, so that
`movem.l (a3)+,d0-d2/a0-a2/a4-a5` loads a slot whole:

| at in the slot | holds |
|---|---|
| +0 | `d0`, the bit queue in the low word |
| +4 | `d1`, the remaining count with the negated ring start above it |
| +8 | `d2`, the byte offset with the ring's bytes above it |
| +12 | `a0`, the position in stream A |
| +16 | `a1`, the write pointer |
| +20 | `a2`, the position in stream B |
| +24 | `a4`, the position in stream C |
| +28 | `a5`, the position in stream D |

`d1` and `d2` are held and put back as longs: ST4's ring decoders keep
the ring's bounds in their high words, so a slot holding only the low
words would decode into another column's ring.

The rings follow, `N` bytes a column at a stride of `N`, which is R5.5's
one size and one stride. Column `i`'s ring is the ring area plus `i`
times `N`, and its read cursor is its class cursor plus the same
displacement, so one cursor a class reaches every column of that class.

Sizes: DTX0 28 bytes. DTX1 24 plus 4 a width class present. DTX2 that
plus 32`C` plus `NC`.

---

## 4. DTX2: the turn

A DTX2 payload holds `C` data sets. A reader that drove `C` decoders a
few bytes a row would swap `C` register sets a row. This one refills
**one column a row**, so the work a row is one `ST4_resume` and the read
is never in the decoder at all. Over `P` rows every column is refilled
once, and what the `C` refills produce in that time is what `P` rows
consume.

**The invariant.** Init leaves every ring holding `P` rows. Column `j` is
refilled on rows `j`, `j+P`, `j+2P` and so on, `P` rows each time. At the
row before its next refill, column `j` has had `P` rows written for every
`P` a read has taken since init, and its ring holds at least one row that
has not been read. A ring of 2`P` rows a column is therefore enough, and a
ring of `P` rows is not: with `N` equal to `P` times `W[j]`, the refill on
row `j` writes over the row the read on that row is about to take.

**The five rules the packager holds, and fails the package on.**

- `P` is at least `C`, so one column a row refills every column in time,
  and at most `R`, since no refill asks for more rows than the table holds
- `N` divides by `P` times `W[i]` for every column, so a full refill
  lands on the ring end or short of it and never straddles it
- `N` is at least 2`P` times `W[i]` for every column, so a ring holds two
  periods and a refill never writes over a row not yet read
- `P` times `W[i]` divided by `k` is a whole number, 1 to 65535: the
  budget range, and the reason `k` divides `P` times the smallest width.
  `k` then divides `N` as well, which is ST4_wrap's assumption 1
- the furthest displacement a read takes off a class cursor is at most
  32767, so `C` minus one times `N` under DTX2, and the distance between
  the outermost two columns of a class under DTX1. A 68000 displacement
  is a signed word and `DTX_read` has no register left to re-base with

The last rule bounds a packed table harder than the format does: at the
`N` of 960 that Write and Rewrite default to, `C` is at most 35.

**Why ST4_wrap and not ST4_ring.** ST4_wrap decodes a fixed budget a call
and leaves the wrap to the caller, which fits exactly: every refill of a
column is the same budget, `N` divides by what a refill writes, so the
reader resets that column's write pointer after `N` divided by `P` times
`W[i]` calls and the decoder never checks a ring end. It is 324, 328 or
330 bytes at `k` of 1, 2 and 4, against ST4_ring's 386, 394 and 396. It
has no done state, which costs nothing here: the caller asks for rows 0
to `R` minus one and no decoder is driven past its data set.

`P` is the lever. `P` equal to `C` gives the smallest rings and the
flattest cost; a larger `P` spends more ring and leaves `P` minus `C`
rows a period with no refill at all. The packager's default is the
smallest `P` at least `C` that meets every rule above, and it fails the
package where no such `P` stands below `R`.

**The repeat.** An advance from row `R` minus one to an `RR` below it is
a jump backward, so it re-seeds and runs forward from row 0: a table that
repeats pays `RR` rows of decoding once a pass. Where `RR` times `W[i]`
divided by `k` is a whole number for every column, the packager instead
packs column `i` with `st4 -r` at that unit, so each data set loops where
the table does, no decoder is ever done, and the wrap costs one cursor
reload. That is what YMX does, and it is the only way the repeat is
flat.

**What advance does that read does not.** Advance steps the clock, moves
the cursors, refills the column whose turn it is, and takes the repeat at
`R` to `RR`. Read writes the caller's row bytes and changes nothing else.

---

## 5. What the packager resolves

It holds the table, the variant and every offset at build time, assembles
with rmac, and emits the five slots, the format block, the bodies for the
variant, the carried decoder under DTX2, and the table's bytes.

Folded into the bodies: the row's bytes, the class bases as PC relative
`lea`s, each width as the size of a move and as an `addq`, `R` and `RR`
as immediates, the state block's offsets, `P` as a mask where it is a
power of two, every column's budget in units, the four stream addresses
of every data set, the ring displacements, and the offset in the row of
every value, with byte moves where a wide value falls odd.

Under DTX2 the carried decoder is ST4's wrap decoder built at `ST4_UNIT`
equal to `k`. The data sets are packed without copies from the literal
stream, so the copy code is out of the build and its init writes no
instruction: the image is read only and may stand in ROM. The packer's
options are `-f -k<k> -m<N/k> -l65535`, and the last of them is what
holds ST4_wrap's assumption 4.

The packager chooses `N` and `P` together and packs the columns for the
`N` it chose, so no payload arrives with an `N` no `P` fits.

At build time the packager checks what no call checks: the variant, `R`,
`C`, `RR` and the widths against R6, that `R` is at most 2147483647 so
that no row number reads as the $FFFFFFFF an advance ends on, and the
format block's variant byte against the table header's own. Under DTX2 it
checks that `R` divides by `k`, the five rules of section 4, that the
ring and every stream meet ST4_wrap's alignment assumptions, and that
every data set's first long is `$53 $34 $07 k`. It fails the package
rather than emitting an image that reads wrong.

---

## 6. What each call costs

**Not yet measured.** Every figure here is read out of the emulation rig
over the emitted code, and this section is written when that rig runs.
Nothing in this document states a cycle figure before then.

What stands without measuring is the shape. Under DTX0 and DTX1 every
call is flat in `R` and the read is linear in `C`. Under DTX2 a read is
flat and linear in `C`, an advance is flat and takes one column's refill
of at most `P` rows, and a jump costs the rows it runs the turn through.

Two calls are not flat, and this document does not claim they are. A
backward jump costs the target row's worth of decoding. So does the
advance from row `R` minus one of a table that repeats, unless its
columns were packed to loop themselves, which section 4 states.

---

## 7. The assumptions, unchecked

1. The state block stands on a long and holds at least the bytes the
   format block states at +4.
2. The block passed to a call was seeded by init on this image, and one
   reader uses it. A call is not re-entrant on one block, and an
   interrupt that calls into the image uses a block of its own.
3. A jump's row in `d0.l` is 0 to `R` minus one.
4. A read's row bytes stand on a long and hold the row's bytes.
5. The table's bytes are the ones the packager checked. No call checks
   any of it. Trusted input only, as ST4 has it: a wrong value reads or
   writes arbitrary memory.
6. Nothing writes the image after packaging, and every reference into it
   is PC relative.
7. DTX2: the five rules of section 4 hold: `P` from `C` to `R`, `N` a
   multiple of `P` times every width and at least twice that, every
   budget a whole number of units from 1 to 65535, and every read
   displacement at most 32767.
8. DTX2: `R` divides by `k` (R5.6).
9. DTX2: the caller asks for rows 0 to `R` minus one. The rows decoded
   in the block shorten the last refill of a column and stop the one
   after it, so a column takes the `ceil(R/P)` calls ST4_wrap's
   assumption 5 allows and no more. ST4_wrap has no done state and none
   is read.
10. DTX2: ST4_wrap's own assumptions hold, its 1 to 7, which the packer
    options and the packager's checks keep.

---

## 8. The trade-offs

**Init fills every ring.** A read then alternates between the streams and
touches no decoder, and the first row is ready when init returns. Given
up: init is the one burst, `C` calls of `P` rows each, and the caller
pays it before the first row rather than during it.

**One column a row, not `C` columns every `P` rows.** The same total
decoding, spread flat. A row takes one `ST4_resume` of one column rather
than `C` of them on one row in `P`. Given up: the rings hold 2`P`
rows a column rather than one group, and `P` is at least `C`.

**The block is the caller's, not the image's.** Two clocks on one table
hold two places, the image stays read only and may stand in ROM, and a
68030 caller flushes no instruction cache. Given up: `a0` holds the block
on every call.

**ST4_wrap over ST4_ring.** No ring end check in the decoder, and 62 to
66 bytes less. Given up: the reader takes the write pointer back to the
ring start itself, which is one compare against the ring end after each
refill and no counter anywhere. The budget is fixed except on the last
refill of a column, and the rows decoded shortens that one.

**One cursor a width class.** Column `i` of a class stands at the class
cursor plus `i` times `N`, so a read is `C` moves off at most three
address registers. Given up: three cursors rather than one, the price of
widths of 1, 2 and 4 over YMX's single byte a stream.

**`movem` of the whole slot.** Reaching the decoder's state through a
pointer would turn every register read into a memory read and fork ST4's
code. Given up: two `movem`s a refill, and one refill a row.

**No checkpoints in DTX2.** A backward jump costs the target row and not
the distance, and the repeat at `R` to `RR` is one. Given up: a bounded
backward jump.

**The row goes down as DTX0 lays it out.** DTX0's read is then one run of
bytes, R3.5's reason for the variant. Given up: under DTX1 and DTX2 a
wide value that falls on an odd offset of the row costs two or four byte
moves, in the emitted code and never in a test.

**One variant an image.** A caller that reads two variants holds two
images, and the five calls read the same in both.
