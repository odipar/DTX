#!/usr/bin/env python3
"""The code a variant assembles to, against the table that follows it.

R, C and RR are the table's, not the reader's: an image holds the same
instructions at any of them, and its format block and its table are
what differ. This assembles a corpus a variant at a time and holds every image's
code to the first one's, byte for byte.

Under DTX2 the decoder is built for one unit and for copies or not, so k and
-copies may move the code and R, C and RR may not: those are grouped.

    python3 68k/test/emu/test_stable.py

Needs `mvn compile`, rmac on the path or at $RMAC, `pip install unicorn`, and
an ST4 packer at $ST4.
"""
import sys
sys.path.insert(0, '68k/test/emu')
import test_dtx as T

def code(variant, rows, width, rr, ring=960, unit=1, copies=False):
    csv = "\n".join(",".join(str((r * (i + 1)) % 97) for i in range(len(width)))
                    for r in range(rows)) + "\n"
    blob = T.write_table(csv, variant, width, rr, unit, ring, copies)
    image, _ = T.package(blob, copies)
    # The instructions alone. The six slots are constant, the format block at
    # +24 is data that states the table, and behind the code stand the column
    # table and the table itself, both of which move with C. The format block
    # says where the first of them begins, at +20 of it.
    import struct
    columns = struct.unpack(">I", image[24 + 20:24 + 24])[0]
    return image[48:columns]

TABLES = [
    ("R=64  C=2 widths 1,1  no repeat", 64, [1, 1], None),
    ("R=128 C=2 widths 1,1  no repeat", 128, [1, 1], None),
    ("R=512 C=2 widths 1,1  no repeat", 512, [1, 1], None),
    ("R=64  C=2 widths 1,1  RR=0", 64, [1, 1], 0),
    ("R=64  C=2 widths 1,1  RR=32", 64, [1, 1], 32),
    ("R=64  C=1 widths 1", 64, [1], None),
    ("R=64  C=3 widths 1,1,1", 64, [1, 1, 1], None),
    ("R=64  C=4 widths 1,2,4,1", 64, [1, 2, 4, 1], None),
    ("R=64  C=8 widths all 1", 64, [1] * 8, None),
]

# k and copies build the decoder, not the table: a DTX2 image may hold
# different code for each of them, and must not for R, C or RR.
bad = 0
for v in (0, 1, 2):
    print("DTX%d" % v)
    first, name0 = None, None
    for name, rows, width, rr in TABLES:
        c = code(v, rows, width, rr)
        if first is None:
            first, name0 = c, name
            print("    %-34s %4d bytes   the one to match" % (name, len(c)))
        elif c == first:
            print("    %-34s %4d bytes   the same code" % (name, len(c)))
        else:
            bad += 1
            n = sum(1 for x, y in zip(c, first) if x != y) if len(c) == len(first) else None
            print("    %-34s %4d bytes   DIFFERS (%s)" % (name, len(c),
                  "%d bytes" % n if n is not None else "%+d bytes long" % (len(c) - len(first))))
print()
print("%d tables assemble to code the base table does not" % bad)

print()
print("DTX2, one blob a decoder: k and copies may move it, R, C and RR may not")
# every width is a whole number of units, or no budget is one
CORPUS = {1: ((64, [1, 2], None), (128, [1, 2], None), (64, [1, 2], 0),
              (64, [1, 2, 1], None), (64, [1, 2, 4, 2], None)),
          2: ((64, [2, 2], None), (128, [2, 2], None), (64, [2, 2], 0),
              (64, [2, 4, 2], None)),
          4: ((64, [4, 4], None), (128, [4, 4], None), (64, [4, 4], 0),
              (64, [4, 4, 4], None))}
blob = {}
for unit in (1, 2, 4):
    for copies in (False, True):
        first, ok, seen = None, True, 0
        for rows, width, rr in CORPUS[unit]:
            c = code(2, rows, width, rr, 960, unit, copies)
            seen += 1
            if first is None:
                first = c
            elif c != first:
                ok = False
        if not ok:
            bad += 1
        blob[(unit, copies)] = first
        print("    k=%d %-14s %5d bytes over %d tables   %s"
              % (unit, "with copies" if copies else "without copies",
                 len(first), seen, "one blob" if ok else "STILL MOVES"))

# How many binaries a caller builds and ships. Every build the template can
# be assembled in is here, and no two of them are the same bytes.
print()
apart = len(set(bytes(c) for c in blob.values()))
print("    %d builds, %d of them different bytes" % (len(blob), apart))
if apart != len(blob):
    bad += 1
    print("    TWO BUILDS ARE THE SAME BLOB: one of them is not a binary")

# The copy code's size, which doc/abi.md 5 states a k.
cost = {k: len(blob[(k, True)]) - len(blob[(k, False)]) for k in (1, 2, 4)}
print("    the copy code: " + ", ".join("%d bytes at k=%d" % (cost[k], k)
                                        for k in (1, 2, 4)))
import re
said = re.search(r"copy code, which measures (\d+) bytes more at `k` of 1\n?"
                 r"\s*and 2 and (\d+) at `k` of 4", open("doc/abi.md").read())
if said is None:
    bad += 1
    print("    doc/abi.md 5 no longer states the copy code's size")
elif (int(said.group(1)), int(said.group(2))) != (cost[1], cost[4]):
    bad += 1
    print("    doc/abi.md 5 says %s and %s, not %d and %d"
          % (said.group(1), said.group(2), cost[1], cost[4]))
else:
    print("    doc/abi.md 5 states both, and both measure true")

print()
print("%d checks failed" % bad if bad else "every check passed")
sys.exit(1 if bad else 0)
