package org.dtx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The conformance kit under doc/conformance, checked against the writer.
 *
 * <p>Every table in the kit is written here, from the text and options
 * SOURCES.md defines, and compared byte for byte with the file in the tree;
 * beside each table stands the rows in it, as DTX0 lays them out, and a
 * reader of it gives those back. A table the tree does not have yet is
 * written, and SOURCES.generated.md beside the kit lists what SOURCES.md
 * then has to say.
 */
class ConformanceTest {

    /** One table of the kit: its name, its text, and how it is written. */
    private record Source(String name, String text, int variant, int width,
            @Nullable Integer repeat, int unit, int ring, boolean copies,
            String exercises) {}

    private static final List<Source> KIT = List.of(
            new Source("dtx0-w1", Rig.numbers(8, 3, 251), 0, 1, null, 1, 960, false,
                    "DTX0 at a width of 1: an odd row, so a row begins on an odd offset"),
            new Source("dtx0-w2", Rig.numbers(8, 3, 251), 0, 2, null, 1, 960, false,
                    "DTX0 at a width of 2"),
            new Source("dtx0-w4", Rig.numbers(6, 3, 251), 0, 4, null, 1, 960, false,
                    "DTX0 at a width of 4"),
            new Source("dtx0-one-column", Rig.numbers(5, 1, 251), 0, 1, null, 1, 960, false,
                    "DTX0: one column, one byte, five rows"),
            new Source("dtx0-repeat", Rig.numbers(8, 2, 251), 0, 1, 3, 1, 960, false,
                    "DTX0: a table that repeats at row 3"),
            new Source("dtx1-w1-odd-rows", Rig.numbers(7, 3, 251), 1, 1, null, 1, 960, false,
                    "DTX1 at a width of 1 and an odd R, so a pad byte stands between columns"),
            new Source("dtx1-w2", Rig.numbers(8, 3, 251), 1, 2, null, 1, 960, false,
                    "DTX1 at a width of 2, where a column is a whole number of words"),
            new Source("dtx1-w4", Rig.numbers(6, 3, 251), 1, 4, null, 1, 960, false,
                    "DTX1 at a width of 4"),
            new Source("dtx1-one-row", Rig.numbers(1, 2, 251), 1, 4, null, 1, 960, false,
                    "DTX1: one row, R of 1"),
            new Source("dtx1-repeat-at-0", Rig.numbers(4, 2, 251), 1, 2, 0, 1, 960, false,
                    "DTX1: RR of 0, the table repeats from its first row"),
            new Source("dtx2-w1-k1", Rig.numbers(64, 3, 251), 2, 1, null, 1, 960, false,
                    "DTX2 at a width of 1 and k of 1: N of 960, P of 3"),
            new Source("dtx2-w2-k2", Rig.numbers(64, 2, 251), 2, 2, null, 2, 960, false,
                    "DTX2 at a width of 2 and k of 2, a unit a value"),
            new Source("dtx2-w4-k4", Rig.numbers(64, 2, 251), 2, 4, null, 4, 960, false,
                    "DTX2 at a width of 4 and k of 4"),
            new Source("dtx2-w4-k1", Rig.numbers(64, 2, 251), 2, 4, null, 1, 960, false,
                    "DTX2 at a width of 4 and k of 1, a unit below the width"),
            new Source("dtx2-w1-k4", Rig.numbers(64, 2, 251), 2, 1, null, 4, 960, false,
                    "DTX2 at a width of 1 and k of 4, a unit above the width"),
            new Source("dtx2-repeat", Rig.numbers(64, 2, 251), 2, 1, 16, 1, 960, false,
                    "DTX2: a table that repeats at row 16, a jump backward on a packed reader"),
            new Source("dtx2-rows-not-a-multiple-of-p", Rig.numbers(50, 3, 251), 2, 1, null, 1, 960, false,
                    "DTX2: R of 50 at P of 3, so the last refill of a column is short"),
            new Source("dtx2-twenty-columns", Rig.numbers(64, 20, 251), 2, 2, null, 1, 960, false,
                    "DTX2: C of 20, so P is 20 and a read walks twenty rings"),
            new Source("dtx2-copies", Rig.repeating(), 2, 2, null, 1, 64, true,
                    "DTX2 with copies from the literal stream, at a ring of 64 the pattern does not fit"));

    private static Path kit() {
        return Rig.root().resolve("doc/conformance");
    }

    /** The table one source writes, through the Java tree. */
    private static byte[] write(Source source) {
        Table table = source.repeat() == null
                ? Csv.table(source.text(), source.width())
                : Csv.table(source.text(), source.width(), source.repeat());
        return switch (source.variant()) {
            case Dtx.DTX0 -> Dtx0.write(table);
            case Dtx.DTX1 -> Dtx1.write(table);
            default -> Dtx2.write(table, source.copies() ? new St4(true, 0) : new St4(),
                    source.unit(), source.ring());
        };
    }

    /** The rows in a table, as DTX0 lays them out: what a reader gives. */
    private static byte[] rows(Source source) {
        return rows(source.repeat() == null
                ? Csv.table(source.text(), source.width())
                : Csv.table(source.text(), source.width(), source.repeat()));
    }

    private static byte[] rows(Table table) {
        byte[] plain = Dtx0.write(table);
        byte[] out = new byte[plain.length - Dtx.HEADER];
        System.arraycopy(plain, Dtx.HEADER, out, 0, out.length);
        return out;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException gone) {
            throw new IllegalStateException(gone);
        }
    }

    /** One row of SOURCES.md, as the test writes it. */
    private static String row(Source source, byte[] table) {
        String options = "-v" + source.variant() + " -w" + source.width()
                + (source.repeat() == null ? "" : " -r" + source.repeat())
                + (source.variant() == Dtx.DTX2 ? " -k" + source.unit() + " -m" + source.ring() : "")
                + (source.copies() ? " -copies" : "");
        String text = source.text().equals(Rig.repeating()) ? "repeating"
                : "numbers " + source.text().split("\n").length + " "
                        + source.text().split("\n")[0].split(",").length;
        return "| `" + source.name() + "` | " + text + " | `" + options + "` | "
                + table.length + " | " + sha256(table).substring(0, 16) + " | "
                + source.exercises() + " |";
    }

    @Test
    void everyTableOfTheKitIsTheOneTheWriterWrites() throws IOException {
        Path tables = kit().resolve("tables");
        Files.createDirectories(tables);
        String sources = Files.exists(kit().resolve("SOURCES.md"))
                ? Files.readString(kit().resolve("SOURCES.md")) : "";
        List<String> listing = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        for (Source source : KIT) {
            byte[] table = write(source);
            byte[] kept = rows(source);
            Path at = tables.resolve(source.name() + ".dtx");
            Path rowsAt = tables.resolve(source.name() + ".rows");
            if (!Files.exists(at)) {
                Files.write(at, table);
                Files.write(rowsAt, kept);
                wrong.add(source.name() + ": written, was not in the tree");
            } else {
                assertArrayEquals(table, Files.readAllBytes(at),
                        source.name() + ".dtx is not the table the writer writes");
                assertArrayEquals(kept, Files.readAllBytes(rowsAt),
                        source.name() + ".rows is not the rows the table holds");
            }
            String row = row(source, table);
            listing.add(row);
            if (!sources.contains(row)) {
                wrong.add(source.name() + ": SOURCES.md does not hold its row");
            }
        }
        if (!wrong.isEmpty()) {
            Files.writeString(kit().resolve("SOURCES.generated.md"),
                    String.join("\n", listing) + "\n", StandardCharsets.UTF_8);
        }
        assertTrue(wrong.isEmpty(), String.join("\n", wrong)
                + "\ndoc/conformance/SOURCES.generated.md holds every row as the"
                + " writer gives it; SOURCES.md takes them.");
    }

    /**
     * The Java reader against the kit: every table in it, read through
     * {@link Dtx#read}, gives the rows beside it. The DTX2 tables go through
     * the copy of ST4 in this repository, so this is the one check here of
     * a reader that unpacks rather than the 68000 one.
     */
    @Test
    void everyTableInTheKitReadsBackToItsRows() throws IOException {
        Path tables = kit().resolve("tables");
        for (Source source : KIT) {
            Table table = Dtx.read(Files.readAllBytes(
                    tables.resolve(source.name() + ".dtx")));
            assertArrayEquals(
                    Files.readAllBytes(tables.resolve(source.name() + ".rows")),
                    rows(table), source.name() + ": the Java reader gives"
                            + " other rows than the kit");
        }
    }

}
