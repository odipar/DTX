package org.dtx;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.dtx.style.HouseStyle;
import org.dtx.doc.Documents;
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
 * numbers and the pointers, which drift as a document is edited: a
 * requirement renumbered, a section renamed, a column added, a ratio left
 * over from the figures before it.
 */
final class ConsistencyTest {

    /**
     * Every line tools.md reports reads the same in the three trees: a
     * line reworded in one tree and the document, or in the document
     * alone, fails here. The letters a table writes for a figure, V or N
     * or K, and the figures a tool builds a line from are outside the
     * comparison, and this reads the words around them.
     *
     * <p>The check came from YMXR, where a release moved a descriptor's
     * version to 2 in one clause and left another reading 1. Writing the
     * table for it found the Java tree wording the refill's mark one way
     * and the other two another, and the C# tree writing no line where
     * the other two report a failed write.
     */
    @Test
    void everyLineTheDocumentReportsReadsTheSameInTheTrees() throws IOException {
        String reference = tree(Path.of("src/main/java/org/dtx"), ".java");
        String go = tree(Path.of("go"), ".go");
        String sharp = tree(Path.of("dotnet"), ".cs");
        int read = 0;
        for (String said : reportedLines(Files.readString(Path.of("doc/tools.md")))) {
            String part = longestRun(said);
            if (part.isEmpty() || !reference.contains(part)) {
                continue;
            }
            read++;
            assertTrue(go.contains(part),
                    "tools.md reports \"" + said + "\" and the Go tree lacks \"" + part + "\"");
            assertTrue(sharp.contains(part),
                    "tools.md reports \"" + said + "\" and the C# tree lacks \"" + part + "\"");
        }
        assertTrue(read >= 6, "tools.md reports " + read + " lines of the tools");
    }

    /** The longest run of words of a line between the figures a tool
     *  writes into it, and the empty text where the line is figures and
     *  short runs. */
    private static String longestRun(String said) {
        String longest = "";
        // a letter a table writes for a figure is a lone capital, one
        // with no letter after it and no capital before it
        for (String part : said.split("(?<![A-Z])[A-Z](?![A-Za-z])|\\bi\\b|\\b[0-9]+\\b")) {
            String one = part.strip();
            if (one.length() >= 12 && one.length() > longest.length()) {
                longest = one;
            }
        }
        return longest;
    }

    /** The lines the tables of a document report: the last cell of a row,
     *  each code span in it of three words or more. */
    private static List<String> reportedLines(String document) {
        List<String> out = new ArrayList<>();
        Matcher row = Pattern.compile("^\\|(.*)\\|\\s*$", Pattern.MULTILINE)
                .matcher(document);
        while (row.find()) {
            String[] cells = row.group(1).split("\\|");
            if (cells.length < 2) {
                continue;
            }
            Matcher said = Pattern.compile("`([^`]+)`").matcher(cells[cells.length - 1]);
            while (said.find()) {
                String one = said.group(1);
                if (one.split("\\s+").length >= 3) {
                    out.add(one);
                }
            }
        }
        return out;
    }

    /** Every source of a tree, read as one text, a line built from two
     *  strings read as one. */
    private static String tree(Path at, String ending) throws IOException {
        StringBuilder out = new StringBuilder();
        try (java.util.stream.Stream<Path> found = Files.walk(at)) {
            for (Path one : found
                    .filter(p -> p.toString().endsWith(ending))
                    .filter(p -> !p.toString().contains("/test")
                            && !p.toString().contains("/obj/")
                            && !p.toString().contains("/bin/"))
                    .toList()) {
                out.append(Files.readString(one)).append('\n');
            }
        }
        return out.toString().replaceAll("\"\\s*\\+\\s*\\$?\"", "");
    }

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

    /** The clause numbers a document defines: its numbered headings and
     *  the bold number that opens a clause. */
    private static Set<String> clausesOf(Path at) throws IOException {
        Set<String> out = new TreeSet<>();
        String said = read(at);
        Matcher heading = Pattern.compile("^#{1,4} (\\d+(?:\\.\\d+)*)\\.?\\s",
                Pattern.MULTILINE).matcher(said);
        while (heading.find()) {
            out.add(heading.group(1));
        }
        Matcher bold = Pattern.compile("^\\*\\*(\\d+(?:\\.\\d+)*)\\b",
                Pattern.MULTILINE).matcher(said);
        while (bold.find()) {
            out.add(bold.group(1));
        }
        return out;
    }

    /**
     * Every clause one document cites in another is a clause that document
     * defines. The check above reads SPEC.md against itself; this reads
     * abi.md's twenty-two citations and every other document's, which no
     * check followed.
     *
     * <p>A citation qualified with ST4, YMXS or YMXR names that
     * repository's document, and so does a document this repository does
     * not have; both are left alone. RELEASES.md records what was true at a
     * release, so a clause renumbered after one leaves its entry as it was.
     */
    @Test
    void everyClauseCitedInAnotherDocumentIsDefined() throws IOException {
        java.util.Map<Path, Set<String>> defined = new java.util.LinkedHashMap<>();
        List<String> dangling = new ArrayList<>();
        int read = 0;
        for (Path at : documents()) {
            if (at.getFileName().toString().equals("RELEASES.md")) {
                continue;
            }
            String said = read(at);
            Matcher cited = Pattern.compile("([A-Za-z_]+)\\.md\\)? (\\d+(?:\\.\\d+)*)")
                    .matcher(said);
            while (cited.find()) {
                int open = said.lastIndexOf('(', Math.max(0, cited.start() - 1));
                String before = open >= 0 && cited.start() - open <= 120
                        ? said.substring(open, cited.start())
                        : said.substring(Math.max(0, cited.start() - 20), cited.start());
                if (before.contains("ST4") || before.contains("YMXS")
                        || before.contains("YMXR")) {
                    continue;
                }
                Path in = Path.of("doc", cited.group(1) + ".md");
                if (!Files.exists(in)) {
                    in = Path.of(cited.group(1) + ".md");
                }
                if (!Files.exists(in)) {
                    continue;
                }
                if (!defined.containsKey(in)) {
                    defined.put(in, clausesOf(in));
                }
                read++;
                if (!defined.get(in).contains(cited.group(2))) {
                    dangling.add(at + " cites " + cited.group());
                }
            }
        }
        final int opened = read;
        assertTrue(opened > 30, () -> "only " + opened
                + " citations read; the check is asleep");
        assertTrue(dangling.isEmpty(), () -> String.join("\n", dangling));
    }

    /**
     * The newest release RELEASES.md lists against the version the build is.
     * {@code release/publish.sh} names every file by the pom's version, so a
     * release cut without its entry, or an entry written before the bump,
     * parts the two.
     */
    @Test
    void theNewestReleaseListedIsTheVersionOfTheBuild() throws IOException {
        Matcher pom = Pattern.compile("<version>([^<]+)</version>")
                .matcher(read(Path.of("pom.xml")));
        assertTrue(pom.find(), "pom.xml names no version");
        Matcher listed = Pattern.compile("^### (\\d+\\.\\d+\\.\\d+), ",
                Pattern.MULTILINE).matcher(read(Path.of("doc/RELEASES.md")));
        assertTrue(listed.find(), "RELEASES.md lists no release");
        assertTrue(pom.group(1).equals(listed.group(1)),
                () -> "the pom is " + pom.group(1) + " and the newest release listed is "
                        + listed.group(1));
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
    /** The number words the pictures' captions write a small count in. */
    private static final List<String> WORD = List.of("zero", "one", "two",
            "three", "four", "five", "six", "seven", "eight", "nine", "ten");

    /**
     * SPEC.md's pictures against the example table the same section defines.
     * Every count a caption names is recomputed from `R`, `C` and the width,
     * so a caption reworded away from what its picture draws fails here
     * rather than lasting.
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
     * 2.3 defines the size of an ST4 header and the version byte of a data
     * set's first long, its stored against packed bullet repeats the size,
     * and the glossary's ST4 header row repeats both. Both figures move
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
        for (String[] row : Documents.glossaryRows(read(GLO))) {
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
        List<String[]> rows = Documents.glossaryRows(read(GLO));
        assertTrue(rows.size() > 5, () -> "the glossary read as " + rows.size()
                + " rows");
        List<String> wrong = Documents.outOfOrder(rows);
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
        for (String[] row : Documents.glossaryRows(read(GLO))) {
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
        List<String> broken = Documents.links(documents());
        assertTrue(broken.isEmpty(), () -> String.join("\n", broken));
    }

    /**
     * The scripts doc/tools.md lists, against the tree. A reader copies a
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
        List<Path> read = documents();
        assertTrue(read.size() > 8, () -> "only " + read.size()
                + " documents read; the check is asleep");
        List<String> wide = Documents.wide(read, 78);
        assertTrue(wide.isEmpty(), () -> String.join("\n", wide)
                + "\nAGENTS.md gives one wrap width, and a document keeps it.");
    }

    /** The clauses one document defines: `**N.N**` and `## N.N`, a section
     *  number marking itself and the clauses under it. */
    private static Set<String> clausesOf(String said) {
        Set<String> out = new HashSet<>();
        Matcher m = Pattern.compile("(?m)^(?:\\*\\*|#+ )R?(\\d+(?:\\.\\d+)*)").matcher(said);
        while (m.find()) {
            String clause = m.group(1);
            out.add(clause);
            for (int dot = clause.indexOf('.'); dot > 0; dot = clause.indexOf('.', dot + 1)) {
                out.add(clause.substring(0, dot));
            }
        }
        return out;
    }

    /**
     * Every citation of a specification lands on a clause of it.
     *
     * <p>A citation in these documents is the clause in brackets, `(2.3)`,
     * and a reader follows it. The first reader of the conformance kit read
     * ST4's 3.4 pointing at a value no clause set; this reads every
     * citation at once, so one that lands nowhere is named where it is
     * written.
     */
    @Test
    void everyCitationLandsOnAClause() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Path at : List.of(SPEC, REQ, Path.of("doc/abi.md"), TERM)) {
            String said = Files.readString(at);
            Set<String> clauses = clausesOf(said);
            Matcher m = Pattern.compile("\\((\\d+(?:\\.\\d+){1,3})\\)").matcher(said);
            while (m.find()) {
                if (!clauses.contains(m.group(1))) {
                    wrong.add(at + " cites (" + m.group(1) + "), which is no clause of it");
                }
            }
        }
        assertTrue(wrong.isEmpty(), () -> String.join("\n", wrong));
    }
}
