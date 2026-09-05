package org.dtx;

/**
 * What packs one column of a DTX2 payload.
 *
 * <p>DTX2 states that a column is an ST4 data set (R5.1) and nothing more
 * about how ST4 packs. {@link St4} packs with the copy of ST4 this
 * repository holds, {@link St4Beside} runs a packer beside it, and a
 * caller that writes DTX2 may supply one of its own. ST4's own repository
 * states the format,
 * and the 68000 decoder carried under {@code 68k/} reads what it packs.
 */
@FunctionalInterface
public interface Packer {

    /**
     * {@code column} as one complete ST4 data set: its own header, and the
     * length of what it unpacks to.
     *
     * @param column the bytes DTX1's column holds
     * @param unit the unit to pack at, 1, 2 or 4, which the data set's own
     *     signature then gives (R5.2)
     * @param ring the bytes no back reference in the data set reaches past
     *     (R5.4)
     */
    byte[] pack(byte[] column, int unit, int ring);

    /**
     * Whether a match beyond the ring copies from the column's own literal
     * stream, which ST4 packs with {@code -c}.
     *
     * <p>A decoder built without the copy code reads such a column wrongly,
     * and nothing in an ST4 data set states which it is. So the payload
     * states it (R5.10), and it is the packer that says so: what packed a
     * column is what knows how, and a flag carried beside the file could
     * disagree with the bytes in it.
     */
    default boolean copies() {
        return false;
    }
}
