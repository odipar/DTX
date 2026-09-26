package org.dtx;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.st4.St4Format;

/**
 * A {@link Packer} that runs an ST4 packer beside this one.
 *
 * <p>{@link St4} packs with the copy in this repository, and a tool
 * uses it where none is named. This runs another: a separate ST4
 * build, named by {@code -p}, so a packer newer than the copy here is used
 * through this. The unit and the ring reach it as {@code -kK} and
 * {@code -mN}, where {@code -m} counts units and {@code N} is in bytes, at
 * most what a word offset reaches.
 *
 * <p>Every column is packed with {@code -l65535}, which meets ST4_wrap's
 * assumption 4: no operation is longer than the 65535 units the 68000
 * decoders count in a word. ST4's own default already fits them, and this
 * names it rather than leaving it to that default.
 *
 * <p>{@code copies} reaches the packer as {@code -c}, or {@code -cS} for a
 * search of {@code S} seconds. In a column packed that way a match beyond
 * the ring copies from its literal stream, which packs a small ring far
 * smaller; the reader of it needs a decoder built with
 * {@code ST4_WINDOW equ 1}, which the payload defines (R5.10).
 */
public final class St4Beside implements Packer {

    private final Path packer;
    private final String copies;

    /** A packer that runs the executable at {@code packer}. */
    public St4Beside(Path packer) {
        this(packer, "");
    }

    /**
     * A packer that runs the executable at {@code packer}, under which a
     * match beyond the ring copies from the literal stream.
     *
     * @param copies {@code -c}, or {@code -cS} for a search of {@code S}
     *     seconds, or empty for none
     */
    public St4Beside(Path packer, String copies) {
        this.packer = packer;
        this.copies = copies;
    }

    @Override
    public boolean copies() {
        return !copies.isEmpty();
    }

    @Override
    public byte[] pack(byte[] column, int unit, int ring, int loop) {
        try {
            Path work = Files.createTempDirectory("dtx");
            try {
                Path in = work.resolve("column");
                Path out = work.resolve("column.st4");
                Files.write(in, column);
                // The offset limit is capped as St4 caps it, so the two
                // packers work to one limit: a word offset is stored
                // scaled to bytes, and 32512 units at k=4 would not fit the
                // word.
                int offsetLimit = Math.min(ring / unit,
                        St4Format.maxOffsetUnits(unit));
                List<String> command = new ArrayList<>(List.of(
                        packer.toString(), "-k" + unit,
                        "-m" + offsetLimit, "-l65535", "-silent"));
                if (loop >= 0) {
                    // st4 -r reads the loop's unit, and resolves for
                    // itself whether a back reference reaches the loop's
                    // first unit or the pass has to be replayed
                    command.add("-r" + loop);
                }
                if (!copies.isEmpty()) {
                    command.add(copies);
                }
                // The packer reads the column on standard input and
                // writes the packed bytes on standard output (ST4,
                // doc/tools.md), so the two files are at those rather
                // than on the command line. Redirecting both keeps the
                // pipes from filling while this writes one and reads the
                // other.
                Process run = new ProcessBuilder(command)
                        .redirectInput(in.toFile())
                        .redirectOutput(out.toFile())
                        .start();
                byte[] said = run.getErrorStream().readAllBytes();
                if (run.waitFor() != 0 || !Files.exists(out)) {
                    throw new IllegalStateException(packer + " reported "
                            + new String(said).trim());
                }
                return Files.readAllBytes(out);
            } finally {
                try (var walk = Files.walk(work)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException gone) {
                                    throw new UncheckedIOException(gone);
                                }
                            });
                }
            }
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(stopped);
        }
    }
}
