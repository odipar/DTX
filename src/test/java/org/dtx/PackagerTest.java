package org.dtx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The assembly a table is packaged as, and the image rmac makes of it.
 *
 * <p>What the image does is held by {@code 68k/test/emu/test_dtx.py}, which
 * runs it on a 68000. This holds what the packager states of it: the
 * figures the format block gives, the state block's size, and what it will
 * not package.
 */
final class PackagerTest {

    private static byte[] table(int variant) {
        Table table = Csv.table("1,300,-2\n2,301,-1\n3,302,0\n4,303,1\n",
                new int[] {1, 2, 1});
        return variant == Dtx.DTX0 ? Dtx0.write(table) : Dtx1.write(table);
    }

    @Test
    void theFiguresStateWhatTheTableStates() {
        for (int variant : new int[] {Dtx.DTX0, Dtx.DTX1}) {
            String out = Packager.table(table(variant));
            assertTrue(out.contains("DTX_VARIANT\tequ\t" + variant),
                    "the variant at DTX" + variant);
            assertTrue(out.contains("DTX_ROWS\tequ\t4"), "R");
            assertTrue(out.contains("DTX_COLUMNS\tequ\t3"), "C");
            assertTrue(out.contains("DTX_ROWBYTES\tequ\t4"), "the row's bytes");
            assertTrue(out.contains("DTX_HEADER\tequ\t20"), "the header");
        }
    }

    @Test
    void theFiguresHoldNoInstruction() {
        // 68k/DTX.S is where every instruction stands. What the packager
        // writes is equates and macro invocations, and a move or a bra in
        // it would be an instruction the template does not hold.
        for (int variant : new int[] {Dtx.DTX0, Dtx.DTX1}) {
            for (String line : Packager.table(table(variant)).split("\n")) {
                String read = line.trim();
                assertTrue(!read.startsWith("move") && !read.startsWith("bra")
                                && !read.startsWith("lea") && !read.startsWith("dc."),
                        "the figures hold an instruction: " + read);
            }
        }
    }

    @Test
    void theRepeatCountIsNeverNegative() {
        // rmac crashes on a negative .rept, conditional or not, so the row
        // shift stays at or above zero and a flag says whether it stands.
        Table table = Csv.table("1,2,3\n4,5,6\n", new int[] {1, 2, 4});
        String out = Packager.table(Dtx0.write(table));
        assertTrue(out.contains("DTX_ROWPOW\tequ\t0"),
                "seven bytes a row is no power of two");
        assertTrue(out.contains("DTX_ROWSHIFT\tequ\t0"),
                "and its shift stands at zero, not -1");
    }

    @Test
    void theStateBlockGrowsWithTheWidthClasses() {
        Table one = Csv.table("1\n2\n", new int[] {1});
        Table three = Csv.table("1,2,3\n4,5,6\n", new int[] {1, 2, 4});
        assertEquals(28, Packager.stateBytes(Dtx.header(Dtx0.write(three))),
                "DTX0 holds one cursor whatever the widths");
        assertEquals(28, Packager.stateBytes(Dtx.header(Dtx1.write(one))),
                "DTX1 with one width class");
        assertEquals(36, Packager.stateBytes(Dtx.header(Dtx1.write(three))),
                "DTX1 with three width classes");
    }

    @Test
    void everyListTheTemplateInvokesIsWritten() {
        String plain = Packager.table(table(Dtx.DTX1));
        for (String list : new String[] {"DTX_SEED_CURSORS", "DTX_JUMP_CURSORS",
                "DTX_STEP_CURSORS", "DTX_LOAD_CURSORS", "DTX_READ_ROW"}) {
            assertTrue(plain.contains("\t.macro\t" + list + "\n"),
                    "DTX1 states no " + list);
        }
        String packed = Packager.table(packed(64, new int[] {1, 2}, 1, 960));
        for (String list : new String[] {"DTX_SEED_CURSORS", "DTX_STEP_CURSORS",
                "DTX_LOAD_CURSORS", "DTX_READ_ROW", "DTX_FILL_COLUMNS",
                "DTX_REFILL_TABLE", "DTX_REFILL_BODIES"}) {
            assertTrue(packed.contains("\t.macro\t" + list + "\n"),
                    "DTX2 states no " + list);
        }
    }

    /** A DTX2 file of {@code rows} rows and these widths, at this ring. */
    private static byte[] packed(int rows, int[] width, int unit, int ring) {
        byte[][] column = new byte[width.length][];
        for (int i = 0; i < width.length; i++) {
            column[i] = new byte[rows * width[i]];
        }
        Table table = Table.of(rows, rows, width, column);
        return Dtx2.write(table, (bytes, k, n) -> {
            byte[] set = new byte[28 + bytes.length];
            set[0] = 'S';
            set[1] = '4';
            set[2] = 7;
            set[3] = (byte) k;
            Dtx.putLong(set, 8, 28);
            Dtx.putLong(set, 12, 28 + bytes.length);
            Dtx.putLong(set, 16, 28 + bytes.length);
            System.arraycopy(bytes, 0, set, 28, bytes.length);
            return set;
        }, unit, ring);
    }

    @Test
    void thePeriodIsTheSmallestThatHoldsEveryRule() {
        byte[] file = packed(64, new int[] {1, 2}, 1, 960);
        Dtx.Header header = Dtx.header(file);
        assertEquals(2, Packager.period(header, Packager.packed(file, header)),
                "P is C where C meets the rules");
        byte[] three = packed(48, new int[] {1, 2, 1}, 1, 960);
        Dtx.Header at3 = Dtx.header(three);
        assertEquals(3, Packager.period(at3, Packager.packed(three, at3)),
                "P is C at three columns of two widths");
    }

    @Test
    void aRingTheWidthsDoNotDivideIsRefused() {
        // A ring of six bytes: every period needs 2P times the widest
        // column, so P is at most one, and P is at least C of two.
        byte[] file = packed(64, new int[] {1, 2}, 1, 6);
        Dtx.Header header = Dtx.header(file);
        String said = assertThrows(IllegalArgumentException.class,
                () -> Packager.period(header, Packager.packed(file, header)))
                .getMessage();
        assertTrue(said != null && said.startsWith(
                "no period from C of 2 to R of 64"), "a ring no period divides");
    }

    @Test
    void aTableTooWideForA68000DisplacementIsRefused() {
        int[] wide = new int[40];
        java.util.Arrays.fill(wide, 1);
        byte[] file = packed(64, wide, 1, 960);
        Dtx.Header header = Dtx.header(file);
        assertEquals("a read reaches column 39 at 37440, past the 32767 a"
                + " 68000 displacement holds: C is at most 35 at N of 960",
                assertThrows(IllegalArgumentException.class,
                        () -> Packager.period(header,
                                Packager.packed(file, header))).getMessage());
    }

    @Test
    void aPackedStateBlockHoldsASlotAndARingAColumn() {
        byte[] file = packed(64, new int[] {1, 2}, 1, 960);
        Dtx.Header header = Dtx.header(file);
        Packager.Packed given = Packager.packed(file, header);
        assertEquals(32, Packager.slot(header), "the slots follow two cursors");
        assertEquals(96, Packager.ring(header), "the rings follow two slots");
        assertEquals(96 + 2 * 960, Packager.stateBytes(header, given),
                "a ring a column");
    }

    @Test
    void thePackedFiguresStateThePeriodTheRingAndTheUnit() {
        String out = Packager.table(packed(64, new int[] {1, 2}, 1, 960));
        assertTrue(out.contains("DTX_VARIANT\tequ\t2"), "the variant");
        assertTrue(out.contains("DTX_PERIOD\tequ\t2"), "P");
        assertTrue(out.contains("DTX_N\t\tequ\t960"), "N");
        assertTrue(out.contains("ST4_UNIT\tequ\t1"),
                "the decoder is built at the payload's unit");
    }

    @Test
    void rmacMakesAnImageThatOpensWithTheSlotsAndTheFormatBlock()
            throws Exception {
        String named = System.getenv("RMAC");
        Path rmac = Path.of(named == null ? "rmac" : named);
        Assumptions.assumeTrue(named != null || onThePath(rmac),
                "no rmac at " + rmac);
        byte[] image = Packager.image(table(Dtx.DTX0), rmac);
        assertEquals(0x60, image[0] & 0xFF, "the first slot is a bra.w");
        assertEquals("DTX", new String(image, 20, 3), "the format block at 20");
        assertEquals(0, image[23], "the variant the format block states");
        assertEquals(28, Dtx.getLong(image, 24), "the state block's bytes");
        int header = Dtx.getLong(image, 28);
        assertEquals("DTX", new String(image, header, 3),
                "the header the format block points at");
    }

    private static boolean onThePath(Path rmac) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String at : path.split(":")) {
            if (Files.isExecutable(Path.of(at).resolve(rmac.toString()))) {
                return true;
            }
        }
        return false;
    }
}
