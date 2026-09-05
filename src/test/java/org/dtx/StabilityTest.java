package org.dtx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The code a variant assembles to, against the table that follows it.
 *
 * <p>R, C and RR are the table's, not the reader's: an image contains the same
 * instructions at any of them, and its format block and its table are what
 * differ. This assembles a corpus a variant at a time and compares every
 * image's code to the first one's, byte for byte.
 *
 * <p>Under DTX2 the decoder is built for one unit and for copies or not, so
 * k and copies may move the code and R, C and RR may not: those are grouped.
 *
 * <p>Skipped where no rmac stands on the path or at {@code $RMAC}.
 */
class StabilityTest {

    /** One table of the corpus: what it is called, and its figures. */
    private record Shape(String name, int rows, int columns, int width,
            @Nullable Integer repeat) {}

    /** The corpus at one width: R, C and RR move, and the width does not. */
    private static List<Shape> corpus(int width) {
        return List.of(
                new Shape("R=64  C=2 no repeat", 64, 2, width, null),
                new Shape("R=128 C=2 no repeat", 128, 2, width, null),
                new Shape("R=512 C=2 no repeat", 512, 2, width, null),
                new Shape("R=64  C=2 RR=0", 64, 2, width, 0),
                new Shape("R=64  C=2 RR=32", 64, 2, width, 32),
                new Shape("R=64  C=1", 64, 1, width, null),
                new Shape("R=64  C=3", 64, 3, width, null),
                new Shape("R=64  C=8", 64, 8, width, null),
                new Shape("R=64  C=20", 64, 20, width, null));
    }

    /** A packed corpus: C stays small beside R, since P is at least C. */
    private static List<Shape> packedCorpus(int width) {
        return List.of(
                new Shape("R=64  C=2", 64, 2, width, null),
                new Shape("R=128 C=2", 128, 2, width, null),
                new Shape("R=64  C=2 RR=0", 64, 2, width, 0),
                new Shape("R=64  C=3", 64, 3, width, null));
    }

    private static void needsRmac() {
        Assumptions.assumeTrue(Rig.onThePath(Rig.rmac()),
                "no rmac at " + Rig.rmac());
    }

    /**
     * The instructions alone: rmac's own assembly of the template for this
     * table, with the four slots, the format block and what stands behind the
     * code taken off.
     *
     * <p>The packager's other path combines code the build already made, and
     * does not define what a template assembles to, so this one assembles.
     */
    private static byte[] code(int variant, Shape shape, int unit, int ring,
            boolean copies) {
        Path work = Rig.work("dtxstable");
        String csv = csv(shape.rows(), shape.columns());
        byte[] blob = Rig.write(work, csv, variant, shape.width(),
                shape.repeat(), unit, ring, copies);
        byte[] image = Packager.combine(
                Packager.code(blob, Path.of(Rig.rmac())), blob);
        // The format block defines where the column table begins, at +20 of
        // it, and the code ends there.
        int columns = Dtx.getLong(image, Packager.FORMAT_AT + Packager.COLUMNS_AT);
        int bodies = Packager.FORMAT_AT + Packager.FORMAT;
        byte[] out = new byte[columns - bodies];
        System.arraycopy(image, bodies, out, 0, out.length);
        return out;
    }

    /** Comma separated text of these rows and columns. */
    private static String csv(int rows, int columns) {
        StringBuilder out = new StringBuilder();
        for (int r = 0; r < rows; r++) {
            for (int i = 0; i < columns; i++) {
                out.append(i == 0 ? "" : ",").append(r * (i + 1) % 97);
            }
            out.append('\n');
        }
        return out.toString();
    }

    @Test
    void oneVariantAssemblesToOneCodeAtAnyRowsColumnsOrRepeat() {
        needsRmac();
        for (int variant : new int[] {Dtx.DTX0, Dtx.DTX1, Dtx.DTX2}) {
            List<Shape> shapes = variant == Dtx.DTX2
                    ? packedCorpus(1) : corpus(1);
            byte[] first = code(variant, shapes.get(0), 1, 960, false);
            String name = shapes.get(0).name();
            for (Shape shape : shapes.subList(1, shapes.size())) {
                assertArrayEquals(first, code(variant, shape, 1, 960, false),
                        "DTX" + variant + ": " + shape.name()
                        + " assembles to code \"" + name + "\" does not");
            }
        }
    }

    @Test
    void dtx0ReadsEveryWidthAndDtx1OneCodeAWidth() {
        needsRmac();
        // A DTX0 read is one run of bytes, so its code does not move with
        // the width. A DTX1 read moves a value, so its code does.
        byte[] plain = code(Dtx.DTX0, corpus(1).get(0), 1, 960, false);
        Set<String> apart = new HashSet<>();
        for (int width : new int[] {1, 2, 4}) {
            assertArrayEquals(plain,
                    code(Dtx.DTX0, corpus(width).get(0), 1, 960, false),
                    "DTX0 at a width of " + width);
            byte[] column = code(Dtx.DTX1, corpus(width).get(0), 1, 960, false);
            for (Shape shape : corpus(width).subList(1, 4)) {
                assertArrayEquals(column,
                        code(Dtx.DTX1, shape, 1, 960, false),
                        "DTX1 at a width of " + width + ": " + shape.name());
            }
            apart.add(new String(column,
                    java.nio.charset.StandardCharsets.ISO_8859_1));
        }
        assertEquals(3, apart.size(),
                "three widths, and no two of them one DTX1 code");
    }

    @Test
    void aPackedImageHasOneCodeABuildAndNoTwoBuildsAreOneCode() {
        needsRmac();
        // The width, k and copies build the code, not the table, so a DTX2
        // image may have different code for each of them and must not for
        // R, C or RR.
        Set<String> apart = new HashSet<>();
        List<Integer> sizes = new ArrayList<>();
        for (int width : new int[] {1, 2, 4}) {
            for (int unit : new int[] {1, 2, 4}) {
                for (boolean copies : new boolean[] {false, true}) {
                    List<Shape> shapes = packedCorpus(width);
                    byte[] first = code(Dtx.DTX2, shapes.get(0), unit, 960,
                            copies);
                    for (Shape shape : shapes.subList(1, shapes.size())) {
                        assertArrayEquals(first,
                                code(Dtx.DTX2, shape, unit, 960, copies),
                                "w=" + width + " k=" + unit
                                + (copies ? " with copies" : " without copies")
                                + ": " + shape.name() + " moved the code");
                    }
                    apart.add(new String(first,
                            java.nio.charset.StandardCharsets.ISO_8859_1));
                    sizes.add(first.length);
                }
            }
        }
        assertEquals(18, apart.size(),
                "eighteen builds, and no two of them one code: " + sizes);
    }

    @Test
    void theDecoderMeasuresWhatExperimentsDefines() throws Exception {
        needsRmac();
        // doc/experiments.md defines ST4_wrap.S's bytes at each unit, with the
        // copy code and without: assembled alone, here, and compared with it.
        String doc = java.nio.file.Files.readString(
                Rig.root().resolve("doc/experiments.md"));
        java.util.regex.Matcher row = java.util.regex.Pattern.compile(
                "^\\| ([124]) \\| (\\d+) \\| (\\d+) \\| (\\d+) \\|$",
                java.util.regex.Pattern.MULTILINE).matcher(doc);
        int units = 0;
        while (row.find()) {
            int unit = Integer.parseInt(row.group(1));
            int without = decoder(unit, false);
            int with = decoder(unit, true);
            assertEquals(Integer.parseInt(row.group(2)), without,
                    "ST4_wrap.S at k of " + unit);
            assertEquals(Integer.parseInt(row.group(3)), with,
                    "ST4_wrap.S with the copy code at k of " + unit);
            assertEquals(Integer.parseInt(row.group(4)), with - without,
                    "the copy code at k of " + unit);
            units++;
        }
        assertEquals(3, units, "three units in the decoder table");
    }

    /** ST4_wrap.S assembled alone at this unit, in bytes. */
    private static int decoder(int unit, boolean copies) throws Exception {
        Path work = Rig.work("dtxdecoder");
        Path source = work.resolve("w.S");
        java.nio.file.Files.writeString(source, "        .68000\n        .text\n"
                + "ST4_UNIT equ " + unit + "\n"
                + (copies ? "ST4_WINDOW equ 1\n" : "")
                + "        include \"ST4_wrap.S\"\n        end\n");
        Path out = work.resolve("w.bin");
        Rig.run(List.of(Rig.rmac(), "-m68000", "-fr", "+o3",
                "-i" + Rig.root().resolve("68k"), "-o", out.toString(),
                source.toString()));
        return (int) java.nio.file.Files.size(out);
    }

    @Test
    void theCopyCodeMeasuresWhatTheDocumentDefines() throws Exception {
        needsRmac();
        // doc/abi.md 5 defines the figure the copy code costs a build, and
        // it is not one figure: 32 bytes at k of 1 and 2, 36 at k of 4.
        int[] cost = new int[5];
        for (int unit : new int[] {1, 2, 4}) {
            Shape shape = packedCorpus(unit).get(0);
            cost[unit] = code(Dtx.DTX2, shape, unit, 960, true).length
                    - code(Dtx.DTX2, shape, unit, 960, false).length;
        }
        String said = java.nio.file.Files.readString(
                Rig.root().resolve("doc/abi.md"));
        java.util.regex.Matcher defines = java.util.regex.Pattern.compile(
                "copy code, which measures (\\d+) bytes more at `k` of 1\\s+"
                + "and 2 and (\\d+) at `k` of 4").matcher(said);
        org.junit.jupiter.api.Assertions.assertTrue(defines.find(),
                "doc/abi.md 5 no longer defines the copy code's size");
        assertEquals(cost[1], Integer.parseInt(defines.group(1)),
                "the copy code at k of 1");
        assertEquals(cost[4], Integer.parseInt(defines.group(2)),
                "the copy code at k of 4");
        assertEquals(cost[1], cost[2], "k of 1 and k of 2 pay the same");
    }
}
