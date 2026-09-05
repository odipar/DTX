package org.dtx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The three trees against each other, byte for byte.
 *
 * <p>One input has one output. Java, Go and C# write the same DTX files,
 * rewrite them the same way, build the same eight images and combine the
 * same packages, so a caller who takes any one of them has the same bytes
 * at every step.
 *
 * <p>No ST4 packer stands beside this: each tree contains a copy, and what
 * these check is that the three copies pack the same bytes.
 *
 * <p>Skipped where Go, the .NET SDK or rmac is not installed.
 */
class ParityTest {

    /** What each tool is written as, in the Java tree. */
    private static final Map<String, String> TOOLS = Map.of(
            "write", "org.dtx.Write",
            "rewrite", "org.dtx.Rewrite",
            "package", "org.dtx.Packager",
            "blobs", "org.dtx.Blobs");

    private static @Nullable Path built;

    @BeforeAll
    static void buildTheOtherTwo() throws IOException {
        Assumptions.assumeTrue(Rig.onThePath("go"), "no go on the path");
        Assumptions.assumeTrue(Rig.onThePath("dotnet"), "no dotnet on the path");
        Assumptions.assumeTrue(Rig.onThePath(Rig.rmac()), "no rmac at " + Rig.rmac());
        Path work = Rig.work("dtxparity");
        for (String tool : TOOLS.keySet()) {
            Rig.run(Rig.root().resolve("go"), List.of("go", "build", "-o",
                    work.resolve("dtx-" + tool).toString(), "./cmd/dtx-" + tool));
        }
        Rig.run(Rig.root().resolve("dotnet"), List.of("dotnet", "build",
                "-v", "q", "--nologo", "-o", work.toString()));
        built = work;
    }

    private static Path work() {
        Path at = built;
        if (at == null) {
            throw new IllegalStateException("the trees were not built");
        }
        return at;
    }

    /** One tool run in every tree, at out.java, out.go and out.cs. */
    private static Map<String, byte[]> each(String tool, List<String> argv,
            String out) throws IOException {
        Map<String, byte[]> written = new LinkedHashMap<>();
        Path work = work();
        for (String tree : List.of("java", "go", "cs")) {
            List<String> command = new ArrayList<>(switch (tree) {
                case "java" -> List.of("java", "-cp",
                        Rig.root().resolve("target/classes").toString(),
                        TOOLS.get(tool));
                case "go" -> List.of(work.resolve("dtx-" + tool).toString());
                default -> List.of("dotnet", work.resolve("dtx.dll").toString(),
                        "dtx-" + tool);
            });
            Path at = work.resolve(out + "." + tree);
            for (String one : argv) {
                command.add(OUT.equals(one) ? at.toString() : one);
            }
            Rig.run(command);
            written.put(tree, Files.readAllBytes(at));
        }
        return written;
    }

    /** What the three trees wrote, compared with one another. */
    private static void same(String name, Map<String, byte[]> written) {
        byte[] one = written.getOrDefault("java", new byte[0]);
        for (Map.Entry<String, byte[]> tree : written.entrySet()) {
            assertArrayEquals(one, tree.getValue(), name + ": the java tree"
                    + " wrote " + one.length + " bytes and the " + tree.getKey()
                    + " tree " + tree.getValue().length);
        }
    }

    /** Where a tool's output stands, as a marker in an argument list. */
    private static final String OUT = "@out";

    @Test
    void everyTreeWritesTheSameFile() throws IOException {
        Path text = work().resolve("t.csv");
        Files.writeString(text, "# a comment, and a blank line\n\n"
                + Rig.numbers(64, 3));
        record Case(String name, int variant, int[] width,
                @Nullable Integer repeat, int unit, int ring, boolean copies) {}
        for (Case one : List.of(
                new Case("DTX0, widths given", 0, new int[] {1, 2, 4}, null, 1, 960, false),
                new Case("DTX1, a repeat", 1, new int[] {1, 2, 4}, 32, 1, 960, false),
                new Case("DTX2, k of 1", 2, new int[] {1, 2, 4}, null, 1, 960, false),
                new Case("DTX2, k of 2", 2, new int[] {2, 4, 2}, null, 2, 960, false),
                new Case("DTX2, k of 4", 2, new int[] {4, 4, 4}, null, 4, 960, false),
                new Case("DTX2, with copies", 2, new int[] {1, 2, 4}, null, 1, 960, true))) {
            List<String> argv = new ArrayList<>();
            argv.add(text.toString());
            argv.add(OUT);
            argv.addAll(Rig.writeArgs(one.variant(), one.width(), one.repeat(),
                    one.unit(), one.ring(), one.copies()));
            same(one.name(), each("write", argv, "w"));
        }
    }

    @Test
    void everyTreeRewritesTheSameWay() throws IOException {
        Path plain = work().resolve("plain.dtx");
        Path text = work().resolve("r.csv");
        Files.writeString(text, Rig.numbers(64, 3));
        List<String> argv = new ArrayList<>(List.of("java", "-cp",
                Rig.root().resolve("target/classes").toString(),
                "org.dtx.Write", text.toString(), plain.toString()));
        argv.addAll(Rig.writeArgs(Dtx.DTX1, new int[] {1, 2, 4}, 32, 1, 960, false));
        Rig.run(argv);
        for (List<String> flags : List.of(List.of("-k1", "-m960"),
                List.of("-k2", "-m960"), List.of("-k1", "-m960", "-copies"))) {
            List<String> run = new ArrayList<>();
            run.add(plain.toString());
            run.add(OUT);
            run.addAll(flags);
            same("rewrite " + flags, each("rewrite", run, "r"));
        }
    }

    @Test
    void everyTreeBuildsTheSameEightImages() throws IOException {
        Path work = work();
        Map<String, Path> into = new LinkedHashMap<>();
        for (String tree : List.of("java", "go", "cs")) {
            Path at = work.resolve("blobs." + tree);
            List<String> command = new ArrayList<>(switch (tree) {
                case "java" -> List.of("java", "-cp",
                        Rig.root().resolve("target/classes").toString(),
                        "org.dtx.Blobs");
                case "go" -> List.of(work.resolve("dtx-blobs").toString());
                default -> List.of("dotnet", work.resolve("dtx.dll").toString(),
                        "dtx-blobs");
            });
            command.add(at.toString());
            command.add("-a" + Rig.rmac());
            command.add("-t" + Rig.root().resolve("68k"));
            Rig.run(command);
            into.put(tree, at);
        }
        try (var listed = Files.list(into.get("java"))) {
            for (Path image : listed.sorted().toList()) {
                Map<String, byte[]> written = new LinkedHashMap<>();
                for (Map.Entry<String, Path> at : into.entrySet()) {
                    written.put(at.getKey(), Files.readAllBytes(
                            at.getValue().resolve(image.getFileName())));
                }
                same(image.getFileName().toString(), written);
            }
        }
    }

    @Test
    void everyTreePackagesTheSameImage() throws IOException {
        Path work = work();
        record Case(String name, int variant, int rows, int columns,
                int[] width, @Nullable Integer repeat, int unit, int ring,
                boolean copies) {}
        for (Case one : List.of(
                new Case("DTX0", 0, 64, 3, new int[] {1, 2, 4}, null, 1, 960, false),
                new Case("DTX0, one column", 0, 9, 1, new int[] {1}, null, 1, 960, false),
                new Case("DTX1", 1, 64, 3, new int[] {1, 2, 4}, null, 1, 960, false),
                new Case("DTX1, one row", 1, 1, 2, new int[] {1, 4}, null, 1, 960, false),
                new Case("DTX2, k of 1", 2, 64, 3, new int[] {1, 2, 4}, null, 1, 960, false),
                new Case("DTX2, k of 4", 2, 64, 2, new int[] {4, 4}, null, 4, 960, false),
                new Case("DTX2, with copies", 2, 64, 2, new int[] {1, 2}, null, 1, 960, true),
                new Case("DTX2, a ring of 480", 2, 64, 2, new int[] {1, 2}, null, 1, 480, false))) {
            Path src = work.resolve("p.dtx");
            Files.write(src, Rig.write(work, Rig.numbers(one.rows(), one.columns()),
                    one.variant(), one.width(), one.repeat(), one.unit(),
                    one.ring(), one.copies()));
            same(one.name(), each("package", List.of(src.toString(), OUT), "p"));
        }
    }
}
