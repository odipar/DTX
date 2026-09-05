package org.dtx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        Path first = work.resolve("first.dtx");
        Write.main(new String[] {text.toString(), first.toString(), "-v2",
                "-w1,2,4", "-k1", "-m960"});
        Path again = work.resolve("again.dtx");
        Write.main(new String[] {first.toString(), again.toString(), "-k2",
                "-m64", "-copies"});
        byte[] file = Files.readAllBytes(again);
        Dtx.Header header = Dtx.header(file);
        assertEquals(Dtx.DTX2, header.variant(), "the variant read is kept");
        Packager.Packed packed = Packager.packed(file, header);
        assertEquals(2, packed.unit());
        assertEquals(64, packed.ring());
        assertTrue(packed.copies());
        assertEquals(Dtx.read(Files.readAllBytes(first)), Dtx.read(file),
                "the same table at another unit and ring");
    }

    @Test
    void aDtxFileIsWrittenOutAsTextAndReadsBack(@TempDir Path work)
            throws IOException {
        Path text = work.resolve("t.csv");
        Files.writeString(text, "# a note\n" + Rig.numbers(8, 2));
        Path packed = work.resolve("p.dtx");
        Write.main(new String[] {text.toString(), packed.toString(), "-v2",
                "-w1,2", "-r3", "-k1", "-m960"});
        Path out = work.resolve("out.csv");
        Write.main(new String[] {packed.toString(), out.toString()});
        String written = Files.readString(out);
        assertTrue(written.startsWith("# 8 rows, 2 columns, widths 1,2, RR 3\n"
                + "c0,c1\n0,0\n1,2\n"), written);
        Path back = work.resolve("back.dtx");
        Write.main(new String[] {out.toString(), back.toString(), "-v1"});
        assertEquals(Dtx.read(Files.readAllBytes(packed)),
                Dtx.read(Files.readAllBytes(back)),
                "through text and back, with the widths and repeat the"
                        + " comment gives");
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
