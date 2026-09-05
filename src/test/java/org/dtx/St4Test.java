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
 * <p>The packer is the copy this repository holds, so these run wherever
 * the build does. The last of them holds that copy to an executable beside
 * it, and is the one skipped without one at {@code ST4}.
 */
final class St4Test {

    private static Path packer() {
        String named = System.getenv("ST4");
        Path path = Path.of(named == null ? "st4" : named);
        Assumptions.assumeTrue(Files.isExecutable(path),
                "no ST4 packer at " + path);
        return path;
    }

    /** A table of 64 rows whose columns pack well, so a data set is real. */
    private static Table table() {
        int rows = 64;
        byte[] one = new byte[rows];
        byte[] two = new byte[rows * 2];
        byte[] four = new byte[rows * 4];
        for (int n = 0; n < rows; n++) {
            one[n] = (byte) (n % 4);
            two[n * 2 + 1] = (byte) (n % 8);
            four[n * 4 + 3] = (byte) (n % 16);
        }
        return Table.of(rows, 0, new int[] {1, 2, 4},
                new byte[][] {one, two, four});
    }

    @Test
    void everyDataSetOpensWithTheSignatureAndTheUnitThePayloadStates()
            throws Exception {
        Packer st4 = new St4();
        for (int unit : new int[] {1, 2, 4}) {
            byte[] file = Dtx2.write(table(), st4, unit, 960);
            int payload = Dtx.headerLength(3);
            assertEquals(unit, file[payload + 2], "the payload's k");
            for (int i = 0; i < 3; i++) {
                int at = payload + Dtx.getLong(file, payload + 4 + 4 * i);
                assertEquals(0, (at - payload) % 4,
                        "a data set off a long at k=" + unit);
                assertArrayEquals(
                        new byte[] {0x53, 0x34, 0x07, (byte) unit},
                        Example.at(file, at, 4),
                        "column " + i + "'s signature at k=" + unit);
            }
        }
    }

    @Test
    void everyColumnIsPackedWithTheOperationLengthTheDecodersCount()
            throws Exception {
        // ST4_wrap assumption 4 asks for -l65535, and a packer that did not
        // take the flag would fail rather than pack.
        byte[] file = Dtx2.write(table(), new St4(), 1, 960);
        assertEquals(2, file[3], "the variant");
    }

    @Test
    void aColumnPackedWithCopiesRunsToFewerBytesAtASmallRing()
            throws Exception {
        // A column that repeats a pattern further back than the ring
        // reaches: what copies from the literal stream are for.
        // The pattern runs 101 rows, further back than a ring of 64
        // bytes reaches, so a match for it is a copy or it is nothing.
        byte[] column = new byte[512];
        for (int r = 0; r < column.length; r++) {
            column[r] = (byte) (r % 101);
        }
        Table table = Table.of(512, 512, new int[] {1}, new byte[][] {column});
        byte[] plain = Dtx2.write(table, new St4(), 1, 64);
        byte[] copies = Dtx2.write(table, new St4(true, 0), 1, 64);
        assertTrue(copies.length < plain.length, "copies " + copies.length
                + " bytes against " + plain.length + " without them");
    }

    @Test
    void aPackedTableRunsToFewerBytesThanTheColumnsItHolds() throws Exception {
        Table table = table();
        byte[] plain = Dtx1.write(table);
        byte[] packed = Dtx2.from(plain, new St4(), 1, 960);
        assertTrue(packed.length < plain.length, "DTX2 " + packed.length
                + " bytes against DTX1's " + plain.length);
    }

    @Test
    void theCarriedPackerPacksWhatAnExecutableBesideItPacks() throws Exception {
        // src/main/java/org/st4 is a copy of ST4's own packer, and a copy
        // that packed otherwise would be a second packer rather than the
        // same one. Skipped where no executable stands beside this to hold
        // it to.
        Path beside = packer();
        for (int unit : new int[] {1, 2, 4}) {
            assertArrayEquals(
                    Dtx2.write(table(), new St4Beside(beside), unit, 960),
                    Dtx2.write(table(), new St4(), unit, 960),
                    "the two packers at k=" + unit);
        }
        byte[] column = new byte[512];
        for (int r = 0; r < column.length; r++) {
            column[r] = (byte) (r % 101);
        }
        Table repeats = Table.of(512, 512, new int[] {1},
                new byte[][] {column});
        assertArrayEquals(
                Dtx2.write(repeats, new St4Beside(beside, "-c"), 1, 64),
                Dtx2.write(repeats, new St4(true, 0), 1, 64),
                "the two packers with copies");
    }
}
