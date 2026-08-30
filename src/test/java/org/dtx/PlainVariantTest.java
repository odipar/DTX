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
        assertEquals(Example.HEADER, Dtx.headerLength(3));
        assertArrayEquals(new byte[] {'D', 'T', 'X', 0}, Example.at(file, 0, 4));
        assertEquals(3, Dtx.getLong(file, 4), "R");
        assertEquals(3, Dtx.getWord(file, 8), "C");
        assertEquals(Example.REPEAT, Dtx.getLong(file, 10), "RR");
        assertArrayEquals(new byte[] {1, 4, 2}, Example.at(file, 14, 3),
                "the widths");
        assertArrayEquals(new byte[] {0, 0, 0}, Example.at(file, 17, 3),
                "the pad to a long");
    }

    @Test
    void dtx0IsARowAtATimeWithNothingBetweenTheColumns() {
        byte[] file = Dtx0.write(Example.table());
        assertEquals(Example.HEADER + Example.DTX0_PAYLOAD, file.length);
        assertArrayEquals(new byte[] {
            0x11, 0x40, 0x41, 0x42, 0x43, 0x70, 0x71,
            0x22, 0x50, 0x51, 0x52, 0x53, (byte) 0x80, (byte) 0x81,
            0x33, 0x60, 0x61, 0x62, 0x63, (byte) 0x90, (byte) 0x91},
                Example.at(file, Example.HEADER, Example.DTX0_PAYLOAD));
    }

    @Test
    void dtx1IsAColumnAtATimeAndEachColumnBeginsOnAWord() {
        byte[] file = Dtx1.write(Example.table());
        assertEquals(Example.HEADER + Example.DTX1_PAYLOAD, file.length);
        assertArrayEquals(new int[] {0, 4, 16},
                Dtx1.offsets(Example.ROWS, Example.WIDTH));
        assertEquals(1, file[3], "the variant");
        assertArrayEquals(new byte[] {
            0x11, 0x22, 0x33, 0,
            0x40, 0x41, 0x42, 0x43, 0x50, 0x51, 0x52, 0x53,
            0x60, 0x61, 0x62, 0x63,
            0x70, 0x71, (byte) 0x80, (byte) 0x81, (byte) 0x90, (byte) 0x91},
                Example.at(file, Example.HEADER, Example.DTX1_PAYLOAD));
        for (int at : Dtx1.offsets(Example.ROWS, Example.WIDTH)) {
            assertEquals(0, at % 2, "column at " + at + " is off a word");
        }
    }

    @Test
    void dtx1CostsOneByteOverDtx0Here() {
        assertEquals(1, Dtx1.write(Example.table()).length
                - Dtx0.write(Example.table()).length);
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
    void aFileShortOfWhatItsHeaderStatesIsRejected() {
        byte[] file = Dtx0.write(Example.table());
        byte[] cut = Example.at(file, 0, file.length - 1);
        assertEquals("a payload of 20 bytes is short of 21", assertThrows(
                IllegalArgumentException.class,
                () -> Dtx0.read(cut)).getMessage());
    }
}
