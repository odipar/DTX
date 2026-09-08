package org.dtx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
 * rewrite them the same way, build the same twenty-two images and combine the
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
            List<String> command = command(tree, tool);
            Path at = work.resolve(tree + "-" + out);
            for (String one : argv) {
                command.add(OUT.equals(one) ? at.toString() : one);
            }
            Rig.run(command);
            written.put(tree, Files.readAllBytes(at));
        }
        return written;
    }

    /** The command that runs {@code tool} in {@code tree}, before its arguments. */
    private static List<String> command(String tree, String tool) {
        Path work = work();
        return new ArrayList<>(switch (tree) {
            case "java" -> List.of("java", "-cp",
                    Rig.root().resolve("target/classes").toString(),
                    TOOLS.get(tool));
            case "go" -> List.of(work.resolve("dtx-" + tool).toString());
            default -> List.of("dotnet", work.resolve("dtx.dll").toString(),
                    "dtx-" + tool);
        });
    }

    @Test
    void everyTreePrintsOneHelp() {
        for (String tool : TOOLS.keySet()) {
            Map<String, String> said = new LinkedHashMap<>();
            for (String tree : List.of("java", "go", "cs")) {
                // the Go packager combines and does not assemble, so its help
                // lists neither -a nor -s
                if (tool.equals("package") && tree.equals("go")) {
                    continue;
                }
                List<String> command = command(tree, tool);
                command.add("-help");
                said.put(tree, Rig.run(command));
            }
            String one = said.getOrDefault("java", "");
            for (Map.Entry<String, String> tree : said.entrySet()) {
                assertEquals(one, tree.getValue(), tool + " -help: the "
                        + tree.getKey() + " tree prints another text");
            }
        }
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
        record Case(String name, int variant, int width,
                @Nullable Integer repeat, int unit, int ring, boolean copies) {}
        for (Case one : List.of(
                new Case("DTX0, a width given", 0, 2, null, 1, 960, false),
                new Case("DTX1, a repeat", 1, 1, 32, 1, 960, false),
                new Case("DTX1, four byte values", 1, 4, null, 1, 960, false),
                new Case("DTX2, k of 1", 2, 1, null, 1, 960, false),
                new Case("DTX2, k of 2 at a width of 2", 2, 2, null, 2, 960, false),
                new Case("DTX2, k of 4 at a width of 4", 2, 4, null, 4, 960, false),
                new Case("DTX2, k of 1 at a width of 4", 2, 4, null, 1, 960, false),
                new Case("DTX2, with copies", 2, 2, null, 1, 960, true))) {
            List<String> argv = new ArrayList<>();
            argv.add(text.toString());
            argv.add(OUT);
            argv.addAll(Rig.writeArgs(one.variant(), one.width(), one.repeat(),
                    one.unit(), one.ring(), one.copies()));
            same(one.name(), each("write", argv, "w"));
        }
    }

    @Test
    void everyTreeWritesTheSameFileFromADtxFile() throws IOException {
        Path work = work();
        Path plain = work.resolve("plain.dtx");
        Files.write(plain, Rig.write(work, Rig.numbers(64, 3), 1,
                2, null, 1, 960, false));
        Path packed = work.resolve("packed.dtx");
        Files.write(packed, Rig.write(work, Rig.numbers(64, 3), 2,
                2, null, 1, 960, false));
        record Case(String name, Path in, List<String> flags, String out) {}
        for (Case one : List.of(
                new Case("DTX1 to DTX2, k of 1", plain, List.of("-v2", "-k1", "-m960"), "r.dtx"),
                new Case("DTX1 to DTX2, k of 2", plain, List.of("-v2", "-k2", "-m960"), "r.dtx"),
                new Case("DTX1 to DTX2, with copies", plain, List.of("-v2", "-k1", "-m960", "-copies"), "r.dtx"),
                new Case("DTX2 to DTX2, k of 2", packed, List.of("-k2"), "r.dtx"),
                new Case("DTX2 to DTX2, copies at a ring of 64", packed, List.of("-m64", "-copies"), "r.dtx"),
                new Case("DTX2 to DTX0", packed, List.of("-v0"), "r.dtx"),
                new Case("DTX2 to DTX1, repeating at 16", packed, List.of("-v1", "-r16"), "r.dtx"),
                new Case("DTX2 to text", packed, List.of(), "r.csv"),
                new Case("DTX1 to text", plain, List.of(), "r.csv"))) {
            List<String> argv = new ArrayList<>(List.of(one.in().toString(), OUT));
            argv.addAll(one.flags());
            same(one.name(), each("write", argv, one.out()));
        }
    }

    @Test
    void everyTreeBuildsTheSameImages() throws IOException {
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
                int width, @Nullable Integer repeat, int unit, int ring,
                boolean copies) {}
        for (Case one : List.of(
                new Case("DTX0", 0, 64, 3, 2, null, 1, 960, false),
                new Case("DTX0, one column", 0, 9, 1, 1, null, 1, 960, false),
                new Case("DTX1", 1, 64, 3, 2, null, 1, 960, false),
                new Case("DTX1, one row", 1, 1, 2, 4, null, 1, 960, false),
                new Case("DTX1, one byte values", 1, 64, 3, 1, null, 1, 960, false),
                new Case("DTX2, k of 1", 2, 64, 3, 1, null, 1, 960, false),
                new Case("DTX2, k of 4", 2, 64, 2, 4, null, 4, 960, false),
                new Case("DTX2, k of 4 at a width of 1", 2, 64, 2, 1, null, 4, 960, false),
                new Case("DTX2, with copies", 2, 64, 2, 2, null, 1, 960, true),
                new Case("DTX2, twenty columns", 2, 64, 20, 2, null, 1, 960, false),
                new Case("DTX2, a ring of 480", 2, 64, 2, 2, null, 1, 480, false))) {
            Path src = work.resolve("p.dtx");
            Files.write(src, Rig.write(work, Rig.numbers(one.rows(), one.columns()),
                    one.variant(), one.width(), one.repeat(), one.unit(),
                    one.ring(), one.copies()));
            same(one.name(), each("package", List.of(src.toString(), OUT), "p"));
        }
    }

    @Test
    void everyTreePackagesTheSameImageOfSeveralTables() throws IOException {
        Path work = work();
        // An image of several tables is where the trees could drift apart
        // without a case of their own: the layout past the first table, the
        // padding between the pairs, and which figures the format block
        // gives (doc/abi.md 1).
        record Case(String name, int variant, int width, int unit, boolean copies) {}
        for (Case one : List.of(
                new Case("DTX0, three tables", 0, 2, 1, false),
                new Case("DTX1, three tables", 1, 2, 1, false),
                new Case("DTX2, three tables", 2, 2, 1, false),
                new Case("DTX2, three tables at k of 2", 2, 2, 2, false),
                new Case("DTX2, three tables with copies", 2, 2, 1, true))) {
            List<String> named = new ArrayList<>();
            int[] rows = {24, 40, 9};
            for (int i = 0; i < rows.length; i++) {
                Path src = work.resolve("p" + i + ".dtx");
                Files.write(src, Rig.write(work, Rig.numbers(rows[i], 3),
                        one.variant(), one.width(), null, one.unit(), 960,
                        one.copies()));
                named.add(src.toString());
            }
            named.add(OUT);
            same(one.name(), each("package", named, "p"));
        }
    }
}
