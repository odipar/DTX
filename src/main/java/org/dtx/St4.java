package org.dtx;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Packer} that runs an ST4 packer beside this one.
 *
 * <p>No ST4 packer is kept in this repository, so a column is packed by the
 * executable ST4's own repository builds. The unit and the ring reach it as
 * {@code -kK} and {@code -mN}, where {@code -m} counts units and {@code N}
 * is in bytes.
 *
 * <p>Every column is packed with {@code -l65535}, which holds ST4_wrap's
 * assumption 4: no operation is longer than the 65535 units the 68000
 * decoders count in a word. ST4's own default already fits them, and this
 * states it rather than taking it.
 *
 * <p>{@code copies} reaches the packer as {@code -c}, or {@code -cS} for a
 * search of {@code S} seconds. A column packed that way lets a match beyond
 * the ring copy from its own literal stream, which packs a small ring far
 * smaller; the reader of it takes a decoder built with
 * {@code ST4_WINDOW equ 1}, which {@code org.dtx.Packager -c} emits.
 */
public final class St4 implements Packer {

    private final Path packer;
    private final String copies;

    /** A packer that runs the executable at {@code packer}. */
    public St4(Path packer) {
        this(packer, "");
    }

    /**
     * A packer that runs the executable at {@code packer}, letting a match
     * beyond the ring copy from the literal stream.
     *
     * @param copies {@code -c}, or {@code -cS} for a search of {@code S}
     *     seconds, or empty for none
     */
    public St4(Path packer, String copies) {
        this.packer = packer;
        this.copies = copies;
    }

    @Override
    public boolean copies() {
        return !copies.isEmpty();
    }

    @Override
    public byte[] pack(byte[] column, int unit, int ring) {
        try {
            Path work = Files.createTempDirectory("dtx");
            try {
                Path in = work.resolve("column");
                Path out = work.resolve("column.st4");
                Files.write(in, column);
                List<String> command = new ArrayList<>(List.of(
                        packer.toString(), "-f", "-k" + unit,
                        "-m" + ring / unit, "-l65535"));
                if (!copies.isEmpty()) {
                    command.add(copies);
                }
                command.add(in.toString());
                command.add(out.toString());
                Process run = new ProcessBuilder(command)
                        .redirectErrorStream(true).start();
                byte[] said = run.getInputStream().readAllBytes();
                if (run.waitFor() != 0 || !Files.exists(out)) {
                    throw new IllegalStateException(packer + " gave "
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
