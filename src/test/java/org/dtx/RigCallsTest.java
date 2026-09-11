package org.dtx;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The calls {@code 68k/test/emu/test_dtx.py} makes, against the tools it
 * makes them to.
 *
 * <p>The rig runs the reader under emulation over every variant and every
 * width, and the misaligned access it watches for proves that a word or a
 * long never stands at an odd address. It takes minutes, so no build runs
 * it; that left its calls unread, and when the two tools became filters
 * (tools.md) the rig went on naming files and stopped at its first table.
 * The check was in the rig and the rig was not run.
 *
 * <p>So this makes the rig's own calls, out of the rig's own code: it
 * imports the module and runs the two helpers that reach a tool, over a
 * table of two rows. No emulator runs and no assembler, and it takes under
 * a second. It catches a tool's interface moving under the rig, the fault
 * that broke it.
 */
final class RigCallsTest {

    private static final Path RIG = Path.of("68k", "test", "emu", "test_dtx.py");

    /** A table the calls are made over: two rows whose values reach two
     *  bytes, so the width is not inferred as one. */
    private static final String CSV = "1,300\\n2,301\\n";

    /**
     * The rig's helpers, run on that table: {@code write_table} for each
     * variant, which is the call that broke, and {@code package_many},
     * which names several tables and reads the image off standard output.
     */
    private static final String DRIVE = String.join("\n",
            "import sys",
            "sys.path.insert(0, '68k/test/emu')",
            "import test_dtx as rig",
            "csv = '" + CSV + "'",
            "for v in (0, 1, 2):",
            "    written = rig.write_table(csv, v, 2)",
            "    assert len(written) > 16, 'DTX%d came back empty' % v",
            "    assert written[3] == v, 'DTX%d is not the variant asked for' % v",
            "    assert written[14] == 2, 'DTX%d is not the width asked for' % v",
            "blobs = [rig.write_table(csv, 1, 2) for _ in range(2)]",
            "image, at = rig.package_many(blobs)",
            "assert len(image) > 0, 'the packager wrote no image'",
            "assert all(one is not None for one in at), 'no place for a table'",
            "print('the rig reaches the tools')");

    private static boolean onThePath(String... argv) {
        try {
            return new ProcessBuilder(argv).redirectErrorStream(true)
                    .start().waitFor() == 0;
        } catch (IOException | InterruptedException no) {
            return false;
        }
    }

    /** The rig's own text, for the argument shapes it builds. */
    private static String rig() throws IOException {
        return Files.readString(Rig.root().resolve(RIG));
    }

    @Test
    void theRigNamesTheToolsThisRepositoryBuilds() throws IOException {
        String said = rig();
        for (String named : List.of("org.dtx.Write", "org.dtx.Packager")) {
            assertTrue(said.contains(named), RIG + " does not run " + named);
        }
    }

    /** The flags in the rig that belong to another program: the JVM's
     *  classpath, and the assembler's own, which rmac reads. */
    private static final List<String> ANOTHER_PROGRAMS =
            List.of("-cp", "-fr", "-i", "-o");

    /**
     * Every flag the rig passes to a tool is one that tool reads.
     *
     * <p>A flag the rig builds with {@code "-v%d"} reaches the tool as
     * {@code -v}, so the stems are compared.
     */
    @Test
    void everyFlagTheRigPassesIsOneTheToolReads() throws IOException {
        String tools = Files.readString(Rig.root()
                .resolve("src/main/java/org/dtx/Write.java"))
                + Files.readString(Rig.root()
                .resolve("src/main/java/org/dtx/Packager.java"));
        Matcher flag = Pattern.compile("\"(-[a-z0-9*]+)(?:%d)?\"").matcher(rig());
        List<String> unread = new ArrayList<>();
        while (flag.find()) {
            String stem = flag.group(1);
            if (ANOTHER_PROGRAMS.contains(stem) || stem.startsWith("-l")
                    || stem.startsWith("-m6")) {
                continue;
            }
            if (!tools.contains('"' + stem + '"') && unread.indexOf(stem) < 0) {
                unread.add(stem);
            }
        }
        assertTrue(unread.isEmpty(), () -> RIG + " passes " + unread
                + ", which neither tool reads");
    }

    /**
     * The rig's calls, made by the rig.
     *
     * <p>Skipped where python3 or Unicorn is absent, the two the rig
     * imports; the build is not made to install them.
     */
    @Test
    void theRigsOwnCallsReachTheTools() throws Exception {
        Assumptions.assumeTrue(onThePath("python3", "--version"), "no python3");
        Assumptions.assumeTrue(onThePath("python3", "-c", "import unicorn"),
                "no unicorn for python3");
        Process ran = new ProcessBuilder("python3", "-c", DRIVE)
                .directory(Rig.root().toFile())
                .redirectErrorStream(true)
                .start();
        String said = new String(ran.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertTrue(ran.waitFor() == 0 && said.contains("the rig reaches the tools"),
                () -> RIG + " does not reach the tools it runs:\n" + said);
    }
}
