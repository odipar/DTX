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
    void theFiguresStateOnlyWhatTheImageCannotReadBack() {
        // R, C, RR and the widths reach the code at run time, out of the
        // table's own header and the column table, so no equate states one.
        // What is left is the row's bytes and the state block, which the
        // format block holds and a caller reads before there is a block to
        // read them from.
        for (int variant : new int[] {Dtx.DTX0, Dtx.DTX1}) {
            String out = Packager.table(table(variant));
            assertTrue(out.contains("DTX_ROWBYTES\tequ\t4"), "the row's bytes");
            assertTrue(out.contains("DTX_STATE\tequ\t"), "the state block");
            for (String gone : new String[] {"DTX_ROWS", "DTX_COLUMNS",
                    "DTX_REPEAT", "DTX_VARIANT", "DTX_HEADER"}) {
                assertTrue(!out.contains(gone + "\tequ\t"),
                        "DTX" + variant + " states " + gone
                                + ", so its code moves with the table");
            }
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
    void theStateBlockGrowsWithTheWidthClasses() {
        Table one = Csv.table("1\n2\n", new int[] {1});
        Table three = Csv.table("1,2,3\n4,5,6\n", new int[] {1, 2, 4});
        assertEquals(28, Packager.stateBytes(Dtx.header(Dtx0.write(three))),
                "DTX0 holds one cursor whatever the widths");
        // DTX1 holds three cursors and three class bases at any width, so
        // its block does not move with C either.
        assertEquals(48, Packager.stateBytes(Dtx.header(Dtx1.write(one))),
                "DTX1 with one width class");
        assertEquals(48, Packager.stateBytes(Dtx.header(Dtx1.write(three))),
                "DTX1 with three width classes");
    }

    @Test
    void theFiguresStateNoMacroAtAll() {
        // A template no longer takes a list of macro invocations, one a
        // column or a width class: the column table states what each of them
        // stated, and one loop reads it. So the figures are equates and
        // nothing else, and the code they reach does not move with C.
        for (String out : new String[] {Packager.table(table(Dtx.DTX1)),
                Packager.table(packed(64, new int[] {1, 2}, 1, 960))}) {
            for (String line : out.split("\n")) {
                assertTrue(!line.trim().startsWith(".macro"),
                        "the figures state a macro: " + line);
            }
        }
    }

    @Test
    void aPackedColumnTableHoldsAStreamRecordAColumn() {
        byte[] file = packed(64, new int[] {1, 2}, 1, 960);
        Dtx.Header header = Dtx.header(file);
        byte[] table = Packager.columnTable(file);
        int entries = Packager.ENTRIES + Packager.ENTRY * header.columns();
        assertEquals(entries + Packager.STREAM * header.columns(), table.length,
                "the records follow the read entries");
        assertEquals(2, Dtx.getWord(table, 20), "C");
        assertEquals(entries, Dtx.getLong(table, 24), "where the records begin");
        assertEquals(960, Dtx.getLong(table, 28), "N");
        for (int i = 0; i < header.columns(); i++) {
            int at = entries + Packager.STREAM * i;
            assertEquals(Packager.ring(header) + i * 960,
                    Dtx.getLong(table, at + 16), "column " + i + "'s ring");
            assertEquals(Packager.slot(header) + 32 * i,
                    Dtx.getLong(table, at + 20), "column " + i + "'s slot");
            assertEquals(i, Dtx.getWord(table, at + 24),
                    "column " + i + "'s width shift");
            assertEquals(0, Dtx.getWord(table, at + 26), "the unit's shift");
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
        assertEquals(80, Packager.slot(header),
                "the slots follow the cursors and the five figures");
        assertEquals(144, Packager.ring(header), "the rings follow two slots");
        assertEquals(144 + 2 * 960, Packager.stateBytes(header, given),
                "a ring a column");
    }

    @Test
    void theCopyCodeIsAskedForOnlyWhereTheColumnsHoldCopies() {
        byte[] file = packed(64, new int[] {1, 2}, 1, 960);
        assertTrue(!Packager.table(file).contains("ST4_WINDOW"),
                "a table packed without copies asks for no copy code");
        assertTrue(Packager.table(file, true).contains("ST4_WINDOW\tequ\t1"),
                "a table packed with copies asks for it");
    }

    @Test
    void thePackedFiguresStateThePeriodTheRingAndTheUnit() {
        String out = Packager.table(packed(64, new int[] {1, 2}, 1, 960));
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
        assertEquals("DTX", new String(image, 24, 3), "the format block at 24");
        assertEquals(0, image[27], "the variant the format block states");
        assertEquals(28, Dtx.getLong(image, 28), "the state block's bytes");
        int header = Dtx.getLong(image, 32);
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
