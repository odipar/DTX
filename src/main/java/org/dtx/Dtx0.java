package org.dtx;

/**
 * DTX0, row by row: {@code R} rows, each column 0 through column
 * {@code C} minus one in order, with nothing between them.
 *
 * <p>A value falls where the width puts it, so under a width of 1 a row can
 * begin on an odd offset and a reader takes it as bytes (R3.4). Under a
 * width of 2 or 4 every value stands on its own boundary.
 */
public final class Dtx0 {

    private Dtx0() {
    }

    /** {@code table} as a DTX0 file. */
    public static byte[] write(Table table) {
        byte[] head = Dtx.header(Dtx.DTX0, table);
        int width = table.width();
        int row = table.rowBytes();
        byte[] out = new byte[head.length + table.rows() * row];
        System.arraycopy(head, 0, out, 0, head.length);
        for (int i = 0; i < table.columns(); i++) {
            byte[] column = table.column(i);
            for (int n = 0; n < table.rows(); n++) {
                System.arraycopy(column, n * width, out,
                        head.length + n * row + i * width, width);
            }
        }
        return out;
    }

    /**
     * The table in a DTX0 file.
     *
     * @throws IllegalArgumentException where the file is not DTX0, or is
     *     short of the rows its header defines
     */
    public static Table read(byte[] file) {
        Dtx.Header header = Dtx.header(file);
        if (header.variant() != Dtx.DTX0) {
            throw new IllegalArgumentException(
                    "variant " + header.variant() + " is not DTX0");
        }
        int width = header.width();
        int row = header.rowBytes();
        int payload = header.rows() * row;
        if (file.length - header.length() < payload) {
            throw new IllegalArgumentException("a payload of "
                    + (file.length - header.length()) + " bytes is short of "
                    + payload);
        }
        byte[][] column = new byte[header.columns()][];
        for (int i = 0; i < header.columns(); i++) {
            column[i] = new byte[header.rows() * width];
            for (int n = 0; n < header.rows(); n++) {
                System.arraycopy(file, header.length() + n * row + i * width,
                        column[i], n * width, width);
            }
        }
        return Table.of(header.rows(), header.repeat(), width, column);
    }
}
