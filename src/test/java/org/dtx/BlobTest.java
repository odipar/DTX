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
 * The code in this repository, against the templates it was assembled
 * from.
 *
 * <p>The packager combines rather than assembles, so a template edited
 * without {@code bin/dtx-blobs} run after it would ship the code as it stood
 * before the edit. These checks assemble every build again and compare the
 * carried file with it, and the combined image with the assembled one.
 * They are skipped where no rmac is on the path.
 */
class BlobTest {

    private static Path rmac() {
        String at = Rig.rmac();
        Assumptions.assumeTrue(Rig.onThePath(at), "no rmac at " + at);
        return Path.of(at);
    }

    @Test
    void everyBuildIsCarriedAndNoTwoAreTheSameBytes() {
        Set<String> name = new HashSet<>();
        Set<String> bytes = new HashSet<>();
        for (Blobs.Build build : Blobs.all()) {
            byte[] code = Packager.carriedCode(build.variant(), build.width(),
                    build.unit(), build.copies());
            assertTrue(name.add(build.name()), "two builds name " + build.name());
            assertTrue(bytes.add(new String(code, java.nio.charset.StandardCharsets.ISO_8859_1)),
                    build.name() + " is the same bytes as another build:"
                            + " one of them is not a build of its own");
            assertEquals(build.variant(), code[Packager.FORMAT_AT + 3],
                    build.name() + "'s variant");
            assertEquals(build.unit(),
                    code[Packager.FORMAT_AT + Packager.UNIT_AT] & 0xFF,
                    build.name() + "'s unit");
            // DTX0 reads a row as one run of bytes, so its code does not
            // move with the width and its format block gives a width of 0.
            assertEquals(build.variant() == Dtx.DTX0 ? 0 : build.width(),
                    code[Packager.FORMAT_AT + Packager.WIDTH_AT] & 0xFF,
                    build.name() + "'s width");
        }
        assertEquals(22, name.size(), "DTX0, three DTX1 and eighteen DTX2");
    }

    @Test
    void theReleaseFilesAndTheClasspathContainTheSameBytes() throws Exception {
        // The build writes each file twice: into the classes a jar is made
        // of, and into build/68k, which a release attaches and a port in
        // another language builds from. A release that shipped other bytes
        // than the jar would be two readers of one table.
        Path loose = Path.of("build", "68k");
        Assumptions.assumeTrue(Files.isDirectory(loose),
                "no build/68k: run mvn process-classes");
        for (Blobs.Build build : Blobs.all()) {
            assertArrayEquals(Files.readAllBytes(loose.resolve(build.name())),
                    Packager.carriedCode(build.variant(), build.width(),
                            build.unit(), build.copies()),
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
        // And on tables the builds were not seeded from: R, C and RR move
        // the table and not the code, so the two paths meet on any of them.
        // The width moves the code as well, so the loop runs each of the
        // three against the code built for it.
        for (int width : new int[] {1, 2, 4}) {
            for (int columns : new int[] {1, 3, 4}) {
                for (int rows : new int[] {1, 100}) {
                    for (int variant : new int[] {Dtx.DTX0, Dtx.DTX1}) {
                        byte[] file = table(variant, rows, columns, width);
                        assertArrayEquals(Packager.image(file, rmac),
                                Packager.image(file),
                                "DTX" + variant + " at R of " + rows + ", C of "
                                        + columns + " and W of " + width);
                    }
                }
            }
        }
    }

    /**
     * A table of {@code rows} rows and {@code columns} columns at this
     * width, its values zero.
     */
    private static byte[] table(int variant, int rows, int columns, int width) {
        byte[][] column = new byte[columns][];
        for (int i = 0; i < columns; i++) {
            column[i] = new byte[rows * width];
        }
        Table built = Table.of(rows, rows, width, column);
        return variant == Dtx.DTX0 ? Dtx0.write(built) : Dtx1.write(built);
    }

    @Test
    void carriedCodeDoesNotDefineATable() {
        // A build does not define a table: the six fields a combine writes
        // read zero, so code shipped without one does not define a table, not
        // even the one it was assembled from.
        for (Blobs.Build build : Blobs.all()) {
            byte[] code = Packager.carriedCode(build.variant(), build.width(),
                    build.unit(), build.copies());
            int at = Packager.FORMAT_AT;
            assertEquals(0, Dtx.getLong(code, at + Packager.STATE_BYTES),
                    build.name() + " defines a state block");
            assertEquals(0, Dtx.getLong(code, at + Packager.TABLE_AT),
                    build.name() + " defines a table");
            assertEquals(0, Dtx.getWord(code, at + Packager.ROWBYTES_AT),
                    build.name() + " defines a row's bytes");
            assertEquals(0, Dtx.getWord(code, at + Packager.PERIOD_AT),
                    build.name() + " defines a period");
            assertEquals(0, Dtx.getWord(code, at + Packager.RING_AT),
                    build.name() + " defines a ring");
            assertEquals(0, Dtx.getLong(code, at + Packager.STRIDE_AT),
                    build.name() + " defines a stride");
            assertEquals(code.length,
                    Dtx.getLong(code, at + Packager.COLUMNS_AT),
                    build.name() + " puts the column table off its own end");
        }
    }
}
