package org.dtx;

/**
 * DTX1, column by column: {@code C} columns, each holding its {@code R}
 * values in row order.
 *
 * <p>A column begins on a word, so where the column before it ends odd a
 * zero byte stands between them (R4.3). Every value of a two or four byte
 * column then sits where a 68000 reads it as one, at a byte a column.
 */
public final class Dtx1 {

    private Dtx1() {
    }

    /** Where column {@code i} begins, from the start of the payload. */
    static int[] offsets(int rows, int[] width) {
        int[] at = new int[width.length];
        int next = 0;
        for (int i = 0; i < width.length; i++) {
            next = Dtx.align(next, 2);
            at[i] = next;
            next += rows * width[i];
        }
        return at;
    }

    /** What a DTX1 payload runs to, for {@code rows} of these widths. */
    static int payloadLength(int rows, int[] width) {
        int[] at = offsets(rows, width);
        int last = width.length - 1;
        return at[last] + rows * width[last];
    }

    /** {@code table} as a DTX1 file. */
    public static byte[] write(Table table) {
        int[] width = new int[table.columns()];
        for (int i = 0; i < width.length; i++) {
            width[i] = table.width(i);
        }
        byte[] head = Dtx.header(Dtx.DTX1, table);
        int[] at = offsets(table.rows(), width);
        byte[] out = new byte[head.length
                + payloadLength(table.rows(), width)];
        System.arraycopy(head, 0, out, 0, head.length);
        for (int i = 0; i < width.length; i++) {
            byte[] column = table.column(i);
            System.arraycopy(column, 0, out, head.length + at[i],
                    column.length);
        }
        return out;
    }

    /**
     * The table a DTX1 file holds.
     *
     * @throws IllegalArgumentException where the file is not DTX1, or is
     *     short of the columns its header states
     */
    public static Table read(byte[] file) {
        Dtx.Header header = Dtx.header(file);
        if (header.variant() != Dtx.DTX1) {
            throw new IllegalArgumentException(
                    "variant " + header.variant() + " is not DTX1");
        }
        int payload = payloadLength(header.rows(), header.width());
        if (file.length - header.length() < payload) {
            throw new IllegalArgumentException("a payload of "
                    + (file.length - header.length()) + " bytes is short of "
                    + payload);
        }
        int[] at = offsets(header.rows(), header.width());
        byte[][] column = new byte[header.columns()][];
        for (int i = 0; i < header.columns(); i++) {
            column[i] = new byte[header.rows() * header.width()[i]];
            System.arraycopy(file, header.length() + at[i], column[i], 0,
                    column[i].length);
        }
        return Table.of(header.rows(), header.repeat(), header.width(), column);
    }
}
