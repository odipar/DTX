package org.dtx;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.dtx.style.HouseStyle;
import org.junit.jupiter.api.Test;

/**
 * The documents against themselves: every reference that can be followed,
 * every figure that can be recomputed.
 *
 * <p>The checks came from YMXR, and each of them reads a document of this
 * repository.
 *
 * <p>{@code HouseStyleTest} checks the prose against {@code AGENTS.md} and
 * {@code GlossaryTest} checks the terms against the glossary. This checks the
 * numbers and the pointers, which drift on their own as a document is
 * edited: a requirement renumbered, a section renamed, a column added, a
 * ratio left over from the figures before it.
 */
final class ConsistencyTest {

    private static final Path SPEC = Path.of("doc/SPEC.md");

    private static final Path REQ = Path.of("doc/requirements.md");
    private static final Path GLO = Path.of("doc/glossary.md");
    private static final Path TERM = Path.of("doc/terminology.md");
    private static final Path EXP = Path.of("doc/experiments.md");
    private static final Path TOOLS = Path.of("doc/tools.md");

    private static final Path ABI = Path.of("doc/abi.md");

    /**
     * The documents these checks read: every one the tree writes, found the
     * way the style check finds them, so a document written under doc/ is
     * read here without a list to add it to.
     */
    private static List<Path> documents() throws IOException {
        return HouseStyle.documents(Path.of("."));
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p);
    }

    @Test
    void everyRequirementCitedIsDefined() throws IOException {
        Set<String> defined = new TreeSet<>();
        Matcher d = Pattern.compile("^- \\*\\*(R\\d+\\.\\d+)\\*\\*",
                Pattern.MULTILINE).matcher(read(REQ));
        while (d.find()) {
            defined.add(d.group(1));
        }
        List<String> dangling = new ArrayList<>();
        for (Path p : documents()) {
            Matcher c = Pattern.compile("\\bR\\d+\\.\\d+\\b").matcher(read(p));
            while (c.find()) {
                if (!defined.contains(c.group())) {
                    dangling.add(p + " cites " + c.group());
                }
            }
        }
        assertTrue(dangling.isEmpty(), () -> String.join("\n", dangling)
                + "\nrequirements.md defines " + defined);
    }

    @Test
    void everySectionCitedExists() throws IOException {
        String spec = read(SPEC);
        Set<String> headings = new TreeSet<>();
        Matcher h = Pattern.compile("^#{2,3} (\\d+(?:\\.\\d+)?)\\.? ",
                Pattern.MULTILINE).matcher(spec);
        while (h.find()) {
            headings.add(h.group(1));
        }
        List<String> missing = new ArrayList<>();
        Matcher r = Pattern.compile("[Ss]ection (\\d+(?:\\.\\d+)?)|\\((\\d\\.\\d+)"
                + "(?:, (\\d\\.\\d+))?(?:, (\\d\\.\\d+))?\\)").matcher(spec);
        while (r.find()) {
            for (int g = 1; g <= r.groupCount(); g++) {
                String ref = r.group(g);
                if (ref != null && !headings.contains(ref)) {
                    missing.add("SPEC.md points at " + ref);
                }
            }
        }
        assertTrue(missing.isEmpty(), () -> String.join("\n", missing)
                + "\nits headings are " + headings);
    }

    /** Every row of the glossary's table: its term, what it defines, and
     * where it points. */
    private static List<String[]> glossaryRows(String glo) {
        List<String[]> out = new ArrayList<>();
        for (String line : glo.split("\n")) {
            if (!line.startsWith("| ") || line.startsWith("| term")) {
                continue;
            }
            String[] cells = line.split("\\|");
            if (cells.length >= 4) {
                out.add(new String[] {cells[1].trim(), cells[2].trim(),
                                      cells[3].trim()});
            }
        }
        return out;
    }

    /** The number words the pictures' captions give a small count in. */
    private static final List<String> WORD = List.of("zero", "one", "two",
            "three", "four", "five", "six", "seven", "eight", "nine", "ten");

    /**
     * SPEC.md's pictures against the example table the same section defines.
     * Every count a caption gives is recomputed from `R`, `C` and the width,
     * so a caption reworded away from what its picture draws fails here
     * rather than standing.
     */
    @Test
    void everyPictureAddsUpToTheExampleItDraws() throws IOException {
        String spec = read(SPEC);
        Matcher example = Pattern.compile("A table of `R` = (\\d+) rows and "
                + "`C` = (\\d+) columns of ([a-z]+) byte values")
                .matcher(spec);
        assertTrue(example.find(), "SPEC.md does not define an example table");
        int rows = Integer.parseInt(example.group(1));
        int columns = Integer.parseInt(example.group(2));
        int width = WORD.indexOf(example.group(3));
        assertTrue(width == 1 || width == 2 || width == 4,
                "the example's width is " + example.group(3));
        int row = columns * width;
        List<String> wrong = new ArrayList<>();

        // the header picture: 16 bytes, the width at 14 and a pad byte
        Matcher offsets = Pattern.compile(
                "^ +0 +3 +4 +8 +10 +14 +(\\d+) +(\\d+)$", Pattern.MULTILINE)
                .matcher(spec);
        if (!offsets.find()) {
            wrong.add("the header picture does not draw offsets");
        } else {
            draws(wrong, offsets.group(1), 15, "the pad byte stands at");
            draws(wrong, offsets.group(2), Dtx.HEADER, "the header runs to");
        }
        defines(wrong, spec, "| " + width + " | 0 |",
                "the header picture's width and pad");

        // 2.1: a row is C times the width, the payload R of them
        defines(wrong, spec, "a row of the example: " + columns
                + " columns of " + width + " bytes, " + WORD.get(row)
                + " bytes", "2.1's row");
        for (int n = 0; n < rows; n++) {
            defines(wrong, spec, "row " + n + ", bytes " + n * row + " to "
                    + (n * row + row - 1), "2.1's row " + n);
        }
        defines(wrong, spec, rows * row + " bytes, the table's values",
                "2.1's payload");

        // 2.2: the columns lie at one stride, and at this width no pad
        int stride = Dtx1.stride(rows, width);
        defines(wrong, spec, "the stride, " + stride, "2.2's stride");
        defines(wrong, spec, Dtx1.payloadLength(rows, columns, width)
                + " bytes, the table's values and no pad at a width of "
                + width, "2.2's payload");
        assertTrue(wrong.isEmpty(), () -> String.join("\n", wrong)
                + "\nthe example is `R` = " + rows + ", `C` = " + columns
                + ", width " + width);
    }

    /** Adds to {@code wrong} where SPEC.md does not define {@code figure}. */
    private static void defines(List<String> wrong, String spec, String figure,
            String what) {
        if (!spec.contains(figure)) {
            wrong.add(what + " should read \"" + figure + '"');
        }
    }

    /** Adds to {@code wrong} where a picture's number is not {@code figure}. */
    private static void draws(List<String> wrong, String drawn, int figure,
            String what) {
        if (Integer.parseInt(drawn) != figure) {
            wrong.add(what + " " + figure + ", and the picture draws " + drawn);
        }
    }

    /**
     * The ST4 figures, in each of the four sentences that define one. SPEC.md
     * 2.3 gives the size of an ST4 header and the version byte of a data
     * set's first long, its stored against packed bullet gives the size
     * again, and the glossary's ST4 header row gives both. Both figures move
     * when ST4 moves, so a move that reaches one sentence and leaves another
     * fails here.
     */
    @Test
    void everyDocumentDefinesTheSameSt4Figures() throws IOException {
        String spec = read(SPEC);
        Matcher first = Pattern.compile("first long is `\\$53 \\$34 "
                + "\\$([0-9A-F]{2}) k`: `'S'`, `'4'`, the ST4 format"
                + " version\\s+(\\d+), and the unit").matcher(spec);
        assertTrue(first.find(),
                "SPEC.md 2.3 does not define an ST4 signature");
        Matcher opens = Pattern.compile("ST4 header is ([a-z-]+) bytes")
                .matcher(spec);
        assertTrue(opens.find(),
                "SPEC.md 2.3 does not define an ST4 header size");
        String bytes = opens.group(1);
        String signature = "`$53 $34 $" + first.group(1) + " k`";
        String term = "";
        for (String[] row : glossaryRows(read(GLO))) {
            if (row[0].equals("ST4 header")) {
                term = row[1];
            }
        }
        assertTrue(!term.isEmpty(),
                "the glossary does not contain an ST4 header row");

        List<String> wrong = new ArrayList<>();
        int third = Integer.parseInt(first.group(1), 16);
        if (third != Integer.parseInt(first.group(2))) {
            wrong.add("the signature's third byte gives version " + third
                    + ", and the sentence beside it reads " + first.group(2));
        }
        defines(wrong, spec, "shorter than " + bytes + " is smaller stored",
                "2.3's run that is smaller stored than packed");
        defines(wrong, term, bytes + " bytes",
                "the glossary's ST4 header size");
        defines(wrong, term, signature, "the glossary's ST4 signature");
        assertTrue(wrong.isEmpty(), () -> String.join("\n", wrong)
                + "\nSPEC.md 2.3 defines " + bytes + " bytes and " + signature);
    }

    @Test
    void theGlossaryIsInOrder() throws IOException {
        List<String[]> rows = glossaryRows(read(GLO));
        assertTrue(rows.size() > 5, () -> "the glossary read as " + rows.size()
                + " rows");
        List<String> wrong = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            String before = rows.get(i - 1)[0].replace("`", "").toLowerCase();
            String after = rows.get(i)[0].replace("`", "").toLowerCase();
            if (before.compareTo(after) > 0) {
                wrong.add('"' + before + "\" stands before \"" + after + '"');
            }
        }
        assertTrue(wrong.isEmpty(), () -> String.join("\n", wrong));
    }

    @Test
    void everyGlossaryRowPointsSomewhereReal() throws IOException {
        Set<String> sections = new TreeSet<>();
        Matcher h = Pattern.compile("^## (.+)$", Pattern.MULTILINE)
                .matcher(read(TERM));
        while (h.find()) {
            sections.add(h.group(1).trim().toLowerCase());
        }
        List<String> bad = new ArrayList<>();
        for (String[] row : glossaryRows(read(GLO))) {
            String where = row[2];
            if (where.startsWith("terminology.md,")) {
                String named = where.substring("terminology.md,".length())
                        .trim().toLowerCase();
                if (!sections.contains(named)) {
                    bad.add(row[0] + " points at terminology.md's \"" + named
                            + '"');
                }
            } else {
                String file = where.split("[ ,;]")[0];
                if (file.endsWith(".md")
                        && !Files.exists(Path.of("doc", file))
                        && !Files.exists(Path.of(file))) {
                    bad.add(row[0] + " points at " + file);
                }
            }
        }
        assertTrue(bad.isEmpty(), () -> String.join("\n", bad)
                + "\nterminology.md holds " + sections);
    }

    @Test
    void everyLinkResolves() throws IOException {
        List<String> broken = new ArrayList<>();
        for (Path p : documents()) {
            Matcher m = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)")
                    .matcher(read(p));
            while (m.find()) {
                String target = m.group(2);
                if (target.startsWith("http")) {
                    continue;
                }
                Path base = p.getParent() == null ? Path.of(".") : p.getParent();
                Path at = base.resolve(target.split("#")[0]).normalize();
                if (!Files.exists(at)) {
                    broken.add(p + ": [" + m.group(1) + "](" + target + ')');
                }
            }
        }
        assertTrue(broken.isEmpty(), () -> String.join("\n", broken));
    }

    /**
     * The scripts doc/tools.md gives, against the tree. A reader copies a
     * usage line, so a script renamed away from the document, or one
     * that is not executable, fails here rather than at the reader's shell.
     */
    @Test
    void everyScriptToolsMdGivesIsThereAndExecutable() throws IOException {
        Matcher m = Pattern.compile("^(bin/[A-Za-z0-9._-]+)", Pattern.MULTILINE)
                .matcher(read(TOOLS));
        List<String> named = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        while (m.find()) {
            Path script = Path.of(m.group(1));
            named.add(m.group(1));
            if (!Files.isRegularFile(script)) {
                wrong.add("tools.md gives " + script + ", which is not there");
            } else if (!Files.isExecutable(script)) {
                wrong.add(script + " is not executable");
            }
        }
        assertTrue(!named.isEmpty(), "tools.md does not give a script");
        assertTrue(wrong.isEmpty(), () -> String.join("\n", wrong)
                + "\ntools.md gives " + named);
    }

    @Test
    void everyDocumentKeepsOneWrapWidth() throws IOException {
        List<String> wide = new ArrayList<>();
        for (Path p : documents()) {
            List<String> lines = Files.readAllLines(p);
            for (int at = 0; at < lines.size(); at++) {
                String line = lines.get(at);
                if (line.startsWith("|") || line.startsWith("    ")
                        || line.contains("](")) {
                    continue;
                }
                if (line.length() > 78) {
                    wide.add(p + ":" + (at + 1) + " runs to " + line.length());
                }
            }
        }
        assertTrue(wide.isEmpty(), () -> String.join("\n", wide)
                + "\nAGENTS.md gives one wrap width, and a document keeps it.");
    }
}
