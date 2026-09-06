package org.dtx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The one tool that writes: text or a DTX file in, a DTX file of any
 * variant or text out, the table the same at every step.
 */
final class WriteTest {

    @Test
    void aDtx2FileIsRepackedAtTheUnitRingAndCopiesGiven(@TempDir Path work)
            throws IOException {
        Path text = work.resolve("t.csv");
        Files.writeString(text, Rig.numbers(64, 3));
        for (int width : new int[] {1, 2, 4}) {
            Path first = work.resolve("first-w" + width + ".dtx");
            Write.main(new String[] {text.toString(), first.toString(), "-v2",
                    "-w" + width, "-k1", "-m960"});
            Path again = work.resolve("again-w" + width + ".dtx");
            Write.main(new String[] {first.toString(), again.toString(), "-k2",
                    "-m64", "-copies"});
            byte[] file = Files.readAllBytes(again);
            Dtx.Header header = Dtx.header(file);
            assertEquals(Dtx.DTX2, header.variant(),
                    "the variant read is kept");
            assertEquals(width, header.width(), "the width read is kept");
            Packager.Packed packed = Packager.packed(file, header);
            assertEquals(2, packed.unit());
            assertEquals(64, packed.ring());
            assertTrue(packed.copies());
            assertEquals(Dtx.read(Files.readAllBytes(first)), Dtx.read(file),
                    "the same table at another unit and ring, at a width of "
                            + width);
        }
    }

    @Test
    void aDtxFileIsWrittenOutAsTextAndReadsBack(@TempDir Path work)
            throws IOException {
        Path text = work.resolve("t.csv");
        Files.writeString(text, "# a note\n" + Rig.numbers(8, 2));
        for (int width : new int[] {1, 2, 4}) {
            Path packed = work.resolve("p-w" + width + ".dtx");
            Write.main(new String[] {text.toString(), packed.toString(), "-v2",
                    "-w" + width, "-r3", "-k1", "-m960"});
            Path out = work.resolve("out-w" + width + ".csv");
            Write.main(new String[] {packed.toString(), out.toString()});
            String written = Files.readString(out);
            assertTrue(written.startsWith("# 8 rows, 2 columns, width " + width
                    + ", RR 3\nc0,c1\n0,0\n1,2\n"), written);
            Path back = work.resolve("back-w" + width + ".dtx");
            Write.main(new String[] {out.toString(), back.toString(), "-v1"});
            assertEquals(Dtx.read(Files.readAllBytes(packed)),
                    Dtx.read(Files.readAllBytes(back)),
                    "through text and back, with the width and repeat the"
                            + " comment gives");
        }
    }

    @Test
    void aFlagWithNoNumberBehindItGivesTheToolsLine(@TempDir Path work)
            throws IOException {
        // Every number a flag gives is read in one place, so a flag with
        // nothing behind it, or with letters, gives the tool's line and
        // exit 2 rather than a stack trace. The tool is run as a caller runs
        // it, since it exits the JVM it stands in.
        Path text = work.resolve("t.csv");
        Files.writeString(text, Rig.numbers(4, 2));
        Path out = work.resolve("t.dtx");
        for (String[] flags : new String[][] {{"-v"}, {"-r"}, {"-k"}, {"-m"},
                {"-wx"}, {"-v2", "-copiesx"}}) {
            List<String> argv = new ArrayList<>(List.of("java", "-cp",
                    Rig.root().resolve("target/classes").toString(),
                    "org.dtx.Write", text.toString(), out.toString()));
            argv.addAll(List.of(flags));
            String said = assertThrows(IllegalStateException.class,
                    () -> Rig.run(argv)).getMessage();
            String last = flags[flags.length - 1];
            assertTrue(said != null && said.contains(
                            "dtx-write does not read " + last),
                    "dtx-write " + last + " gave " + said);
        }
    }

    @Test
    void theRepeatOfADtxFileIsChangedAndItsVariantKept(@TempDir Path work)
            throws IOException {
        Path text = work.resolve("t.csv");
        Files.writeString(text, Rig.numbers(8, 2));
        Path plain = work.resolve("p.dtx");
        Write.main(new String[] {text.toString(), plain.toString(), "-v1"});
        Path repeating = work.resolve("r.dtx");
        Write.main(new String[] {plain.toString(), repeating.toString(), "-r2"});
        Dtx.Header header = Dtx.header(Files.readAllBytes(repeating));
        assertEquals(Dtx.DTX1, header.variant());
        assertEquals(2, header.repeat());
    }
}
