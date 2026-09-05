package org.dtx;

import java.util.ArrayList;
import java.util.List;

/**
 * A table out of comma separated text: one row a line, one value a column.
 *
 * <p>The first row of numbers gives {@code C}. A line that is blank, or
 * whose first character other than a space is {@code #}, is not a row, and
 * neither is a line before the first row of numbers in which no cell is a
 * number: a line of column names, or a line describing the table. A value is
 * decimal, or hexadecimal where it opens with {@code $}, and negative where
 * it opens with {@code -}.
 *
 * <p>Every value of the table takes the same width {@code W} (R6.3). A value
 * of {@code W} bytes is stored most significant byte first, as every field
 * of the header is, and a negative one in two's complement. A value fits
 * {@code W} bytes where it lies from -2^(8W-1) to 2^(8W)-1, so one width
 * takes a signed column's values and an unsigned one's alike. DTX does not
 * define more of a column than its width, so which of the two a column is,
 * the caller defines elsewhere.
 *
 * <p>A table is written out the other way as well: a comment that gives the
 * shape, a line of column names, then one row a line, each value the
 * unsigned number its bytes give. Read back, the comment gives the width and
 * the repeat where the caller does not give them, and the names are passed
 * over, so the text a table was written as reads back to that table.
 */
public final class Csv {

    private Csv() {
    }

    /**
     * The rows of {@code text} at the width the first comment gives or else
     * the narrowest that takes every value, repeating at the row the first
     * comment gives or else at {@code R}.
     */
    public static Table table(String text) {
        return table(text, width(text));
    }

    /**
     * The rows of {@code text} at the given width, repeating at the row the
     * first comment gives or else at {@code R}.
     */
    public static Table table(String text, int width) {
        List<long[]> row = rows(text);
        int repeat = repeat(text);
        return table(row, width, repeat < 0 ? row.size() : repeat);
    }

    /**
     * The rows of {@code text} at the given width.
     *
     * @param repeat {@code RR}, the row the table repeats to, or {@code R}
     *     where it does not
     * @throws IllegalArgumentException where a line does not give one value
     *     a column, where a value is not a number, or where a value does not
     *     fit the width
     */
    public static Table table(String text, int width, int repeat) {
        return table(rows(text), width, repeat);
    }

    /**
     * The width the first comment of {@code text} gives, or else the
     * narrowest of 1, 2 and 4 that takes every value of it.
     */
    public static int width(String text) {
        String given = comment(text, "width ");
        return given.isEmpty() ? narrowest(rows(text))
                : Integer.parseInt(given);
    }

    /**
     * The repeat the first comment of {@code text} gives, or -1 where it
     * does not give one.
     */
    public static int repeat(String text) {
        String given = comment(text, "RR ");
        return given.isEmpty() ? -1 : Integer.parseInt(given);
    }

    /**
     * {@code table} as text: a comment giving {@code R}, {@code C}, the
     * width and {@code RR}; a line of column names, {@code c0} onward; then
     * one row a line, one value a column, each the unsigned number its bytes
     * give. {@link #table(String)} reads it back to the same table.
     */
    public static String text(Table table) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(table.rows()).append(" rows, ")
                .append(table.columns()).append(" columns, width ")
                .append(table.width()).append(", RR ").append(table.repeat())
                .append('\n');
        for (int i = 0; i < table.columns(); i++) {
            out.append(i == 0 ? "c" : ",c").append(i);
        }
        out.append('\n');
        int width = table.width();
        for (int r = 0; r < table.rows(); r++) {
            for (int i = 0; i < table.columns(); i++) {
                out.append(i == 0 ? "" : ",")
                        .append(get(table.column(i), r * width, width));
            }
            out.append('\n');
        }
        return out.toString();
    }

    /**
     * What follows {@code key} in the first comment of {@code text}, up to a
     * comma followed by a space or the end of the line, or empty where the
     * text does not open with a comment giving it. The comment is the one
     * {@link #text(Table)} writes: {@code # 3 rows, 3 columns, width 2,
     * RR 3}.
     */
    private static String comment(String text, String key) {
        for (String line : text.split("\n", -1)) {
            String read = line.strip();
            if (read.isEmpty()) {
                continue;
            }
            if (!read.startsWith("#")) {
                return "";
            }
            int at = read.indexOf(key);
            if (at < 0) {
                return "";
            }
            String rest = read.substring(at + key.length());
            int end = rest.indexOf(", ");
            return (end < 0 ? rest : rest.substring(0, end)).strip();
        }
        return "";
    }

    /** The unsigned number of {@code width} bytes at {@code at}. */
    private static long get(byte[] in, int at, int width) {
        long value = 0;
        for (int i = 0; i < width; i++) {
            value = value << 8 | in[at + i] & 0xFF;
        }
        return value;
    }

    private static Table table(List<long[]> row, int width, int repeat) {
        if (width != 1 && width != 2 && width != 4) {
            throw new IllegalArgumentException(
                    "the width is 1, 2 or 4 bytes, not " + width);
        }
        int columns = row.get(0).length;
        byte[][] column = new byte[columns][];
        for (int i = 0; i < columns; i++) {
            column[i] = new byte[row.size() * width];
            for (int r = 0; r < row.size(); r++) {
                long value = row.get(r)[i];
                if (!fits(value, width)) {
                    throw new IllegalArgumentException("row " + r + " column "
                            + i + " gives " + value + ", which " + width
                            + " bytes do not take");
                }
                put(column[i], r * width, value, width);
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
            if (columns < 0 && !aNumberAmong(cell)) {
                // a line of column names, or one describing the table
                continue;
            }
            if (columns < 0) {
                columns = cell.length;
                if (columns > 256) {
                    throw new IllegalArgumentException(
                            "C is 1 to 256, not " + columns);
                }
            } else if (cell.length != columns) {
                throw new IllegalArgumentException("line " + (at + 1)
                        + " gives " + cell.length + " values, not " + columns);
            }
            long[] row = new long[columns];
            for (int i = 0; i < columns; i++) {
                row[i] = value(cell[i].strip(), at + 1, i);
            }
            out.add(row);
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException(
                    "the text does not contain a row");
        }
        return out;
    }

    /** Whether a cell of the line is a number, as {@link #value} reads one. */
    private static boolean aNumberAmong(String[] cell) {
        for (String one : cell) {
            String read = one.strip();
            boolean hex = read.startsWith("$") || read.startsWith("-$");
            String digits = read.startsWith("-$") ? read.substring(2)
                    : hex || read.startsWith("-") ? read.substring(1) : read;
            if (digits.isEmpty()) {
                continue;
            }
            boolean number = true;
            for (int i = 0; i < digits.length(); i++) {
                if (Character.digit(digits.charAt(i), hex ? 16 : 10) < 0) {
                    number = false;
                }
            }
            if (number) {
                return true;
            }
        }
        return false;
    }

    /** The narrowest width that takes every value of every column. */
    private static int narrowest(List<long[]> row) {
        int taken = 1;
        for (long[] read : row) {
            for (long value : read) {
                while (!fits(value, taken)) {
                    if (taken == 4) {
                        throw new IllegalArgumentException("the text gives "
                                + value + ", which no width of 1, 2 or 4"
                                + " bytes takes");
                    }
                    taken = taken == 1 ? 2 : 4;
                }
            }
        }
        return taken;
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
