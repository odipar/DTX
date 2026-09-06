#!/usr/bin/env python3
"""The DTX reader, under emulation, against the text a table came from.

Two kinds of check run here.

THE CALLS. Every table is written by the Java tools, packaged by
org.dtx.Packager, assembled by rmac and run on a plain 68000 through
Unicorn: every row through advance, a jump to every row forward and
backward, the repeat at RR, the sticky end, metadata before init, the
registers that stand across a call, and every word or long on its
alignment.

THE ROUND TRIP. The same text through the writer, the packager and the
68000 at DTX0, DTX1 and DTX2, compared with the rows the text defines and
with one another (R1.3). What a row should be is worked out in this file,
from the text, by a reader that does not share code with the one under
test - so neither the writer nor the 68000 is checked against itself.
Under DTX2 it also counts the decoder's calls, the one thing a wrong
stopping rule shows up in: the output does not change when a column is
driven one call past its end marker, but the count does.

    python3 68k/test/emu/test_dtx.py

Needs `mvn process-classes` with rmac on the path or at $RMAC, `pip
install unicorn`, and $ST4 to pack with a packer other than the carried
one.
"""

import os
import struct
import subprocess
import sys
import tempfile

from unicorn import (Uc, UC_ARCH_M68K, UC_HOOK_CODE, UC_HOOK_MEM_READ,
                     UC_HOOK_MEM_WRITE, UC_MODE_BIG_ENDIAN, UcError)
from unicorn.m68k_const import (
    UC_CPU_M68K_M68000,
    UC_M68K_REG_A0, UC_M68K_REG_A1, UC_M68K_REG_A2, UC_M68K_REG_A3,
    UC_M68K_REG_A4, UC_M68K_REG_A5, UC_M68K_REG_A6, UC_M68K_REG_A7,
    UC_M68K_REG_D0, UC_M68K_REG_D1, UC_M68K_REG_D2, UC_M68K_REG_D3,
    UC_M68K_REG_D4, UC_M68K_REG_D5, UC_M68K_REG_D6, UC_M68K_REG_D7,
    UC_M68K_REG_PC, UC_M68K_REG_SR,
)

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
CLASSES = os.path.join(ROOT, "target", "classes")
RMAC = os.environ.get("RMAC", "rmac")
ST4 = os.environ.get("ST4")

IMAGE = 0x10000          # the packaged image
STATE = 0x30000          # the state block the caller supplies
STACK = 0x40000          # the caller's stack
DONE = 0x50000           # the return address a call comes back to


SLOT = {"init": 0, "metadata": 4, "jump": 8, "advance": 12}

A = [UC_M68K_REG_A0, UC_M68K_REG_A1, UC_M68K_REG_A2, UC_M68K_REG_A3,
     UC_M68K_REG_A4, UC_M68K_REG_A5, UC_M68K_REG_A6, UC_M68K_REG_A7]
D = [UC_M68K_REG_D0, UC_M68K_REG_D1, UC_M68K_REG_D2, UC_M68K_REG_D3,
     UC_M68K_REG_D4, UC_M68K_REG_D5, UC_M68K_REG_D6, UC_M68K_REG_D7]


# --------------------------------------------------------------------------
# An independent reader: doc/SPEC.md 1, 2.1 and 2.2, in Python.

def read_dtx(blob):
    """The header of a DTX file, and one row of bytes a row."""
    assert blob[:3] == b"DTX", "the file does not open with DTX"
    variant = blob[3]
    rows, columns, repeat = struct.unpack(">IHI", blob[4:14])
    width = blob[14]
    length = 16
    payload = blob[length:]
    row_bytes = columns * width
    out = []
    if variant == 0:
        for r in range(rows):
            out.append(payload[r * row_bytes:(r + 1) * row_bytes])
    elif variant == 1:
        stride = (rows * width + 1) // 2 * 2
        for r in range(rows):
            row = b""
            for i in range(columns):
                at = i * stride + r * width
                row += payload[at:at + width]
            out.append(row)
    else:
        raise AssertionError("this rig reads DTX0 and DTX1, not %d" % variant)
    return variant, rows, columns, repeat, width, length, row_bytes, out


# --------------------------------------------------------------------------
# An independent CSV reader: doc/tools.md, "The text", in Python. The rows a
# table should give come out of the text the writer was given, not out of the
# file it wrote.

def csv_values(csv):
    """The values of `csv`, a row a line, as whole numbers."""
    out, columns = [], -1
    for at, line in enumerate(csv.split("\n"), 1):
        read = line.strip()
        if not read or read.startswith("#"):
            continue
        cell = [c.strip() for c in read.split(",")]
        if columns < 0:
            columns = len(cell)
        assert len(cell) == columns, \
            "line %d has %d values, not %d" % (at, len(cell), columns)
        row = []
        for c in cell:
            if c.startswith("$"):
                row.append(int(c[1:], 16))
            elif c.startswith("-$"):
                row.append(-int(c[2:], 16))
            else:
                row.append(int(c))
        out.append(row)
    assert out, "the text does not contain a row"
    return out


def fits(value, width):
    """Whether `value` lies from -2^(8W-1) to 2^(8W)-1."""
    return -(1 << (8 * width - 1)) <= value <= (1 << (8 * width)) - 1


def csv_width(values):
    """The narrowest width of 1, 2 and 4 that takes every value of the table."""
    taken = 1
    for row in values:
        for one in row:
            while not fits(one, taken):
                assert taken != 4, "%d does not take a width" % one
                taken = 2 if taken == 1 else 4
    return taken


def csv_rows(csv, width=None):
    """What each row of `csv` is, as the bytes a read writes out."""
    values = csv_values(csv)
    width = width or csv_width(values)
    out = []
    for row in values:
        bytes_out = b""
        for one in row:
            assert fits(one, width), \
                "%d does not fit %d bytes" % (one, width)
            bytes_out += (one & ((1 << (8 * width)) - 1)).to_bytes(width, "big")
        out.append(bytes_out)
    return width, out


# --------------------------------------------------------------------------
# The tools.

def run(argv):
    done = subprocess.run(argv, capture_output=True, text=True)
    if done.returncode != 0:
        raise SystemExit("%s gave %s%s" % (argv[0], done.stdout, done.stderr))
    return done.stdout


def write_table(csv, variant, width=None, repeat=None, unit=1, ring=960,
                copies=False):
    """A .dtx file of `csv`, through the Java writer."""
    work = tempfile.mkdtemp(prefix="dtx68")
    text, out = os.path.join(work, "t.csv"), os.path.join(work, "t.dtx")
    with open(text, "w") as f:
        f.write(csv)
    argv = ["java", "-cp", CLASSES, "org.dtx.Write", text, out, "-v%d" % variant]
    if width:
        argv.append("-w%d" % width)
    if repeat is not None:
        argv.append("-r%d" % repeat)
    if variant == 2:
        argv += ["-k%d" % unit, "-m%d" % ring]
        # The tree contains a copy of ST4, so no packer stands beside it. $ST4
        # names one to pack with instead of the carried one.
        if ST4:
            argv.append("-p" + ST4)
        if copies:
            argv.append("-copies")
    run(argv)
    with open(out, "rb") as f:
        return f.read()


def package(blob):
    """The raw image the packager makes, and where every label of it stands.

    The image comes from the packager, which combines the code the build made
    from 68k/DTX*.S: the path a caller takes. A run of rmac over the template
    gives the same bytes, and BlobTest checks that.

    The labels come from a run of rmac over the same template and the same
    figures, for the listing's symbol table alone: the rig counts the decoder's
    calls at ST4_resume's address in it.
    """
    work = tempfile.mkdtemp(prefix="dtx68")
    src = os.path.join(work, "t.dtx")
    img = os.path.join(work, "t.bin")
    lst = os.path.join(work, "t.lst")
    with open(src, "wb") as f:
        f.write(blob)
    # No -copies: the payload defines it (R5.10), so the packager reads
    # which decoder the table needs out of the file.
    run(["java", "-cp", CLASSES, "org.dtx.Packager", src, img])
    run(["java", "-cp", CLASSES, "org.dtx.Packager", src,
         os.path.join(work, "DTX_table.i"), "-s"])
    run([RMAC, "-m68000", "-fr", "+o3", "-i" + work,
         "-i" + os.path.join(ROOT, "68k"), "-l*" + lst,
         "-o", os.path.join(work, "code.bin"),
         os.path.join(ROOT, "68k", "DTX%d.S" % blob[3])])
    at = {}
    for line in open(lst):
        # rmac lays its symbol table in as many columns as the page fits,
        # so a line has one name, address and kind or several.
        cell = line.split()
        for c in range(0, len(cell) - 2, 3):
            if len(cell[c + 1]) == 16 and cell[c + 2] in ("t", "d", "b"):
                try:
                    at[cell[c]] = int(cell[c + 1], 16)
                except ValueError:
                    pass
    with open(img, "rb") as f:
        return f.read(), at


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
        # A 68000 takes an address error on a word or long at an odd
        # address, and Unicorn's model does not: it reads and writes the
        # bytes. So the rig watches every access itself, and a misaligned one
        # is a fault here as it is on the hardware.
        self.misaligned = []
        self.mu.hook_add(UC_HOOK_MEM_READ | UC_HOOK_MEM_WRITE, self._aligned)
        # every call runs until the pc reaches DONE, the return address the
        # rig pushes
        self.seed()

    def _aligned(self, mu, access, address, size, value, data):
        if size > 1 and address & 1:
            self.misaligned.append((mu.reg_read(UC_M68K_REG_PC), address, size))

    def seed(self):
        self.mu.mem_write(STATE, b"\x00" * max(0x1000, self.state_bytes))


    def call(self, name, d0=0, a0=STATE):
        """One call through its slot, back at the sentinel."""
        mu = self.mu
        for r in D + A:
            mu.reg_write(r, 0)
        # d6, d7 and a6 stand across a call: seed them with a mark and check
        # it after.
        mu.reg_write(UC_M68K_REG_D6, 0x6D6D6D6D)
        mu.reg_write(UC_M68K_REG_D7, 0x7D7D7D7D)
        mu.reg_write(UC_M68K_REG_A6, 0x00046000)
        mu.reg_write(UC_M68K_REG_D0, d0 & 0xFFFFFFFF)
        mu.reg_write(UC_M68K_REG_A0, a0)
        sp = STACK + 0x8000
        mu.mem_write(sp - 4, struct.pack(">I", DONE))
        mu.reg_write(UC_M68K_REG_A7, sp - 4)
        try:
            mu.emu_start(IMAGE + SLOT[name], DONE, count=2_000_000)
        except UcError as bad:
            raise AssertionError("%s faulted at %08x: %s"
                                 % (name, mu.reg_read(UC_M68K_REG_PC), bad))
        assert not self.misaligned, "%s took a word or long at an odd" \
            " address, which a 68000 faults on: pc %08x, address %08x, %d bytes" \
            % ((name,) + self.misaligned[0])
        assert mu.reg_read(UC_M68K_REG_D6) == 0x6D6D6D6D, name + " moved d6"
        assert mu.reg_read(UC_M68K_REG_D7) == 0x7D7D7D7D, name + " moved d7"
        assert mu.reg_read(UC_M68K_REG_A6) == 0x00046000, name + " moved a6"
        return {"d0": mu.reg_read(UC_M68K_REG_D0),
                "d1": mu.reg_read(UC_M68K_REG_D1),
                "d2": mu.reg_read(UC_M68K_REG_D2),
                "d3": mu.reg_read(UC_M68K_REG_D3),
                "a0": mu.reg_read(UC_M68K_REG_A0),
                "a1": mu.reg_read(UC_M68K_REG_A1)}

    def count(self, at):
        """A counter of how often the instruction at `at` is reached."""
        hits = [0]
        self.mu.hook_add(UC_HOOK_CODE,
                         lambda u, a, s, d: hits.__setitem__(0, hits[0] + 1),
                         begin=at, end=at)
        return hits

    def row(self, at, columns, stride, width):
        """The row an advance points at, as the caller reads it: one value
        from the pointer, and the next a stride further on."""
        out = b""
        for i in range(columns):
            out += bytes(self.mu.mem_read(at + i * stride, width))
        return out


# --------------------------------------------------------------------------

def check(name, csv, variant, width=None, repeat=None, unit=1, ring=960):
    blob = write_table(csv, variant, width, repeat, unit, ring)
    if variant == 2:
        # The table is the same under every variant (R1.3), so what a row
        # is, is read out of the plain file, by the reader in this rig.
        plain = write_table(csv, 1, width, repeat)
        _, rows, columns, rr, width, _, row_bytes, want = read_dtx(plain)
        kind = 2
    else:
        kind, rows, columns, rr, width, _, row_bytes, want = read_dtx(blob)
    image, _ = package(blob)

    # the format block, doc/abi.md 1, behind the four slots
    fmt = image[16:16 + 28]
    assert fmt[:3] == b"DTX" and fmt[3] == kind, "the format block's variant"
    state_bytes, header_at = struct.unpack(">II", fmt[4:12])
    defined_row, p, n = struct.unpack(">HHH", fmt[12:18])
    stride = struct.unpack(">I", fmt[24:28])[0]
    assert defined_row == row_bytes, "the format block's row bytes"
    want_stride = {0: width, 1: (rows * width + 1) // 2 * 2}.get(kind, n)
    assert stride == want_stride, \
        "the format block gives a stride of %d, not %d" % (stride, want_stride)
    if kind == 2:
        assert p >= columns, "P is at least C"
        assert n == ring and fmt[18] == unit, "N and k the payload defines"
        assert fmt[19] == width, "the width the code was built for"
        assert n % (p * width) == 0, "N divides by P times the width"
        assert n >= 2 * p * width, "N is at least twice P times the width"
        assert (p * width) % unit == 0, "the budget is a whole number of units"
    else:
        assert p == 1 and n == 0 and fmt[18] == 0, \
            "P, N and k under a plain variant"
        assert fmt[19] == (0 if kind == 0 else width), \
            "the width the code was built for"
    assert image[header_at:header_at + 3] == b"DTX", "the header the block points at"

    m = Machine(image, state_bytes)

    # metadata may be called before init
    got = m.call("metadata")
    assert got["d0"] == rows, "metadata gave R = %d" % got["d0"]
    assert got["d1"] & 0xFFFF == columns, "metadata gave C"
    assert got["d2"] == rr, "metadata gave RR"
    assert got["d3"] == stride, "metadata gave a stride of %d, not %d" \
        % (got["d3"], stride)
    assert got["a0"] == IMAGE + 16, "metadata gave the format block"
    assert got["a1"] == IMAGE + header_at, "metadata gave the header"

    m.call("init")

    def values(got):
        """The row an advance or a jump points at, read as a caller reads
        it: a1 the first value, and a stride to the next column's."""
        return m.row(got["a1"], columns, stride, width)

    # every row, one advance each. No call gives a row number: the caller
    # counts, as doc/abi.md 2 defines.
    for r in range(rows):
        got = m.call("advance")
        assert values(got) == want[r], \
            "row %d reads %s, not %s" % (r, values(got).hex(), want[r].hex())

    # A table that repeats, under DTX2: every data set loops at RR (R5.11),
    # so the rows come round from there and nothing is decoded twice. Where
    # the table does not repeat the set loops on its last unit, which is a
    # row only where the unit divides the width, so what stands past the
    # last row is not defined and the caller stops (abi.md 7). Under DTX0
    # and DTX1 an advance past the last row reads past the table.
    if kind == 2 and rr < rows:
        for pass_ in range(2):
            for r in range(rr, rows):
                got = m.call("advance")
                assert values(got) == want[r], \
                    "the rows past the last, pass %d row %d" % (pass_, r)

    # a jump to every row, forward and backward
    for r in list(range(rows)) + list(reversed(range(rows))):
        got = m.call("jump", d0=r)
        assert values(got) == want[r], "the row after a jump to %d" % r
        # the row after the one jumped to
        if r + 1 < rows:
            got = m.call("advance")
            assert values(got) == want[r + 1], \
                "the row after a jump and an advance"

    # the row an advance gives stands until the next advance (doc/abi.md 2):
    # under DTX2 the refill that would write over it is P advances away
    m.call("init")
    before = None
    for r in range(rows):
        got = m.call("advance")
        if before is not None:
            assert m.row(before, columns, stride, width) == want[r - 1], \
                "row %d moved under the caller" % (r - 1)
        before = got["a1"]

    print("  %-40s DTX%d  R=%-5d C=%-3d W=%d row=%-3d P=%-4d state=%-6d image=%d"
          % (name, kind, rows, columns, width, row_bytes, p, state_bytes,
             len(image)))


TABLES = [
    ("one column of one byte", "1\n2\n3\n4\n5\n", 1, None),
    ("an odd row count, one byte", "1,3,2\n2,4,1\n3,5,0\n", 1, None),
    ("two byte values", "1,300\n2,301\n3,302\n", 2, None),
    ("four byte values", "1,2,3\n4,5,6\n", 4, None),
    ("a table that repeats", "1\n2\n3\n4\n5\n6\n", 1, 2),
    ("a repeat at row 0", "10\n20\n30\n40\n", 1, 0),
    ("one row", "7,8\n", 4, None),
    ("a wide row", ",".join(str(i) for i in range(20)) + "\n"
                   + ",".join(str(i + 1) for i in range(20)) + "\n",
     1, None),
    ("twenty columns of four bytes",
     ",".join(str(i * 70000) for i in range(20)) + "\n"
     + ",".join(str(i * 70001) for i in range(20)) + "\n", 4, None),
    ("a long table", "\n".join("%d,%d" % (i % 251, i % 65521)
                              for i in range(300)) + "\n", 2, None),
]


def numbers(rows, columns, span=251):
    return "\n".join(",".join(str((r * (i + 1)) % span) for i in range(columns))
                     for r in range(rows)) + "\n"


# --------------------------------------------------------------------------
# The round trip: text, through the writer, through the packager, through a
# 68000, and back to the rows the text defines.

def rows_through_68k(csv, variant, width, repeat, unit, ring, copies=False):
    """Every row a packaged reader of this variant gives, and its image."""
    blob = write_table(csv, variant, width, repeat, unit, ring, copies)
    image, at = package(blob)
    header_at = struct.unpack(">I", image[16 + 8:16 + 12])[0]
    fmt = image[16:16 + 28]
    assert fmt[:3] == b"DTX" and fmt[3] == variant, "the format block"
    state_bytes = struct.unpack(">I", fmt[4:8])[0]
    stride = struct.unpack(">I", fmt[24:28])[0]
    m = Machine(image, state_bytes)
    # The decoder is counted at ST4_resume's address in the listing. A missing
    # symbol is a fault in the rig, not a count of zero.
    if variant == 2:
        assert "ST4_resume" in at, \
            "no ST4_resume in the listing: the rig cannot count the decoder"
    resumes = m.count(IMAGE + at["ST4_resume"]) if "ST4_resume" in at else None
    given = m.call("metadata")
    rows, columns = given["d0"], given["d1"] & 0xFFFF
    width = image[header_at + 14]
    m.call("init")
    out = []
    for r in range(rows):
        got = m.call("advance")
        out.append(m.row(got["a1"], columns, stride, width))
    p = struct.unpack(">H", fmt[14:16])[0]
    return out, blob, image, (resumes[0] if resumes else 0), p


def roundtrip(name, csv, width=None, repeat=None, unit=1, ring=960,
              copies=False):
    """The text against every variant, and every variant against the rest."""
    width, want = csv_rows(csv, width)
    columns = len(csv_values(csv)[0])
    given, sizes, asked = {}, [], ""
    for variant in (0, 1, 2):
        got, blob, image, resumes, p = rows_through_68k(
            csv, variant, width, repeat, unit, ring,
            copies and variant == 2)
        if variant == 2:
            # One call a column at init and one a column a period after it,
            # every period, since every data set loops (R5.11) and no set
            # runs out. Reading R rows takes the seed and one period a P
            # rows, so a column is called once more than the periods those
            # rows cover.
            due = columns * (1 + (len(want) + p - 1) // p)
            assert resumes <= due, \
                "the decoder was called %d times, and reading %d rows at a" \
                " period of %d takes at most %d" % (resumes, len(want), p, due)
            asked = "  %d resumes at P=%d" % (resumes, p)
        # The writer's link, where this rig can read the file: the bytes the
        # writer laid down are the rows the text gave.
        if variant != 2:
            _, _, _, _, _, _, _, laid = read_dtx(blob)
            assert laid == want, \
                "DTX%d: the writer laid down rows the text does not give" % variant
        # The reader's link: a 68000 gives the rows the text gave.
        assert len(got) == len(want), \
            "DTX%d gave %d rows, not %d" % (variant, len(got), len(want))
        for r, (a, b) in enumerate(zip(got, want)):
            assert a == b, "DTX%d row %d read %s, the text gives %s" % (
                variant, r, a.hex(), b.hex())
        given[variant] = got
        sizes.append(len(image))
    assert given[0] == given[1], "DTX0 and DTX1 give different rows"
    assert given[1] == given[2], "DTX1 and DTX2 give different rows"
    print("  %-32s R=%-5d C=%-2d W=%d images %s%s"
          % (name, len(want), columns, width,
             "/".join(str(s) for s in sizes), asked))


ROUND = [
    ("one column of one byte", numbers(64, 1), 1, None, 1, 960),
    ("three columns of one byte", numbers(64, 3), 1, None, 1, 960),
    ("two byte values", numbers(64, 3), 2, None, 1, 960),
    ("four byte values", numbers(64, 3), 4, None, 1, 960),
    ("k of 2", numbers(64, 2), 2, None, 2, 960),
    ("k of 4", numbers(64, 2), 4, None, 4, 960),
    ("k of 4 at a width of 1", numbers(64, 2), 1, None, 4, 960),
    ("a table that repeats", numbers(64, 2), 1, 16, 1, 960),
    ("the width inferred", numbers(64, 2), None, None, 1, 960),
    ("negatives and hexadecimal",
     "".join("-%d,$%X\n" % (r % 128, (r * 7) % 65536) for r in range(64)),
     2, None, 1, 960),
    ("a comment and a blank line",
     "# what follows is a table\n\n" + numbers(64, 2), 1, None, 1, 960),
    ("R not a multiple of P", numbers(50, 3), 1, None, 1, 960),
    ("twenty columns", numbers(64, 20), 2, None, 1, 960),
    ("a long table", numbers(300, 2), 2, None, 1, 960),
]

# A column that repeats a pattern further back than the ring reaches: what
# copies from the literal stream are for.
REPEATING = "\n".join("%d,%d" % (r % 37, (r % 37) * 7) for r in range(512)) + "\n"


# Tables a DTX2 image is made of: P is at least C and at most R, and N
# divides by P times every width, so C stays small beside R.
PACKED = [
    ("one byte a row", numbers(64, 1), 1, None, 1, 960),
    ("two columns of one byte", numbers(64, 2), 1, None, 1, 960),
    ("three columns of two bytes", numbers(48, 3), 2, None, 1, 960),
    ("four byte values", numbers(64, 3), 4, None, 1, 960),
    ("k of 2", numbers(64, 2), 2, None, 2, 960),
    ("k of 4", numbers(64, 2), 4, None, 4, 960),
    ("k of 4 at a width of 1", numbers(64, 2), 1, None, 4, 960),
    ("k of 1 at a width of 4", numbers(64, 2), 4, None, 1, 960),
    ("a table that repeats", numbers(64, 2), 1, 16, 1, 960),
    ("a repeat at row 0", numbers(48, 2), 1, 0, 1, 960),
    ("R not a multiple of P", numbers(50, 3), 1, None, 1, 960),
    ("a small ring", numbers(64, 2), 1, None, 1, 64),
    ("twenty columns", numbers(64, 20), 2, None, 1, 960),
    ("a long table", numbers(600, 2), 2, None, 1, 960),
]


# --------------------------------------------------------------------------
# 68000 cycles. Every instruction the machine runs is given the cycles the
# M68000 user's manual's tables give it, out of its opcode words and, for a
# branch or a loop, out of where the machine went next. The cycles are the
# processor's own, with no wait state: a machine whose bus rounds an access
# up, as an Atari ST's does, takes longer.

class Unknown(Exception):
    """An instruction the tables here do not cover."""


# The effective address fetch, by mode and, under mode 7, register: the
# cycles for a byte or word and for a long, and the extension words.
EA_TIME = {(0, 0): (0, 0), (1, 0): (0, 0), (2, 0): (4, 8), (3, 0): (4, 8),
           (4, 0): (6, 10), (5, 0): (8, 12), (6, 0): (10, 14),
           (7, 0): (8, 12), (7, 1): (12, 16), (7, 2): (8, 12),
           (7, 3): (10, 14), (7, 4): (4, 8)}
EA_WORDS = {(0, 0): 0, (1, 0): 0, (2, 0): 0, (3, 0): 0, (4, 0): 0, (5, 0): 1,
            (6, 0): 1, (7, 0): 1, (7, 1): 2, (7, 2): 1, (7, 3): 1}
SIZE = {0: "b", 1: "w", 2: "l"}
# The control modes lea, pea, jsr, jmp and movem take.
LEA = {(2, 0): 4, (5, 0): 8, (6, 0): 12, (7, 0): 8, (7, 1): 12, (7, 2): 8,
       (7, 3): 12}
PEA = {key: cycles + 8 for key, cycles in LEA.items()}
JSR = {(2, 0): 16, (5, 0): 18, (6, 0): 22, (7, 0): 18, (7, 1): 20,
       (7, 2): 18, (7, 3): 22}
JMP = {(2, 0): 8, (5, 0): 10, (6, 0): 14, (7, 0): 10, (7, 1): 12, (7, 2): 10,
       (7, 3): 14}
MOVEM_TO = {(2, 0): 8, (4, 0): 8, (5, 0): 12, (6, 0): 14, (7, 0): 12,
            (7, 1): 16}
MOVEM_FROM = {(2, 0): 12, (3, 0): 12, (5, 0): 16, (6, 0): 18, (7, 0): 16,
              (7, 1): 20, (7, 2): 16, (7, 3): 18}


def condition(cc, sr):
    """Whether condition `cc` is true under status register `sr`."""
    c, v, z, n = sr & 1, sr >> 1 & 1, sr >> 2 & 1, sr >> 3 & 1
    return (True, False, not c and not z, c or z, not c, c, not z, z,
            not v, v, not n, n, n == v, n != v, n == v and not z,
            n != v or z)[cc]


def cycles_of(words, sr, dn, pc, next_pc, source=None):
    """The cycles of the instruction at `pc` that `words` begin, and how many
    words it is.

    `sr` is the status register before it and `dn` the data registers before
    it, or None where the instruction reads neither. `next_pc` is where the
    machine went after it, which tells a branch taken from one not, or None
    where the rig does not have it. `source` is the word at the effective
    address, for a multiply whose source is in memory, or None where the rig
    does not have it.
    """
    op = words[0]
    top = op >> 12
    mode, reg = op >> 3 & 7, op & 7
    key = (mode, reg if mode == 7 else 0)

    def fetch(size):
        return EA_TIME[key][1 if size == "l" else 0]

    def extra(size):
        if key == (7, 4):
            return 2 if size == "l" else 1
        return EA_WORDS[key]

    def no_fetch():
        # a register or an immediate source: two cycles more on a long
        return mode in (0, 1) or key == (7, 4)

    if top == 0:
        if op >> 8 & 1:                          # a bit operation by register
            if mode == 1:
                raise Unknown("movep")
            kind = op >> 6 & 3                   # btst bchg bclr bset
            if mode == 0:
                return (6, 8, 10, 8)[kind], 1
            return (4 if kind == 0 else 8) + fetch("b"), 1 + extra("b")
        group = op >> 9 & 7                      # ori andi subi addi - eori cmpi
        if group == 4:                           # a bit operation by number
            kind = op >> 6 & 3
            if mode == 0:
                return (10, 12, 14, 12)[kind], 2
            return (8 if kind == 0 else 12) + fetch("b"), 2 + extra("b")
        size = SIZE[op >> 6 & 3]
        immediate = 2 if size == "l" else 1
        if mode == 0:
            long = 14 if group in (1, 6) else 16
            return (long if size == "l" else 8), 1 + immediate
        if key == (7, 4):                        # to the condition codes or sr
            return 20, 1 + immediate
        if group == 6:
            return ((12 if size == "l" else 8) + fetch(size),
                    1 + immediate + extra(size))
        return ((20 if size == "l" else 12) + fetch(size),
                1 + immediate + extra(size))
    if top in (1, 2, 3):                         # move, movea
        size = {1: "b", 3: "w", 2: "l"}[top]
        dmode, dreg = op >> 6 & 7, op >> 9 & 7
        dkey = (dmode, dreg if dmode == 7 else 0)
        # a predecrement destination costs what a plain indirect one does
        to = ((8 if size == "l" else 4) if dmode == 4
              else EA_TIME[dkey][1 if size == "l" else 0])
        return 4 + fetch(size) + to, 1 + extra(size) + EA_WORDS[dkey]
    if top == 4:
        if op == 0x4E75:
            return 16, 1                         # rts
        if op == 0x4E71:
            return 4, 1                          # nop
        if op in (0x4E73, 0x4E77):
            return 20, 1                         # rte rtr
        if op & 0xFFF8 == 0x4E50:
            return 16, 2                         # link
        if op & 0xFFF8 == 0x4E58:
            return 12, 1                         # unlk
        if op & 0xFFF0 == 0x4E40:
            return 34, 1                         # trap
        if op & 0xFFF0 == 0x4E60:
            return 4, 1                          # move usp
        if op & 0xFFC0 == 0x4E80:
            return JSR[key], 1 + extra("w")
        if op & 0xFFC0 == 0x4EC0:
            return JMP[key], 1 + extra("w")
        if op & 0xF1C0 == 0x41C0:
            return LEA[key], 1 + extra("w")
        if op & 0xF1C0 == 0x4180:
            return 10 + fetch("w"), 1 + extra("w")   # chk
        if op & 0xFFF8 == 0x4840:
            return 4, 1                          # swap
        if op & 0xFFC0 == 0x4840:
            return PEA[key], 1 + extra("w")
        if op & 0xFFB8 == 0x4880:
            return 4, 1                          # ext
        if op & 0xFB80 == 0x4880:                # movem
            each = 8 if op & 0x40 else 4
            count = bin(words[1]).count("1")
            base = MOVEM_FROM[key] if op & 0x400 else MOVEM_TO[key]
            return base + each * count, 2 + extra("w")
        if op & 0xFF00 in (0x4000, 0x4200, 0x4400, 0x4600):
            if op >> 6 & 3 == 3:                 # move from sr, to ccr, to sr
                if op & 0xFF00 == 0x4000:
                    return ((6 if mode == 0 else 8 + fetch("w")),
                            1 + extra("w"))
                return 12 + fetch("w"), 1 + extra("w")
            size = SIZE[op >> 6 & 3]             # negx clr neg not
            if mode == 0:
                return (6 if size == "l" else 4), 1
            return (12 if size == "l" else 8) + fetch(size), 1 + extra(size)
        if op & 0xFF00 == 0x4A00:                # tst, tas
            if op >> 6 & 3 == 3:
                return (4 if mode == 0 else 14 + fetch("b")), 1 + extra("b")
            size = SIZE[op >> 6 & 3]
            return 4 + fetch(size), 1 + extra(size)
        if op & 0xFFC0 == 0x4800:                # nbcd
            return (6 if mode == 0 else 8 + fetch("b")), 1 + extra("b")
        raise Unknown("%04x" % op)
    if top == 5:
        if op >> 6 & 3 == 3:
            cc = op >> 8 & 0xF
            if mode == 1:                        # dbcc
                if next_pc is not None and next_pc != pc + 4:
                    return 10, 2
                return (12 if condition(cc, sr) else 14), 2
            if mode == 0:                        # scc
                return (6 if condition(cc, sr) else 4), 1
            return 8 + fetch("b"), 1 + extra("b")
        size = SIZE[op >> 6 & 3]                 # addq subq
        if mode == 0:
            return (8 if size == "l" else 4), 1
        if mode == 1:
            return 8, 1
        return (12 if size == "l" else 8) + fetch(size), 1 + extra(size)
    if top == 6:                                 # bra bsr bcc
        cc = op >> 8 & 0xF
        length = 2 if op & 0xFF == 0 else 1
        if cc == 0:
            return 10, length
        if cc == 1:
            return 18, length
        if next_pc is not None and next_pc != pc + 2 * length:
            return 10, length
        return (12 if length == 2 else 8), length
    if top == 7:
        return 4, 1                              # moveq
    if top in (8, 0xC):                          # or and, div mul, bcd, exg
        opmode = op >> 6 & 7
        if opmode in (3, 7):
            if top == 8:                         # divu divs, at their longest
                return ((140 if opmode == 3 else 158) + fetch("w"),
                        1 + extra("w"))
            if mode == 0:
                value = dn[reg] & 0xFFFF
            elif key == (7, 4):
                value = words[1]
            elif source is not None:
                value = source
            else:
                raise Unknown("a multiply from memory")
            if opmode == 3:                      # mulu: the ones in the source
                n = bin(value).count("1")
            else:                                # muls: the changes of bit
                v = value << 1
                n = sum(1 for i in range(16)
                        if (v >> i & 1) != (v >> (i + 1) & 1))
            return 38 + 2 * n + fetch("w"), 1 + extra("w")
        if opmode == 4 and mode in (0, 1):       # sbcd abcd
            return (6 if mode == 0 else 18), 1
        if top == 0xC and opmode in (5, 6) and mode in (0, 1):
            return 6, 1                          # exg
        size = SIZE[opmode & 3]
        if opmode < 4:
            if size == "l":
                return (8 if no_fetch() else 6) + fetch("l"), 1 + extra("l")
            return 4 + fetch(size), 1 + extra(size)
        return (12 if size == "l" else 8) + fetch(size), 1 + extra(size)
    if top in (9, 0xD):                          # sub add, suba adda, subx addx
        opmode = op >> 6 & 7
        if opmode in (3, 7):
            if opmode == 3:
                return 8 + fetch("w"), 1 + extra("w")
            return (8 if no_fetch() else 6) + fetch("l"), 1 + extra("l")
        size = SIZE[opmode & 3]
        if opmode >= 4 and mode in (0, 1):
            if mode == 0:
                return (8 if size == "l" else 4), 1
            return (30 if size == "l" else 18), 1
        if opmode < 4:
            if size == "l":
                return (8 if no_fetch() else 6) + fetch("l"), 1 + extra("l")
            return 4 + fetch(size), 1 + extra(size)
        return (12 if size == "l" else 8) + fetch(size), 1 + extra(size)
    if top == 0xB:                               # cmp cmpa cmpm eor
        opmode = op >> 6 & 7
        if opmode in (3, 7):
            size = "w" if opmode == 3 else "l"
            return 6 + fetch(size), 1 + extra(size)
        size = SIZE[opmode & 3]
        if opmode < 4:
            return (6 if size == "l" else 4) + fetch(size), 1 + extra(size)
        if mode == 1:
            return (20 if size == "l" else 12), 1
        if mode == 0:
            return (8 if size == "l" else 4), 1
        return (12 if size == "l" else 8) + fetch(size), 1 + extra(size)
    if top == 0xE:                               # shifts and rotates
        if op >> 6 & 3 == 3:
            return 8 + fetch("w"), 1 + extra("w")
        size = SIZE[op >> 6 & 3]
        count = dn[op >> 9 & 7] & 63 if op & 0x20 else (op >> 9 & 7 or 8)
        return (8 if size == "l" else 6) + 2 * count, 1
    raise Unknown("%04x" % op)


# Encodings against the manual's tables: the words, the status register, the
# data registers, where the machine went next relative to the instruction (None
# for a fall-through that is not a branch), and the cycles. A sixth entry is
# the word at the effective address, for a multiply that takes its source
# there.
TIMED = [
    ((0x3000,), 0, None, None, 4),                  # move.w d0,d0
    ((0x2280,), 0, None, None, 12),                 # move.l d0,(a1)
    ((0x3018,), 0, None, None, 8),                  # move.w (a0)+,d0
    ((0x2018,), 0, None, None, 12),                 # move.l (a0)+,d0
    ((0x2F00,), 0, None, None, 12),                 # move.l d0,-(a7)
    ((0x203C, 0, 1), 0, None, None, 12),            # move.l #1,d0
    ((0x303C, 1), 0, None, None, 8),                # move.w #1,d0
    ((0x3032, 0x1000), 0, None, None, 14),          # move.w 0(a2,d1.w),d0
    ((0x33B2, 0x1000, 0x2000), 0, None, None, 24),  # move.w 0(a2,d1.w),0(a1,d2.w)
    ((0x23B2, 0x1000, 0x2000), 0, None, None, 32),  # move.l 0(a2,d1.w),0(a1,d2.w)
    ((0x13B2, 0x1000, 0x2000), 0, None, None, 24),  # move.b 0(a2,d1.w),0(a1,d2.w)
    ((0x2D40, 0x0008), 0, None, None, 16),          # move.l d0,8(a6)
    ((0x202E, 0x0008), 0, None, None, 16),          # move.l 8(a6),d0
    ((0x2E5F,), 0, None, None, 12),                 # movea.l (a7)+,a7
    ((0x41EA, 0x0008), 0, None, None, 8),           # lea 8(a2),a0
    ((0x49FA, 0x0002), 0, None, None, 8),           # lea 2(pc),a4
    ((0x41F2, 0x1000), 0, None, None, 12),          # lea 0(a2,d1.w),a0
    ((0x4E75,), 0, None, None, 16),                 # rts
    ((0x4E92,), 0, None, None, 16),                 # jsr (a2)
    ((0x4EB9, 0, 0), 0, None, None, 20),            # jsr abs.l
    ((0x4ED2,), 0, None, None, 8),                  # jmp (a2)
    ((0x6100, 0x0010), 0, None, None, 18),          # bsr.w
    ((0x6000, 0x0010), 0, None, None, 10),          # bra.w
    ((0x6010,), 0, None, None, 10),                 # bra.s
    ((0x6610,), 0x04, None, 2, 8),                  # bne.s, z set, not taken
    ((0x6610,), 0x00, None, 0x12, 10),              # bne.s taken
    ((0x6600, 0x0100), 0x04, None, 4, 12),          # bne.w not taken
    ((0x51C8, 0xFFFE), 0, None, 0, 10),             # dbf, branch taken
    ((0x51C8, 0xFFFE), 0, None, 4, 14),             # dbf, counter expired
    ((0x57C8, 0xFFFE), 0x04, None, 4, 12),          # dbeq, z set: cc true
    ((0x7001,), 0, None, None, 4),                  # moveq #1,d0
    ((0xD040,), 0, None, None, 4),                  # add.w d0,d0
    ((0xD080,), 0, None, None, 8),                  # add.l d0,d0
    ((0xD0AE, 0x0004), 0, None, None, 18),          # add.l 4(a6),d0
    ((0xD1C9,), 0, None, None, 8),                  # adda.l a1,a0
    ((0xD0C9,), 0, None, None, 8),                  # adda.w a1,a0
    ((0xD1EE, 0x0004), 0, None, None, 18),          # adda.l 4(a6),a0
    ((0x5240,), 0, None, None, 4),                  # addq.w #1,d0
    ((0x5280,), 0, None, None, 8),                  # addq.l #1,d0
    ((0x5288,), 0, None, None, 8),                  # addq.l #1,a0
    ((0x5340,), 0, None, None, 4),                  # subq.w #1,d0
    ((0x0680, 0, 1), 0, None, None, 16),            # addi.l #1,d0
    ((0x0280, 0, 1), 0, None, None, 14),            # andi.l #1,d0
    ((0xC040,), 0, None, None, 4),                  # and.w d0,d0
    ((0x0C80, 0, 1), 0, None, None, 14),            # cmpi.l #1,d0
    ((0xB0AE, 0x0004), 0, None, None, 18),          # cmp.l 4(a6),d0
    ((0xB080,), 0, None, None, 6),                  # cmp.l d0,d0
    ((0xB040,), 0, None, None, 4),                  # cmp.w d0,d0
    ((0xB097,), 0, None, None, 14),                 # cmp.l (a7),d0
    ((0x0802, 0x0000), 0, None, None, 10),          # btst #0,d2
    ((0x48E7, 0x0302), 0, None, None, 32),          # movem.l d6-d7/a6,-(a7): 3 registers
    ((0x4CDF, 0x40C0), 0, None, None, 36),          # movem.l (a7)+,d6-d7/a6
    ((0x4CEE, 0x40C0, 0x000C), 0, None, None, 40),  # movem.l 12(a6),d6-d7/a6
    ((0x48E8, 0x40C0, 0x000C), 0, None, None, 36),  # movem.l d6-d7/a6,12(a0)
    ((0xE248,), 0, None, None, 8),                  # lsr.w #1,d0
    ((0xE288,), 0, None, None, 10),                 # lsr.l #1,d0
    ((0xE048,), 0, None, None, 22),                 # lsr.w #8,d0
    ((0xE268,), 0, (0, 3, 0, 0, 0, 0, 0, 0), None, 12),  # lsr.w d1,d0 with d1 of 3
    ((0x4840,), 0, None, None, 4),                  # swap d0
    ((0x4440,), 0, None, None, 4),                  # neg.w d0
    ((0x4240,), 0, None, None, 4),                  # clr.w d0
    ((0x4280,), 0, None, None, 6),                  # clr.l d0
    ((0x4A80,), 0, None, None, 4),                  # tst.l d0
    ((0x4AAE, 0x0004), 0, None, None, 16),          # tst.l 4(a6)
    ((0x4A40,), 0, None, None, 4),                  # tst.w d0
    ((0xD140,), 0, None, None, 4),                  # addx.w d0,d0
    ((0x9040,), 0, None, None, 4),                  # sub.w d0,d0
    ((0x90AE, 0x0004), 0, None, None, 18),          # sub.l 4(a6),d0
    ((0x90C9,), 0, None, None, 8),                  # suba.w a1,a0
    ((0xC0C1,), 0, (0, 0xFFFF, 0, 0, 0, 0, 0, 0), None, 70),  # mulu.w d1,d0, d1 all ones
    ((0xC0C1,), 0, (0, 0, 0, 0, 0, 0, 0, 0), None, 38),       # mulu.w d1,d0, d1 zero
    ((0xC0EE, 0x0004), 0, None, None, 78, 0xFFFF),  # mulu.w 4(a6),d0, all ones
    ((0xC0EE, 0x0004), 0, None, None, 46, 0),       # mulu.w 4(a6),d0, zero there
    ((0x4880,), 0, None, None, 4),                  # ext.w d0
    ((0x4E71,), 0, None, None, 4),                  # nop
]


def cycle_tables():
    """The tables here against the manual, on the encodings above."""
    for one in TIMED:
        words, sr, dn, went, cycles = one[:5]
        source = one[5] if len(one) > 5 else None
        pc = 0x1000
        got, length = cycles_of(words, sr, dn, pc,
                                None if went is None else pc + went, source)
        assert got == cycles, "%s: the tables give %d cycles, the manual %d" % (
            " ".join("%04x" % w for w in words), got, cycles)
        assert length == len(words), "%s: read as %d words, not %d" % (
            " ".join("%04x" % w for w in words), length, len(words))
    print("  %d encodings timed as the manual times them" % len(TIMED))


class Cycles:
    """The cycles the machine takes from here on, and the instructions."""

    def __init__(self, m):
        self.mu = m.mu
        self.cycles = 0
        self.instructions = 0
        self.pending = None
        m.mu.hook_add(UC_HOOK_CODE, self._code)

    def _code(self, mu, address, size, data):
        self._settle(address)
        raw = bytes(mu.mem_read(address, 10))
        op = raw[0] << 8 | raw[1]
        # The registers are read only where the instruction's cycles depend
        # on one: the data registers for a shift counted in one or a
        # multiply, the status register for a dbcc or scc on a condition.
        # Reading the status register inside a hook disturbs Unicorn's
        # deferred flags on a computed jump, so it is not read on any other.
        reads = (op >> 12 == 0xE and op & 0x20) or (
            op >> 12 in (8, 0xC) and op >> 6 & 7 in (3, 7))
        dn = [mu.reg_read(r) for r in D] if reads else None
        tests = op >> 12 == 5 and op >> 6 & 3 == 3 and op >> 8 & 0xF >= 2
        sr = mu.reg_read(UC_M68K_REG_SR) if tests else 0
        # A multiply's cycles move with its source, so where that source is
        # in memory the rig reads it at the effective address. Only d16(An),
        # the mode DTX_ring takes, is read.
        source = None
        if op >> 12 == 0xC and op >> 6 & 7 in (3, 7) and op >> 3 & 7 == 5:
            at = mu.reg_read(A[op & 7]) + struct.unpack(">h", raw[2:4])[0]
            source = struct.unpack(">H", mu.mem_read(at, 2))[0]
        self.pending = (address, raw, sr, dn, source)

    def _settle(self, next_pc):
        if self.pending is None:
            return
        pc, raw, sr, dn, source = self.pending
        self.pending = None
        words = struct.unpack(">5H", raw)
        try:
            cycles, _ = cycles_of(words, sr, dn, pc, next_pc, source)
        except Unknown as what:
            raise AssertionError("the cycle tables do not cover %s at %08x"
                                 % (what, pc))
        self.cycles += cycles
        self.instructions += 1

    def call(self, m, name, **at):
        """One call, and the cycles it took."""
        before = self.cycles
        m.call(name, **at)
        self._settle(None)
        return self.cycles - before


# --------------------------------------------------------------------------
# doc/performance.md, read back: every figure it records is one this rig
# counts, or the rig fails naming the cell.

CALLS = ("init", "advance", "jump to row 0", "jump to row 63", "code, bytes")
# The two example tables, both of 64 rows at a ring of 960 bytes and a width
# of 2: one of three columns and one of twenty. Each is read under DTX0,
# DTX1, and DTX2 at a unit of 1, 2 and 4.
BUILDS = [(0, 1), (1, 1), (2, 1), (2, 2), (2, 4)]
EXAMPLES = (("three columns", 3), ("twenty columns", 20))
WIDTH = 2


def measured(variant, width, unit, columns):
    """The column of a call table one image gives, in cycles."""
    blob = write_table(numbers(64, columns), variant, width, None, unit, 960)
    image, _ = package(blob)
    state = struct.unpack(">I", image[20:24])[0]
    code = struct.unpack(">I", image[36:40])[0]
    m = Machine(image, state)
    cycles = Cycles(m)
    init = cycles.call(m, "init")
    advance = [cycles.call(m, "advance") for _ in range(8)]
    jump0 = cycles.call(m, "jump", d0=0)
    jump63 = cycles.call(m, "jump", d0=63)
    return {"init": str(init),
            # one figure where every advance took the same, a range where not
            "advance": str(min(advance)) if min(advance) == max(advance)
            else "%d-%d" % (min(advance), max(advance)),
            "jump to row 0": str(jump0), "jump to row 63": str(jump63),
            "code, bytes": str(code - 44)}


def copy_cost(unit):
    """The cycles of init and 64 rows read and advanced, on a column packed
    without copies, by the decoder without the copy code and by the one with
    it. The packager takes the decoder the payload's flag names, so the flag
    is set on a copy of the file to get the second."""
    blob = write_table(numbers(64, 3), 2, WIDTH, None, unit, 960)
    flagged = bytearray(blob)
    flagged[16 + 3] |= 1
    out = []
    for file in (blob, bytes(flagged)):
        image, _ = package(file)
        m = Machine(image, struct.unpack(">I", image[20:24])[0])
        cycles = Cycles(m)
        total = cycles.call(m, "init")
        for _ in range(64):
            total += cycles.call(m, "advance")
        out.append(total)
    return out


def performance_tables():
    """The tables doc/performance.md lists: what every call costs on the two
    example tables, what a read costs at each width, and what the copy code
    costs, each as Markdown."""
    out = []
    calls = {}
    for name, columns in EXAMPLES:
        got = [measured(variant, WIDTH, unit, columns)
               for variant, unit in BUILDS]
        calls[name] = got
        out.append("### %s\n" % name.capitalize())
        out.append("| call | DTX0 | DTX1 | DTX2 k=1 | DTX2 k=2 | DTX2 k=4 |")
        out.append("|---|---|---|---|---|---|")
        for call in CALLS:
            out.append("| %s | %s |" % (call, " | ".join(one[call] for one in got)))
        out.append("")
    # What one value costs the caller: a move of that width off the pointer
    # an advance gives, at a displacement. The rig times the encoding from
    # the same tables it counts a call with.
    reads = {}
    out.append("| width | the move | cycles |")
    out.append("|---|---|---|")
    for width, opcode, name in ((1, 0x1029, "move.b d(a1),d0"),
                                (2, 0x3029, "move.w d(a1),d0"),
                                (4, 0x2029, "move.l d(a1),d0")):
        cost, _ = cycles_of((opcode, 0, 0, 0, 0), 0, None, 0x1000, None)
        reads[width] = ["`%s`" % name, str(cost)]
        out.append("| %d | `%s` | %d |" % (width, name, cost))
    out.append("")
    costs = {}
    out.append("| k | without | with | more |")
    out.append("|---|---|---|---|")
    for unit in (1, 2, 4):
        without, with_ = copy_cost(unit)
        costs[unit] = (without, with_)
        out.append("| %d | %d | %d | %d |" % (unit, without, with_, with_ - without))
    return "\n".join(out), calls, reads, costs


def performance():
    """doc/performance.md against the machine, cell by cell."""
    doc = open(os.path.join(ROOT, "doc", "performance.md")).read()
    listed, said_reads, said_costs = [], {}, {}
    under = None
    for line in doc.splitlines():
        if not line.startswith("|"):
            under = None
            continue
        cell = [c.strip() for c in line.strip().strip("|").split("|")]
        if cell[0] == "width":
            under = said_reads
        elif cell[0] == "k":
            under = said_costs
        elif len(cell) == 6 and cell[0] in CALLS:
            listed.append(cell)
        elif under is not None and cell[0] in ("1", "2", "4"):
            under[int(cell[0])] = cell[1:]
    tables, calls, reads, costs = performance_tables()
    cells = 0
    at = 0
    for name, _ in EXAMPLES:
        rows = listed[at:at + len(CALLS)]
        assert [row[0] for row in rows] == list(CALLS), \
            "doc/performance.md's %s table does not list the %d rows %s" \
            % (name, len(CALLS), ", ".join(CALLS))
        for row in rows:
            for column, one in enumerate(calls[name]):
                assert row[1 + column] == one[row[0]], \
                    "doc/performance.md gives %s for %s, %s, column %d; the" \
                    " rig counts %s" % (row[1 + column], row[0], name,
                                        column + 1, one[row[0]])
                cells += 1
        at += len(CALLS)
    assert len(listed) == at, "doc/performance.md lists %d call rows, not %d" % (
        len(listed), at)
    for width, row in reads.items():
        assert said_reads.get(width) == row, \
            "doc/performance.md gives %s for one value at a width of %d; the" \
            " rig counts %s" % (said_reads.get(width), width, row)
        cells += 2
    for unit, (without, with_) in costs.items():
        want = [str(without), str(with_), str(with_ - without)]
        assert said_costs.get(unit) == want, \
            "doc/performance.md gives %s for the copy code at k of %d; the rig" \
            " counts %s" % (said_costs.get(unit), unit, want)
        cells += 3
    print("  %d cells of doc/performance.md, each the figure the rig counts"
          % cells)


def main():
    if not os.path.isdir(CLASSES):
        raise SystemExit("run `mvn process-classes` first: no " + CLASSES)
    bad = 0
    for variant in (0, 1):
        print("DTX%d" % variant)
        for name, csv, width, repeat in TABLES:
            try:
                check(name, csv, variant, width, repeat)
            except AssertionError as wrong:
                bad += 1
                print("  %-40s FAILED: %s" % (name, wrong))
    print("DTX2")
    for name, csv, width, repeat, unit, ring in PACKED:
        try:
            check(name, csv, 2, width, repeat, unit, ring)
        except AssertionError as wrong:
            bad += 1
            print("  %-40s FAILED: %s" % (name, wrong))
    print("the round trip: text, writer, packager, 68000, back to the text")
    for name, csv, width, repeat, unit, ring in ROUND:
        try:
            roundtrip(name, csv, width, repeat, unit, ring)
        except AssertionError as wrong:
            bad += 1
            print("  %-34s FAILED: %s" % (name, wrong))
    print("copies from the literal stream, at a small ring")
    for name, ring, copies in (("a small ring, plain", 64, False),
                               ("a small ring, copies", 64, True),
                               ("a ring the pattern fits, copies", 128, True)):
        try:
            roundtrip(name, REPEATING, 2, None, 1, ring, copies)
        except AssertionError as wrong:
            bad += 1
            print("  %-32s FAILED: %s" % (name, wrong))
    print("68000 cycles")
    try:
        cycle_tables()
    except AssertionError as wrong:
        bad += 1
        print("  FAILED: %s" % wrong)
    print("doc/performance.md, read back")
    try:
        performance()
    except AssertionError as wrong:
        bad += 1
        print("  FAILED: %s" % wrong)
    if bad:
        raise SystemExit("%d checks failed" % bad)
    print("every check passed")


if __name__ == "__main__":
    if sys.argv[1:] == ["performance"]:
        # the tables doc/performance.md lists, to paste in after a change
        print(performance_tables()[0])
    else:
        main()
