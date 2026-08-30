package org.dtx;

/**
 * The table SPEC.md's pictures draw: {@code R} = 3 rows, {@code C} = 3
 * columns, of widths 1, 4 and 2. Its payload is 21 bytes under DTX0 and 22
 * under DTX1, and its header runs to 20.
 */
final class Example {

    static final int ROWS = 3;
    static final int REPEAT = 1;
    static final int[] WIDTH = {1, 4, 2};
    static final int HEADER = 20;
    static final int DTX0_PAYLOAD = 21;
    static final int DTX1_PAYLOAD = 22;

    /** a0 a1 a2, one byte each. */
    static final byte[] A = {0x11, 0x22, 0x33};

    /** b0 b1 b2, four bytes each. */
    static final byte[] B = {
        0x40, 0x41, 0x42, 0x43,
        0x50, 0x51, 0x52, 0x53,
        0x60, 0x61, 0x62, 0x63};

    /** c0 c1 c2, two bytes each. */
    static final byte[] C = {0x70, 0x71, (byte) 0x80, (byte) 0x81, (byte) 0x90,
        (byte) 0x91};

    private Example() {
    }

    static Table table() {
        return Table.of(ROWS, REPEAT, WIDTH, new byte[][] {A, B, C});
    }

    /** The bytes of {@code file} from {@code at}, {@code length} of them. */
    static byte[] at(byte[] file, int at, int length) {
        byte[] out = new byte[length];
        System.arraycopy(file, at, out, 0, length);
        return out;
    }
}
