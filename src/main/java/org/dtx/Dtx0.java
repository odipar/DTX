package org.dtx;

/**
 * DTX0, row by row: {@code R} rows, each column 0 through column
 * {@code C} minus one in order, with nothing between them.
 *
 * <p>A value falls where the widths put it, so a two or four byte column can
 * fall on an odd offset and a reader takes it as bytes (R3.4).
 */
public final class Dtx0 {

    private Dtx0() {
    }

    /** {@code table} as a DTX0 file. */
    public static byte[] write(Table table) {
        byte[] head = Dtx.header(Dtx.DTX0, table);
        int row = table.rowBytes();
        byte[] out = new byte[head.length + table.rows() * row];
        System.arraycopy(head, 0, out, 0, head.length);
        int at = head.length;
        for (int n = 0; n < table.rows(); n++) {
            for (int i = 0; i < table.columns(); i++) {
                int w = table.width(i);
                System.arraycopy(table.column(i), n * w, out, at, w);
                at += w;
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
        int row = 0;
        for (int w : header.width()) {
            row += w;
        }
        int payload = header.rows() * row;
        if (file.length - header.length() < payload) {
            throw new IllegalArgumentException("a payload of "
                    + (file.length - header.length()) + " bytes is short of "
                    + payload);
        }
        byte[][] column = new byte[header.columns()][];
        for (int i = 0; i < header.columns(); i++) {
            column[i] = new byte[header.rows() * header.width()[i]];
        }
        int at = header.length();
        for (int n = 0; n < header.rows(); n++) {
            for (int i = 0; i < header.columns(); i++) {
                int w = header.width()[i];
                System.arraycopy(file, at, column[i], n * w, w);
                at += w;
            }
        }
        return Table.of(header.rows(), header.repeat(), header.width(), column);
    }
}
