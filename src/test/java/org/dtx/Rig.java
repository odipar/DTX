package org.dtx;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What the checks that drive the tools share: where the tools stand, how one
 * is run, and the tables they are run over.
 *
 * <p>A check here drives the built tools rather than the classes behind
 * them, so what it holds is what a caller gets.
 */
final class Rig {

    private Rig() {
    }

    /** The repository's own directory, found from the working one. */
    static Path root() {
        Path at = Path.of("").toAbsolutePath();
        while (at != null && !Files.isDirectory(at.resolve("68k"))) {
            at = at.getParent();
        }
        if (at == null) {
            throw new IllegalStateException("no 68k/ above the working directory");
        }
        return at;
    }

    /** The assembler: {@code $RMAC}, or {@code rmac} on the path. */
    static String rmac() {
        String named = System.getenv("RMAC");
        return named == null ? "rmac" : named;
    }

    /** Whether one stands where that names it. */
    static boolean onThePath(String tool) {
        if (tool.contains("/")) {
            return Files.isExecutable(Path.of(tool));
        }
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String at : path.split(":")) {
            if (Files.isExecutable(Path.of(at).resolve(tool))) {
                return true;
            }
        }
        return false;
    }

    /** One command run to its end, or what it said where it failed. */
    static String run(Path in, List<String> argv) {
        try {
            Process ran = new ProcessBuilder(argv).directory(in.toFile())
                    .redirectErrorStream(true).start();
            String said = new String(ran.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            if (ran.waitFor() != 0) {
                throw new IllegalStateException(
                        argv.get(0) + " gave " + said.trim());
            }
            return said;
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(stopped);
        }
    }

    /** The same, in the repository's own directory. */
    static String run(List<String> argv) {
        return run(root(), argv);
    }

    /** A directory the caller writes into, removed with the JVM. */
    static Path work(String named) {
        try {
            Path at = Files.createTempDirectory(named);
            at.toFile().deleteOnExit();
            return at;
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    /** Comma separated text of {@code rows} rows and {@code columns} columns. */
    static String numbers(int rows, int columns) {
        StringBuilder out = new StringBuilder();
        for (int r = 0; r < rows; r++) {
            for (int i = 0; i < columns; i++) {
                out.append(i == 0 ? "" : ",").append(r * (i + 1) % 97);
            }
            out.append('\n');
        }
        return out.toString();
    }

    /** The arguments Write takes for one table. */
    static List<String> writeArgs(int variant, int[] width,
            @Nullable Integer repeat, int unit, int ring, boolean copies) {
        List<String> argv = new ArrayList<>();
        argv.add("-v" + variant);
        StringBuilder drawn = new StringBuilder();
        for (int i = 0; i < width.length; i++) {
            drawn.append(i == 0 ? "" : ",").append(width[i]);
        }
        argv.add("-w" + drawn);
        if (repeat != null) {
            argv.add("-r" + repeat);
        }
        if (variant == Dtx.DTX2) {
            argv.add("-k" + unit);
            argv.add("-m" + ring);
            if (copies) {
                argv.add("-copies");
            }
        }
        return argv;
    }

    /** One table written by the Java tree, as a .dtx file at {@code out}. */
    static byte[] write(Path work, String csv, int variant, int[] width,
            @Nullable Integer repeat, int unit, int ring, boolean copies) {
        try {
            Path text = work.resolve("t.csv");
            Path out = work.resolve("t.dtx");
            Files.writeString(text, csv);
            List<String> argv = new ArrayList<>(List.of("java", "-cp",
                    root().resolve("target/classes").toString(),
                    "org.dtx.Write", text.toString(), out.toString()));
            argv.addAll(writeArgs(variant, width, repeat, unit, ring, copies));
            run(argv);
            return Files.readAllBytes(out);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }
}
