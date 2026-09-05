# releases

No release has been published. What one holds stands here, and each one
published lists below it.

## What a release holds

`release/publish.sh` writes `dist/release` (tools.md, Release):

- one zip a platform, over six: Windows, macOS and Linux, each on x64 and
  arm64. A zip holds the four tools as executables, and each executable
  holds the eight 68000 images inside it, so a caller who takes one takes
  the whole of what packaging needs
- the eight images beside the zips, one file a build, for a caller who
  takes an image and no tool (BINARIES.md)
- `MANIFEST.txt`: every file's size and sha256 beside what picks it, a
  variant, a unit and copies for an image and a platform for a zip, and the
  source commit the release was built from

The version names every file. It is read out of `pom.xml`, or given as the
script's one argument, and stands at `0.1-SNAPSHOT` until the first release
is cut.

The images are built from `68k/` by rmac on the machine that cuts the
release, and nowhere else: a caller who takes a release runs no assembler,
and no image is tracked in the tree.

## Published

None.
