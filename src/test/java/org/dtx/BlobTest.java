package org.dtx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The code this repository carries, against the templates it was assembled
 * from.
 *
 * <p>The packager combines rather than assembles, so a template edited
 * without {@code bin/dtx-blobs} run after it would ship the code as it stood
 * before the edit. These checks assemble every build again and hold the
 * carried file to it, and hold the combined image to the assembled one.
 * They are skipped where no rmac is on the path.
 */
class BlobTest {

    private static Path rmac() {
        String named = System.getenv("RMAC");
        Path at = Path.of(named == null ? "rmac" : named);
        Assumptions.assumeTrue(named != null || onThePath(at), "no rmac at " + at);
        return at;
    }

    private static boolean onThePath(Path rmac) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String at : path.split(":")) {
            if (Files.isExecutable(Path.of(at).resolve(rmac.toString()))) {
                return true;
            }
        }
        return false;
    }

    @Test
    void everyBuildIsCarriedAndNoTwoAreTheSameBytes() {
        Set<String> name = new HashSet<>();
        Set<String> bytes = new HashSet<>();
        for (Blobs.Build build : Blobs.all()) {
            byte[] code = Packager.carriedCode(build.variant(), build.unit(),
                    build.copies());
            assertTrue(name.add(build.name()), "two builds name " + build.name());
            assertTrue(bytes.add(new String(code, java.nio.charset.StandardCharsets.ISO_8859_1)),
                    build.name() + " holds the bytes another build holds:"
                            + " one of them is not a build of its own");
            assertEquals(build.variant(), code[Packager.FORMAT_AT + 3],
                    build.name() + "'s variant");
            assertEquals(build.unit(),
                    code[Packager.FORMAT_AT + Packager.UNIT_AT] & 0xFF,
                    build.name() + "'s unit");
        }
        assertEquals(8, name.size(), "DTX0, DTX1 and six decoders");
    }

    @Test
    void theReleaseFilesAndTheClasspathHoldTheSameBytes() throws Exception {
        // The build writes each file twice: into the classes a jar is made
        // of, and into build/68k, which a release attaches and a port in
        // another language builds from. A release that shipped other bytes
        // than the jar would be two readers of one table.
        Path loose = Path.of("build", "68k");
        Assumptions.assumeTrue(Files.isDirectory(loose),
                "no build/68k: run mvn process-classes");
        for (Blobs.Build build : Blobs.all()) {
            assertArrayEquals(Files.readAllBytes(loose.resolve(build.name())),
                    Packager.carriedCode(build.variant(), build.unit(),
                            build.copies()),
                    build.name() + " is different bytes in build/68k and on"
                            + " the classpath");
        }
    }

    @Test
    void combiningAndAssemblingGiveTheSameImage() {
        Path rmac = rmac();
        for (Blobs.Build build : Blobs.all()) {
            byte[] file = Blobs.seed(build);
            assertArrayEquals(Packager.image(file, rmac), Packager.image(file),
                    build.name() + ": the combined image and the assembled one"
                            + " are different bytes");
        }
        // And on tables the builds were not seeded from: R, C, RR and the
        // widths move the table and not the code, so the two paths meet on
        // any of them.
        for (int[] width : new int[][] {{1}, {1, 1, 1}, {4, 2, 1}, {1, 2, 4, 2}}) {
            for (int rows : new int[] {1, 100}) {
                for (int variant : new int[] {Dtx.DTX0, Dtx.DTX1}) {
                    byte[] file = table(variant, rows, width);
                    assertArrayEquals(Packager.image(file, rmac),
                            Packager.image(file),
                            "DTX" + variant + " at R of " + rows + " and C of "
                                    + width.length);
                }
            }
        }
    }

    /** A table of {@code rows} rows and these widths, holding no values. */
    private static byte[] table(int variant, int rows, int[] width) {
        byte[][] column = new byte[width.length][];
        for (int i = 0; i < width.length; i++) {
            column[i] = new byte[rows * width[i]];
        }
        Table held = Table.of(rows, rows, width, column);
        return variant == Dtx.DTX0 ? Dtx0.write(held) : Dtx1.write(held);
    }

    @Test
    void carriedCodeStatesNoTable() {
        // A build states no table: the five fields a combine writes read
        // zero, so code shipped without one is refused rather than read as
        // whatever table it was assembled from.
        for (Blobs.Build build : Blobs.all()) {
            byte[] code = Packager.carriedCode(build.variant(), build.unit(),
                    build.copies());
            int at = Packager.FORMAT_AT;
            assertEquals(0, Dtx.getLong(code, at + Packager.STATE_BYTES),
                    build.name() + " states a state block");
            assertEquals(0, Dtx.getLong(code, at + Packager.TABLE_AT),
                    build.name() + " states a table");
            assertEquals(0, Dtx.getWord(code, at + Packager.ROWBYTES_AT),
                    build.name() + " states a row's bytes");
            assertEquals(0, Dtx.getWord(code, at + Packager.PERIOD_AT),
                    build.name() + " states a period");
            assertEquals(0, Dtx.getWord(code, at + Packager.RING_AT),
                    build.name() + " states a ring");
            assertEquals(code.length,
                    Dtx.getLong(code, at + Packager.COLUMNS_AT),
                    build.name() + " puts the column table off its own end");
        }
    }
}
