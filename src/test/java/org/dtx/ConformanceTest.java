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
 * The conformance kit under doc/conformance, held to the writer.
 *
 * <p>Every table the kit holds is written here, from the text and options
 * SOURCES.md states, and held byte for byte to the file in the tree; beside
 * each table stands the rows it holds, as DTX0 lays them out, and a reader of
 * it gives those back. A table the tree does not hold yet is written,
 * and SOURCES.generated.md beside the kit lists what SOURCES.md then has to
 * say.
 */
class ConformanceTest {

    /** One table of the kit: its name, its text, and how it is written. */
    private record Source(String name, String text, int variant, int[] width,
            @Nullable Integer repeat, int unit, int ring, boolean copies,
            String exercises) {}

    /** The rig's numbers table: row r, column i holds r(i+1) modulo 251. */
    private static String numbers(int rows, int columns) {
        StringBuilder out = new StringBuilder();
        for (int r = 0; r < rows; r++) {
            for (int i = 0; i < columns; i++) {
                out.append(i == 0 ? "" : ",").append(r * (i + 1) % 251);
            }
            out.append('\n');
        }
        return out.toString();
    }

    /** A table repeating a pattern 37 rows long, 512 rows. */
    private static String repeating() {
        StringBuilder out = new StringBuilder();
        for (int r = 0; r < 512; r++) {
            out.append(r % 37).append(',').append(r % 37 * 7).append('\n');
        }
        return out.toString();
    }

    private static final List<Source> KIT = List.of(
            new Source("dtx0-three-widths", numbers(8, 3), 0, new int[] {1, 2, 4}, null, 1, 960, false,
                    "DTX0: a row of 1, 2 and 4 byte columns, the wide ones on odd offsets"),
            new Source("dtx0-one-column", numbers(5, 1), 0, new int[] {1}, null, 1, 960, false,
                    "DTX0: one column, one byte, five rows"),
            new Source("dtx0-repeat", numbers(8, 2), 0, new int[] {1, 1}, 3, 1, 960, false,
                    "DTX0: a table that repeats at row 3"),
            new Source("dtx1-three-widths", numbers(8, 3), 1, new int[] {1, 2, 4}, null, 1, 960, false,
                    "DTX1: a byte column before a word one, so the word column begins on a pad byte"),
            new Source("dtx1-widest-first", numbers(6, 3), 1, new int[] {4, 2, 1}, null, 1, 960, false,
                    "DTX1: the widest column first, so no pad byte at all"),
            new Source("dtx1-one-row", numbers(1, 2), 1, new int[] {1, 4}, null, 1, 960, false,
                    "DTX1: one row, R of 1"),
            new Source("dtx1-repeat-at-0", numbers(4, 2), 1, new int[] {2, 2}, 0, 1, 960, false,
                    "DTX1: RR of 0, the table repeats from its first row"),
            new Source("dtx2-k1", numbers(64, 3), 2, new int[] {1, 2, 4}, null, 1, 960, false,
                    "DTX2 at k of 1: three widths, N of 960, P of 3"),
            new Source("dtx2-k2", numbers(64, 2), 2, new int[] {2, 2}, null, 2, 960, false,
                    "DTX2 at k of 2: every column a whole number of units"),
            new Source("dtx2-k4", numbers(64, 2), 2, new int[] {4, 4}, null, 4, 960, false,
                    "DTX2 at k of 4"),
            new Source("dtx2-repeat", numbers(64, 2), 2, new int[] {1, 1}, 16, 1, 960, false,
                    "DTX2: a table that repeats at row 16, a jump backward on a packed reader"),
            new Source("dtx2-rows-not-a-multiple-of-p", numbers(50, 3), 2, new int[] {1, 1, 1}, null, 1, 960, false,
                    "DTX2: R of 50 at P of 3, so the last refill of a column is short"),
            new Source("dtx2-copies", repeating(), 2, new int[] {1, 2}, null, 1, 64, true,
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

    /** The rows a table holds, as DTX0 lays them out: what a reader gives. */
    private static byte[] rows(Source source) {
        Table table = source.repeat() == null
                ? Csv.table(source.text(), source.width())
                : Csv.table(source.text(), source.width(), source.repeat());
        byte[] plain = Dtx0.write(table);
        int header = Dtx.headerLength(table.columns());
        byte[] out = new byte[plain.length - header];
        System.arraycopy(plain, header, out, 0, out.length);
        return out;
    }

    private static String sha256(byte[] held) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(held));
        } catch (NoSuchAlgorithmException gone) {
            throw new IllegalStateException(gone);
        }
    }

    /** One row of SOURCES.md, as the test writes it. */
    private static String row(Source source, byte[] table) {
        StringBuilder widths = new StringBuilder();
        for (int i = 0; i < source.width().length; i++) {
            widths.append(i == 0 ? "" : ",").append(source.width()[i]);
        }
        String options = "-v" + source.variant() + " -w" + widths
                + (source.repeat() == null ? "" : " -r" + source.repeat())
                + (source.variant() == Dtx.DTX2 ? " -k" + source.unit() + " -m" + source.ring() : "")
                + (source.copies() ? " -copies" : "");
        String text = source.text().equals(repeating()) ? "repeating"
                : "numbers " + source.text().split("\n").length + " "
                        + source.width().length;
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
            byte[] held = rows(source);
            Path at = tables.resolve(source.name() + ".dtx");
            Path rowsAt = tables.resolve(source.name() + ".rows");
            if (!Files.exists(at)) {
                Files.write(at, table);
                Files.write(rowsAt, held);
                wrong.add(source.name() + ": written, was not in the tree");
            } else {
                assertArrayEquals(table, Files.readAllBytes(at),
                        source.name() + ".dtx is not the table the writer writes");
                assertArrayEquals(held, Files.readAllBytes(rowsAt),
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
}
