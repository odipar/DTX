package org.dtx;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every document against the house style's ban list.
 *
 * <p>{@code AGENTS.md} states the rules - nothing acts on its own, and no
 * flourish - and this test holds the phrases struck in review under them.
 * Each entry is one struck phrase or the stem of one; a hit names the file
 * and line. A phrase that is legitimate in a new context comes off the list
 * in the same change that uses it.
 *
 * <p>The documents are found rather than listed. A list is a place a new
 * document is not, and the one that reached review unchecked was the one
 * nobody had added.
 *
 * <p>AGENTS.md holds code comments to the same rules, so the comments of
 * this repository's own source are read as well. What is carried from
 * another repository is not: a copy is held to its own tree's style, and
 * editing it here would be editing the copy.
 */
final class HouseStyleTest {

    /** The two documents that state the rules, and so quote what they
     * strike. Every other Markdown file in the tree is held. */
    private static final List<String> STATES_THE_RULES =
            List.of("AGENTS.md", "CLAUDE.md");

    /** Struck in review, lowercase; matched as substrings. */
    private static final List<String> STRUCK = List.of(
            // roles and abstractions acting: a writer promising, a source
            // implying, a player being told, roles standing
            "promise",
            "guarantee",
            "implies",
            "imply ",
            "can be told",
            "roles stand",
            // a format does not rule, and does not measure: a measurement
            // is taken of it, and its specification states what it states
            "it ruled",
            "it measured",
            // a format does not answer a constraint: a choice is what
            // it was, and the constraint is what bound it
            "answered",
            // a specification defines; a tune and a build carry, and keep
            // the verb for what a thing holds
            "carries",
            // a column holds a value; nothing sits anywhere
            "sits in",
            "stand apart",
            // the cleft: "X is what makes Y" is "X makes Y". R3.5, R4.4
            // and R5.7 keep "That is what DTXn is for", so the bare "is
            // what" stays off the list and the struck forms are listed
            "which is what",
            "this is what",
            " is what lets",
            // a rule justified by quoting a speaking thing
            "spells out",
            "spell out",
            "because it says",
            "says it",
            "says so",
            "says what to take",
            // a format does not say, and a length is not a data set's to
            // state: the data set states it
            "own to say",
            "own to state",
            "set-ness",
            "takes the machine with it",
            // "consumer" is a role the specification defines, as "caller"
            // and "owner" are roles: only the verb is struck
            "consume ",
            "consumes",
            "consumed",
            "consuming",
            "stand as they were",
            // a consumer does not understand a stream, it implements it
            "understand",
            // AGENTS.md: no file, program or algorithm wants or knows. The
            // plain verbs are there - a variant needs, a header gives. Both
            // entries lead with a space, which the matcher below reads as a
            // word boundary, so "unknown" and "acknowledge" pass
            " want",
            " know",
            "refuse",
            // the sweep: a trailing clause generalising the sentence
            "whatever",
            "whichever way",
            "where it sits",
            "stood still",
            // the metaphor in place of the operation
            " a tail ",
            "sliver",
            "literally",
            "smear",
            "bears it out",
            "pressure point",
            "door left open",
            "cover version",
            "smuggl",
            "catastroph",
            // shape: no em dash construct anywhere - a dash that must stay
            // is a single '-'; the list strikes the en dash and the minus
            // sign too
            "—",
            "–",
            "−",
            // nothing does what a person does: a file contains and a value
            // is in a field (not held), a table needs (not asks for), packing
            // costs (not pays, buys), a figure is given (not settled), one
            // build reads every column (not serves), a flag marks (not picks),
            // bytes match or differ (not agree). A rule is met, not held.
            " hold",
            "held",
            "buys",
            "buy ",
            "bought",
            " pays",
            "pay for",
            "paid",
            "asks for",
            "ask for",
            "asked for",
            "chose",
            "choose",
            "agree",
            "spend",
            "spent",
            "picks",
            "pick ",
            " serve",
            "settle",
            "trust",
            "lets ",
            " let ",
            "answer",
            // the flourish: a format that gives a shape a meaning
            "meaning",
            // one word for a thing that has one: a data set, and the ring
            // it unpacks through
            "container",
            "window",
            "buffer",
            // a noun pressed into service as a verb
            "vendor",
            // the verdict: the sentence grading itself or its subject
            "is deliberate",
            "by design",
            "on purpose",
            "asked properly",
            "not a shrug",
            "most of the point",
            "the answer to that",
            "worth reading",
            "the ones that matter",
            "the whole point",
            // filler: cut unless the word carries the meaning
            "actually",
            // "state" is the noun - a state block, a decoder state - and a
            // document, header or payload defines
            "stated",
            "stating",
            " states",
            "to state",
            "can state",
            "not state",
            "does state",
            "must state",
            "should state",
            "state what",
            "state which",
            "state whether",
            "state how");

    /**
     * What this repository carries rather than writes. A copy states its
     * own tree's prose, and the two files DTX wrote into the Go copy's
     * directory are its own.
     */
    private static boolean carried(Path path) {
        String at = path.toString();
        if (at.contains("/org/st4/") || at.contains("/dotnet/nt4/")
                || at.endsWith("ST4_wrap.S")) {
            return true;
        }
        return at.contains("/go/internal/st4/")
                && !at.endsWith("beside.go") && !at.endsWith("packer.go");
    }

    /** Every source file whose comments this repository writes. */
    private static List<Path> sources() throws IOException {
        try (Stream<Path> tree = Files.walk(Path.of("."))) {
            return tree.filter(Files::isRegularFile)
                    .filter(path -> {
                        String at = path.toString();
                        return at.endsWith(".java") || at.endsWith(".go")
                                || at.endsWith(".cs") || at.endsWith(".S")
                                || at.endsWith(".py") || at.endsWith(".sh");
                    })
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.toString().contains("/obj/"))
                    .filter(path -> !path.toString().contains("/dotnet/bin/"))
                    .filter(path -> !carried(path))
                    // This file quotes every phrase it strikes.
                    .filter(path -> !path.getFileName().toString()
                            .equals("HouseStyleTest.java"))
                    .sorted()
                    .toList();
        }
    }

    /** One line of prose out of a source file, and where it stands. */
    private record Comment(int line, String text) {}

    /**
     * The comments of one file, by the marks its language writes them with.
     *
     * <p>A mark inside a string is not a comment, so the scan tracks what it
     * stands in: a URL in a literal opens no comment, and neither does a
     * struck word in one.
     */
    private static List<Comment> comments(Path path, String held) {
        String name = path.toString();
        boolean cLike = name.endsWith(".java") || name.endsWith(".go")
                || name.endsWith(".cs");
        boolean python = name.endsWith(".py");
        char mark = cLike ? '/' : name.endsWith(".S") ? ';' : '#';
        List<Comment> out = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        int line = 1;
        int began = 1;
        // 0 code, 1 a string, 2 a line comment, 3 a block comment, 4 a
        // python docstring
        int in = 0;
        char quote = 0;
        for (int at = 0; at < held.length(); at++) {
            char one = held.charAt(at);
            char next = at + 1 < held.length() ? held.charAt(at + 1) : 0;
            if (one == '\n') {
                line++;
            }
            switch (in) {
                case 0 -> {
                    if (one == '"' || one == '\'') {
                        if (python && next == one
                                && at + 2 < held.length()
                                && held.charAt(at + 2) == one) {
                            in = 4;
                            quote = one;
                            began = line;
                            at += 2;
                        } else {
                            in = 1;
                            quote = one;
                        }
                    } else if (cLike && one == mark && next == '/') {
                        in = 2;
                        began = line;
                        at++;
                    } else if (cLike && one == mark && next == '*') {
                        in = 3;
                        began = line;
                        at++;
                    } else if (!cLike && one == mark) {
                        in = 2;
                        began = line;
                    }
                }
                case 1 -> {
                    if (one == '\\') {
                        at++;
                    } else if (one == quote || one == '\n') {
                        in = 0;
                    }
                }
                case 2 -> {
                    if (one == '\n') {
                        out.add(new Comment(began, run.toString()));
                        run.setLength(0);
                        in = 0;
                    } else {
                        run.append(one);
                    }
                }
                case 3 -> {
                    if (one == '*' && next == '/') {
                        out.add(new Comment(began, run.toString()));
                        run.setLength(0);
                        in = 0;
                        at++;
                    } else {
                        run.append(one);
                    }
                }
                default -> {
                    if (one == quote && next == quote
                            && at + 2 < held.length()
                            && held.charAt(at + 2) == quote) {
                        out.add(new Comment(began, run.toString()));
                        run.setLength(0);
                        in = 0;
                        at += 2;
                    } else {
                        run.append(one);
                    }
                }
            }
        }
        if (run.length() != 0) {
            out.add(new Comment(began, run.toString()));
        }
        return out;
    }

    @Test
    void theCommentScannerReadsCommentsAndNotStrings() {
        // A struck phrase in a comment is a hit and one in a string is not,
        // or the check would read a URL's // as prose and a literal as a
        // sentence. Each language is tried in the marks it writes.
        record Sample(String name, String held, String prose, String hidden) {}
        for (Sample one : List.of(
                new Sample("a.java",
                        "String at = \"http://x/promise\"; // a promise here\n"
                                + "/* and a guarantee */\n",
                        "a promise here", "http"),
                new Sample("a.go",
                        "s := \"a promise\" // a guarantee here\n",
                        "a guarantee here", "promise"),
                new Sample("a.cs",
                        "var s = \"a promise\";\n/// a guarantee here\n",
                        "a guarantee here", "promise"),
                new Sample("a.S",
                        "\tmove.l  #1,d0          ; a promise here\n",
                        "a promise here", ""),
                new Sample("a.py",
                        "at = \"a promise\"  # a guarantee here\n"
                                + "\"\"\"and a docstring\"\"\"\n",
                        "a guarantee here", "promise"),
                new Sample("a.sh",
                        "echo \"a promise\"   # a guarantee here\n",
                        "a guarantee here", "promise"))) {
            StringBuilder read = new StringBuilder();
            for (Comment held : comments(Path.of(one.name()), one.held())) {
                read.append(held.text()).append('\n');
            }
            String found = read.toString();
            assertTrue(found.contains(one.prose()),
                    one.name() + ": the scanner read \"" + found.strip()
                            + "\", which does not hold \"" + one.prose() + '"');
            if (!one.hidden().isEmpty()) {
                assertTrue(!found.contains(one.hidden()),
                        one.name() + ": the scanner read \"" + one.hidden()
                                + "\" out of a string");
            }
        }
    }

    @Test
    void noCommentHasAStruckPhrase() throws IOException {
        List<Path> sources = sources();
        assertTrue(!sources.isEmpty(), "no source was found to hold");
        List<String> hits = new ArrayList<>();
        for (Path source : sources) {
            for (Comment one : comments(source, Files.readString(source))) {
                String prose = read(one.text());
                for (String struck : STRUCK) {
                    if (prose.contains(struck)) {
                        hits.add(source + ":" + one.line()
                                + " has \"" + struck + '"');
                    }
                }
                for (String shape : shapes(prose)) {
                    hits.add(source + ":" + one.line() + " has \"" + shape
                            + '"');
                }
            }
        }
        assertTrue(hits.isEmpty(), () -> String.join("\n", hits)
                + "\nAGENTS.md holds a code comment to the rules a document"
                + " is held to; reword the comment, or take the entry off"
                + " this list in the same change.");
    }

    /** Every Markdown file in the tree but the two that state the rules. */
    private static List<Path> documents() throws IOException {
        try (Stream<Path> tree = Files.walk(Path.of("."))) {
            return tree.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !STATES_THE_RULES
                            .contains(path.getFileName().toString()))
                    .sorted()
                    .toList();
        }
    }

    /**
     * The names a struck entry stands inside, which are not that entry.
     *
     * <p>Windows is an operating system and ST4_WINDOW an assembler symbol,
     * and the entry struck is {@code window}, the reach a data set decodes
     * through: a name spelled like a word is not that word. They are
     * matched before the line is lowered, so the noun still reads as
     * struck. `decoder states` is the plural noun, and {@code  states} the
     * verb.
     */
    private static final List<String> NAMES = List.of("Windows", "ST4_WINDOW",
            "decoder states", "Decoder states");

    /**
     * A shape struck as a pattern rather than a phrase: a verb negating its
     * object, `defines no table`, where the verb is what to negate - `does
     * not define a table`. A word in s before ` no ` is read as the verb;
     * `this`, `thus`, `as`, `unless`, `its` and `yes` are not verbs and pass.
     */
    private static final Pattern SHAPE = Pattern.compile(
            "\\b(?!this\\b|thus\\b|as\\b|unless\\b|its\\b|yes\\b)"
            + "[a-z]+s no [a-z]+");

    /** The struck shapes in {@code prose}, as the text each matched. */
    private static List<String> shapes(String prose) {
        List<String> found = new ArrayList<>();
        Matcher shape = SHAPE.matcher(prose);
        while (shape.find()) {
            found.add(shape.group());
        }
        return found;
    }

    /** {@code line} with the names out and the rest lowered. */
    private static String read(String line) {
        String held = line;
        for (String name : NAMES) {
            held = held.replace(name, " ");
        }
        // a space in front, so an entry that leads with one matches a word
        // at the start of a line as well as inside
        return " " + held.toLowerCase();
    }

    @Test
    void noDocumentHasAStruckPhrase() throws IOException {
        List<Path> documents = documents();
        assertTrue(!documents.isEmpty(), "no document was found to hold");
        List<String> hits = new ArrayList<>();
        for (Path document : documents) {
            List<String> lines = Files.readAllLines(document);
            for (int at = 0; at < lines.size(); at++) {
                String line = read(lines.get(at));
                for (String struck : STRUCK) {
                    if (line.contains(struck)) {
                        hits.add(document + ":" + (at + 1)
                                + " has \"" + struck + '"');
                    }
                }
                for (String shape : shapes(line)) {
                    hits.add(document + ":" + (at + 1) + " has \"" + shape
                            + '"');
                }
            }
            hits.addAll(wrappedHits(document, lines));
        }
        assertTrue(hits.isEmpty(), () -> String.join("\n", hits)
                + "\nAGENTS.md has the rule each phrase was struck under;"
                + " reword the line, or take the entry off this list in the"
                + " same change.");
    }

    /**
     * The hits a line wrap hides. A phrase broken across two lines stands in
     * neither of them, so every paragraph is read joined as well, and what
     * the joined text holds beyond what its own lines hold is reported at
     * the line the paragraph begins on. A line joins without its indent, or
     * a list item's two spaces would stand inside the phrase a wrap broke. A
     * table row, an indented block and a fence break a paragraph: joining
     * those would put words side by side that no sentence puts there.
     */
    private static List<String> wrappedHits(Path document, List<String> lines) {
        List<String> hits = new ArrayList<>();
        int from = 0;
        for (int at = 0; at <= lines.size(); at++) {
            boolean breaks = at == lines.size() || lines.get(at).isBlank()
                    || lines.get(at).startsWith("|")
                    || lines.get(at).startsWith("    ")
                    || lines.get(at).startsWith("```");
            if (!breaks) {
                continue;
            }
            if (at > from) {
                List<String> paragraph = lines.subList(from, at);
                StringBuilder run = new StringBuilder();
                for (String line : paragraph) {
                    run.append(' ').append(line.strip());
                }
                String joined = read(run.toString());
                for (String struck : STRUCK) {
                    int whole = occurrences(joined, struck);
                    int apart = 0;
                    for (String line : paragraph) {
                        apart += occurrences(read(line), struck);
                    }
                    for (int n = apart; n < whole; n++) {
                        hits.add(document + ":" + (from + 1) + " has \""
                                + struck + "\", broken by a line wrap");
                    }
                }
                int whole = shapes(joined).size();
                int apart = 0;
                for (String line : paragraph) {
                    apart += shapes(read(line)).size();
                }
                for (int n = apart; n < whole; n++) {
                    hits.add(document + ":" + (from + 1) + " has a verb"
                            + " negating its object, broken by a line wrap");
                }
            }
            from = at + 1;
        }
        return hits;
    }

    /** How many times a struck phrase stands in a run of text. */
    private static int occurrences(String text, String struck) {
        int found = 0;
        for (int at = text.indexOf(struck); at >= 0;
                at = text.indexOf(struck, at + 1)) {
            found++;
        }
        return found;
    }
}
