package org.dtx;

/**
 * The table SPEC.md's pictures draw: {@code R} = 3 rows, {@code C} = 3
 * columns, of width 2. Its payload is 18 bytes under DTX0 and 18 under
 * DTX1, and its header runs to 16.
 */
final class Example {

    static final int ROWS = 3;
    static final int REPEAT = 1;
    static final int WIDTH = 2;
    static final int HEADER = 16;
    static final int DTX0_PAYLOAD = 18;
    static final int DTX1_PAYLOAD = 18;

    /** a0 a1 a2, two bytes each. */
    static final byte[] A = {0x11, 0x12, 0x21, 0x22, 0x31, 0x32};

    /** b0 b1 b2. */
    static final byte[] B = {0x40, 0x41, 0x50, 0x51, 0x60, 0x61};

    /** c0 c1 c2. */
    static final byte[] C = {0x70, 0x71, (byte) 0x80, (byte) 0x81,
        (byte) 0x90, (byte) 0x91};

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
