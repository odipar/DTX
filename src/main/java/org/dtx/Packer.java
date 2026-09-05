package org.dtx;

/**
 * What packs one column of a DTX2 payload.
 *
 * <p>DTX2 defines a column as an ST4 data set (R5.1) and does not define
 * how ST4 packs. {@link St4} packs with the copy of ST4 in this
 * repository, {@link St4Beside} runs a packer beside it, and a
 * caller that writes DTX2 may supply one of its own. ST4's own repository
 * defines the format,
 * and the 68000 decoder carried under {@code 68k/} reads what it packs.
 */
@FunctionalInterface
public interface Packer {

    /**
     * {@code column} as one complete ST4 data set: its own header, and the
     * length of what it unpacks to.
     *
     * @param column the bytes of DTX1's column
     * @param unit the unit to pack at, 1, 2 or 4, which the data set's own
     *     signature then gives (R5.2)
     * @param ring the bytes past which no back reference in the data set
     *     reaches (R5.4)
     */
    byte[] pack(byte[] column, int unit, int ring);

    /**
     * Whether a match beyond the ring copies from the column's own literal
     * stream, which ST4 packs with {@code -c}.
     *
     * <p>A decoder built without the copy code reads such a column wrongly,
     * and nothing in an ST4 data set defines which it is. So the payload
     * defines it (R5.10), and the packer defines it: a flag carried beside
     * the file could differ from the bytes in it, and one the packer
     * wrote cannot.
     */
    default boolean copies() {
        return false;
    }
}
