#!/usr/bin/env python3
"""The Java tools and the Go ones, against each other, byte for byte.

One input has one output. The two trees write the same DTX files, rewrite
them the same way, build the same eight images and combine the same
packages, so a caller who takes the Go executables and a caller who takes
the jar hold the same bytes at every step.

    python3 test/test_parity.py

Needs `mvn package`, Go on the path, rmac on it or at $RMAC, and an ST4
packer at $ST4 for the packed tables.
"""
import os
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RMAC = os.environ.get("RMAC", "rmac")
ST4 = os.environ.get("ST4", "st4")
sys.path.insert(0, os.path.join(ROOT, "68k", "test", "emu"))
import test_dtx as T  # noqa: E402  the writer and its ST4 packer


def run(argv, **at):
    done = subprocess.run(argv, capture_output=True, text=True, **at)
    if done.returncode != 0:
        raise SystemExit("%s gave %s%s" % (argv[0], done.stdout, done.stderr))
    return done.stdout


def numbers(rows, columns):
    return "\n".join(",".join(str((r * (i + 1)) % 97) for i in range(columns))
                     for r in range(rows)) + "\n"


# One a variant and, under DTX2, a build of the decoder: the packager picks
# an image by the variant and by k and copies, so every pick is tried here.
TABLES = [
    ("DTX0", 0, numbers(64, 3), [1, 2, 4], None, 1, 960, False),
    ("DTX0, one column", 0, numbers(9, 1), [1], None, 1, 960, False),
    ("DTX0, a table that repeats", 0, numbers(64, 2), [1, 1], 32, 1, 960, False),
    ("DTX1", 1, numbers(64, 3), [1, 2, 4], None, 1, 960, False),
    ("DTX1, one row", 1, numbers(1, 2), [1, 4], None, 1, 960, False),
    ("DTX1, a repeat at row 0", 1, numbers(64, 2), [2, 2], 0, 1, 960, False),
    ("DTX2, k of 1", 2, numbers(64, 3), [1, 2, 4], None, 1, 960, False),
    ("DTX2, k of 2", 2, numbers(64, 2), [2, 2], None, 2, 960, False),
    ("DTX2, k of 4", 2, numbers(64, 2), [4, 4], None, 4, 960, False),
    ("DTX2, k of 1 with copies", 2, numbers(64, 2), [1, 2], None, 1, 960, True),
    ("DTX2, k of 2 with copies", 2, numbers(64, 2), [2, 2], None, 2, 960, True),
    ("DTX2, k of 4 with copies", 2, numbers(64, 2), [4, 4], None, 4, 960, True),
    ("DTX2, a table that repeats", 2, numbers(64, 2), [1, 1], 32, 1, 960, False),
    ("DTX2, a ring of 480", 2, numbers(64, 2), [1, 2], None, 1, 480, False),
]


# What each tool is written as, one tree and the other. A Go command and a
# Java class take the same arguments and write the same file.
TOOLS = {"write": "org.dtx.Write", "rewrite": "org.dtx.Rewrite",
         "package": "org.dtx.Packager", "blobs": "org.dtx.Blobs"}


def both(work, classes, tool, argv, out):
    """One tool run each way, at `out`.java and `out`.go. Gives the two."""
    java = os.path.join(work, out + ".java")
    go = os.path.join(work, out + ".go")
    run(["java", "-cp", classes, TOOLS[tool]]
        + [java if a is None else a for a in argv])
    run([os.path.join(work, "dtx-" + tool)]
        + [go if a is None else a for a in argv])
    with open(java, "rb") as f:
        one = f.read()
    with open(go, "rb") as f:
        other = f.read()
    return one, other


def report(name, one, other):
    """Prints how the two compare, and gives 1 where they differ."""
    if one == other:
        print("  %-30s %6d bytes   the same" % (name, len(one)))
        return 0
    if len(one) != len(other):
        print("  %-30s DIFFER: java %d bytes, go %d"
              % (name, len(one), len(other)))
        return 1
    at = [i for i in range(len(one)) if one[i] != other[i]]
    print("  %-30s DIFFER: %d bytes, the first at %d"
          % (name, len(at), at[0]))
    return 1


def main():
    work = tempfile.mkdtemp(prefix="dtxparity")
    for tool in TOOLS:
        run(["go", "build", "-o", os.path.join(work, "dtx-" + tool),
             "./cmd/dtx-" + tool], cwd=os.path.join(ROOT, "go"))
    classes = os.path.join(ROOT, "target", "classes")
    bad = 0

    print("dtx-write: the same text into the same file")
    text = os.path.join(work, "t.csv")
    with open(text, "w") as f:
        f.write("# a comment, and a blank line\n\n"
                + "\n".join("%d,$%X,-%d" % (r % 97, (r * 7) % 65535, r % 40)
                             for r in range(64)) + "\n")
    for name, argv in [
            ("DTX0, widths inferred", ["-v0"]),
            ("DTX0, widths given", ["-v0", "-w1,4,2"]),
            ("DTX1, a repeat", ["-v1", "-w1,4,2", "-r32"]),
            ("DTX2, k of 1", ["-v2", "-w1,4,2", "-k1", "-m960"]),
            ("DTX2, k of 2", ["-v2", "-w2,4,2", "-k2", "-m960"]),
            ("DTX2, k of 4", ["-v2", "-w4,4,4", "-k4", "-m960"]),
            ("DTX2, with copies", ["-v2", "-w1,4,2", "-k1", "-m960",
                                   "-copies"])]:
        one, other = both(work, classes, "write",
                          [text, None] + argv + ["-p" + ST4], "w")
        bad += report(name, one, other)

    print()
    print("dtx-rewrite: the same plain file into the same DTX2 one")
    plain = os.path.join(work, "plain.dtx")
    run(["java", "-cp", classes, "org.dtx.Write", text, plain, "-v1",
         "-w1,4,2", "-r32"])
    for name, argv in [("k of 1", ["-k1", "-m960"]),
                       ("k of 2", ["-k2", "-m960"]),
                       ("with copies", ["-k1", "-m960", "-copies"])]:
        one, other = both(work, classes, "rewrite",
                          [plain, None] + argv + ["-p" + ST4], "r")
        bad += report(name, one, other)

    print()
    print("dtx-blobs: the same eight images")
    java = os.path.join(work, "blobs.java")
    go = os.path.join(work, "blobs.go")
    run(["java", "-cp", classes, "org.dtx.Blobs", java, "-a" + RMAC])
    run([os.path.join(work, "dtx-blobs"), go, "-a" + RMAC])
    for image in sorted(os.listdir(java)):
        with open(os.path.join(java, image), "rb") as f:
            one = f.read()
        with open(os.path.join(go, image), "rb") as f:
            other = f.read()
        bad += report(image, one, other)

    print()
    print("dtx-package: the same table into the same image")
    for name, variant, csv, width, rr, unit, ring, copies in TABLES:
        blob = T.write_table(csv, variant, width, rr, unit, ring, copies)
        src = os.path.join(work, "t.dtx")
        with open(src, "wb") as f:
            f.write(blob)
        # Neither tool takes a word for it: the payload states whether
        # its columns hold copies (R5.10) and both read it out of the file.
        one, other = both(work, classes, "package", [src, None], "p")
        bad += report(name, one, other)
    print()
    print("%d runs give two files" % bad if bad
          else "every run gives one file")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
