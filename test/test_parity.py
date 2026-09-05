#!/usr/bin/env python3
"""The Java packager and the Go one, against each other, byte for byte.

One table has one image. The two hold the same eight built images and put
the same figures into the format block, so a table packaged either way is
the same file: a caller who takes the Go executable and a caller who takes
the jar read the same bytes on a 68000.

    python3 test/test_parity.py

Needs `mvn package`, Go on the path, and an ST4 packer at $ST4 for the
packed tables.
"""
import os
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
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


def main():
    work = tempfile.mkdtemp(prefix="dtxparity")
    tool = os.path.join(work, "dtx-package")
    run(["go", "build", "-o", tool, "./cmd/dtx-package"],
        cwd=os.path.join(ROOT, "go"))
    classes = os.path.join(ROOT, "target", "classes")
    bad = 0
    for name, variant, csv, width, rr, unit, ring, copies in TABLES:
        blob = T.write_table(csv, variant, width, rr, unit, ring, copies)
        src = os.path.join(work, "t.dtx")
        with open(src, "wb") as f:
            f.write(blob)
        # Neither tool takes a word for it: the payload states whether
        # its columns hold copies (R5.10) and both read it out of the file.
        java = os.path.join(work, "java.bin")
        go = os.path.join(work, "go.bin")
        run(["java", "-cp", classes, "org.dtx.Packager", src, java])
        run([tool, src, go])
        with open(java, "rb") as f:
            one = f.read()
        with open(go, "rb") as f:
            other = f.read()
        if one == other:
            print("  %-28s %5d bytes   the same image" % (name, len(one)))
            continue
        bad += 1
        if len(one) != len(other):
            print("  %-28s DIFFER: java %d bytes, go %d"
                  % (name, len(one), len(other)))
            continue
        at = [i for i in range(len(one)) if one[i] != other[i]]
        print("  %-28s DIFFER: %d bytes, the first at %d"
              % (name, len(at), at[0]))
    print()
    print("%d tables package to two images" % bad if bad
          else "every table packages to one image")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
