package org.dtx;

/**
 * A table in memory: {@code R} rows, {@code C} columns of one width, and a
 * row {@code RR} it repeats to. Every variant is this same table (R1.3), so
 * a variant reads into one and writes out of one.
 *
 * <p>The values are stored column by column, {@code R} times the width bytes
 * each. DTX1 and DTX2 lay them out that way, and DTX0 walks them a row at a
 * time.
 */
public final class Table {

    private final int rows;
    private final int repeat;
    private final int width;
    private final byte[][] column;

    private Table(int rows, int repeat, int width, byte[][] column) {
        this.rows = rows;
        this.repeat = repeat;
        this.width = width;
        this.column = column;
    }

    /**
     * A table of the given columns, each {@code rows} times {@code width}
     * bytes. The arrays are copied, so a later write to the caller's does not
     * reach this table.
     *
     * @throws IllegalArgumentException where R6's bounds are not met, or
     *     where a column is not the length {@code width} and {@code rows}
     *     give
     */
    public static Table of(int rows, int repeat, int width, byte[][] column) {
        if (rows < 1) {
            throw new IllegalArgumentException("R is 1 upward, not " + rows);
        }
        if (column.length < 1 || column.length > 256) {
            throw new IllegalArgumentException(
                    "C is 1 to 256, not " + column.length);
        }
        if (width != 1 && width != 2 && width != 4) {
            throw new IllegalArgumentException(
                    "the width is 1, 2 or 4 bytes, not " + width);
        }
        if (repeat < 0 || repeat > rows) {
            throw new IllegalArgumentException("RR is 0 to R, not " + repeat);
        }
        byte[][] kept = new byte[column.length][];
        for (int i = 0; i < column.length; i++) {
            if (column[i].length != rows * width) {
                throw new IllegalArgumentException("column " + i + " is "
                        + column[i].length + " bytes, not " + rows * width);
            }
            kept[i] = column[i].clone();
        }
        return new Table(rows, repeat, width, kept);
    }

    /** {@code R}, the rows in the table. */
    public int rows() {
        return rows;
    }

    /** {@code C}, the column count. */
    public int columns() {
        return column.length;
    }

    /** {@code RR}, the row the table repeats to, or {@code R} where it does not. */
    public int repeat() {
        return repeat;
    }

    /** {@code W}, the bytes every value of the table takes. */
    public int width() {
        return width;
    }

    /** Column {@code i}'s {@code R} values, in row order. */
    public byte[] column(int i) {
        return column[i].clone();
    }

    /** A row's bytes: {@code C} values of {@code W} bytes. */
    public int rowBytes() {
        return column.length * width;
    }

    /** Whether two tables are the same rows, width, {@code R} and {@code RR}. */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Table that)) {
            return false;
        }
        if (rows != that.rows || repeat != that.repeat || width != that.width
                || column.length != that.column.length) {
            return false;
        }
        for (int i = 0; i < column.length; i++) {
            if (!java.util.Arrays.equals(column[i], that.column[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = (rows * 31 + repeat) * 31 + width;
        for (byte[] one : column) {
            hash = hash * 31 + java.util.Arrays.hashCode(one);
        }
        return hash;
    }
}
