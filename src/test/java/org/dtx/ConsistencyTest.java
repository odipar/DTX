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
import org.junit.jupiter.api.Test;

/**
 * The documents against themselves: every reference that can be followed,
 * every figure that can be recomputed.
 *
 * <p>Ported from YMXR, which holds the same three tests. Two of its checks
 * read documents this repository has not written - a column table, and the
 * figures of an experiment - and they come back with those documents.
 *
 * <p>{@code HouseStyleTest} holds the prose to {@code AGENTS.md} and
 * {@code GlossaryTest} holds the terms to the glossary. This holds the
 * numbers and the pointers, which drift on their own as a document is
 * edited: a requirement renumbered, a section renamed, a column added, a
 * ratio left over from the figures before it.
 *
 * <p>Every check here found something on the day it was written.
 */
final class ConsistencyTest {

    private static final Path SPEC = Path.of("doc/SPEC.md");

    private static final Path REQ = Path.of("doc/requirements.md");
    private static final Path GLO = Path.of("doc/glossary.md");
    private static final Path TERM = Path.of("doc/terminology.md");
    private static final Path EXP = Path.of("doc/experiments.md");

    private static final List<Path> DOCUMENTS =
            List.of(Path.of("README.md"), SPEC, REQ, GLO, TERM, EXP);

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
        for (Path p : DOCUMENTS) {
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

    /** Every row of the glossary's table, its term and where it points. */
    private static List<String[]> glossaryRows(String glo) {
        List<String[]> out = new ArrayList<>();
        for (String line : glo.split("\n")) {
            if (!line.startsWith("| ") || line.startsWith("| term")) {
                continue;
            }
            String[] cells = line.split("\\|");
            if (cells.length >= 4) {
                out.add(new String[] {cells[1].trim(), cells[3].trim()});
            }
        }
        return out;
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
            String where = row[1];
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
        for (Path p : DOCUMENTS) {
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

    @Test
    void everyDocumentHoldsOneWrapWidth() throws IOException {
        List<String> wide = new ArrayList<>();
        for (Path p : DOCUMENTS) {
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
                + "\nAGENTS.md asks one width, held.");
    }
}
