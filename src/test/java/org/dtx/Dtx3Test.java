package org.dtx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * DTX3's layout (SPEC.md 2.4): the column map, the constant values, a
 * stream record a slot and the slots' streams, written by the carried ST4
 * and read back.
 */
final class Dtx3Test {

    /** Column 1 of the example table, made constant: 0x4041 in every row. */
    private static final byte[] B = {0x40, 0x41, 0x40, 0x41, 0x40, 0x41};

    private static Table mixed() {
        return Table.of(Example.ROWS, Example.REPEAT, Example.WIDTH,
                new byte[][] {Example.A, B, Example.C});
    }

    @Test
    void thePayloadDefinesTheRingTheUnitTheMapAndTheValues() {
        byte[] file = Dtx3.write(mixed(), new St4(), 2, 960);
        int p = Example.HEADER;
        assertEquals(Dtx.DTX3, file[3], "the variant");
        assertEquals(960, Dtx.getWord(file, p), "N");
        assertEquals(2, file[p + 2], "k");
        assertEquals(0, file[p + 3], "the flags: no copies, no replayed loop");
        assertEquals(2, Dtx.getWord(file, p + 4), "L");
        assertEquals(0, Dtx.getWord(file, p + 6), "the word before the map");
        assertArrayEquals(new byte[] {0, (byte) Dtx3.CONSTANT, 1},
                Example.at(file, p + 8, 3), "the map: slot 0, constant, slot 1");
        assertArrayEquals(new byte[] {0x40, 0x41}, Example.at(file, p + 11, 2),
                "column 1's value");
        assertEquals(0, file[p + 13] | file[p + 14] | file[p + 15],
                "the pad up to a long before the records");
    }

    @Test
    void theRecordsBeginOnALongAndEachSlotsStreamsOnALong() {
        byte[] file = Dtx3.write(mixed(), new St4(), 2, 960);
        Dtx3.Payload payload = Dtx3.payload(file, Dtx.header(file));
        int records = 16;
        assertEquals(records + 16 * 2, payload.records()[0][0],
                "slot 0's stream A follows the two records");
        for (int[] record : payload.records()) {
            assertEquals(0, record[0] % 4, "a slot's streams begin on a long");
        }
    }

    @Test
    void theTableReadsBackUnderEveryUnitAndEveryVariantBack() {
        for (int unit : new int[] {1, 2}) {
            byte[] file = Dtx3.write(mixed(), new St4(), unit, 960);
            assertEquals(mixed(), Dtx3.read(file), "DTX3 at k of " + unit);
            assertEquals(mixed(), Dtx.read(Dtx1.write(Dtx.read(file))),
                    "DTX3 to DTX1 at k of " + unit);
            assertEquals(mixed(), Dtx3.read(Dtx3.from(Dtx2.write(mixed(),
                    new St4(), unit, 960), List.of(new St4()), unit, 960)),
                    "DTX2 to DTX3 at k of " + unit);
        }
    }

    @Test
    void everyColumnConstantHasNoSlot() {
        byte[] same = {7, 7, 7, 7, 7, 7};
        Table table = Table.of(6, 6, 1, new byte[][] {same, same});
        byte[] file = Dtx3.write(table, new St4(), 1, 960);
        int p = Example.HEADER;
        assertEquals(0, Dtx.getWord(file, p + 4), "L");
        assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xFF, 7, 7},
                Example.at(file, p + 8, 4), "the map and the two values");
        assertEquals(p + 12, file.length, "the payload ends on the values' long");
        assertEquals(table, Dtx3.read(file));
    }

    @Test
    void aColumnPacksWithCopiesOnlyWhereTheyPackItSmaller() {
        // 512 rows repeating a pattern 37 rows long, against a ring of 64:
        // copies pack it smaller, and the flag marks the copy code.
        byte[] pattern = new byte[512];
        byte[] counting = new byte[512];
        for (int r = 0; r < 512; r++) {
            pattern[r] = (byte) (r % 37);
            counting[r] = (byte) (r * 7);
        }
        Table table = Table.of(512, 512, 1, new byte[][] {pattern, counting});
        List<Packer> both = List.of(new St4(), new St4(true, 0));
        byte[] file = Dtx3.write(table, both, 1, 64);
        assertEquals(Dtx3.COPIES, file[Example.HEADER + 3] & Dtx3.COPIES,
                "the copies flag, where a column packs smaller with copies");
        assertTrue(file.length < Dtx3.write(table, new St4(), 1, 64).length,
                "copies where they pay pack the table smaller than none");
        assertEquals(table, Dtx3.read(file));
        // At a ring the pattern fits, neither column packs smaller with
        // copies, and the flag stays clear.
        byte[] wide = Dtx3.write(table, both, 1, 960);
        assertEquals(0, wide[Example.HEADER + 3] & Dtx3.COPIES,
                "the copies flag, where no column packs smaller with copies");
    }

    @Test
    void aLoopLongerThanTheRingIsMarkedReplayed() {
        byte[] counting = new byte[512];
        for (int r = 0; r < 512; r++) {
            counting[r] = (byte) (r * 7 + r / 3);
        }
        Table table = Table.of(512, 0, 1, new byte[][] {counting});
        byte[] file = Dtx3.write(table, new St4(), 1, 64);
        assertEquals(Dtx3.REPLAYED, file[Example.HEADER + 3] & Dtx3.REPLAYED,
                "a loop of 512 bytes against a ring of 64");
        assertEquals(table, Dtx3.read(file));
        byte[] fits = Dtx3.write(table, new St4(), 1, 960);
        assertEquals(0, fits[Example.HEADER + 3] & Dtx3.REPLAYED,
                "a loop the ring reaches loops by its end code");
    }

    @Test
    void aPayloadThatBreaksItsRulesIsAnError() {
        byte[] file = Dtx3.write(mixed(), new St4(), 2, 960);
        int p = Example.HEADER;
        byte[] order = file.clone();
        order[p + 10] = 0;
        assertEquals("column 2 maps to slot 0, and the live columns' slots are"
                + " 0 to L minus one in column order",
                assertThrows(IllegalArgumentException.class,
                        () -> Dtx3.read(order)).getMessage());
        byte[] many = file.clone();
        Dtx.putWord(many, p + 4, 4);
        assertEquals("L is 0 to C of 3, not 4", assertThrows(
                IllegalArgumentException.class, () -> Dtx3.read(many))
                .getMessage());
        byte[] odd = file.clone();
        Dtx.putLong(odd, p + 16, Dtx.getLong(file, p + 16) + 1);
        String said = String.valueOf(assertThrows(
                IllegalArgumentException.class, () -> Dtx3.read(odd))
                .getMessage());
        assertTrue(said.startsWith("slot 0's streams A and D begin on a word"),
                said);
    }

    @Test
    void aRingOutsideWhatThePayloadDefinesIsAnError() {
        assertEquals("N is a multiple of k from 2 to 32512, not 961",
                assertThrows(IllegalArgumentException.class,
                        () -> Dtx3.write(mixed(), new St4(), 2, 961)).getMessage());
        assertEquals("N is a multiple of k from 1 to 32512, not 32513",
                assertThrows(IllegalArgumentException.class,
                        () -> Dtx3.write(mixed(), new St4(), 1, 32513)).getMessage());
    }
}
