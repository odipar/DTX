package org.dtx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * DTX2's layout, with a packer that stands in for ST4: a twenty-eight byte
 * header and the column behind it. What it packs to does not matter here,
 * only where the payload puts it.
 */
final class PackedVariantTest {

    /** How long a stand-in data set runs for a column of {@code bytes}. */
    private static final int ST4_HEADER = 28;

    private static final Packer STANDIN = (column, unit, ring) -> {
        byte[] set = new byte[ST4_HEADER + column.length];
        set[0] = 'S';
        set[1] = '4';
        set[2] = 7;
        set[3] = (byte) unit;
        System.arraycopy(column, 0, set, ST4_HEADER, column.length);
        return set;
    };

    @Test
    void thePayloadDefinesTheRingAndTheUnitOnceAndThenAnOffsetAColumn() {
        byte[] file = Dtx2.write(Example.table(), STANDIN, 1, 960);
        int payload = Example.HEADER;
        assertEquals(2, file[3], "the variant");
        assertEquals(960, Dtx.getWord(file, payload), "N");
        assertEquals(1, file[payload + 2], "k");
        assertEquals(0, file[payload + 3], "the byte after k");
        assertArrayEquals(new int[] {16, 52, 88}, offsets(file));
    }

    @Test
    void everyDataSetBeginsOnALongAndThePadBetweenIsZero() {
        byte[] file = Dtx2.write(Example.table(), STANDIN, 1, 960);
        int payload = Example.HEADER;
        for (int at : offsets(file)) {
            assertEquals(0, at % 4, "a data set at " + at + " is off a long");
            assertEquals('S', file[payload + at], "a data set at " + at);
        }
        // column 0 packs to 34 bytes from offset 16, so two bytes to the long
        assertArrayEquals(new byte[] {0, 0}, Example.at(file, payload + 50, 2),
                "the pad before the next data set");
        assertEquals(Example.HEADER + 122, file.length);
    }

    @Test
    void aDataSetContainsTheColumnDtx1WritesInTheSameOrder() {
        byte[] file = Dtx2.write(Example.table(), STANDIN, 1, 960);
        int payload = Example.HEADER;
        int[] at = offsets(file);
        int bytes = Example.ROWS * Example.WIDTH;
        assertArrayEquals(Example.A,
                Example.at(file, payload + at[0] + ST4_HEADER, bytes));
        assertArrayEquals(Example.B,
                Example.at(file, payload + at[1] + ST4_HEADER, bytes));
        assertArrayEquals(Example.C,
                Example.at(file, payload + at[2] + ST4_HEADER, bytes));
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
        assertArrayEquals(Example.at(zero, 4, Example.HEADER - 4),
                Example.at(two, 4, Example.HEADER - 4),
                "everything the header defines but the variant");
    }

    @Test
    void aUnitOrRingOutsideItsBoundsIsRejected() {
        Table table = Example.table();
        assertEquals("a column is 3 times 2 bytes, which does not divide by"
                + " k of 4", assertThrows(
                IllegalArgumentException.class,
                () -> Dtx2.write(table, STANDIN, 4, 960)).getMessage());
        assertEquals("k is 1, 2 or 4, not 3", assertThrows(
                IllegalArgumentException.class,
                () -> Dtx2.write(table, STANDIN, 3, 960)).getMessage());
        assertEquals("N is 1 to 65535, not 65536", assertThrows(
                IllegalArgumentException.class,
                () -> Dtx2.write(table, STANDIN, 1, 65536)).getMessage());
    }

    @Test
    void aTableWhoseColumnBytesDivideTakesEveryUnitAtEveryWidth() {
        for (int width : new int[] {1, 2, 4}) {
            byte[][] column = {new byte[4 * width], new byte[4 * width],
                new byte[4 * width]};
            Table table = Table.of(4, 0, width, column);
            for (int unit : new int[] {1, 2, 4}) {
                byte[] file = Dtx2.write(table, STANDIN, unit, 480);
                assertEquals(unit, file[Example.HEADER + 2],
                        "k at a width of " + width);
                assertEquals(480, Dtx.getWord(file, Example.HEADER),
                        "N at a width of " + width);
            }
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

    @Test
    void aDtx2FileReadsBackToTheTableItWasWrittenFrom() {
        for (int unit : new int[] {1, 2, 4}) {
            for (int width : new int[] {1, 2, 4}) {
                Table wide = Csv.table(Rig.numbers(64, 2), width, 16);
                byte[] file = Dtx2.write(wide, new St4(), unit, 960);
                String at = "k of " + unit + " at a width of " + width;
                assertEquals(wide, Dtx2.read(file), at);
                assertEquals(wide, Dtx.read(file), at + " through Dtx.read");
            }
        }
        for (int width : new int[] {1, 2, 4}) {
            Table table = Csv.table(Rig.numbers(64, 3), width, 16);
            byte[] copies = Dtx2.write(table, new St4(true, 0), 1, 64);
            assertEquals(table, Dtx2.read(copies),
                    "copies at a ring of 64, a width of " + width);
            byte[] plain = Dtx1.write(table);
            assertEquals(Dtx2.read(Dtx2.from(plain, new St4(), 1, 960)), table,
                    "DTX1 to DTX2 and back at a width of " + width);
            assertEquals(Dtx2.read(Dtx2.from(copies, new St4(), 2, 960)), table,
                    "DTX2 to DTX2 at another unit and back, a width of "
                            + width);
        }
    }

    @Test
    void aDataSetOpeningWithAnotherUnitIsRefusedOnRead() {
        Table table = Csv.table(Rig.numbers(8, 1), 2, 8);
        byte[] file = Dtx2.write(table, new St4(), 2, 960);
        file[Dtx.HEADER + 2] = 1;
        assertThrows(IllegalArgumentException.class, () -> Dtx2.read(file));
    }

}
