package org.dtx;

import java.util.Arrays;
import org.st4.St4Decompressor;
import org.st4.St4Format;

/**
 * DTX2, column by column and packed: {@code C} ST4 data sets, one a column,
 * behind an offset each.
 *
 * <p>The payload defines {@code N} and {@code k} once, so one ring size and
 * one decoder build reads every column (R5.3, R5.5), then an offset a
 * column, then the data sets, each beginning on a long (R5.9).
 */
public final class Dtx2 {

    /** The largest ring a payload can define, in bytes: {@code N} is two
     * bytes. */
    public static final int MAX_RING = 65535;

    /**
     * The flags bit that marks every column was packed with copies from its
     * own literal stream (R5.10), at payload byte 3.
     */
    public static final int COPIES = 1;

    private Dtx2() {
    }

    /**
     * {@code table} as a DTX2 file, every column packed at one unit.
     *
     * @param packer what makes an ST4 data set of a column
     * @param unit {@code k}: 1, 2 or 4, and a column's bytes divide by it
     *     (R5.6)
     * @param ring {@code N}: the bytes a column unpacks through (R5.4)
     * @throws IllegalArgumentException where {@code unit} or {@code ring} is
     *     outside what the payload can define, or a column's bytes do not
     *     divide by {@code unit}
     */
    public static byte[] write(Table table, Packer packer, int unit, int ring) {
        if (unit != 1 && unit != 2 && unit != 4) {
            throw new IllegalArgumentException("k is 1, 2 or 4, not " + unit);
        }
        if (ring < 1 || ring > MAX_RING) {
            throw new IllegalArgumentException(
                    "N is 1 to " + MAX_RING + ", not " + ring);
        }
        if (table.rows() * table.width() % unit != 0) {
            throw new IllegalArgumentException("a column is " + table.rows()
                    + " times " + table.width() + " bytes, which does not"
                    + " divide by k of " + unit);
        }
        byte[][] set = new byte[table.columns()][];
        for (int i = 0; i < table.columns(); i++) {
            set[i] = packer.pack(table.column(i), unit, ring);
        }

        // 2.3: `N`, `k`, the flags, then an offset a column
        int prefix = 4 + 4 * table.columns();
        int[] at = new int[table.columns()];
        int next = prefix;
        for (int i = 0; i < set.length; i++) {
            next = Dtx.align(next, 4);
            at[i] = next;
            next += set[i].length;
        }

        byte[] head = Dtx.header(Dtx.DTX2, table);
        byte[] out = new byte[head.length + next];
        System.arraycopy(head, 0, out, 0, head.length);
        Dtx.putWord(out, head.length, ring);
        out[head.length + 2] = (byte) unit;
        out[head.length + 3] = (byte) (packer.copies() ? COPIES : 0);
        for (int i = 0; i < set.length; i++) {
            Dtx.putLong(out, head.length + 4 + 4 * i, at[i]);
            System.arraycopy(set[i], 0, out, head.length + at[i],
                    set[i].length);
        }
        return out;
    }

    /**
     * The DTX2 file of the table in a DTX file of any variant. The table is
     * the same under every variant (R1.3), so what comes back has the same
     * rows, width, {@code R} and {@code RR} as what went in, and a DTX2
     * file comes back packed at the unit and ring given here.
     */
    public static byte[] from(byte[] file, Packer packer, int unit, int ring) {
        return write(Dtx.read(file), packer, unit, ring);
    }

    /**
     * The table in a DTX2 file, each column unpacked with the copy of ST4 in
     * this repository. A data set runs from its offset to the next offset
     * above it, or to the end of the file.
     *
     * @throws IllegalArgumentException where the file is not DTX2, a data set
     *     does not open with the payload's own unit (R5.2), or a column
     *     unpacks to other than {@code R} times its width
     */
    public static Table read(byte[] file) {
        Dtx.Header header = Dtx.header(file);
        if (header.variant() != Dtx.DTX2) {
            throw new IllegalArgumentException(
                    "variant " + header.variant() + " is not DTX2");
        }
        Packager.Packed packed = Packager.packed(file, header);
        int[] at = packed.at();
        byte[][] column = new byte[header.columns()][];
        for (int i = 0; i < column.length; i++) {
            int from = header.length() + at[i];
            int to = file.length;
            for (int other : at) {
                int begins = header.length() + other;
                if (begins > from && begins < to) {
                    to = begins;
                }
            }
            St4Format.Container set = St4Format.read(
                    Arrays.copyOfRange(file, from, to));
            byte[] out = St4Decompressor.decode(set.control(), set.literal(),
                    set.byteOffsets(), set.wordOffsets(), set.unit(),
                    set.size(), set.window(), set.rewind()).output();
            int bytes = header.rows() * header.width();
            if (out.length != bytes) {
                throw new IllegalArgumentException("column " + i
                        + " unpacks to " + out.length + " bytes, not the "
                        + bytes + " of R rows at its width");
            }
            column[i] = out;
        }
        return Table.of(header.rows(), header.repeat(), header.width(),
                column);
    }
}
