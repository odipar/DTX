package org.dtx;

import java.util.Arrays;
import java.util.List;
import org.st4.St4Decompressor;
import org.st4.St4Format;

/**
 * DTX3, column by column, a constant column as its value and every other
 * packed: a column map, the constant values, a stream record a live column,
 * then the live columns' ST4 streams.
 *
 * <p>A column whose {@code R} values are equal is constant, and the payload
 * has its value once. Every other column is live: its slot is its rank
 * among the live columns, and slot {@code j}'s ring, decoder state and
 * stream record are {@code j} strides past slot 0's (R8.4). A live column is
 * an ST4 data set without its 28-byte header, whose fields the table and
 * the payload define once (R8.6).
 */
public final class Dtx3 {

    /**
     * The largest ring a payload can define, in bytes: the furthest a back
     * reference reaches, so that {@code M} is {@code N} over {@code k} at
     * every unit (R8.6).
     */
    public static final int MAX_RING = St4Format.MAX_OFFSET;

    /**
     * The flags bit that marks a payload some live column of which was
     * packed with copies from its literal stream (R8.7), at payload byte 3.
     */
    public static final int COPIES = 1;

    /**
     * The flags bit that marks a payload whose loop is replayed at
     * {@code RR}: a loop longer than a back reference reaches, whose streams
     * end plainly (R8.8).
     */
    public static final int REPLAYED = 2;

    /** The column map's byte for a constant column. */
    public static final int CONSTANT = 0xFF;

    /** Where the column map begins in the payload. */
    static final int MAP = 8;

    /** What one stream record runs to: streams A, B, C and D. */
    static final int RECORD = 16;

    private Dtx3() {
    }

    /**
     * {@code table} as a DTX3 file, every live column packed at one unit by
     * one packer.
     *
     * @see #write(Table, List, int, int)
     */
    public static byte[] write(Table table, Packer packer, int unit, int ring) {
        return write(table, List.of(packer), unit, ring);
    }

    /**
     * {@code table} as a DTX3 file. Each live column is packed by every
     * packer named, and the smallest data set is kept, the first named where
     * two are the same size: so a column packs with copies where they pay
     * and without them where they do not.
     *
     * @param packers what makes an ST4 data set of a column, one at least
     * @param unit {@code k}: 1, 2 or 4, and a column's bytes divide by it
     * @param ring {@code N}: the bytes a live column unpacks through, a
     *     multiple of {@code unit} and at most {@link #MAX_RING}
     * @throws IllegalArgumentException where {@code unit} or {@code ring} is
     *     outside what the payload can define, a column's bytes do not divide
     *     by {@code unit}, the table repeats at a row whose first byte is not
     *     a unit of the column, or a data set defines a unit, a length, an
     *     {@code M} or a loop other than the payload's
     */
    public static byte[] write(Table table, List<Packer> packers, int unit,
            int ring) {
        if (packers.isEmpty()) {
            throw new IllegalArgumentException("no packer: a live column is"
                    + " packed by one at least");
        }
        if (unit != 1 && unit != 2 && unit != 4) {
            throw new IllegalArgumentException("k is 1, 2 or 4, not " + unit);
        }
        if (ring < unit || ring > MAX_RING || ring % unit != 0) {
            throw new IllegalArgumentException("N is a multiple of k from "
                    + unit + " to " + MAX_RING + ", not " + ring);
        }
        int bytes = table.rows() * table.width();
        if (bytes % unit != 0) {
            throw new IllegalArgumentException("a column is " + table.rows()
                    + " times " + table.width() + " bytes, which does not"
                    + " divide by k of " + unit);
        }
        int loop = Dtx2.loop(table, unit);
        int columns = table.columns();
        int width = table.width();
        boolean[] live = new boolean[columns];
        int count = 0;
        for (int i = 0; i < columns; i++) {
            live[i] = !constant(table.column(i), width);
            count += live[i] ? 1 : 0;
        }

        // Each live column's data set, the smallest the packers make, and
        // whether the one kept has copies. A column packs apart from every
        // other, so the columns pack at once and the bytes are the same.
        int[] column = new int[count];
        for (int i = 0, slot = 0; i < columns; i++) {
            if (live[i]) {
                column[slot++] = i;
            }
        }
        byte[][] set = new byte[count][];
        boolean[] withCopies = new boolean[count];
        java.util.stream.IntStream.range(0, count).parallel().forEach(j -> {
            byte[] values = table.column(column[j]);
            byte[] kept = packers.get(0).pack(values, unit, ring, loop);
            boolean keptCopies = packers.get(0).copies();
            for (Packer packer : packers.subList(1, packers.size())) {
                byte[] made = packer.pack(values, unit, ring, loop);
                if (made.length < kept.length) {
                    kept = made;
                    keptCopies = packer.copies();
                }
            }
            set[j] = kept;
            withCopies[j] = keptCopies;
        });
        // Every set of a payload has one loop and one form of it, which the
        // first defines.
        boolean copies = false;
        int rewind = St4Format.NO_REWIND;
        for (int j = 0; j < count; j++) {
            int i = column[j];
            check(set[j], i, unit, ring, bytes);
            int said = Dtx.getLong(set[j], St4Format.OFFSET_REWIND);
            if (j == 0) {
                rewind = said;
            }
            if (said != rewind || said != St4Format.NO_REWIND
                    && (loop < 0 || said != loop * unit)) {
                throw new IllegalArgumentException("column " + i + "'s data"
                        + " set records a rewind point of " + said + ", where"
                        + " every live column has the loop at unit " + loop
                        + " and the form of the first");
            }
            copies |= withCopies[j];
        }

        // SPEC.md 2.4: N, k, the flags, L, the map, the constant values up
        // to a long, a record a slot, then each slot's streams on a long.
        int records = Dtx.align(MAP + columns + width * (columns - count), 4);
        int[] at = new int[count];
        int next = records + RECORD * count;
        for (int j = 0; j < count; j++) {
            next = Dtx.align(next, 4);
            at[j] = next;
            next += set[j].length - St4Format.HEADER_SIZE;
        }
        byte[] head = Dtx.header(Dtx.DTX3, table);
        int p = head.length;
        byte[] out = new byte[p + next];
        System.arraycopy(head, 0, out, 0, p);
        Dtx.putWord(out, p, ring);
        out[p + 2] = (byte) unit;
        out[p + 3] = (byte) ((copies ? COPIES : 0)
                | (rewind != St4Format.NO_REWIND ? REPLAYED : 0));
        Dtx.putWord(out, p + 4, count);
        int value = p + MAP + columns;
        for (int i = 0, slot = 0; i < columns; i++) {
            if (live[i]) {
                out[p + MAP + i] = (byte) slot++;
            } else {
                out[p + MAP + i] = (byte) CONSTANT;
                System.arraycopy(table.column(i), 0, out, value, width);
                value += width;
            }
        }
        // A record's four offsets are from the payload's first byte. The
        // streams are the data set's as it laid them out, so each is the
        // data set's offset of it, less the 28-byte header, past the slot's.
        for (int j = 0; j < count; j++) {
            int rec = p + records + RECORD * j;
            Dtx.putLong(out, rec, at[j]);
            Dtx.putLong(out, rec + 4, at[j] + stream(set[j],
                    St4Format.OFFSET_LITERAL));
            Dtx.putLong(out, rec + 8, at[j] + stream(set[j],
                    St4Format.OFFSET_BYTE_OFFSETS));
            Dtx.putLong(out, rec + 12, at[j] + stream(set[j],
                    St4Format.OFFSET_WORD_OFFSETS));
            System.arraycopy(set[j], St4Format.HEADER_SIZE, out, p + at[j],
                    set[j].length - St4Format.HEADER_SIZE);
        }
        return out;
    }

    /** Where the stream whose offset is at {@code field} begins, past
     *  stream A. */
    private static int stream(byte[] set, int field) {
        return Dtx.getLong(set, field) - St4Format.HEADER_SIZE;
    }

    /**
     * A data set against the payload: its signature, its length, and its
     * {@code M}, which a reader of the payload reads as {@code N} over
     * {@code k}.
     */
    private static void check(byte[] set, int column, int unit, int ring,
            int bytes) {
        St4Format.Container read = St4Format.read(set);
        if (read.unit() != unit) {
            throw new IllegalArgumentException("column " + column + "'s data"
                    + " set is packed at k of " + read.unit() + ", and the"
                    + " payload's k is " + unit);
        }
        if (read.size() != bytes) {
            throw new IllegalArgumentException("column " + column + "'s data"
                    + " set unpacks to " + read.size() + " bytes, not the "
                    + bytes + " of R rows at its width");
        }
        if (read.window() != ring / unit) {
            throw new IllegalArgumentException("column " + column + "'s data"
                    + " set has an M of " + read.window() + " units, and"
                    + " the payload's M is N over k, " + ring / unit);
        }
    }

    /** Whether every value of {@code column} is its first. */
    static boolean constant(byte[] column, int width) {
        for (int at = width; at < column.length; at += width) {
            for (int b = 0; b < width; b++) {
                if (column[at + b] != column[b]) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The DTX3 file of the table in a DTX file of any variant, packed by
     * the packers named at the unit and ring named here.
     */
    public static byte[] from(byte[] file, List<Packer> packers, int unit,
            int ring) {
        return write(Dtx.read(file), packers, unit, ring);
    }

    /**
     * What a DTX3 payload defines, SPEC.md 2.4.
     *
     * @param ring {@code N}
     * @param unit {@code k}
     * @param copies flags bit 0
     * @param replayed flags bit 1
     * @param map the column map, a slot or {@link #CONSTANT} a column
     * @param constants a constant column's value, in its column; 0 in a
     *     live column's
     * @param records slot {@code j}'s four stream offsets, from the
     *     payload's first byte
     */
    public record Payload(int ring, int unit, boolean copies, boolean replayed,
            int[] map, long[] constants, int[][] records) {

        /** {@code L}, the live columns. */
        public int live() {
            return records.length;
        }

        /** Whether column {@code i} is live. */
        public boolean live(int i) {
            return map[i] != CONSTANT || records.length == 256;
        }

        /**
         * The bytes of slot {@code j}'s four streams in a payload of
         * {@code payload} bytes: from its stream A to the next slot's, or
         * to the payload's end.
         */
        public int bytes(int j, int payload) {
            return (j + 1 < records.length ? records[j + 1][0] : payload)
                    - records[j][0];
        }
    }

    /** What the DTX3 file {@code file} defines in its payload, checked as
     *  {@link #payload(byte[], Dtx.Header)} checks it. */
    public static Payload payload(byte[] file) {
        Dtx.Header header = Dtx.header(file);
        if (header.variant() != Dtx.DTX3) {
            throw new IllegalArgumentException(
                    "variant " + header.variant() + " is not DTX3");
        }
        return payload(file, header);
    }

    /**
     * What a DTX3 payload defines, checked against the rules SPEC.md 2.4
     * lays down for it: {@code L} at most {@code C}, the slots of the live
     * columns 0 to {@code L} minus one in column order, and every stream
     * inside the file, in order, A and D on a word.
     *
     * @throws IllegalArgumentException naming the rule the payload breaks
     */
    static Payload payload(byte[] file, Dtx.Header header) {
        int p = header.length();
        int columns = header.columns();
        int width = header.width();
        if (file.length < p + MAP + columns) {
            throw new IllegalArgumentException("a DTX3 payload of " + columns
                    + " columns has " + (MAP + columns) + " bytes before its"
                    + " values, and the file ends at " + (file.length - p));
        }
        int ring = Dtx.getWord(file, p);
        int unit = file[p + 2] & 0xFF;
        int flags = file[p + 3] & 0xFF;
        int live = Dtx.getWord(file, p + 4);
        if (live > columns) {
            throw new IllegalArgumentException("L is 0 to C of " + columns
                    + ", not " + live);
        }
        int[] map = new int[columns];
        long[] constants = new long[columns];
        int value = p + MAP + columns;
        int slot = 0;
        for (int i = 0; i < columns; i++) {
            map[i] = file[p + MAP + i] & 0xFF;
            if (map[i] == CONSTANT && live < 256) {
                if (value + width > file.length) {
                    throw new IllegalArgumentException("column " + i + "'s"
                            + " value is past the file's end");
                }
                long v = 0;
                for (int b = 0; b < width; b++) {
                    v = v << 8 | file[value + b] & 0xFF;
                }
                constants[i] = v;
                value += width;
            } else if (map[i] != slot++) {
                throw new IllegalArgumentException("column " + i + " maps to"
                        + " slot " + map[i] + ", and the live columns'"
                        + " slots are 0 to L minus one in column order");
            }
        }
        if (slot != live) {
            throw new IllegalArgumentException("the map has " + slot
                    + " live columns, and L is " + live);
        }
        int records = Dtx.align(MAP + columns + width * (columns - live), 4);
        int[][] record = new int[live][4];
        int last = records + RECORD * live;
        for (int j = 0; j < live; j++) {
            for (int s = 0; s < 4; s++) {
                int at = p + records + RECORD * j + 4 * s;
                if (at + 4 > file.length) {
                    throw new IllegalArgumentException("slot " + j + "'s"
                            + " record is past the file's end");
                }
                record[j][s] = Dtx.getLong(file, at);
                if (record[j][s] < last || p + record[j][s] > file.length) {
                    throw new IllegalArgumentException("slot " + j + "'s"
                            + " stream " + (char) ('A' + s) + " is at "
                            + record[j][s] + ", out of order or outside the"
                            + " payload");
                }
                last = record[j][s];
            }
            if ((record[j][0] & 1) != 0 || (record[j][3] & 1) != 0) {
                throw new IllegalArgumentException("slot " + j + "'s streams"
                        + " A and D begin on a word, and they are at "
                        + record[j][0] + " and " + record[j][3]);
            }
        }
        return new Payload(ring, unit, (flags & COPIES) != 0,
                (flags & REPLAYED) != 0, map, constants, record);
    }

    /**
     * The table in a DTX3 file: each constant column its value {@code R}
     * times, and each live column unpacked with the copy of ST4 in this
     * repository. A slot's stream D runs to the next slot's stream A, or to
     * the end of the file.
     *
     * @throws IllegalArgumentException where the file is not DTX3, breaks
     *     a rule of its payload, or a column unpacks to other than {@code R}
     *     times its width
     */
    public static Table read(byte[] file) {
        Dtx.Header header = Dtx.header(file);
        if (header.variant() != Dtx.DTX3) {
            throw new IllegalArgumentException(
                    "variant " + header.variant() + " is not DTX3");
        }
        Payload payload = payload(file, header);
        int p = header.length();
        int width = header.width();
        int bytes = header.rows() * width;
        int unit = payload.unit();
        if (unit != 1 && unit != 2 && unit != 4) {
            throw new IllegalArgumentException("k is 1, 2 or 4, not " + unit);
        }
        byte[][] column = new byte[header.columns()][];
        for (int i = 0; i < column.length; i++) {
            column[i] = new byte[bytes];
            if (!payload.live(i)) {
                for (int at = 0; at < bytes; at += width) {
                    for (int b = 0; b < width; b++) {
                        column[i][at + b] = (byte) (payload.constants()[i]
                                >> 8 * (width - 1 - b));
                    }
                }
                continue;
            }
            int[] r = payload.records()[payload.map()[i]];
            int end = payload.map()[i] + 1 < payload.live()
                    ? payload.records()[payload.map()[i] + 1][0] : file.length - p;
            byte[] out = St4Decompressor.decode(
                    Arrays.copyOfRange(file, p + r[0], p + r[1]),
                    Arrays.copyOfRange(file, p + r[1], p + r[2]),
                    Arrays.copyOfRange(file, p + r[2], p + r[3]),
                    Arrays.copyOfRange(file, p + r[3], p + end),
                    unit, bytes, payload.ring() / unit,
                    payload.replayed() ? header.repeat() * width
                            : St4Format.NO_REWIND).output();
            if (out.length != bytes) {
                throw new IllegalArgumentException("column " + i
                        + " unpacks to " + out.length + " bytes, not the "
                        + bytes + " of R rows at its width");
            }
            column[i] = out;
        }
        return Table.of(header.rows(), header.repeat(), width, column);
    }
}
