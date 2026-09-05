package org.dtx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Comma separated text into a table, and that table out under every variant.
 *
 * <p>The text is the source a writer takes; what it builds is the same
 * {@code Table} every variant writes, so a row read back out of a DTX0 or a
 * DTX1 file gives the values the text gave.
 */
final class CsvTest {

    private static final String TEXT = """
            # a comment, and the blank line below it

            1, 300, -2
            2, 301, -1
            3, 302,  0
            """;

    @Test
    void everyColumnTakesTheNarrowestWidthThatFitsIt() {
        assertArrayEquals(new int[] {1, 2, 1}, Csv.widths(TEXT));
    }

    @Test
    void aValueIsStoredMostSignificantByteFirst() {
        Table table = Csv.table(TEXT);
        assertEquals(3, table.rows(), "R");
        assertEquals(3, table.columns(), "C");
        assertEquals(3, table.repeat(), "RR, where the table does not repeat");
        assertArrayEquals(new byte[] {1, 2, 3}, table.column(0));
        assertArrayEquals(new byte[] {1, 44, 1, 45, 1, 46}, table.column(1));
    }

    @Test
    void aNegativeValueIsStoredInTwosComplement() {
        assertArrayEquals(new byte[] {(byte) 0xFE, (byte) 0xFF, 0},
                Csv.table(TEXT).column(2));
    }

    @Test
    void aValueOpeningWithADollarIsHexadecimal() {
        Table table = Csv.table("$10, -$1\n$FF, $7FFF\n");
        assertArrayEquals(new byte[] {16, (byte) 0xFF}, table.column(0));
        assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xFF, 0x7F, (byte) 0xFF},
                table.column(1));
    }

    @Test
    void theWidthsGivenAreTakenOverTheNarrowest() {
        Table table = Csv.table(TEXT, new int[] {4, 2, 2});
        assertEquals(4, table.width(0));
        assertArrayEquals(new byte[] {0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 3},
                table.column(0));
    }

    @Test
    void theRepeatRowGivenReachesTheTable() {
        assertEquals(1, Csv.table(TEXT, new int[] {1, 2, 1}, 1).repeat());
    }

    @Test
    void everyVariantWritesTheTableTheTextGives() {
        Table table = Csv.table(TEXT);
        assertEquals(table, Dtx0.read(Dtx0.write(table)));
        assertEquals(table, Dtx1.read(Dtx1.write(table)));
    }

    @Test
    void whatTheTextDoesNotGiveIsReported() {
        assertEquals("line 2 holds 2 values, not 3",
                assertThrows(IllegalArgumentException.class,
                        () -> Csv.table("1,2,3\n4,5\n")).getMessage());
        assertEquals("line 1 column 1 gives \"x\", which is not a number",
                assertThrows(IllegalArgumentException.class,
                        () -> Csv.table("1,x\n")).getMessage());
        assertEquals("the text does not contain a row",
                assertThrows(IllegalArgumentException.class,
                        () -> Csv.table("# nothing but a comment\n")).getMessage());
        assertEquals("row 0 column 0 gives 300, which 1 bytes do not take",
                assertThrows(IllegalArgumentException.class,
                        () -> Csv.table("300\n", new int[] {1})).getMessage());
        assertEquals("a column is 1, 2 or 4 bytes wide, not 3",
                assertThrows(IllegalArgumentException.class,
                        () -> Csv.table("1\n", new int[] {3})).getMessage());
        assertEquals("2 widths for 1 columns",
                assertThrows(IllegalArgumentException.class,
                        () -> Csv.table("1\n", new int[] {1, 1})).getMessage());
    }

    @Test
    void aValueNoWidthTakesIsReported() {
        assertEquals("column 0 gives 4294967296, which no width of 1, 2 or 4"
                + " bytes takes", assertThrows(IllegalArgumentException.class,
                        () -> Csv.table("4294967296\n")).getMessage());
    }
}
