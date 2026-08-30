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
 * <p>No ST4 code is kept in this repository, so a column is packed by the
 * executable ST4's own repository builds. The unit and the ring reach it as
 * {@code -kK} and {@code -mN}, where {@code -m} counts units and {@code N}
 * is in bytes.
 */
public final class St4 implements Packer {

    private final Path packer;

    /** A packer that runs the executable at {@code packer}. */
    public St4(Path packer) {
        this.packer = packer;
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
                        "-m" + ring / unit, in.toString(), out.toString()));
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
