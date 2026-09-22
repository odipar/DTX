# releases

What a release contains stands here, and each one published is listed
below it.

## What a release contains

`release/publish.sh` writes `dist/release` (tools.md, Release), and
`release/manifest.sh` the manifest in it:

- one zip a platform, over six: Windows, macOS and Linux, each on x64 and
  arm64. A zip contains the three tools as executables, and each executable
  contains the twenty-two 68000 images, so a caller who unpacks one has the
  whole of what packaging needs
- one zip of the twenty-two images, one file a build inside it, for a
  caller who needs an image and no tool (BINARIES.md)
- `MANIFEST.txt`: every file's size and sha256 beside what identifies it -
  a variant, a width, a unit and copies for an image, what it contains for
  a zip - and the source commit the release was built from

The version names every file. It is read out of `pom.xml`, or named as the
script's one argument. The pom names the release being cut, and moves to
the next `-SNAPSHOT` once it is.

The images are built from `68k/` by rmac on the machine that cuts the
release, so the caller's machine has an assembler to install or not as it
pleases. They are committed under `go/image/data`: a Go module fetched by
its import path contains the files a commit has in it, and an executable
built from one embeds these. `ImageTest` reads them against a fresh
assembly, so an image that does not match what rmac writes today fails the
build.

## Published

### 0.11.11, 2026-09-22

<https://github.com/odipar/DTX/releases/tag/v0.11.11>, built from the commit
tagged `v0.11.11`.

What a caller sees when a tool reports something: tools.md writes those
lines down for the first time, and the Java tree, the Go tree and the C#
tree are read against them. Two of the three wrote a line the others do
not.

- **A refill's mark read two ways.** The Java tree reported `a replayed
  loop of L rows is under the period of P: a refill holds one mark at
  most` where the Go tree and the C# tree read `meets`. The three read
  `meets` now, which the house style asks of the verb in any case.
- **A write that fails.** The Java tree and the Go tree report `cannot
  write standard output`, where the C# tool left the failure to the
  runtime. It reports the line now.
- **The lines are in the document.** tools.md has a table for reading a
  table, one for writing a DTX2 table and one for either tool, and
  `ConsistencyTest` reads each line against the three trees: the longest
  run of words between the figures a tool writes into a line must stand
  in each.

A data set loops where its table does (SPEC.md 2.3's note), the
citations of the specifications land on clauses, and a first reader read
the kit cold: those moved the documents alone.

Checks: `mvn -o clean test` green, 116 tests; `dotnet test` green; `go
test ./...` green.

### 0.11.10, 2026-09-18

<https://github.com/odipar/DTX/releases/tag/v0.11.10>, built from the commit
tagged `v0.11.10`.

One build runs at a time, and a quoted block keeps its words. Every file a
tool is built from stands as 0.11.8 has it - `go/`, `68k/`, `dotnet/` and
every document - so the tools are that release's, the twenty-two images
are its bytes, and `go/dtx` reads ST4 at v0.1.9 still, whose module is the
module v0.1.11 tags. This release is the style check, the script under
`bin/` and the parity tests.

- **One build runs at a time.** `bin/dtx-write -v2 -k1 < in.csv |
  bin/dtx-package > out.bin`, the pipeline doc/tools.md opens with, starts
  both tools at once, and with a source newer than the last build both
  found a build owed and both ran Maven into the same `target/classes`:
  measured here with a wrapper counting invocations, that pipeline ran
  `mvn` twice. `bin/dtx-run` builds under the lock `target/.building` now,
  as YMXS's runner has since it hit the same race. The output of the build
  went to standard output, which for the first tool of a pipeline is the
  stream the second reads; it goes to standard error now.
- **A code span that wraps is quoted whole.** The style check blanked a
  code span a line at a time while the rest of it reads a paragraph
  joined, so a span broken by a wrap was two unpaired backticks and its
  words were read as prose - and the pairing ran from one span's closing
  backtick to the next span's opening one, which blanked the prose between
  them. Over the four repositories that is 150 regions, 5,506 characters,
  blanked as Markdown reads them, and 82 fragments, 906 characters, of
  prose read back. The blanking runs over the joined lines now, and ST4,
  YMXS and YMXR carry the change.
- **A fenced block is quoted as a code span is.** A fence broke the
  paragraph and was read alone, and the lines inside it were read as
  paragraphs of this tree: 53 blocks, 106 fences and 188 lines over the
  four repositories, every one of them a command, a file, a run of output
  or a diagram. The check reads past a block and the two fences around it.
- **The parity tests read the error paths**, so a tree that reports a
  wrong input differently from the other two fails the build.

### 0.11.9, 2026-09-18

<https://github.com/odipar/DTX/releases/tag/v0.11.9>, built from the commit
tagged `v0.11.9`.

The document checks are one package, kept here. Every file a tool is built
from stands as 0.11.8 has it - `go/`, `68k/`, `dotnet/`, `bin/` and every
document - so the tools are that release's, the twenty-two images are its
bytes, and `go/dtx` reads ST4 at v0.1.9 still, whose module is the module
v0.1.10 tags.

- **`org.dtx.doc.Documents`** reads a link that resolves, one wrap width, a
  glossary in order and the rows it is read from. Those four were written
  in each of the four repositories of the family, and the copies had
  drifted in both directions: this tree read fenced blocks and anchors
  where ST4 skipped them, and reported neither the line a broken link is
  written at nor how many documents it had read, which YMXS and YMXR did.
  The package reads the best of the four, and ST4, YMXS and YMXR each
  carry a copy of it beside the copy each has of `org.dtx.style`.
- `ConsistencyTest` is 46 lines shorter for it, and reads the figures of
  this repository as it did.

### 0.11.8, 2026-09-18

<https://github.com/odipar/DTX/releases/tag/v0.11.8>, built from the commit
tagged `v0.11.8`.

ST4 0.1.9 in all three trees, and two lines of the style check. The packer
moved in its comments alone, so every table packed here and every one of
the twenty-two images is 0.11.7's byte for byte.

- **A directory named `bin` under a build is a build's.** The check read
  every file whose parent directory is named `bin` as a script, since a
  script under `bin/` is named without an extension, and a Go build run as
  `go build -o bin/` writes executables there. Reading one as text ended
  the check in a decoding fault rather than a hit. YMXS carried this
  package and hit it on the first run.
- **A code span is quoted material**, and the words inside one are not read:
  a document that quotes the message a tool writes, or spells a construct
  in order to strike it, reported a hit on the words it quotes. AGENTS.md says
  a quoted message keeps its words. YMXR carried the package and hit that
  one, on two of its tools' messages.
- Both lines stand in the copies ST4, YMXS and YMXR carry, so the four read
  the same 370 lines.
- `README.md` counts the Java tests the tree runs: 110, where the row read
  101 and no check read the figure back.
- The copies of the packer here are `odipar/ST4@0af4845`, and `go/dtx`
  reads the module at v0.1.9.

### 0.11.7, 2026-09-17

<https://github.com/odipar/DTX/releases/tag/v0.11.7>, built from the commit
tagged `v0.11.7`.

ST4 0.1.7 in all three trees, and two checks over the documents. The
packer files are 0.1.6's, which are 0.1.7's, so every table packed here and
every one of the twenty-two images is 0.11.6's byte for byte.

- `everyClauseCitedInAnotherDocumentIsDefined` reads every `<document>.md
  N` citation against the clauses that document defines.
  `everySectionCitedExists` reads SPEC.md against itself alone, and abi.md's
  twenty-two citations and every other document's went unread. There are 43
  and each resolves.
- `theNewestReleaseListedIsTheVersionOfTheBuild` reads this document's
  newest entry against `pom.xml`, which `release/publish.sh` names every
  file by.
- The copies of the packer here are `odipar/ST4@6341b8f`, and `go/dtx`
  reads the module at v0.1.7.

### 0.11.6, 2026-09-17

<https://github.com/odipar/DTX/releases/tag/v0.11.6>, built from the commit
tagged `v0.11.6`.

ST4 0.1.6 in all three trees: the Java and C# copies here are that
release's files, and `go/dtx` reads the module at v0.1.6. The fix is in the
packer, where a copy the offsets cannot reach is written as literals.

**No byte of this release differs from 0.11.5's.** The decoder is the same
bytes, so the twenty-two images are, and every table a DTX2 payload packs
here is the same: the ST4 change moves a stream only where a copy's source
lies further back than an offset reaches, which needs a literal stream of
more than 32,512 bytes behind the copy, and no table of this repository's
corpora reaches that.

- What it was: `st4 -k1 -m16 -c` over a stream with a repeat 32,512 units
  back ended the Go tool and the C# port at `a copy reaches past the
  offsets`, and the Java tool, whose check is an assertion, packed a stream
  with an offset the format does not encode. One packer, copied into three
  trees, read one call three ways.
- `ParityTest` reads the three trees over a corpus and requires the same
  bytes, so the three copies stay one packer.

### 0.11.5, 2026-09-15

<https://github.com/odipar/DTX/releases/tag/v0.11.5>, built from the commit
tagged `v0.11.5`.

The ST4 inside this one comes from a release rather than from a commit.
0.11.4 copied the decoder here from ST4 at `acbef72`, which no ST4 release
had reached; ST4 go/v0.1.5 is that decoder, and `go/dtx` reads the module at
v0.1.5.

**No byte of this release differs from 0.11.4's.** The decoder is the same
bytes, so the twenty-two images are, and the packer is the same, so a table
is. The content of the Go module did not change between v0.1.4 and v0.1.5
either: ST4 moved no packer.

### 0.11.4, 2026-09-16

<https://github.com/odipar/DTX/releases/tag/v0.11.4>, built from the commit
tagged `v0.11.4`.

The ST4 decoder inside an image enters on an instruction rather than on a
branch, so every DTX2 image is smaller and an init costs less. A table
packs to the bytes 0.11.3 packed: the packer is that release's, and the
conformance kit reads back byte for byte.

- `68k/ST4_wrap.S` comes from ST4 at `acbef72`, where the jump table's last
  slot became `ST4_resume` itself. The Java and C# copies of the packer and
  the Go module are 0.11.3's, ST4 having changed no packer since.
- The eighteen DTX2 images lose 12 to 16 bytes each; DTX0's and DTX1's
  stand. Assembled alone the decoder is 310, 314 and 316 bytes where it was
  324, 328 and 330.
- An init of three columns at `k` of 1 costs 7,700 cycles where it cost
  7,844, and of twenty columns 54,524 where it cost 55,324. An advance is
  within a few cycles either way. performance.md lists all 65 figures, and
  the rig counts every one of them again.
- The copy code is 28 bytes at `k` of 1 in an image, against 32 before, and
  32 and 36 at `k` of 2 and 4 as it was.

The documents were swept in the same release: abi.md is a quarter shorter
and tools.md a fifth, the cleft is struck in its bare form, and the README
names the family and links what it names.

### 0.11.3, 2026-09-16

<https://github.com/odipar/DTX/releases/tag/v0.11.3>, built from the commit
tagged `v0.11.3`.

The ST4 under this one searches with two moves that read the parse. A
column packed with `-copies` alone is the bytes 0.11.2 packed, only a
search with seconds moving; a column packed with `-copiesS` is smaller for
the same seconds.

- The Java and C# copies, and `68k/ST4_wrap.S`, come from ST4 at
  `5081f2e`, and `go/dtx` reads `github.com/odipar/st4/go` at v0.1.4.
- ST4's search gained a move that grows the dictionary where a copy reads
  from, and one that fills the gap between a literal run and the one after
  it. Over 120 columns at a second a column through a ring of 256 bytes,
  1.20 per cent fewer bytes, and over 24 of them at three seconds a column,
  2.77.
- No class, no image and no packaged byte of the tests moves: the
  conformance kit reads back byte for byte and the twenty-two images are
  the bytes they were.

### 0.11.2, 2026-09-16

<https://github.com/odipar/DTX/releases/tag/v0.11.2>, built from the commit
tagged `v0.11.2`.

The ST4 under this one searches better. A column packed with `-copies`
alone is the bytes 0.11.1 packed, only a search with seconds moving; a
column packed with `-copiesS` is smaller for the same seconds.

- The Java and C# copies, and `68k/ST4_wrap.S`, come from ST4 at
  `aa01118`, and `go/st4` reads `github.com/odipar/st4/go` at v0.1.3.
- ST4 read what each of its search's moves saved and weighted the odds by
  it: extending a literal run saves bits where freeing one at random is the
  walk the annealing makes. A second a column writes 0.51 per cent fewer bytes
  over 120 columns, and three seconds 1.3 per cent.
- No class, no image and no packaged byte of the tests moves: the
  conformance kit reads back byte for byte and the twenty-two images are
  the bytes they were.

### 0.11.1, 2026-09-16

<https://github.com/odipar/DTX/releases/tag/v0.11.1>, built from the commit
tagged `v0.11.1`.

The ST4 under this one packs faster. The library, the images and the
packaging are 0.11.0's, so a table this release packages is the bytes that
one packaged.

- The Java and C# copies, and `68k/ST4_wrap.S`, come from ST4 at
  `07097fb`, and `go/st4` reads `github.com/odipar/st4/go` at v0.1.2.
- ST4's literal channel reads its least in one step where it read a
  min-tree in a logarithm. A search of a column with copies fits a fifth
  more steps in a second at a small ring, 0.35 per cent smaller at
  a fixed budget; at a wide ring it reads as it read before.

### 0.11.0, 2026-09-15

<https://github.com/odipar/DTX/releases/tag/v0.11.0>, built from the commit
tagged `v0.11.0`.

The Go tree reads ST4 as a module rather than a copy, and the copies the
Java and C# trees carry are copied again from the ST4 that collects its
node pool. No class, no image and no packaged byte moves: the conformance kit
reads back byte for byte and the twenty-two images are the bytes they were,
so a table this release packages is the bytes 0.10.1 packaged.

- `go/st4` is `github.com/odipar/st4/go` at v0.1.1 and a small re-export
  beside it, where it was ten library files copied here. A Go caller
  fetches ST4 the way it fetches this, and a packer change reaches the Go
  tree by a version rather than by a fresh copy.
- The Java and C# copies, and `68k/ST4_wrap.S`, come from ST4 at
  `19a77dd`. The assembly changed in its comments alone.
- Packing a column with copies costs a fifth of the memory it did. ST4's
  search kept a node for every state it reached and 98 per cent of them
  were unreachable by the end; its pool collects now. A 48 KB file peaks at
  1,386 MB where it peaked at 6,472, and the Java tools pack it at
  `-Xmx1g` where they needed 12 GB.
- The house style is the one the family shares, and the two stand-in verbs
  of *The verb that says the action* have a pattern each now: 720 lines of
  documents, code comments and scripts read as the rules say. `HouseStyle`
  fails a build on what it finds, so every rule of AGENTS.md that a pattern
  can match now has one.

### 0.10.1, 2026-09-13

<https://github.com/odipar/DTX/releases/tag/v0.10.1>, built from the commit
tagged `v0.10.1`.

Four more examples in `-help`. The library, the images and the packaging
are 0.10.0's, so a table this release packages is the bytes that one
packaged.

- `dtx-write` gains the pipe into `dtx-package`: 0.9.0 made the tools
  filters so they would compose, and no example showed two of them
  composing. It gains `-copies` with a search of seconds besides, and
  `-pPACKER`, an ST4 executable of the caller's.
- `dtx-blobs` gains `-tTEMPLATES`, the one flag of its three with no
  example.
- `dtx-package` keeps the four it had. They cover both its flags and both
  shapes of input, and a fifth would say what one of them says.

### 0.10.0, 2026-09-13

<https://github.com/odipar/DTX/releases/tag/v0.10.0>, built from the commit
tagged `v0.10.0`.

The Go module is fetched by its import path, and the rig reaches the tools
again. The library is as it was: no class, no image and no packaged byte
moves, so a table this release packages is the bytes 0.9.0 packaged.

- **The Go module is `github.com/odipar/dtx/go`**, the path a
  caller fetches it by: a module in a repository's `go/` has the path of
  that directory, and the name `dtx` reached no one. Every import follows.
  A version is a tag of the directory, `go/v0.10.0` beside `v0.10.0`.
- **The twenty-two images are committed under `go/image/data`.** A module
  fetched by its path contains the files a commit has in it, so a tree
  without them built an executable that embedded none. `ImageTest` reads
  them against a fresh assembly of `68k/`, so an image that does not match
  what rmac writes today fails the build.
- **The rig reaches the tools again.** 0.9.0 made the two tools filters
  and the rig went on naming files, so it stopped at its first table and
  the alignment check of issue #73 had not run since. The rig hands the
  tools bytes on standard input now. With it running, every table passes
  at every width, and the check bites: inverted to fault on an aligned
  access it reports one at the first read.
- Both widths reach the paths a pointer moves in, which a width of 1 or 2
  reached alone: a ring that wraps at a width of 4, a loop the ring does
  not fit at 4, an odd `R` at 2 and at 4, a unit of 2 at a width of 4, a
  copy from the literal stream at 1 and at 4, and two tables in one image
  at 4. Twelve cases, and no address odd in any.
- **`RigCallsTest` runs on every build.** The rig costs four minutes, so
  no build runs it, and that is how its calls went unread. The test
  imports the rig and runs the two helpers that reach a tool, over a table
  of two rows: no emulator, no assembler, under a second.
- The copy case at a width of 1 is out of the rig. No boundary applies at
  a width of 1, so it read the copy path a second time and cost 1.2
  seconds. The width of 4 stays, the copy path where a 68000
  aligns.

### 0.9.0, 2026-09-10

<https://github.com/odipar/DTX/releases/tag/v0.9.0>, built from the commit
tagged `v0.9.0`.

**Every caller of the two converting tools changes: they read standard
input and write standard output.** The library is as it was: no class, no
image and no packaged byte moves.

- `dtx-write` reads the table on standard input and writes it on standard
  output. `-text` writes the table as comma separated text, in place of
  the `.csv` the output name used to end in, and `-text` with `-v` is a
  wrong call.
- `dtx-package` reads one table on standard input, or several as names,
  and writes the image on standard output. `-s` writes the figures there
  too.
- `dtx-blobs` writes the twenty-two images into the directories it is
  named, so it keeps its arguments; its listing moves to standard error.
- Every report, fault and usage goes to standard error, so a conversion
  composes in a pipe: `dtx-write -v2 -k1 < in.csv | dtx-package > out.bin`.
- The exits are the three they were: 0 done, 1 the input is wrong, 2 the
  call is wrong.
- `go/internal/dtx`, `pack`, `st4` and `image` moved to `go/dtx`,
  `go/pack`, `go/st4` and `go/image`, so another Go module imports them:
  Go forbids a module from importing another module's `internal`.

### 0.8.0, 2026-09-08

<https://github.com/odipar/DTX/releases/tag/v0.8.0>, built from the commit
tagged `v0.8.0`.

**Every caller changes: the state block stands in `a6` and not in `a0`,
on the three calls that read it.** `DTX_metadata` reads the image alone
and is as it was.

- Under DTX2 the reader keeps its figures in `a6` and ST4 leaves
  `a6` alone, so a block in `a0` cost a call three instructions: the
  caller's `a6` parked, `a0` moved into it, and `a6` restored. Those
  are gone, and DTX0 and DTX1 reach their fields through `a6` where
  they reached them through `a0`.
- An advance under DTX2 reads 640 to 892 cycles on three columns of two
  byte values at `k` of 1 where it read 676 to 928, and 1,076 on twenty
  where it read 1,112. DTX0's and DTX1's are as they were, at 70 and 66:
  neither ever used `a6`.
- DTX2's code is 1,412 bytes at `k` of 1 where it was 1,444, 1,408 at
  `k` of 2 and 1,420 at `k` of 4.
- The state block's long at +4 is unused under DTX2, where it parked
  `a6`; under DTX0 and DTX1 it stands at the payload, as it did.
- A caller whose own base is `a6` reaches the block at a fixed offset of
  it, so the `lea` that forms the argument writes `a6` itself and costs
  what the old one cost.

### 0.7.0, 2026-09-08

<https://github.com/odipar/DTX/releases/tag/v0.7.0>, built from the commit
tagged `v0.7.0`.

**Every caller changes: `DTX_init` reads the header of the table to read in
`a1`.** A caller that leaves `a1` where it stood seeds its block on the
address that register stood at. A caller of an image of one table passes
the image plus the format block's +8, which it may read at build time.

- An image contains one table or several: the code once, then a column
  table and a table's bytes for each, every pair on a long. Every table
  in an image is of the variant and the width the code was built for, and
  under DTX2 of its unit and copies flag, and shares `P` and `N` with the
  rest; `R`, `C` and `RR` are each table's. So a caller with several
  tables of one shape packages the reader once rather than once a table.
- `DTX_init` reads `a1`, the table's header. `DTX_payload` is handed that
  header rather than deriving one, and under DTX2 the column table is
  reached at the header less 16`C`, which is where the packager lays it.
- Init parks the payload it was seeded on, and `DTX_jump` reads it there
  rather than deriving the format block's again. Under DTX0 and DTX1 the
  block's +4 stands at that payload, where it parked the caller's `a6`
  under DTX2 and stood unused under the plain two.
- Under DTX0 init forms the row's bytes from the header in `a1`,
  `C` times the width, where it read the format block's field: the figure
  stands in three instructions, so it is the table the block was seeded
  on.
- The format block's +4, +8, +12, +20 and +24 are the first table's, and
  +14, +16, +18 and +19 the image's. An image of one table is the bytes
  it always was.
- A jump costs less: 246 cycles to 186 under DTX0 and 138 to 78 under
  DTX1, since it no longer walks the block. Init costs 210 to 248 under
  DTX0 for the multiply, and 138 to 128 under DTX1 and 7,894 to 7,880
  under DTX2 on the three column example. DTX1's code is 80 bytes where
  it was 84 and DTX2's 1,444 at `k` of 1 where it was 1,448.
- `Packager.packaged`, Go's `pack.Images` and C#'s `Pack.Image` read the
  files and return the image and where each table's header stands.
  `dtx-package [in.dtx...] > out.bin` reads the tables it is named and
  prints where each stands.
- Two sentences of abi.md that were wrong are corrected: DTX1 leaves its
  code unwritten, so a DTX1 image may stand in ROM, and no site is
  written with `R` or `RR` for an advance's compares.

### 0.6.0, 2026-09-07

<https://github.com/odipar/DTX/releases/tag/v0.6.0>, built from the commit
tagged `v0.6.0`.

**No file changes, and no caller's code but one that read the block's
fields from +36 on, or a decoder state's past its registers.**

- A replayed pass puts each column's registers away and puts them back at the
  exact row its loop begins and ends at, splitting the refill the row falls
  inside, where it did so at a period's end and asked of the packager that
  `RR` and `R` minus `RR` divide by `P`. That rule is gone: a table repeats at
  any row, and packs at the period its ring needs rather than one its loop
  divides by; one rule stays, a replayed loop a period long at least, which
  binds only a table whose period is above 32512 bytes.
- A table of fewer rows than a period packages, where the packager failed
  it: where its sets end the reader seeds its rows and the first period's
  budget is 0, and where they loop it seeds a period's rows round the
  loop.
- The block's count at +36 no longer comes round to `RR`, and +40 is
  $7FFFFFFF where the sets loop; the longs at +44 and +48 are the units
  before the loop and the units of the loop; +68 is unused; a decoder
  state's budget at +40 is never negated, and it has a phase and a mark
  at +42 and +44.
- On 64 rows of three two byte columns at `k` of 1 an advance is 676 to
  928 cycles where it was 658 to 1002, and on twenty columns 1112 where
  it was 1094: the mark's test on every refill, and no marked period.
  DTX2's code is 1448 bytes at `k` of 1 where it was 1244.

### 0.5.0, 2026-09-06

<https://github.com/odipar/DTX/releases/tag/v0.5.0>, built from the commit
tagged `v0.5.0`.

**A caller written against 0.4.0's state block has to read its size out
of the format block, as abi.md 3 has always said.** Every file reads as
before, and no tool's output changes but the state block's size.

- DTX2's advance walks the decoder states rather than indexing them: a
  refill reads its state's address out of the block, and the state records
  its ring's end, its budget and where its registers go at a loop. On 64
  rows of three two byte columns an advance is 658 to 1002 cycles where
  it was 1180 to 1358, and on twenty columns 1094 where it was 1616.
- A replayed pass puts each column's registers away, and puts them back,
  at that column's refill, in the period after the loop's row and the
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

- An advance leaves the address of the row's first value and nothing else.
  It left the row in `d0` as well, or $FFFFFFFF at the end. A jump reads a
  row number, and a caller counts its rows against the `R` and `RR` of
  `DTX_metadata`.
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
  two byte columns it costs 1432 cycles on average over 64 rows where
  0.3.0 cost 1512.
- R5.11 is the rule this adds: `RR` times the width divides by `k`, so row
  `RR` begins a unit of the column. A writer fails on a table that breaks
  it, naming the rule.
- The README has sections, a usage section and an attribution section.

### 0.3.0, 2026-09-06

<https://github.com/odipar/DTX/releases/tag/v0.3.0>, built from the commit
tagged `v0.3.0`.

**It reads 0.2.0's files, and 0.2.0 reads 0.3.0's.** 0.2.0 defined the
format, the four calls and the state block; the code behind the calls and
what the tools print are the two that moved.

What changed since 0.2.0:

- **The reader is smaller.** `DTX_payload` and the macro that installs a
  long moved into `68k/DTX_image.S`, where three templates include the one
  copy, and the six fields a combine writes stand at zero in the format
  block rather than being assembled in and then written over. DTX0's code
  is 208 bytes where it was 232, DTX1's 160 where it was 176, and DTX2's
  884 where it was 924. An image the packager assembles and one it reads
  from the build are the same bytes now, at every variant and width.
- **The three trees read alike.** A bare `-a`, a header with an `R` above
  2147483647, and code with no format block were each read or reported
  differently by the Java, Go and C# tools. The three now print one line for
  each, and a tool that cannot do the work prints the reason rather than a
  stack trace.
- **The documents read back what the code does.** A review of every file
  against the code it describes recorded 213 findings: the format block's
  place and size in the glossary, six slots where there are four, five fields
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
- **Four calls, and none of them copies a value.** An advance leaves the
  address of the row's first value and `DTX_metadata` reports the stride, so
  a caller reads the columns it needs where they stand. `DTX_read` and
  `DTX_take` are gone, the slots run to 16 bytes and the format block
  stands at +16 in 28 bytes.
- **Twenty-two images**, not eight: one for DTX0 at every width, one a
  width for DTX1, and one a width a unit with the copy code and without
  for DTX2.
- **Three tools**, not four: `dtx-rewrite` folded into `dtx-write`, which
  reads text or a DTX file and writes a DTX file or text.
- The reader is smaller and faster for it. On 64 rows of three two byte
  columns, DTX1's code is 176 bytes where it was 716, DTX2's 924 where it
  was 1476, and reading a value costs a caller the 12 or 16 cycles of one
  move.

### 0.1.0, 2026-09-05

<https://github.com/odipar/DTX/releases/tag/v0.1.0>, built from the commit
tagged `v0.1.0`. The first release, so this lists what there is rather than
what changed: DTX0, DTX1 and DTX2 as SPEC.md defined them at that tag, each
column of its width; one 68000 image a build, eight in one zip, under the
calling convention of abi.md; the four tools of tools.md, as executables for
the six platforms; and the ST4 packer carried in each tree, so a DTX2 table
packs with nothing installed beside the tools.
