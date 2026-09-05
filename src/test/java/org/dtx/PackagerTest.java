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
 * <p>What the image does is checked by {@code 68k/test/emu/test_dtx.py}, which
 * runs it on a 68000. This checks what the packager writes of it: the
 * figures the format block gives, the state block's size, and what it will
 * not package.
 */
final class PackagerTest {

    /** Four rows of three columns at this width, under a plain variant. */
    private static byte[] table(int variant, int width) {
        Table table = Csv.table("1,2,3\n4,5,6\n7,8,9\n10,11,12\n", width);
        return variant == Dtx.DTX0 ? Dtx0.write(table) : Dtx1.write(table);
    }

    @Test
    void theFiguresDefineOnlyWhatTheImageCannotReadBack() {
        // R, C and RR reach the code at run time, out of the table's own
        // header, so no equate defines one. What is left is the width, the
        // row's bytes and the state block: one build reads one width, so the
        // move a value takes is assembled from it, and a caller reads the
        // block's size before there is a block to read it from.
        for (int width : new int[] {1, 2, 4}) {
            for (byte[] file : new byte[][] {table(Dtx.DTX0, width),
                    table(Dtx.DTX1, width), packed(64, 3, width, 1, 960)}) {
                int variant = Dtx.header(file).variant();
                String out = Packager.table(file);
                assertTrue(out.contains("DTX_WIDTH\tequ\t" + width),
                        "DTX" + variant + "'s width of " + width);
                assertTrue(out.contains("DTX_ROWBYTES\tequ\t" + 3 * width),
                        "the row's bytes, C of 3 at a width of " + width);
                assertTrue(out.contains("DTX_STATE\tequ\t"), "the state block");
                for (String gone : new String[] {"DTX_ROWS", "DTX_COLUMNS",
                        "DTX_REPEAT", "DTX_VARIANT", "DTX_HEADER"}) {
                    assertTrue(!out.contains(gone + "\tequ\t"),
                            "DTX" + variant + " defines " + gone
                                    + ", so its code moves with the table");
                }
            }
        }
    }

    @Test
    void theFiguresDoNotContainAnInstruction() {
        // 68k/DTX.S is where every instruction stands. What the packager
        // writes is equates and macro invocations, and a move or a bra in
        // it would be an instruction the template does not contain.
        for (int variant : new int[] {Dtx.DTX0, Dtx.DTX1}) {
            for (String line : Packager.table(table(variant, 2)).split("\n")) {
                String read = line.trim();
                assertTrue(!read.startsWith("move") && !read.startsWith("bra")
                                && !read.startsWith("lea") && !read.startsWith("dc."),
                        "the figures contain an instruction: " + read);
            }
        }
    }

    @Test
    void theStateBlockIsTheSameAtEveryWidth() {
        // One width covers the whole table, so one pointer walks every column
        // of it: the block is the head and that pointer at each of the three
        // widths, at any C, and under DTX0 and DTX1 alike.
        for (int width : new int[] {1, 2, 4}) {
            Table one = Csv.table("1\n2\n", width);
            Table three = Csv.table("1,2,3\n4,5,6\n", width);
            assertEquals(20, Packager.stateBytes(Dtx.header(Dtx0.write(three))),
                    "DTX0 of three columns at a width of " + width);
            assertEquals(20, Packager.stateBytes(Dtx.header(Dtx1.write(one))),
                    "DTX1 of one column at a width of " + width);
            assertEquals(20, Packager.stateBytes(Dtx.header(Dtx1.write(three))),
                    "DTX1 of three columns at a width of " + width);
        }
    }

    @Test
    void theFiguresDoNotDefineAMacro() {
        // A template no longer takes a list of macro invocations, one a
        // column: under DTX2 the column table defines what each of them
        // defined and one loop reads it, and under DTX0 and DTX1 a pointer
        // and a stride walk every column. So the figures are equates, and
        // the code they reach does not move with C.
        for (String out : new String[] {Packager.table(table(Dtx.DTX1, 2)),
                Packager.table(packed(64, 2, 2, 1, 960))}) {
            for (String line : out.split("\n")) {
                assertTrue(!line.trim().startsWith(".macro"),
                        "the figures define a macro: " + line);
            }
        }
    }

    @Test
    void aPackedColumnTableContainsAStreamRecordAColumn() {
        byte[] file = packed(64, 2, 2, 1, 960);
        Dtx.Header header = Dtx.header(file);
        byte[] table = Packager.columnTable(file);
        assertEquals(4 * 4 * header.columns(), table.length,
                "four longs a column, and the records are the whole of it");
        int payload = header.length();
        for (int i = 0; i < header.columns(); i++) {
            int at = Packager.STREAM * i;
            int set = Dtx.getLong(file, payload + 4 + 4 * i);
            assertEquals(set + 28, Dtx.getLong(table, at),
                    "column " + i + "'s stream A, past the data set's header");
            for (int stream = 1; stream < 4; stream++) {
                assertEquals(set + Dtx.getLong(file, payload + set + 4 + 4 * stream),
                        Dtx.getLong(table, at + 4 * stream),
                        "column " + i + "'s stream " + (char) ('A' + stream)
                                + ", from the payload's first byte");
            }
        }
    }

    @Test
    void aPlainColumnTableIsEmpty() {
        // Every column is one width, so a pointer and a stride reach them
        // all: a plain read is arithmetic on R, C and the width, and the
        // image is the code and then the table's bytes.
        for (int variant : new int[] {Dtx.DTX0, Dtx.DTX1}) {
            for (int width : new int[] {1, 2, 4}) {
                assertEquals(0,
                        Packager.columnTable(table(variant, width)).length,
                        "DTX" + variant + " at a width of " + width);
            }
        }
    }

    /** A DTX2 file of these rows and columns at this width, at this ring. */
    private static byte[] packed(int rows, int columns, int width, int unit,
            int ring) {
        return packed(rows, columns, width, unit, ring, false);
    }

    /** The same, whose payload defines what the packer packed. */
    private static byte[] packed(int rows, int columns, int width, int unit,
            int ring, boolean copies) {
        byte[][] column = new byte[columns][];
        for (int i = 0; i < columns; i++) {
            column[i] = new byte[rows * width];
        }
        Table table = Table.of(rows, rows, width, column);
        return Dtx2.write(table, new Packer() {

            @Override
            public boolean copies() {
                return copies;
            }

            @Override
            public byte[] pack(byte[] bytes, int k, int n) {
                byte[] set = new byte[28 + bytes.length];
                set[0] = 'S';
                set[1] = '4';
                set[2] = 7;
                set[3] = (byte) k;
                // Three offsets apart, so a record that carried B where C
                // stands fails rather than passing on equal values.
                Dtx.putLong(set, 8, 28 + bytes.length / 4);
                Dtx.putLong(set, 12, 28 + bytes.length / 2);
                Dtx.putLong(set, 16, 28 + bytes.length);
                System.arraycopy(bytes, 0, set, 28, bytes.length);
                return set;
            }
        }, unit, ring);
    }

    @Test
    void thePeriodIsTheSmallestThatMeetsEveryRule() {
        byte[] file = packed(64, 2, 1, 1, 960);
        Dtx.Header header = Dtx.header(file);
        assertEquals(2, Packager.period(header, Packager.packed(file, header)),
                "P is C where C meets the rules");
        byte[] wide = packed(48, 3, 4, 1, 960);
        Dtx.Header at3 = Dtx.header(wide);
        assertEquals(3, Packager.period(at3, Packager.packed(wide, at3)),
                "P is C at three columns of four bytes");
        // N divides by P times the width, so a C that does not divide N
        // takes the first period above C that does: 960 by 7 leaves 1.
        byte[] seven = packed(64, 7, 1, 1, 960);
        Dtx.Header at7 = Dtx.header(seven);
        assertEquals(8, Packager.period(at7, Packager.packed(seven, at7)),
                "the first period above C of 7 that divides N");
    }

    @Test
    void aRingTheWidthDoesNotDivideIsRefused() {
        // A ring of six bytes: a period needs a ring of 2P times the width,
        // so at a width of four the smallest period needs eight bytes, and
        // P is at least C of two.
        byte[] file = packed(64, 2, 4, 1, 6);
        Dtx.Header header = Dtx.header(file);
        String said = assertThrows(IllegalArgumentException.class,
                () -> Packager.period(header, Packager.packed(file, header)))
                .getMessage();
        assertTrue(said != null && said.startsWith(
                "no period from C of 2 to R of 64"),
                "a ring that no period divides");
    }

    @Test
    void aTableOfFortyColumnsIsPackaged() {
        // While a column had a width of its own, a read reached a column by
        // a displacement off its class base, and 40 columns at a ring of 960
        // reached past the 32767 a 68000 displacement runs to. One width for
        // the table ended that rule: a column's ring stands its own number
        // times N from the first, so C of 40 packages.
        byte[] file = packed(64, 40, 1, 1, 960);
        Dtx.Header header = Dtx.header(file);
        assertEquals(40, Packager.period(header, Packager.packed(file, header)),
                "P is C at forty columns");
    }

    @Test
    void aPackedStateBlockContainsADecoderStateAndARingAColumn() {
        byte[] file = packed(64, 2, 2, 1, 960);
        Dtx.Header header = Dtx.header(file);
        Packager.Packed given = Packager.packed(file, header);
        assertEquals(52, Packager.decoders(header),
                "the decoder states follow the pointer, the payload, the"
                        + " records, the fill, C, the rings and the four"
                        + " figures, where 68k/DTX2.S puts them:"
                        + " DTX_DECODERS equ 52");
        assertEquals(52 + 32 * 2, Packager.ring(header),
                "the rings follow two decoder states of 32 bytes");
        assertEquals(52 + 32 * 2 + 2 * 960, Packager.stateBytes(header, given),
                "a ring a column");
    }

    @Test
    void theCopyCodeIsNeededOnlyWhereTheColumnsContainCopies() {
        // The payload defines it (R5.10), so no word from a caller enters
        // this: the file fixes which decoder reads it.
        byte[] plain = packed(64, 2, 2, 1, 960);
        byte[] copies = packed(64, 2, 2, 1, 960, true);
        assertTrue(!Packager.table(plain).contains("ST4_WINDOW"),
                "a table packed without copies does not need the copy code");
        assertTrue(Packager.table(copies).contains("ST4_WINDOW\tequ\t1"),
                "a table packed with copies needs it");
        assertTrue(!Packager.packed(plain, Dtx.header(plain)).copies(),
                "the plain payload does not flag copies");
        assertTrue(Packager.packed(copies, Dtx.header(copies)).copies(),
                "the other flags them");
    }

    @Test
    void aDataSetThatDoesNotDefineThePayloadsUnitIsRefused() {
        // R5.2: the k a payload defines and the k in every data set's own
        // signature are the same, and the packager checks one against the
        // other. One compare checks ST4's signature, its version and the
        // unit at once.
        byte[] file = packed(64, 2, 2, 1, 960);
        Dtx.Header header = Dtx.header(file);
        int at = header.length() + Dtx.getLong(file, header.length() + 4 + 4);
        file[at + 3] = 2;                       // column 1 now defines k of 2
        assertEquals("column 1's data set opens 53340702 and the payload"
                + " defines 53340701: an ST4 data set opens with S4, the"
                + " format version 7 and the payload's own k",
                assertThrows(IllegalArgumentException.class,
                        () -> Packager.packed(file, header)).getMessage());
    }

    @Test
    void aDataSetOfAnotherFormatVersionIsRefused() {
        byte[] file = packed(64, 2, 2, 1, 960);
        Dtx.Header header = Dtx.header(file);
        int at = header.length() + Dtx.getLong(file, header.length() + 4);
        file[at + 2] = 6;                       // the version before this one
        assertEquals("column 0's data set opens 53340601 and the payload"
                + " defines 53340701: an ST4 data set opens with S4, the"
                + " format version 7 and the payload's own k",
                assertThrows(IllegalArgumentException.class,
                        () -> Packager.packed(file, header)).getMessage());
    }

    @Test
    void thePackedFiguresDefineThePeriodTheRingAndTheUnit() {
        String out = Packager.table(packed(64, 2, 2, 1, 960));
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
        byte[] image = Packager.image(table(Dtx.DTX0, 2), rmac);
        assertEquals(0x60, image[0] & 0xFF, "the first slot is a bra.w");
        assertEquals("DTX", new String(image, 16, 3),
                "the format block behind the four slots");
        assertEquals(0, image[19], "the variant the format block defines");
        assertEquals(20, Dtx.getLong(image, 20), "the state block's bytes");
        int header = Dtx.getLong(image, 24);
        assertEquals("DTX", new String(image, header, 3),
                "the header the format block points at");
        // The width byte stands beside the unit at +19 of the block, so at
        // 35 of the image: the format defines the place, and reading it
        // there checks the packager against the format.
        assertEquals(0, image[35],
                "DTX0 reads a row as one run of bytes, so its code does not"
                        + " move with the width and the byte is zero");
        // DTX1 moves a value in one instruction, one build a width, and the
        // width byte is where a combine checks the code against the table.
        byte[] one = Packager.image(table(Dtx.DTX1, 4), rmac);
        assertEquals(4, one[35], "the width DTX1's code reads values at");
        // The stride a caller steps from one column's value to the next,
        // which DTX_metadata gives out of the block at +24.
        assertEquals(2, Dtx.getLong(image, 16 + Packager.STRIDE_AT),
                "DTX0 strides by the width");
        assertEquals(Dtx1.stride(4, 4), Dtx.getLong(one, 16 + Packager.STRIDE_AT),
                "DTX1 strides by a column's length");
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
