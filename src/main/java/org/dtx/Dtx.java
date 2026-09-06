package org.dtx;

/**
 * The header every variant shares, and the numbers the variants take.
 *
 * <p>{@code doc/SPEC.md} section 1: {@code DTX}, the variant, {@code R},
 * {@code C}, {@code RR}, the width every value takes, and one zero byte, so
 * the payload begins on a long. Every field of more than one byte is most
 * significant byte first.
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

    /** What a header runs to, under every variant and every {@code C}. */
    public static final int HEADER = 16;

    /**
     * The largest {@code R} a header defines (R6.1). The field is four
     * bytes, and a reader takes it as a signed long, so a count above this
     * is out of bounds; the error gives the count the field defines rather
     * than the negative it reads as. {@code RR} is 0 to {@code R}, so the
     * one bound covers both.
     */
    public static final int MAX_ROWS = Integer.MAX_VALUE;

    private Dtx() {
    }

    /** {@code at} up to the next multiple of {@code to}. */
    static int align(int at, int to) {
        return (at + to - 1) / to * to;
    }

    /** The header of {@code table} under {@code variant}. */
    public static byte[] header(int variant, Table table) {
        byte[] out = new byte[HEADER];
        System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
        out[3] = (byte) variant;
        putLong(out, 4, table.rows());
        putWord(out, 8, table.columns());
        putLong(out, 10, table.repeat());
        out[14] = (byte) table.width();
        return out;
    }

    /**
     * What a file's header defines.
     *
     * @param variant the byte at offset 3
     * @param rows {@code R}
     * @param columns {@code C}
     * @param repeat {@code RR}
     * @param width the bytes every value takes
     */
    public record Header(int variant, int rows, int columns, int repeat,
            int width) {

        /** What the header runs to, the payload's first byte. */
        public int length() {
            return HEADER;
        }

        /** A row's bytes: {@code C} values of {@code W} bytes. */
        public int rowBytes() {
            return columns * width;
        }
    }

    /**
     * The header at the start of {@code file}.
     *
     * @throws IllegalArgumentException where the file is short of a header,
     *     does not open with {@code DTX}, or breaks a bound R6 sets
     */
    public static Header header(byte[] file) {
        if (file.length < HEADER) {
            throw new IllegalArgumentException(
                    "a file of " + file.length
                            + " bytes does not contain a header");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (file[i] != MAGIC[i]) {
                throw new IllegalArgumentException("the file does not open"
                        + " with DTX");
            }
        }
        long rows = getLong(file, 4) & 0xFFFFFFFFL;
        int columns = getWord(file, 8);
        long repeat = getLong(file, 10) & 0xFFFFFFFFL;
        int width = file[14] & 0xFF;
        if (rows < 1 || rows > MAX_ROWS) {
            throw new IllegalArgumentException(
                    "R is 1 to " + MAX_ROWS + ", not " + rows);
        }
        if (columns < 1 || columns > 256) {
            throw new IllegalArgumentException("C is 1 to 256, not " + columns);
        }
        if (repeat > rows) {
            throw new IllegalArgumentException("RR is 0 to R, not " + repeat);
        }
        if (width != 1 && width != 2 && width != 4) {
            throw new IllegalArgumentException("the width is 1, 2 or 4 bytes,"
                    + " not " + width);
        }
        return new Header(file[3] & 0xFF, (int) rows, columns,
                (int) repeat, width);
    }

    /**
     * The table in {@code file}, under any variant. A DTX2 file is unpacked
     * with the copy of ST4 in this repository.
     *
     * @throws IllegalArgumentException where the variant is not 0, 1 or 2
     */
    public static Table read(byte[] file) {
        Header header = header(file);
        return switch (header.variant()) {
            case DTX0 -> Dtx0.read(file);
            case DTX1 -> Dtx1.read(file);
            case DTX2 -> Dtx2.read(file);
            default -> throw new IllegalArgumentException(
                    "variant " + header.variant() + " is not 0, 1 or 2");
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
