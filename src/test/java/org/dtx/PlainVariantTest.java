package org.dtx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * DTX0 and DTX1 against the pictures SPEC.md 2.1 and 2.2 draw of the same
 * table, byte for byte, and against each other through a round trip.
 */
final class PlainVariantTest {

    @Test
    void theHeaderIsWhatSectionOneDraws() {
        byte[] file = Dtx0.write(Example.table());
        assertEquals(Example.HEADER, Dtx.HEADER);
        assertArrayEquals(new byte[] {'D', 'T', 'X', 0}, Example.at(file, 0, 4));
        assertEquals(3, Dtx.getLong(file, 4), "R");
        assertEquals(3, Dtx.getWord(file, 8), "C");
        assertEquals(Example.REPEAT, Dtx.getLong(file, 10), "RR");
        assertEquals(Example.WIDTH, file[14], "the width");
        assertEquals(0, file[15], "the pad to a long");
    }

    @Test
    void dtx0IsARowAtATimeWithNothingBetweenTheColumns() {
        byte[] file = Dtx0.write(Example.table());
        assertEquals(Example.HEADER + Example.DTX0_PAYLOAD, file.length);
        assertArrayEquals(new byte[] {
            0x11, 0x12, 0x40, 0x41, 0x70, 0x71,
            0x21, 0x22, 0x50, 0x51, (byte) 0x80, (byte) 0x81,
            0x31, 0x32, 0x60, 0x61, (byte) 0x90, (byte) 0x91},
                Example.at(file, Example.HEADER, Example.DTX0_PAYLOAD));
    }

    @Test
    void dtx1IsAColumnAtATimeAtOneStride() {
        byte[] file = Dtx1.write(Example.table());
        assertEquals(Example.HEADER + Example.DTX1_PAYLOAD, file.length);
        assertEquals(6, Dtx1.stride(Example.ROWS, Example.WIDTH));
        assertEquals(1, file[3], "the variant");
        assertArrayEquals(new byte[] {
            0x11, 0x12, 0x21, 0x22, 0x31, 0x32,
            0x40, 0x41, 0x50, 0x51, 0x60, 0x61,
            0x70, 0x71, (byte) 0x80, (byte) 0x81, (byte) 0x90, (byte) 0x91},
                Example.at(file, Example.HEADER, Example.DTX1_PAYLOAD));
    }

    @Test
    void aColumnBeginsOnAWord() {
        // At a width of 2 or 4 a column is a whole number of words already,
        // so DTX1 is the same length as DTX0 (R4.3). At a width of 1 and an
        // odd R one zero byte stands between one column and the next.
        assertEquals(0, Dtx1.write(Example.table()).length
                - Dtx0.write(Example.table()).length);
        Table odd = Table.of(3, 3, 1, new byte[][] {
            {1, 2, 3}, {4, 5, 6}});
        assertEquals(4, Dtx1.stride(3, 1));
        assertEquals(7, Dtx1.payloadLength(3, 2, 1));
        byte[] file = Dtx1.write(odd);
        assertArrayEquals(new byte[] {1, 2, 3, 0, 4, 5, 6},
                Example.at(file, Dtx.HEADER, 7));
        assertEquals(odd, Dtx1.read(file));
    }

    @Test
    void eachVariantReadsBackTheTableTheOtherWrote() {
        Table table = Example.table();
        assertEquals(table, Dtx0.read(Dtx0.write(table)));
        assertEquals(table, Dtx1.read(Dtx1.write(table)));
        assertEquals(table, Dtx.read(Dtx0.write(table)));
        assertEquals(table, Dtx.read(Dtx1.write(table)));
        assertArrayEquals(Dtx1.write(table),
                Dtx1.write(Dtx0.read(Dtx0.write(table))),
                "DTX1 out of a DTX0 file");
        assertArrayEquals(Dtx0.write(table),
                Dtx0.write(Dtx1.read(Dtx1.write(table))),
                "DTX0 out of a DTX1 file");
    }

    @Test
    void aVariantReadsOnlyItsOwn() {
        byte[] one = Dtx1.write(Example.table());
        assertEquals("variant 1 is not DTX0", assertThrows(
                IllegalArgumentException.class,
                () -> Dtx0.read(one)).getMessage());
        byte[] two = Dtx0.write(Example.table());
        assertEquals("variant 0 is not DTX1", assertThrows(
                IllegalArgumentException.class,
                () -> Dtx1.read(two)).getMessage());
    }

    @Test
    void aFileShortOfWhatItsHeaderDefinesIsRejected() {
        byte[] file = Dtx0.write(Example.table());
        byte[] cut = Example.at(file, 0, file.length - 1);
        assertEquals("a payload of 17 bytes is short of 18", assertThrows(
                IllegalArgumentException.class,
                () -> Dtx0.read(cut)).getMessage());
    }
}
