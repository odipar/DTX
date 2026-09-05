package org.dtx;

import org.st4.St4Compressor;
import org.st4.St4EventOptimizer;
import org.st4.St4Format;
import org.st4.St4LiteralCopySearch;
import org.st4.Units;

/**
 * A {@link Packer} that packs with the copy of ST4 in this repository.
 *
 * <p>{@code src/main/java/org/st4} is that copy, taken from
 * odipar/ST4@498aa25 and not edited here. So a tool writes DTX2 with no
 * packer beside it, and {@link St4Beside} runs one where a caller names it.
 *
 * <p>The packer takes what {@code st4 -f -kK -mN -l65535} gives it, and {@code -c} beside them where the columns contain copies. The
 * ring is bytes and the packer counts units, so {@code -m} is the ring
 * divided by the unit, at most what a word offset can give.
 *
 * <p>{@code -l65535} meets ST4_wrap's assumption 4: no operation is longer
 * than the 65535 units the 68000 decoders count in a word. ST4's own
 * default already fits them, and this defines it rather than taking it.
 */
public final class St4 implements Packer {

    /** The longest operation, ST4_wrap assumption 4. */
    private static final int MAX_OP = 65535;

    private final boolean copies;
    private final double seconds;

    /** A packer that does not pack copies from the literal stream. */
    public St4() {
        this(false, 0);
    }

    /**
     * A packer that so that a match beyond the ring copies from the column's own
     * literal stream, which packs a small ring far smaller.
     *
     * @param copies whether to pack copies
     * @param seconds how long to search beyond the opening passes for a
     *     better parse, or zero for those passes alone. A search of no
     *     seconds is the same parse every run; one of some seconds is not
     */
    public St4(boolean copies, double seconds) {
        this.copies = copies;
        this.seconds = seconds;
    }

    @Override
    public boolean copies() {
        return copies;
    }

    @Override
    public byte[] pack(byte[] column, int unit, int ring) {
        String problem = St4Format.checkUnit(unit);
        if (!problem.isEmpty()) {
            throw new IllegalArgumentException(problem);
        }
        // A word offset is stored scaled to bytes, so the limit is a byte
        // figure: 32512 units at k=4 would not fit the word.
        int offsetLimit = Math.min(ring / unit, St4Format.maxOffsetUnits(unit));
        int[] units = Units.split(column, unit);
        var parsed = copies
                ? St4LiteralCopySearch.optimize(units, unit, offsetLimit,
                        MAX_OP, seconds, false)
                : St4EventOptimizer.optimize(units, unit, offsetLimit, false);
        St4Compressor.Result result = St4Compressor.compress(
                parsed, units, unit, MAX_OP, -1, offsetLimit);
        return org.st4.St4.container(result);
    }
}
