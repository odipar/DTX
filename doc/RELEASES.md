# releases

What a release contains stands here, and each one published lists below
it.

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

### 0.2.0, 2026-09-05

<https://github.com/odipar/DTX/releases/tag/v0.2.0>, built from the commit
tagged `v0.2.0`.

**It does not read 0.1.0's files, and 0.1.0 does not read its own.** The
header changed shape under the same variant numbers, so a table written by
0.1.0 reads as a table of another shape rather than as an error: its first
column's width byte reads as the table's width and its payload begins four
bytes later than the reader looks. Rewrite such a file with 0.1.0's
`dtx-write` into text and read the text back with this release's.

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
