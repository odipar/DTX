package org.dtx;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What the checks that drive the tools share: where the tools stand, how one
 * is run, and the tables they are run over.
 *
 * <p>A check here drives the built tools rather than the classes behind
 * them, so it has what a caller gets.
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

    /** Every directory {@link #work} made, in the order it made them. */
    private static final List<Path> WORK = new ArrayList<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(Rig::removeWork));
    }

    /**
     * A directory the caller writes into. It and everything under it are
     * removed when the JVM goes: {@code deleteOnExit} removes an empty
     * directory alone, and every caller writes files into this one.
     */
    static Path work(String named) {
        try {
            Path at = Files.createTempDirectory(named);
            synchronized (WORK) {
                WORK.add(at);
            }
            return at;
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    /** Every work directory and what stands under it, removed deepest
     * first. */
    private static void removeWork() {
        synchronized (WORK) {
            for (Path at : WORK) {
                try (var walk = Files.walk(at)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException gone) {
                            // a temporary directory the system clears
                        }
                    });
                } catch (IOException gone) {
                    // the same
                }
            }
        }
    }

    /** Comma separated text of {@code rows} rows and {@code columns} columns. */
    static String numbers(int rows, int columns) {
        return numbers(rows, columns, 97);
    }

    /**
     * The same, row r column i being r(i+1) modulo {@code modulo}. The
     * conformance kit and the experiments are written at 251.
     */
    static String numbers(int rows, int columns, int modulo) {
        StringBuilder out = new StringBuilder();
        for (int r = 0; r < rows; r++) {
            for (int i = 0; i < columns; i++) {
                out.append(i == 0 ? "" : ",").append(r * (i + 1) % modulo);
            }
            out.append('\n');
        }
        return out.toString();
    }

    /** A table repeating a pattern 37 rows long, 512 rows. */
    static String repeating() {
        StringBuilder out = new StringBuilder();
        for (int r = 0; r < 512; r++) {
            out.append(r % 37).append(',').append(r % 37 * 7).append('\n');
        }
        return out.toString();
    }

    /** The arguments Write takes for one table. */
    static List<String> writeArgs(int variant, int width,
            @Nullable Integer repeat, int unit, int ring, boolean copies) {
        List<String> argv = new ArrayList<>();
        argv.add("-v" + variant);
        argv.add("-w" + width);
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

    /** One table written by the Java tree, through org.dtx.Write, and its
     * bytes. */
    static byte[] write(Path work, String csv, int variant, int width,
            @Nullable Integer repeat, int unit, int ring, boolean copies) {
        try {
            Path text = work.resolve("t.csv");
            Path out = work.resolve("t.dtx");
            Files.writeString(text, csv);
            List<String> argv = new ArrayList<>(List.of("java", "-cp",
                    root().resolve("target/classes").toString(),
                    "org.dtx.Write"));
            argv.addAll(writeArgs(variant, width, repeat, unit, ring, copies));
            filter(argv, text, out);
            return Files.readAllBytes(out);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    /**
     * One tool run as a filter: its input from {@code in}, or none where
     * that is null, and its output into {@code out}. What it reported on
     * standard error comes back, and a run that fails stops the test.
     */
    static String filter(List<String> argv, @Nullable Path in, Path out) {
        try {
            ProcessBuilder built = new ProcessBuilder(argv).directory(root().toFile())
                    .redirectOutput(out.toFile());
            if (in != null) {
                built.redirectInput(in.toFile());
            }
            Process ran = built.start();
            String said = new String(ran.getErrorStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            if (ran.waitFor() != 0) {
                throw new IllegalStateException(argv.get(0) + " gave " + said.trim());
            }
            return said;
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(stopped);
        }
    }
}
