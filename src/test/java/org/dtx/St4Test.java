package org.dtx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * DTX2 through a real ST4 packer.
 *
 * <p>The packer is the copy in this repository, so these run where the
 * build does. The last of them checks that copy against an executable beside
 * it, and is the one skipped without one at {@code ST4}.
 */
final class St4Test {

    /** The widths R6.3 defines: 1, 2 or 4 bytes a value. */
    private static final int[] WIDTHS = {1, 2, 4};

    private static Path packer() {
        String named = System.getenv("ST4");
        Path path = Path.of(named == null ? "st4" : named);
        Assumptions.assumeTrue(Files.isExecutable(path),
                "no ST4 packer at " + path);
        return path;
    }

    /**
     * A table of 64 rows at {@code width}, whose three columns pack well, so
     * a data set is real. The three columns' values run 0 to 3, 0 to 7 and
     * 0 to 15, in the last byte of a value.
     */
    private static Table table(int width) {
        int rows = 64;
        byte[][] column = new byte[3][];
        for (int c = 0; c < column.length; c++) {
            column[c] = new byte[rows * width];
            for (int n = 0; n < rows; n++) {
                column[c][n * width + width - 1] = (byte) (n % (4 << c));
            }
        }
        return Table.of(rows, 0, width, column);
    }

    @Test
    void everyDataSetOpensWithTheSignatureAndTheUnitThePayloadDefines()
            throws Exception {
        Packer st4 = new St4();
        for (int width : WIDTHS) {
            for (int unit : new int[] {1, 2, 4}) {
                byte[] file = Dtx2.write(table(width), st4, unit, 960);
                int payload = Dtx.HEADER;
                assertEquals(unit, file[payload + 2],
                        "the payload's k at W=" + width);
                for (int i = 0; i < 3; i++) {
                    int at = payload + Dtx.getLong(file, payload + 4 + 4 * i);
                    assertEquals(0, (at - payload) % 4,
                            "a data set off a long at k=" + unit
                                    + " and W=" + width);
                    assertArrayEquals(
                            new byte[] {0x53, 0x34, 0x07, (byte) unit},
                            Example.at(file, at, 4),
                            "column " + i + "'s signature at k=" + unit
                                    + " and W=" + width);
                }
            }
        }
    }

    @Test
    void aColumnPackedWithCopiesRunsToFewerBytesAtASmallRing()
            throws Exception {
        // A column that repeats a pattern further back than the ring
        // reaches: what copies from the literal stream are for.
        // The pattern runs 101 rows, further back than a ring of 64
        // bytes reaches, so the only match for it is a copy.
        byte[] column = new byte[512];
        for (int r = 0; r < column.length; r++) {
            column[r] = (byte) (r % 101);
        }
        Table table = Table.of(512, 512, 1, new byte[][] {column});
        byte[] plain = Dtx2.write(table, new St4(), 1, 64);
        byte[] copies = Dtx2.write(table, new St4(true, 0), 1, 64);
        assertTrue(copies.length < plain.length, "copies " + copies.length
                + " bytes against " + plain.length + " without them");
    }

    @Test
    void aPackedTableRunsToFewerBytesThanTheColumnsItContains()
            throws Exception {
        for (int width : WIDTHS) {
            Table table = table(width);
            byte[] plain = Dtx1.write(table);
            byte[] packed = Dtx2.from(plain, new St4(), 1, 960);
            assertTrue(packed.length < plain.length, "DTX2 " + packed.length
                    + " bytes against DTX1's " + plain.length
                    + " at W=" + width);
        }
    }

    @Test
    void theCarriedPackerPacksWhatAnExecutableBesideItPacks() throws Exception {
        // src/main/java/org/st4 is a copy of ST4's own packer, and a copy
        // that packed otherwise would be a second packer rather than the
        // same one. The executable is run with -l65535, ST4_wrap's
        // assumption 4, and the carried packer passes the same figure, so
        // one set of bytes from the two covers the longest operation as
        // well. Skipped where no executable stands beside this to check it
        // to.
        Path beside = packer();
        for (int width : WIDTHS) {
            for (int unit : new int[] {1, 2, 4}) {
                assertArrayEquals(
                        Dtx2.write(table(width), new St4Beside(beside), unit,
                                960),
                        Dtx2.write(table(width), new St4(), unit, 960),
                        "the two packers at k=" + unit + " and W=" + width);
            }
        }
        byte[] column = new byte[512];
        for (int r = 0; r < column.length; r++) {
            column[r] = (byte) (r % 101);
        }
        Table repeats = Table.of(512, 512, 1, new byte[][] {column});
        assertArrayEquals(
                Dtx2.write(repeats, new St4Beside(beside, "-c"), 1, 64),
                Dtx2.write(repeats, new St4(true, 0), 1, 64),
                "the two packers with copies");
    }
}
