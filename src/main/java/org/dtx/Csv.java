package org.dtx;

import java.util.ArrayList;
import java.util.List;

/**
 * A table out of comma separated text: one row a line, one value a column.
 *
 * <p>The first row that holds values gives {@code C}. A line that is blank,
 * or whose first character other than a space is {@code #}, is not a row.
 * A value is decimal, or hexadecimal where it opens with {@code $}, and
 * negative where it opens with {@code -}.
 *
 * <p>A value of {@code W} bytes is stored most significant byte first, as
 * every field of the header is, and a negative one in two's complement. A
 * value fits {@code W} bytes where it lies from -2^(8W-1) to 2^(8W)-1, so
 * one width takes what a signed column holds and what an unsigned one holds
 * alike. DTX states no more of a column than its width, so which of the two
 * a column holds is the caller's to state elsewhere.
 */
public final class Csv {

    private Csv() {
    }

    /**
     * The rows of {@code text}, each column at the narrowest width that
     * takes every value of it, and repeating at {@code R}.
     */
    public static Table table(String text) {
        List<long[]> row = rows(text);
        return table(row, narrowest(row), row.size());
    }

    /** The rows of {@code text} at the given widths, repeating at {@code R}. */
    public static Table table(String text, int[] width) {
        List<long[]> row = rows(text);
        return table(row, width, row.size());
    }

    /**
     * The rows of {@code text} at the given widths.
     *
     * @param repeat {@code RR}, the row the table repeats to, or {@code R}
     *     where it does not
     * @throws IllegalArgumentException where a line does not hold one value
     *     a column, where a value is not a number, or where a value does not
     *     fit the width of its column
     */
    public static Table table(String text, int[] width, int repeat) {
        return table(rows(text), width, repeat);
    }

    /**
     * The narrowest width of 1, 2 and 4 that takes every value of each
     * column of {@code text}.
     */
    public static int[] widths(String text) {
        return narrowest(rows(text));
    }

    private static Table table(List<long[]> row, int[] width, int repeat) {
        for (int w : width) {
            if (w != 1 && w != 2 && w != 4) {
                throw new IllegalArgumentException(
                        "a column is 1, 2 or 4 bytes wide, not " + w);
            }
        }
        if (row.get(0).length != width.length) {
            throw new IllegalArgumentException(width.length + " widths for "
                    + row.get(0).length + " columns");
        }
        byte[][] column = new byte[width.length][];
        for (int i = 0; i < width.length; i++) {
            column[i] = new byte[row.size() * width[i]];
            for (int r = 0; r < row.size(); r++) {
                long value = row.get(r)[i];
                if (!fits(value, width[i])) {
                    throw new IllegalArgumentException("row " + r + " column "
                            + i + " gives " + value + ", which " + width[i]
                            + " bytes do not take");
                }
                put(column[i], r * width[i], value, width[i]);
            }
        }
        return Table.of(row.size(), repeat, width, column);
    }

    /** Every row of {@code text}, a value a column, in the order read. */
    private static List<long[]> rows(String text) {
        List<long[]> out = new ArrayList<>();
        String[] line = text.split("\n", -1);
        int columns = -1;
        for (int at = 0; at < line.length; at++) {
            String read = line[at].strip();
            if (read.isEmpty() || read.startsWith("#")) {
                continue;
            }
            String[] cell = read.split(",", -1);
            if (columns < 0) {
                columns = cell.length;
                if (columns > 256) {
                    throw new IllegalArgumentException(
                            "C is 1 to 256, not " + columns);
                }
            } else if (cell.length != columns) {
                throw new IllegalArgumentException("line " + (at + 1)
                        + " holds " + cell.length + " values, not " + columns);
            }
            long[] row = new long[columns];
            for (int i = 0; i < columns; i++) {
                row[i] = value(cell[i].strip(), at + 1, i);
            }
            out.add(row);
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("the text holds no row");
        }
        return out;
    }

    /** The narrowest width each column of {@code row} takes. */
    private static int[] narrowest(List<long[]> row) {
        int[] width = new int[row.get(0).length];
        for (int i = 0; i < width.length; i++) {
            int taken = 1;
            for (long[] read : row) {
                while (!fits(read[i], taken)) {
                    if (taken == 4) {
                        throw new IllegalArgumentException("column " + i
                                + " gives " + read[i] + ", which no width of"
                                + " 1, 2 or 4 bytes takes");
                    }
                    taken = taken == 1 ? 2 : 4;
                }
            }
            width[i] = taken;
        }
        return width;
    }

    /** The number {@code cell} gives, or what it is that is not one. */
    private static long value(String cell, int line, int column) {
        String where = "line " + line + " column " + column;
        try {
            if (cell.startsWith("$")) {
                return Long.parseLong(cell.substring(1), 16);
            }
            if (cell.startsWith("-$")) {
                return -Long.parseLong(cell.substring(2), 16);
            }
            return Long.parseLong(cell);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(where + " gives \"" + cell
                    + "\", which is not a number");
        }
    }

    /** Whether {@code value} lies from -2^(8W-1) to 2^(8W)-1. */
    private static boolean fits(long value, int width) {
        return value >= -(1L << (8 * width - 1))
                && value <= (1L << (8 * width)) - 1;
    }

    /** {@code value} at {@code at}, most significant byte first. */
    private static void put(byte[] out, int at, long value, int width) {
        for (int i = 0; i < width; i++) {
            out[at + i] = (byte) (value >> (8 * (width - 1 - i)));
        }
    }
}
