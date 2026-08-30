package org.dtx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * DTX2's layout, with a packer that stands in for ST4: a twenty-byte header
 * and the column behind it. What it packs to does not matter here, only
 * where the payload puts it.
 */
final class PackedVariantTest {

    /** How long a stand-in data set runs for a column of {@code bytes}. */
    private static final int ST4_HEADER = 20;

    private static final Packer STANDIN = (column, unit, ring) -> {
        byte[] set = new byte[ST4_HEADER + column.length];
        set[0] = 'S';
        set[1] = '4';
        set[2] = 4;
        set[3] = (byte) unit;
        System.arraycopy(column, 0, set, ST4_HEADER, column.length);
        return set;
    };

    @Test
    void thePayloadStatesTheRingAndTheUnitOnceAndThenAnOffsetAColumn() {
        byte[] file = Dtx2.write(Example.table(), STANDIN, 1, 960);
        int payload = Example.HEADER;
        assertEquals(2, file[3], "the variant");
        assertEquals(960, Dtx.getWord(file, payload), "N");
        assertEquals(1, file[payload + 2], "k");
        assertEquals(0, file[payload + 3], "the byte after k");
        assertArrayEquals(new int[] {16, 40, 72}, offsets(file));
    }

    @Test
    void everyDataSetBeginsOnALongAndThePadBetweenIsZero() {
        byte[] file = Dtx2.write(Example.table(), STANDIN, 1, 960);
        int payload = Example.HEADER;
        for (int at : offsets(file)) {
            assertEquals(0, at % 4, "a data set at " + at + " is off a long");
            assertEquals('S', file[payload + at], "a data set at " + at);
        }
        // column 0 packs to 23 bytes from offset 16, so one byte to the long
        assertEquals(0, file[payload + 39], "the pad before the next data set");
        assertEquals(Example.HEADER + 98, file.length);
    }

    @Test
    void aDataSetHoldsTheColumnDtx1HoldsInTheSameOrder() {
        byte[] file = Dtx2.write(Example.table(), STANDIN, 1, 960);
        int payload = Example.HEADER;
        int[] at = offsets(file);
        assertArrayEquals(Example.A,
                Example.at(file, payload + at[0] + ST4_HEADER, 3));
        assertArrayEquals(Example.B,
                Example.at(file, payload + at[1] + ST4_HEADER, 12));
        assertArrayEquals(Example.C,
                Example.at(file, payload + at[2] + ST4_HEADER, 6));
    }

    @Test
    void aDtx0FileAndADtx1FileOfOneTableGiveTheSameDtx2File() {
        Table table = Example.table();
        assertArrayEquals(
                Dtx2.from(Dtx0.write(table), STANDIN, 1, 960),
                Dtx2.from(Dtx1.write(table), STANDIN, 1, 960));
        assertArrayEquals(
                Dtx2.write(table, STANDIN, 1, 960),
                Dtx2.from(Dtx0.write(table), STANDIN, 1, 960));
    }

    @Test
    void theHeaderIsTheSameUnderEveryVariantButTheVariantByte() {
        Table table = Example.table();
        byte[] zero = Dtx0.write(table);
        byte[] two = Dtx2.write(table, STANDIN, 1, 960);
        assertArrayEquals(Example.at(zero, 4, 16), Example.at(two, 4, 16),
                "everything the header states but the variant");
    }

    @Test
    void aUnitOrRingOutsideItsBoundsIsRejected() {
        Table table = Example.table();
        assertEquals("R is 3, which does not divide by k of 2", assertThrows(
                IllegalArgumentException.class,
                () -> Dtx2.write(table, STANDIN, 2, 960)).getMessage());
        assertEquals("k is 1, 2 or 4, not 3", assertThrows(
                IllegalArgumentException.class,
                () -> Dtx2.write(table, STANDIN, 3, 960)).getMessage());
        assertEquals("N is 1 to 65535, not 65536", assertThrows(
                IllegalArgumentException.class,
                () -> Dtx2.write(table, STANDIN, 1, 65536)).getMessage());
    }

    @Test
    void aTableOfRowsThatDivideTakesEveryUnit() {
        byte[][] column = {new byte[4], new byte[16], new byte[8]};
        Table table = Table.of(4, 0, Example.WIDTH, column);
        for (int unit : new int[] {1, 2, 4}) {
            byte[] file = Dtx2.write(table, STANDIN, unit, 480);
            assertEquals(unit, file[Example.HEADER + 2], "k");
            assertEquals(480, Dtx.getWord(file, Example.HEADER), "N");
        }
    }

    /** The offset a column, out of a DTX2 payload. */
    private static int[] offsets(byte[] file) {
        int payload = Example.HEADER;
        int columns = Dtx.getWord(file, 8);
        int[] at = new int[columns];
        for (int i = 0; i < columns; i++) {
            at[i] = Dtx.getLong(file, payload + 4 + 4 * i);
        }
        return at;
    }
}
