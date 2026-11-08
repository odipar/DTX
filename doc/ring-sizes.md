# Ring sizes by slot

A design for DTX3, ahead of the code: each live column's ring is a
separate size, and `N` is the largest of them. Every other document in
this directory defines DTX as built. Issue
[#144](https://github.com/odipar/DTX/issues/144) defines the change, and
section 7 lists the figures it is judged on.

The refill period is one for the table and one slot is refilled a row, as
abi.md 4 defines them. A payload whose rings are one size reads as today.

## 1. The requirements

R8.3 and R8.4 read, as amended:

- **R8.3** A ring size for each live column, a multiple of `k` from `k`
  to 32512, which the column was packed for (R5.4). `N` is the largest of
  them. Each table defines its rings, so two tables read by one reader
  differ in their rings.
- **R8.4** A live column's slot is its rank among the live columns. Where
  every ring is `N`, a reader's rings are at a fixed stride by slot, and
  slot `j`'s value for a row is `j` rings past slot 0's. Where the rings
  differ, slot `j`'s value for a row is the reader's cursor plus a
  displacement of slot `j`, which the reader steps on the rows section 4
  defines. A constant column has neither ring nor decoder.

## 2. The payload

Flags bit 2 marks a payload whose rings differ in size. Bit 2 set: a ring
table of `L` words follows the constant values, slot 0 first, word `j`
slot `j`'s ring, and `N` at offset 0 is the largest word of it. Bit 2
clear: every ring is `N`, and the ring table runs to zero bytes.

| offset | bytes | contains |
|---|---|---|
| 0 | 2 | `N`, the largest ring |
| 2 | 1 | `k` |
| 3 | 1 | flags: bit 0 copies, bit 1 a replayed loop, bit 2 a ring table |
| 4 | 2 | `L` |
| 6 | 2 | zero |
| 8 | `C` | the column map |
| 8 + `C` | `W` times `C` less `L` | the constant values |
| .. | 2`L` where bit 2 is set | the ring table, a word a slot |
| .. | 16`L` | a stream record a slot, beginning on a long |
| .. | .. | each slot's four streams, each slot's beginning on a long |

The streams of slot `j` are those of an ST4 data set whose `M` is slot
`j`'s ring over `k`. A writer sets bit 2 where two slots' rings differ,
and clears it where every ring is `N`.

## 3. The packager's rules

The five rules of abi.md 4 apply to every slot's ring `N_j` in place of
`N`:

- `P` is at least `C`, as today, and the packager resolves `P` against
  `N`, the largest ring
- each `N_j` divides by `P` times the width
- each `N_j` is at least 2`P` times the width
- the budget, `P` times the width over `k`, is 1 to 65535, as today
- a replayed loop is `P` rows or more, as today

Where bit 2 is set, a sixth: the rings and their copies (section 4) come
to 32767 bytes or fewer, so every displacement is a signed word.

ST4 replays a loop longer than the ring a column is packed for, so the
form of a slot's loop follows from its ring. Every slot of a payload has
one form (R8.8): a writer packs every slot at a ring of the form the
payload's bit 1 marks.

## 4. The reader

Where bit 2 is clear, the reader is today's. The rest of this section
defines the reader where bit 2 is set. One template reads both, and the
path an advance runs between wraps is the same instructions in both.

**The rings.** Slot `j`'s ring is `N_j` bytes, and a copy of its first
`j` rows follows it, `j` times the width rounded up to a word. Slot `j`'s
ring begins where slot `j` minus one's copy ends. The seed adds each
slot's ring and copy to the address of the next, and `ST4_init` reads
`N_j` in place of `N`. The decoder state stays 48 bytes.

**The cursor and the displacements.** The pointer is a cursor that steps
the width a row over `K` bytes from the ring area's first byte, `K` the
largest multiple of `P` times the width up to 32768. The state block has a
long a slot after the rings: slot `j`'s displacement, the bytes from the
cursor to slot `j`'s value for the row. The seed writes each slot's ring
start less the cursor's.

**A slot's wrap.** Slot `j`'s value reaches its ring's end on a period's
first row, where the period's rows begin again at the ring's first byte.
Slot `j`'s refill one period before writes those rows, and copies the
first `j` of them past the ring's end. The displacement keeps its value
over rows 0 to `j` minus one of the period, which read the copy, and the
advance onto row `j`, slot `j`'s turn, subtracts `N_j` from it. So at most
one slot's displacement changes on a row, the slot whose turn it is.

**The refill.** A refill whose write pointer lands on its ring's end sets
the pointer back by `N_j`. ST4 keeps `N_j` in the high word of `d2`, and
the refill reads it there.

**The copy threshold.** `ST4_init` writes `-N_j` into the decoder's
compare of an offset against the ring. Where bit 0 is set as well, each
refill writes slot `j`'s `-N_j` there before it resumes slot `j`.

**The cursor's end.** The advance that steps the cursor onto its end sets
it back by `K` and adds `K` to every displacement, so every slot's value
keeps its address. That row comes once in `K` over the width rows.

**The advance** leaves `a1` the cursor, and `d0` 0 on a row where every
displacement keeps its value, `j` plus 1 where slot `j`'s changed, and -1
where every one changed at the cursor's end. Where bit 2 is clear, `a1`
is slot 0's value, as today, and `d0` is 0 on every row.

**Jump and repeat.** A jump seeds and steps row by row, so the
displacements follow the rows it steps. At the repeat the rings, the
copies and the displacements run on, as today: each set decodes the
loop's rows into the next bytes of its ring, and a replayed set leaves
the write pointer as it is (abi.md 4).

**The workspace.** DTX3 72 plus 48 times `L` plus 1, plus the rings and
their copies, plus 4`L` where bit 2 is set, and 32`L` more where a pass is
replayed. Where bit 2 is clear the rings are `NL` and the figure is
today's.

## 5. The caller

Slot `j`'s value for a row is `a1` plus slot `j`'s displacement.
`DTX_metadata` reports where the displacements are, where bit 2 is set,
and the stride `N` where it is clear. A caller that patches a read site's
displacement, as YMXR's player does at init, patches slot `j`'s sites
again after an advance that returns `j` plus 1, and every site after one
that returns -1. A caller that reads through the displacements adds slot
`j`'s to `a1` on every read. Where bit 2 is clear, slot `j`'s
displacement is `j` times `N` on every row.

## 6. YMXR

**The writer.** For each ring the writer compares today, `2C` shifted
left by 0 to 5, as the bound `N`, each slot is packed at every multiple of
`P` times the width from 2`P` times the width to `N`, and its ring is the
one at which its data set and its ring come to the fewest bytes, the
smaller ring of two that come to the same. Each slot's ring depends on
that slot's data set alone, so the search is one pass a slot. Every slot
is packed at a ring of one loop form (section 3). The footprint a set is
compared by is the tables' bytes and the state block, as today, with the
state block of section 4, so a table whose copies and displacements cost
more than its rings save keeps bit 2 clear.

**The player.** Init patches slot `j`'s read sites with slot `j`'s
displacement, in place of `j` times `N`, and lists each live slot's sites.
After an advance that returns `j` plus 1, the player patches slot `j`'s
sites from that list; after one that returns -1, every live site.

**The trees.** The Java and Go trees of DTX and YMXR write and read bit 2,
and the 68000 reader and YMXR's player read it. The C# tree of DTX writes
bit 2 clear.

## 7. Figures

**Bytes, measured.** YMXR's eleven corpus tunes, packed at `k` of 2 with
ST4's copies off, `P` of 30, the bound 1920. The bytes are the packed
streams and the rings. Each design is charged its additions: for a ring a
slot, 2`L` bytes of ring table, 4`L` of displacements and the copies; for
a fraction of the bound, `L` bytes of shifts in place of the ring table.
A tune smallest at one ring keeps bit 2 clear. Every data set at the rings
of the design above decodes back to its column, 177 slots in all. These
figures were measured once, at this design.

| design | bytes | less than today |
|---|---:|---:|
| one ring, `2C` shifted left 0 to 5, today | 66336 | |
| one ring, any multiple of `P` times the width | 64874 | 2.2% |
| a ring a slot, `N` shifted right | 64402 | 2.9% |
| a ring a slot, any multiple of `P` times the width | 61562 | 7.2% |

A ring a slot at any multiple is the design above. DBA 2 is 14.0% smaller,
Retrigger retune 11.7%, Synergy Credits 6.7%, DBA 5 6.2% and Turrican
world 4-3 4.3%. The other six keep bit 2 clear: five are smallest at one
ring of 60, the least the period allows, and in Digidrum preempt the
copies and the displacements cost more than the rings save.

**Cycles, estimated.** From the 68000's instruction timings, over every
row of the five tunes that set bit 2, with YMXR's player at its default
build and every site of a live column patched. YMXR budgets 6656 cycles a
frame.

| tune | a frame on average | the costliest row | the cursor's end |
|---|---:|---:|---:|
| DBA 2 | +76 | +632 | +2436 |
| DBA 5 | +75 | +632 | +2436 |
| Retrigger retune | +126 | +654 | +2608 |
| Synergy Credits | +113 | +742 | +3296 |
| Turrican world 4-3 | +108 | +632 | +2436 |

The player's test of `d0` costs 12 cycles a frame and the reader's test
of a wrap 16 a refill row; a slot's turn after its wrap adds the copy, the
step and the patch of that slot's sites. The cursor's end comes
once in 32760 rows, 10.9 minutes at 50 Hz, and patches every live site.

Measured next, in the code:

- the 68000 cycles of an advance and of a frame, on DTX's rig and on
  YMXR's, against the estimates above
- the workspace of a set, sized for the largest of its tunes
