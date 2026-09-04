#!/usr/bin/env python3
"""The packaged DTX0 and DTX1 reader, under emulation, against the bytes.

Every table is written by the Java tools, packaged by org.dtx.Packager and
assembled by rmac, then run on a plain 68000 through Unicorn. What a row
should hold is read out of the .dtx file here, by a reader that shares no
code with the one under test: this file parses the header and the payload
itself, as an independent reader would from doc/SPEC.md.

    python3 68k/test/emu/test_dtx.py

Needs `mvn compile`, rmac on the path or at $RMAC, and `pip install unicorn`.
"""

import os
import struct
import subprocess
import sys
import tempfile

from unicorn import Uc, UC_ARCH_M68K, UC_MODE_BIG_ENDIAN, UcError
from unicorn.m68k_const import (
    UC_CPU_M68K_M68000,
    UC_M68K_REG_A0, UC_M68K_REG_A1, UC_M68K_REG_A2, UC_M68K_REG_A3,
    UC_M68K_REG_A4, UC_M68K_REG_A5, UC_M68K_REG_A6, UC_M68K_REG_A7,
    UC_M68K_REG_D0, UC_M68K_REG_D1, UC_M68K_REG_D2, UC_M68K_REG_D3,
    UC_M68K_REG_D4, UC_M68K_REG_D5, UC_M68K_REG_D6, UC_M68K_REG_D7,
    UC_M68K_REG_PC,
)

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
CLASSES = os.path.join(ROOT, "target", "classes")
RMAC = os.environ.get("RMAC", "rmac")
ST4 = os.environ.get("ST4", "st4")

IMAGE = 0x10000          # the packaged image
STATE = 0x30000          # the state block the caller supplies
ROWBUF = 0x31000         # where a row goes
STACK = 0x40000          # the caller's stack
DONE = 0x50000           # the return address a call comes back to
GUARD = 0xCC             # what stands around a buffer, to catch an overrun

SLOT = {"init": 0, "metadata": 4, "jump": 8, "advance": 12, "read": 16}

A = [UC_M68K_REG_A0, UC_M68K_REG_A1, UC_M68K_REG_A2, UC_M68K_REG_A3,
     UC_M68K_REG_A4, UC_M68K_REG_A5, UC_M68K_REG_A6, UC_M68K_REG_A7]
D = [UC_M68K_REG_D0, UC_M68K_REG_D1, UC_M68K_REG_D2, UC_M68K_REG_D3,
     UC_M68K_REG_D4, UC_M68K_REG_D5, UC_M68K_REG_D6, UC_M68K_REG_D7]


# --------------------------------------------------------------------------
# An independent reader: doc/SPEC.md 1, 2.1 and 2.2, in Python.

def read_dtx(blob):
    """The header a DTX file states, and one row of bytes a row."""
    assert blob[:3] == b"DTX", "the file does not open with DTX"
    variant = blob[3]
    rows, columns, repeat = struct.unpack(">IHI", blob[4:14])
    width = list(blob[14:14 + columns])
    length = (14 + columns + 3) // 4 * 4
    payload = blob[length:]
    row_bytes = sum(width)
    out = []
    if variant == 0:
        for r in range(rows):
            out.append(payload[r * row_bytes:(r + 1) * row_bytes])
    elif variant == 1:
        at, next_at = [], 0
        for w in width:
            next_at = (next_at + 1) // 2 * 2
            at.append(next_at)
            next_at += rows * w
        for r in range(rows):
            row = b""
            for i, w in enumerate(width):
                row += payload[at[i] + r * w:at[i] + r * w + w]
            out.append(row)
    else:
        raise AssertionError("this rig reads DTX0 and DTX1, not %d" % variant)
    return variant, rows, columns, repeat, width, length, row_bytes, out


# --------------------------------------------------------------------------
# The tools.

def run(argv):
    done = subprocess.run(argv, capture_output=True, text=True)
    if done.returncode != 0:
        raise SystemExit("%s gave %s%s" % (argv[0], done.stdout, done.stderr))
    return done.stdout


def write_table(csv, variant, widths=None, repeat=None, unit=1, ring=960):
    """A .dtx file of `csv`, through the Java writer."""
    work = tempfile.mkdtemp(prefix="dtx68")
    text, out = os.path.join(work, "t.csv"), os.path.join(work, "t.dtx")
    with open(text, "w") as f:
        f.write(csv)
    argv = ["java", "-cp", CLASSES, "org.dtx.Write", text, out, "-v%d" % variant]
    if widths:
        argv.append("-w" + ",".join(str(w) for w in widths))
    if repeat is not None:
        argv.append("-r%d" % repeat)
    if variant == 2:
        argv += ["-k%d" % unit, "-m%d" % ring, "-p" + ST4]
    run(argv)
    with open(out, "rb") as f:
        return f.read()


def package(blob):
    """The raw image org.dtx.Packager and rmac make of `blob`."""
    work = tempfile.mkdtemp(prefix="dtx68")
    src, img = os.path.join(work, "t.dtx"), os.path.join(work, "t.bin")
    with open(src, "wb") as f:
        f.write(blob)
    run(["java", "-cp", CLASSES, "org.dtx.Packager", src, img, "-a" + RMAC])
    with open(img, "rb") as f:
        return f.read()


# --------------------------------------------------------------------------
# The machine.

class Machine:
    def __init__(self, image, state_bytes):
        self.mu = Uc(UC_ARCH_M68K, UC_MODE_BIG_ENDIAN)
        # A plain 68000, not the ColdFire Unicorn defaults to: ColdFire
        # dropped adda.w, which ST4's match path takes on its first call.
        self.mu.ctl_set_cpu_model(UC_CPU_M68K_M68000)
        for at, size in ((IMAGE, 0x10000), (STATE, 0x10000), (STACK, 0x10000),
                         (DONE & ~0xFFF, 0x1000)):
            self.mu.mem_map(at & ~0xFFF, size)
        self.mu.mem_write(IMAGE, image)
        self.state_bytes = state_bytes
        # rts at DONE would run on: an illegal word stops the emulation
        # instead, and every call is run to the DONE address explicitly.
        self.seed()

    def seed(self):
        self.mu.mem_write(STATE, b"\x00" * 0x1000)
        self.mu.mem_write(ROWBUF, bytes([GUARD]) * 0x100)

    def call(self, name, d0=0, a0=STATE, a1=ROWBUF):
        """One call through its slot, back at the sentinel."""
        mu = self.mu
        for r in D + A:
            mu.reg_write(r, 0)
        # d6, d7, a6 and the stack beyond the return address stand across a
        # call: seed them with a mark and check it after.
        mu.reg_write(UC_M68K_REG_D6, 0x6D6D6D6D)
        mu.reg_write(UC_M68K_REG_D7, 0x7D7D7D7D)
        mu.reg_write(UC_M68K_REG_A6, 0x00046000)
        mu.reg_write(UC_M68K_REG_D0, d0 & 0xFFFFFFFF)
        mu.reg_write(UC_M68K_REG_A0, a0)
        mu.reg_write(UC_M68K_REG_A1, a1)
        sp = STACK + 0x8000
        mu.mem_write(sp - 4, struct.pack(">I", DONE))
        mu.reg_write(UC_M68K_REG_A7, sp - 4)
        try:
            mu.emu_start(IMAGE + SLOT[name], DONE, count=2_000_000)
        except UcError as bad:
            raise AssertionError("%s faulted at %08x: %s"
                                 % (name, mu.reg_read(UC_M68K_REG_PC), bad))
        assert mu.reg_read(UC_M68K_REG_D6) == 0x6D6D6D6D, name + " moved d6"
        assert mu.reg_read(UC_M68K_REG_D7) == 0x7D7D7D7D, name + " moved d7"
        assert mu.reg_read(UC_M68K_REG_A6) == 0x00046000, name + " moved a6"
        return {"d0": mu.reg_read(UC_M68K_REG_D0),
                "d1": mu.reg_read(UC_M68K_REG_D1),
                "d2": mu.reg_read(UC_M68K_REG_D2),
                "a0": mu.reg_read(UC_M68K_REG_A0),
                "a1": mu.reg_read(UC_M68K_REG_A1)}

    def row(self, wrote, row_bytes):
        """What a read left in the row buffer, and nothing past it."""
        out = bytes(self.mu.mem_read(ROWBUF, row_bytes))
        past = bytes(self.mu.mem_read(ROWBUF + row_bytes, 8))
        assert past == bytes([GUARD]) * 8, "a read wrote past the row"
        assert wrote == ROWBUF + row_bytes, \
            "a read left a1 at %08x, not %08x" % (wrote, ROWBUF + row_bytes)
        return out


# --------------------------------------------------------------------------

def check(name, csv, variant, widths=None, repeat=None, unit=1, ring=960):
    blob = write_table(csv, variant, widths, repeat, unit, ring)
    if variant == 2:
        # The table is the same under every variant (R1.3), so what a row
        # holds is read out of the plain file, by the reader in this rig.
        plain = write_table(csv, 1, widths, repeat)
        _, rows, columns, rr, width, _, row_bytes, want = read_dtx(plain)
        kind = 2
    else:
        kind, rows, columns, rr, width, _, row_bytes, want = read_dtx(blob)
    image = package(blob)

    # the format block, doc/abi.md 1, at the image's byte 20
    fmt = image[20:44]
    assert fmt[:3] == b"DTX" and fmt[3] == kind, "the format block's variant"
    state_bytes, header_at = struct.unpack(">II", fmt[4:12])
    stated_row, p, n = struct.unpack(">HHH", fmt[12:18])
    assert stated_row == row_bytes, "the format block's row bytes"
    if kind == 2:
        assert p >= columns, "P is at least C"
        assert n == ring and fmt[18] == unit, "N and k the payload states"
        for w in set(width):
            assert n % (p * w) == 0, "N divides by P times %d" % w
            assert n >= 2 * p * w, "N is at least twice P times %d" % w
            assert (p * w) % unit == 0, "the budget of a %d byte column" % w
    else:
        assert p == 1 and n == 0 and fmt[18] == 0, \
            "P, N and k under a plain variant"
    assert image[header_at:header_at + 3] == b"DTX", "the header the block points at"

    m = Machine(image, state_bytes)

    # metadata may be called before init
    got = m.call("metadata")
    assert got["d0"] == rows, "metadata gave R = %d" % got["d0"]
    assert got["d1"] & 0xFFFF == columns, "metadata gave C"
    assert got["d2"] == rr, "metadata gave RR"
    assert got["a0"] == IMAGE + 20, "metadata gave the format block"
    assert got["a1"] == IMAGE + header_at, "metadata gave the header"

    m.call("init")

    # a read before the first advance writes nothing and gives a1 back
    got = m.call("read")
    assert got["a1"] == ROWBUF, "a read on no row moved a1"
    assert bytes(m.mu.mem_read(ROWBUF, row_bytes)) == bytes([GUARD]) * row_bytes, \
        "a read on no row wrote bytes"

    # every row, advance then read
    for r in range(rows):
        got = m.call("advance")
        assert got["d0"] == r, "advance gave row %d, not %d" % (got["d0"], r)
        got = m.call("read")
        assert m.row(got["a1"], row_bytes) == want[r], \
            "row %d read %s, not %s" % (r, m.row(got["a1"], row_bytes).hex(),
                                        want[r].hex())
        # a second read of one row gives the same bytes
        m.mu.mem_write(ROWBUF, bytes([GUARD]) * 0x100)
        got = m.call("read")
        assert m.row(got["a1"], row_bytes) == want[r], "a second read differed"

    # the end
    got = m.call("advance")
    if rr >= rows:
        assert got["d0"] == 0xFFFFFFFF, "the end gave %08x" % got["d0"]
        got = m.call("advance")
        assert got["d0"] == 0xFFFFFFFF, "the end is not sticky"
        got = m.call("read")
        assert m.row(got["a1"], row_bytes) == want[rows - 1], \
            "a read after the end gave another row"
    else:
        assert got["d0"] == rr, "the repeat gave row %d, not %d" % (got["d0"], rr)
        got = m.call("read")
        assert m.row(got["a1"], row_bytes) == want[rr], "the repeat's row"

    # a jump to every row, and a read of it
    for r in list(range(rows)) + list(reversed(range(rows))):
        got = m.call("jump", d0=r)
        assert got["d0"] == r, "jump gave %d, not %d" % (got["d0"], r)
        m.mu.mem_write(ROWBUF, bytes([GUARD]) * 0x100)
        got = m.call("read")
        assert m.row(got["a1"], row_bytes) == want[r], \
            "the row after a jump to %d" % r
        # the row after the one jumped to
        if r + 1 < rows:
            got = m.call("advance")
            assert got["d0"] == r + 1, "advance after a jump"
            m.mu.mem_write(ROWBUF, bytes([GUARD]) * 0x100)
            got = m.call("read")
            assert m.row(got["a1"], row_bytes) == want[r + 1], \
                "the row after a jump and an advance"

    print("  %-40s DTX%d  R=%-5d C=%-3d row=%-3d P=%-4d state=%-6d image=%d"
          % (name, kind, rows, columns, row_bytes, p, state_bytes, len(image)))


TABLES = [
    ("one column of one byte", "1\n2\n3\n4\n5\n", [1], None),
    ("three widths, an odd row", "1,300,-2\n2,301,-1\n3,302,0\n", [1, 2, 1], None),
    ("a row that divides by four", "1,2\n3,4\n5,6\n7,8\n", [2, 2], None),
    ("a four byte column", "1,2,3\n4,5,6\n", [1, 2, 4], None),
    ("every width, widest first", "1,2,3\n4,5,6\n", [4, 2, 1], None),
    ("a table that repeats", "1\n2\n3\n4\n5\n6\n", [1], 2),
    ("a repeat at row 0", "10\n20\n30\n40\n", [1], 0),
    ("one row", "7,8\n", [1, 4], None),
    ("a wide row", ",".join(str(i) for i in range(20)) + "\n"
                   + ",".join(str(i + 1) for i in range(20)) + "\n",
     [1] * 20, None),
    ("a long table", "\n".join("%d,%d" % (i % 251, i % 65521)
                              for i in range(300)) + "\n", [1, 2], None),
]


# Tables a DTX2 image is made of: P is at least C and at most R, and N
# divides by P times every width, so C stays small beside R.
def numbers(rows, columns, span=251):
    return "\n".join(",".join(str((r * (i + 1)) % span) for i in range(columns))
                     for r in range(rows)) + "\n"


PACKED = [
    ("one byte a row", numbers(64, 1), [1], None, 1, 960),
    ("two columns, one and two bytes", numbers(64, 2), [1, 2], None, 1, 960),
    ("three columns", numbers(48, 3), [1, 2, 1], None, 1, 960),
    ("a four byte column", numbers(64, 3), [1, 2, 4], None, 1, 960),
    ("k of 2", numbers(64, 2), [2, 2], None, 2, 960),
    ("k of 4", numbers(64, 2), [4, 4], None, 4, 960),
    ("a table that repeats", numbers(64, 2), [1, 1], 16, 1, 960),
    ("a repeat at row 0", numbers(48, 2), [1, 1], 0, 1, 960),
    ("R not a multiple of P", numbers(50, 3), [1, 1, 1], None, 1, 960),
    ("a small ring", numbers(64, 2), [1, 1], None, 1, 64),
    ("a long table", numbers(600, 2), [1, 2], None, 1, 960),
]


def main():
    if not os.path.isdir(CLASSES):
        raise SystemExit("run `mvn compile` first: no " + CLASSES)
    bad = 0
    for variant in (0, 1):
        print("DTX%d" % variant)
        for name, csv, widths, repeat in TABLES:
            try:
                check(name, csv, variant, widths, repeat)
            except AssertionError as wrong:
                bad += 1
                print("  %-40s FAILED: %s" % (name, wrong))
    print("DTX2")
    for name, csv, widths, repeat, unit, ring in PACKED:
        try:
            check(name, csv, 2, widths, repeat, unit, ring)
        except AssertionError as wrong:
            bad += 1
            print("  %-40s FAILED: %s" % (name, wrong))
    if bad:
        raise SystemExit("%d checks failed" % bad)
    print("every check passed")


if __name__ == "__main__":
    main()
