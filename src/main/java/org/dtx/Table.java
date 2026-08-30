package org.dtx;

/**
 * A table in memory: {@code R} rows, {@code C} columns, and a row
 * {@code RR} it repeats to. Every variant holds this same table (R1.3), so
 * a variant reads into one and writes out of one.
 *
 * <p>The values are held column by column, {@code R} times a column's width
 * bytes each. DTX1 and DTX2 lay them out that way, and DTX0 walks them a row
 * at a time.
 */
public final class Table {

    private final int rows;
    private final int repeat;
    private final int[] width;
    private final byte[][] column;

    private Table(int rows, int repeat, int[] width, byte[][] column) {
        this.rows = rows;
        this.repeat = repeat;
        this.width = width;
        this.column = column;
    }

    /**
     * A table of the given columns, each {@code rows} times its width bytes.
     * The arrays are copied, so a later write to the caller's does not reach
     * this table.
     *
     * @throws IllegalArgumentException where R6's bounds do not hold, or
     *     where a column is not the length its width and {@code rows} give
     */
    public static Table of(int rows, int repeat, int[] width, byte[][] column) {
        if (rows < 1) {
            throw new IllegalArgumentException("R is 1 upward, not " + rows);
        }
        if (width.length < 1 || width.length > 256) {
            throw new IllegalArgumentException(
                    "C is 1 to 256, not " + width.length);
        }
        if (width.length != column.length) {
            throw new IllegalArgumentException(width.length + " widths for "
                    + column.length + " columns");
        }
        if (repeat < 0 || repeat > rows) {
            throw new IllegalArgumentException("RR is 0 to R, not " + repeat);
        }
        byte[][] held = new byte[column.length][];
        for (int i = 0; i < column.length; i++) {
            if (width[i] != 1 && width[i] != 2 && width[i] != 4) {
                throw new IllegalArgumentException("column " + i
                        + " is " + width[i] + " bytes wide, not 1, 2 or 4");
            }
            if (column[i].length != rows * width[i]) {
                throw new IllegalArgumentException("column " + i + " holds "
                        + column[i].length + " bytes, not " + rows * width[i]);
            }
            held[i] = column[i].clone();
        }
        return new Table(rows, repeat, width.clone(), held);
    }

    /** {@code R}, the rows the table holds. */
    public int rows() {
        return rows;
    }

    /** {@code C}, the column count. */
    public int columns() {
        return width.length;
    }

    /** {@code RR}, the row the table repeats to, or {@code R} where it does not. */
    public int repeat() {
        return repeat;
    }

    /** Column {@code i}'s width in bytes. */
    public int width(int i) {
        return width[i];
    }

    /** Column {@code i}'s {@code R} values, in row order. */
    public byte[] column(int i) {
        return column[i].clone();
    }

    /** A row's bytes: column 0 through column {@code C} minus one, in order. */
    public int rowBytes() {
        int bytes = 0;
        for (int w : width) {
            bytes += w;
        }
        return bytes;
    }

    /** Whether two tables hold the same rows, widths, {@code R} and {@code RR}. */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Table that)) {
            return false;
        }
        if (rows != that.rows || repeat != that.repeat
                || width.length != that.width.length) {
            return false;
        }
        for (int i = 0; i < width.length; i++) {
            if (width[i] != that.width[i]
                    || !java.util.Arrays.equals(column[i], that.column[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = rows * 31 + repeat;
        for (int i = 0; i < width.length; i++) {
            hash = hash * 31 + width[i];
            hash = hash * 31 + java.util.Arrays.hashCode(column[i]);
        }
        return hash;
    }
}
