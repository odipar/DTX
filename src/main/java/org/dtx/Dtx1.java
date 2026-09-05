package org.dtx;

/**
 * DTX1, column by column: {@code C} columns, each its {@code R}
 * values in row order.
 *
 * <p>A column begins on a word, so under a width of 1 and an odd {@code R} a
 * zero byte stands between one column and the next (R4.3). Under a width of
 * 2 or 4 a column is a whole number of words already and nothing stands
 * between them. Every column is the same length, so they lie at one stride
 * and a reader steps from one to the next by adding it.
 */
public final class Dtx1 {

    private Dtx1() {
    }

    /** The stride from one column to the next: {@code R} times the width,
     * up to a word. */
    static int stride(int rows, int width) {
        return Dtx.align(rows * width, 2);
    }

    /** What a DTX1 payload runs to, for {@code rows} of this width. */
    static int payloadLength(int rows, int columns, int width) {
        return (columns - 1) * stride(rows, width) + rows * width;
    }

    /** {@code table} as a DTX1 file. */
    public static byte[] write(Table table) {
        byte[] head = Dtx.header(Dtx.DTX1, table);
        int stride = stride(table.rows(), table.width());
        byte[] out = new byte[head.length
                + payloadLength(table.rows(), table.columns(), table.width())];
        System.arraycopy(head, 0, out, 0, head.length);
        for (int i = 0; i < table.columns(); i++) {
            byte[] column = table.column(i);
            System.arraycopy(column, 0, out, head.length + i * stride,
                    column.length);
        }
        return out;
    }

    /**
     * The table in a DTX1 file.
     *
     * @throws IllegalArgumentException where the file is not DTX1, or is
     *     short of the columns its header defines
     */
    public static Table read(byte[] file) {
        Dtx.Header header = Dtx.header(file);
        if (header.variant() != Dtx.DTX1) {
            throw new IllegalArgumentException(
                    "variant " + header.variant() + " is not DTX1");
        }
        int width = header.width();
        int payload = payloadLength(header.rows(), header.columns(), width);
        if (file.length - header.length() < payload) {
            throw new IllegalArgumentException("a payload of "
                    + (file.length - header.length()) + " bytes is short of "
                    + payload);
        }
        int stride = stride(header.rows(), width);
        byte[][] column = new byte[header.columns()][];
        for (int i = 0; i < header.columns(); i++) {
            column[i] = new byte[header.rows() * width];
            System.arraycopy(file, header.length() + i * stride, column[i], 0,
                    column[i].length);
        }
        return Table.of(header.rows(), header.repeat(), width, column);
    }
}
