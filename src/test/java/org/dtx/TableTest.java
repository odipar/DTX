package org.dtx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/** A table against the bounds R6 sets, and against a caller's own arrays. */
final class TableTest {

    @Test
    void aTableTakesTheBoundsR6Sets() {
        assertEquals(Example.ROWS, Example.table().rows());
        assertEquals(3, Example.table().columns());
        assertEquals(6, Example.table().rowBytes());
        assertEquals(2, Example.table().width());
    }

    @Test
    void aTableOutsideR6IsRejectedWithWhatItBroke() {
        byte[][] column = {Example.A, Example.B, Example.C};
        assertEquals("R is 1 upward, not 0", assertThrows(
                IllegalArgumentException.class,
                () -> Table.of(0, 0, Example.WIDTH, column)).getMessage());
        assertEquals("RR is 0 to R, not 4", assertThrows(
                IllegalArgumentException.class,
                () -> Table.of(3, 4, Example.WIDTH, column)).getMessage());
        assertEquals("the width is 1, 2 or 4 bytes, not 3", assertThrows(
                IllegalArgumentException.class,
                () -> Table.of(3, 1, 3, column)).getMessage());
        assertEquals("column 0 is 6 bytes, not 12", assertThrows(
                IllegalArgumentException.class,
                () -> Table.of(3, 1, 4, column)).getMessage());
    }

    @Test
    void aWriteToTheCallersArrayDoesNotReachTheTable() {
        byte[] mine = Example.A.clone();
        Table table = Table.of(Example.ROWS, Example.REPEAT, Example.WIDTH,
                new byte[][] {mine, Example.B, Example.C});
        mine[0] = 0x7F;
        assertEquals(0x11, table.column(0)[0]);
        assertNotEquals(0x7F, table.column(0)[0]);
    }
}
