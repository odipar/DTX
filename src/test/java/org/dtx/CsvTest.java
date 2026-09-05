package org.dtx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Comma separated text into a table, and that table out under every variant.
 *
 * <p>The text is the source a writer takes; what it builds is the same
 * {@code Table} every variant writes, one width over the whole table (R6.3),
 * so a row read back out of a DTX0 or a DTX1 file gives the values the text
 * gave.
 */
final class CsvTest {

    private static final String TEXT = """
            # a comment, and the blank line below it

            1, 300, -2
            2, 301, -1
            3, 302,  0
            """;

    /** The three widths R6.3 defines. */
    private static final int[] WIDTHS = {1, 2, 4};

    @Test
    void theTableTakesTheNarrowestWidthThatFitsEveryValue() {
        // 300 does not fit one byte, so every value of the table takes two.
        assertEquals(2, Csv.width(TEXT));
        assertEquals(1, Csv.width("1, -2\n3, 255\n"));
        assertEquals(4, Csv.width("1, 2\n3, 70000\n"));
    }

    @Test
    void aValueIsStoredMostSignificantByteFirst() {
        Table table = Csv.table(TEXT);
        assertEquals(3, table.rows(), "R");
        assertEquals(3, table.columns(), "C");
        assertEquals(2, table.width(), "W");
        assertEquals(3, table.repeat(), "RR, where the table does not repeat");
        assertArrayEquals(new byte[] {0, 1, 0, 2, 0, 3}, table.column(0));
        assertArrayEquals(new byte[] {1, 44, 1, 45, 1, 46}, table.column(1));
    }

    @Test
    void aNegativeValueIsStoredInTwosComplement() {
        assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xFE,
            (byte) 0xFF, (byte) 0xFF, 0, 0}, Csv.table(TEXT).column(2));
        assertArrayEquals(new byte[] {(byte) 0xFE, (byte) 0xFF, 0},
                Csv.table("1, -2\n2, -1\n3, 0\n").column(1));
    }

    @Test
    void aValueOpeningWithADollarIsHexadecimal() {
        Table table = Csv.table("$10, -$1\n$FF, $7FFF\n");
        assertEquals(2, table.width(), "$7FFF does not fit one byte");
        assertArrayEquals(new byte[] {0, 16, 0, (byte) 0xFF}, table.column(0));
        assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xFF, 0x7F, (byte) 0xFF},
                table.column(1));
        Table narrow = Csv.table("$10, -$1\n$FF, $7F\n");
        assertEquals(1, narrow.width());
        assertArrayEquals(new byte[] {16, (byte) 0xFF}, narrow.column(0));
        assertArrayEquals(new byte[] {(byte) 0xFF, 0x7F}, narrow.column(1));
    }

    @Test
    void theWidthGivenIsTakenOverTheNarrowest() {
        Table table = Csv.table(TEXT, 4);
        assertEquals(4, table.width());
        assertArrayEquals(new byte[] {0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 3},
                table.column(0));
    }

    @Test
    void theRepeatRowGivenReachesTheTable() {
        assertEquals(1, Csv.table(TEXT, 2, 1).repeat());
    }

    @Test
    void everyVariantWritesTheTableTheTextGives() {
        Table table = Csv.table(TEXT);
        assertEquals(table, Dtx0.read(Dtx0.write(table)));
        assertEquals(table, Dtx1.read(Dtx1.write(table)));
        for (int width : WIDTHS) {
            Table one = Csv.table("1, -2\n3, 4\n5, 6\n", width);
            assertEquals(one, Dtx0.read(Dtx0.write(one)),
                    "DTX0 at a width of " + width);
            assertEquals(one, Dtx1.read(Dtx1.write(one)),
                    "DTX1 at a width of " + width);
        }
    }

    @Test
    void whatTheTextDoesNotGiveIsReported() {
        assertEquals("line 2 gives 2 values, not 3",
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
                        () -> Csv.table("300\n", 1)).getMessage());
        assertEquals("the width is 1, 2 or 4 bytes, not 3",
                assertThrows(IllegalArgumentException.class,
                        () -> Csv.table("1\n", 3)).getMessage());
    }

    @Test
    void aValueNoWidthTakesIsReported() {
        assertEquals("the text gives 4294967296, which no width of 1, 2 or 4"
                + " bytes takes", assertThrows(IllegalArgumentException.class,
                        () -> Csv.table("4294967296\n")).getMessage());
    }

    @Test
    void aTableWrittenAsTextOpensWithItsShapeAndItsColumnNames() {
        Table table = Csv.table(TEXT);
        assertEquals("""
                # 3 rows, 3 columns, width 2, RR 3
                c0,c1,c2
                1,300,65534
                2,301,65535
                3,302,0
                """, Csv.text(table));
    }

    @Test
    void theTextATableWasWrittenAsReadsBackToThatTable() {
        Table table = Csv.table(TEXT, 4, 1);
        Table back = Csv.table(Csv.text(table));
        assertEquals(table, back, "the same width and repeat, through the"
                + " comment the text opens with");
        assertEquals(4, back.width());
        assertEquals(1, back.repeat());
        for (int width : WIDTHS) {
            Table one = Csv.table("1, -2\n3, 4\n5, 6\n", width, 2);
            assertEquals(one, Csv.table(Csv.text(one)),
                    "at a width of " + width);
        }
    }

    @Test
    void aLineOfNamesBeforeTheRowsIsNotARow() {
        Table named = Csv.table("a table of two columns\nleft, right\n1, 2\n3, 4\n");
        assertEquals(2, named.rows());
        assertArrayEquals(new byte[] {1, 3}, named.column(0));
        assertThrows(IllegalArgumentException.class,
                () -> Csv.table("1, 2\nleft, right\n"),
                "a line of names among the rows is not a row of numbers");
    }

    @Test
    void theWidthAndRepeatGivenOutrankTheComment() {
        String text = "# 2 rows, 1 columns, width 4, RR 0\nc0\n1\n2\n";
        assertEquals(4, Csv.table(text).width());
        assertEquals(0, Csv.table(text).repeat());
        assertEquals(1, Csv.table(text, 1).width());
        assertEquals(2, Csv.table(text, 1, 2).repeat());
    }

}
