# releases

What a release contains stands here, and each one published is listed
below it.

## What a release contains

`release/publish.sh` writes `dist/release` (tools.md, Release), and
`release/manifest.sh` the manifest in it:

- one zip a platform, over six: Windows, macOS and Linux, each on x64 and
  arm64. A zip contains the three tools as executables, and each executable
  contains the twenty-two 68000 images, so a caller who takes one has the
  whole of what packaging needs
- one zip of the twenty-two images, one file a build inside it, for a
  caller who takes an image and no tool (BINARIES.md)
- `MANIFEST.txt`: every file's size and sha256 beside what identifies it -
  a variant, a width, a unit and copies for an image, what it contains for
  a zip - and the source commit the release was built from

The version names every file. It is read out of `pom.xml`, or given as the
script's one argument. The pom names the release being cut, and moves to
the next `-SNAPSHOT` once it is.

The images are built from `68k/` by rmac on the machine that cuts the
release, and nowhere else: a caller who takes a release does not run an
assembler, and no image is tracked in the tree.

## Published

### Not yet cut

**No file changes, and no caller's code but one that read the block's
words at +44, +48 and +68.**

- A replayed pass puts each column's registers away and takes them back
  at the exact row its loop begins and ends at, splitting the refill the
  row falls inside, where it did so at a period's end and asked of the
  packager that `RR` and `R` minus `RR` divide by `P`. That rule is gone:
  a table repeats at any row, and packs at the period its ring gives
  rather than one its loop divides by.
- A table of fewer rows than a period packages: the reader seeds its rows
  and its first period's budget is 0, where the packager failed it.
- The block's words at +44 and +48 are the units before the loop and the
  units of the loop; +68 is unused; a decoder state has a phase and a
  mark at +42 and +44.
- On 64 rows of three two byte columns an advance is 676 to 928 cycles
  where it was 658 to 1002, and on twenty columns 1112 where it was 1094:
  the mark's test on every refill, and no marked period. DTX2's code is
  1448 bytes at `k` of 1 where it was 1244.

### 0.5.0, 2026-09-06

<https://github.com/odipar/DTX/releases/tag/v0.5.0>, built from the commit
tagged `v0.5.0`.

**A caller written against 0.4.0's state block has to take its size out
of the format block, as abi.md 3 has always said.** Every file reads as
before, and no tool's output changes but the state block's size.

- DTX2's advance walks the decoder states rather than indexing them: a
  refill reads its state's address out of the block, and the state gives
  its ring's end, its budget and where its registers go at a loop. On 64
  rows of three two byte columns an advance is 658 to 1002 cycles where
  it was 1180 to 1358, and on twenty columns 1094 where it was 1616.
- A replayed pass puts each column's registers away, and takes them back,
  at that column's own refill, in the period after the loop's row and the
  period after the pass's row. It copied every column's in one call, 170
  cycles a column, on the row before each.
- The state block is 72 plus 48`P` plus `NC` under DTX2, and 32`C` more
  where a pass is replayed, where it was 56 plus 32`C` plus `NC` and 32`C`
  more. The decoder state is 48 bytes, one a turn, and the word at +0 is
  the turns left in the period rather than the turn.
- A DTX2 table that repeats, whose loop a back reference reaches and
  whose `R` does not divide by `P`, read wrong past its first pass: the
  reader's count never came round, so its refills stopped. The count
  comes round at the first period end at or past `R`.
- DTX2's code is 1244 bytes at `k` of 1 and 2, and 1252 at 4, where it
  was 1056 and 1064.

### 0.4.0, 2026-09-06

<https://github.com/odipar/DTX/releases/tag/v0.4.0>, built from the commit
tagged `v0.4.0`.

**A caller written against 0.3.0's calling convention has to change, and a
DTX2 file that repeats has to be written again.** The tools of both
releases read every file the other writes, and only one file differs at
all: a DTX2 table that repeats. What broke is the 68000 side.

- An advance gives the address of the row's first value and nothing else.
  It gave the row in `d0` as well, or $FFFFFFFF at the end. A row is what a
  jump takes, and a caller counts its own rows against the `R` and `RR`
  that `DTX_metadata` gives.
- The state block is 12 bytes under DTX0 and DTX1 where it was 20, and 56
  plus 32`C` plus `NC` under DTX2 where it was 52. A caller reads its size
  out of the format block, as it always could.
- A 0.4.0 image reads a 0.3.0 DTX2 file that repeats wrongly: its data sets
  end where this release's loop, so the reader runs the decoder past the
  end marker. Write such a file again with this release's `dtx-write`. A DTX2
  file that does not repeat, and every DTX0 and DTX1 file, is byte for byte
  what 0.3.0 wrote.

What changed since 0.3.0:

- **A DTX2 table that repeats loops in ST4, not in the reader.** The
  advance out of the last row was a jump that re-seeded every decoder and
  ran the turn forward to `RR`: on 64 rows of three two byte columns,
  46514 cycles against 1264 for an ordinary advance. Every data set of a
  table that repeats loops at `RR` now, so the rows come round and the
  repeat is an advance. A loop longer than a back reference reaches is
  replayed, as ST4 defines: the decoder's registers go away at the loop's
  first unit and come back at the column's end.
- **No call keeps a row.** Every compare left the advance with it. On 64
  rows of three two byte columns an advance is 70 cycles under DTX0 where
  it was 126, and 66 under DTX1 where it was 122. DTX0's code is 132 bytes
  where it was 208, and DTX1's 84 where it was 160.
- **The rows decoded still shorten a column's last refill** where the data
  sets end. That is why a DTX2 advance is cheaper than 0.3.0's: on twenty
  two byte columns it takes 1432 cycles on average over 64 rows where
  0.3.0 took 1512.
- R5.11 is the rule this adds: `RR` times the width divides by `k`, so row
  `RR` begins a unit of the column. A writer given a table that breaks it
  fails, naming the rule.
- The README has sections, a usage section and an attribution section.

### 0.3.0, 2026-09-06

<https://github.com/odipar/DTX/releases/tag/v0.3.0>, built from the commit
tagged `v0.3.0`.

**It reads 0.2.0's files, and 0.2.0 reads its own.** The format, the four
calls and the state block are what 0.2.0 defined; what changed is the code
behind the calls and what the tools print.

What changed since 0.2.0:

- **The reader is smaller.** `DTX_payload` and the macro that installs a
  long moved into `68k/DTX_image.S`, where three templates include the one
  copy, and the six fields a combine writes stand at zero in the format
  block rather than being assembled in and then written over. DTX0's code
  is 208 bytes where it was 232, DTX1's 160 where it was 176, and DTX2's
  884 where it was 924. An image the packager assembles and one it takes
  from the build are the same bytes now, at every variant and width.
- **The three trees read alike.** A bare `-a`, a header with an `R` above
  2147483647, and code with no format block were each taken or reported
  differently by the Java, Go and C# tools. The three now give one line for
  each, and a tool that cannot do the work it was given prints the reason
  rather than a stack trace.
- **The documents read back what the code does.** A review of every file
  against the code it describes took 213 findings: the format block's place
  and size in the glossary, six slots where there are four, five fields
  where a combine writes six, image sizes from two changes ago, a
  conformance kit whose README named a check that no test in this
  repository runs, a cell formula that read as `r` times `i` plus one where
  the writer computes `r` times (`i` plus one), and a rule that left one
  width out.

### 0.2.0, 2026-09-05

<https://github.com/odipar/DTX/releases/tag/v0.2.0>, built from the commit
tagged `v0.2.0`.

**It does not read 0.1.0's files, and 0.1.0 does not read this release's.**
The header changed shape under the same variant numbers, so a table written
by 0.1.0 reads as a table of another shape rather than as an error: its
first column's width byte reads as the table's width, and its payload
begins where 14 plus `C` rounds up to a long, past where the reader looks
for every `C` but 1 and 2. Rewrite such a file with 0.1.0's `dtx-write`
into text and read the text back with this release's.

What changed since 0.1.0:

- **One width a table**, not one a column (R6.3). The header is 16 bytes
  under every variant and every `C`, with the width at 14. A row is `C`
  times `W` and a column is `R` times `W`, so DTX1's columns lie at one
  stride and DTX2's rule weakens to `R` times `W` divides by `k`.
- **Four calls, and none of them copies a value.** An advance gives the
  address of the row's first value and `DTX_metadata` gives the stride, so
  a caller reads the columns it needs where they stand. `DTX_read` and
  `DTX_take` are gone, the slots run to 16 bytes and the format block
  stands at +16 in 28 bytes.
- **Twenty-two images**, not eight: one for DTX0 at every width, one a
  width for DTX1, and one a width a unit with the copy code and without
  for DTX2.
- **Three tools**, not four: `dtx-rewrite` folded into `dtx-write`, which
  takes text or a DTX file and writes a DTX file or text.
- The reader is smaller and faster for it. On 64 rows of three two byte
  columns, DTX1's code is 176 bytes where it was 716, DTX2's 924 where it
  was 1476, and reading a value costs a caller the 12 or 16 cycles of one
  move.

### 0.1.0, 2026-09-05

<https://github.com/odipar/DTX/releases/tag/v0.1.0>, built from the commit
tagged `v0.1.0`. The first release, so this lists what there is rather than
what changed: DTX0, DTX1 and DTX2 as SPEC.md defined them at that tag, each
column of its own width; one 68000 image a build, eight in one zip, under the
calling convention of abi.md; the four tools of tools.md, as executables for
the six platforms; and the ST4 packer carried in each tree, so a DTX2 table
packs with nothing installed beside the tools.
