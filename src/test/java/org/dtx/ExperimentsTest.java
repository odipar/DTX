package org.dtx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * doc/experiments.md, read back: every byte figure it defines is one the
 * writer and the packager give, or the test names the cell.
 *
 * <p>The images come out of the code the build made, so this runs wherever
 * the build does and does not need an assembler.
 */
class ExperimentsTest {

    private static String doc() throws IOException {
        return Files.readString(Rig.root().resolve("doc/experiments.md"));
    }

    /** The rows of one table in the document, by the cells' text. */
    private static List<String[]> rows(String doc, String heading) {
        int at = doc.indexOf("## " + heading);
        assertTrue(at >= 0,
                "doc/experiments.md does not have a section " + heading);
        int end = doc.indexOf("\n## ", at + 1);
        String section = doc.substring(at, end < 0 ? doc.length() : end);
        List<String[]> out = new ArrayList<>();
        Matcher row = Pattern.compile("^\\| ([^|\\n]+) \\| (\\d+) \\| (\\d+) \\|",
                Pattern.MULTILINE).matcher(section);
        while (row.find()) {
            out.add(new String[] {row.group(1).trim(), row.group(2), row.group(3)});
        }
        assertTrue(!out.isEmpty(), heading + " does not list a row of figures");
        return out;
    }

    @Test
    void whereDtx2BecomesTheSmallerOfTheTwo() throws IOException {
        for (String[] row : rows(doc(), "Where DTX2 becomes the smaller of the two")) {
            int r = Integer.parseInt(row[0]);
            Table table = Csv.table(Rig.numbers(r, 3, 251), 1);
            int one = Dtx1.write(table).length;
            int two = Dtx2.write(table, new St4(), 1, 960).length;
            assertEquals(Integer.parseInt(row[1]), one, "DTX1 at R of " + r);
            assertEquals(Integer.parseInt(row[2]), two, "DTX2 at R of " + r);
        }
    }

    @Test
    void copiesFromTheLiteralStreamAtASmallRing() throws IOException {
        Table table = Csv.table(Rig.repeating(), 2);
        for (String[] row : rows(doc(), "Copies from the literal stream, at a small ring")) {
            byte[] file = switch (row[0]) {
                case "DTX1" -> Dtx1.write(table);
                case "DTX2, N=64" -> Dtx2.write(table, new St4(), 1, 64);
                case "DTX2, N=64, copies" -> Dtx2.write(table, new St4(true, 0), 1, 64);
                case "DTX2, N=128, copies" -> Dtx2.write(table, new St4(true, 0), 1, 128);
                default -> throw new AssertionError("a row this test does not make: " + row[0]);
            };
            assertEquals(Integer.parseInt(row[1]), file.length, row[0] + ", the file");
            assertEquals(Integer.parseInt(row[2]), Packager.image(file).length,
                    row[0] + ", the image");
        }
    }
}
