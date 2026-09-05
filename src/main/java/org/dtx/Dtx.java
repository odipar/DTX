package org.dtx;

/**
 * The header every variant shares, and the numbers the variants take.
 *
 * <p>{@code doc/SPEC.md} section 1: {@code DTX}, the variant, {@code R},
 * {@code C}, {@code RR}, a width a column, and zero bytes up to the next
 * long, so the payload begins on one. Every field of more than one byte is
 * most significant byte first.
 */
public final class Dtx {

    /** The three bytes a file opens with. */
    public static final byte[] MAGIC = {'D', 'T', 'X'};

    /** The variant byte of a table laid out row by row. */
    public static final int DTX0 = 0;

    /** The variant byte of a table laid out column by column. */
    public static final int DTX1 = 1;

    /** The variant byte of a table laid out column by column and packed. */
    public static final int DTX2 = 2;

    private Dtx() {
    }

    /** What a header runs to: 14 plus {@code C}, up to the next long. */
    public static int headerLength(int columns) {
        return align(14 + columns, 4);
    }

    /** {@code at} up to the next multiple of {@code to}. */
    static int align(int at, int to) {
        return (at + to - 1) / to * to;
    }

    /** The header of {@code table} under {@code variant}. */
    public static byte[] header(int variant, Table table) {
        byte[] out = new byte[headerLength(table.columns())];
        System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
        out[3] = (byte) variant;
        putLong(out, 4, table.rows());
        putWord(out, 8, table.columns());
        putLong(out, 10, table.repeat());
        for (int i = 0; i < table.columns(); i++) {
            out[14 + i] = (byte) table.width(i);
        }
        return out;
    }

    /**
     * What a file's header states.
     *
     * @param variant the byte at offset 3
     * @param rows {@code R}
     * @param repeat {@code RR}
     * @param width one entry a column
     * @param length what the header runs to, the payload's first byte
     */
    public record Header(int variant, int rows, int repeat, int[] width,
            int length) {

        /** {@code C}, the column count. */
        public int columns() {
            return width.length;
        }
    }

    /**
     * The header at the start of {@code file}.
     *
     * @throws IllegalArgumentException where the file is short of a header,
     *     does not open with {@code DTX}, or breaks a bound R6 sets
     */
    public static Header header(byte[] file) {
        if (file.length < 16) {
            throw new IllegalArgumentException(
                    "a file of " + file.length + " bytes holds no header");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (file[i] != MAGIC[i]) {
                throw new IllegalArgumentException("the file does not open"
                        + " with DTX");
            }
        }
        int rows = getLong(file, 4);
        int columns = getWord(file, 8);
        int repeat = getLong(file, 10);
        if (rows < 1) {
            throw new IllegalArgumentException("R is 1 upward, not " + rows);
        }
        if (columns < 1 || columns > 256) {
            throw new IllegalArgumentException("C is 1 to 256, not " + columns);
        }
        if (repeat < 0 || repeat > rows) {
            throw new IllegalArgumentException("RR is 0 to R, not " + repeat);
        }
        int length = headerLength(columns);
        if (file.length < length) {
            throw new IllegalArgumentException("a file of " + file.length
                    + " bytes is short of a header of " + length);
        }
        int[] width = new int[columns];
        for (int i = 0; i < columns; i++) {
            width[i] = file[14 + i] & 0xFF;
            if (width[i] != 1 && width[i] != 2 && width[i] != 4) {
                throw new IllegalArgumentException("column " + i + " is "
                        + width[i] + " bytes wide, not 1, 2 or 4");
            }
        }
        return new Header(file[3] & 0xFF, rows, repeat, width, length);
    }

    /**
     * The table in {@code file}, under either plain variant.
     *
     * @throws IllegalArgumentException where the variant is not DTX0 or DTX1
     */
    public static Table read(byte[] file) {
        Header header = header(file);
        return switch (header.variant()) {
            case DTX0 -> Dtx0.read(file);
            case DTX1 -> Dtx1.read(file);
            default -> throw new IllegalArgumentException(
                    "variant " + header.variant() + " is not read here");
        };
    }

    static void putWord(byte[] out, int at, int value) {
        out[at] = (byte) (value >> 8);
        out[at + 1] = (byte) value;
    }

    static void putLong(byte[] out, int at, int value) {
        out[at] = (byte) (value >> 24);
        out[at + 1] = (byte) (value >> 16);
        out[at + 2] = (byte) (value >> 8);
        out[at + 3] = (byte) value;
    }

    static int getWord(byte[] in, int at) {
        return (in[at] & 0xFF) << 8 | in[at + 1] & 0xFF;
    }

    static int getLong(byte[] in, int at) {
        return (in[at] & 0xFF) << 24 | (in[at + 1] & 0xFF) << 16
                | (in[at + 2] & 0xFF) << 8 | in[at + 3] & 0xFF;
    }
}
