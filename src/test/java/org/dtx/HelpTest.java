package org.dtx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * What {@code -help} prints, read back against the tool it describes:
 * every flag in the synopsis has its line, {@code -help} is among them,
 * the doc/tools.md section named is a heading there, and the text fits a
 * terminal. Then each Java tool is run with {@code -help} and prints it.
 */
final class HelpTest {

    private static final Map<String, String> HELP = Map.of(
            "Write", Help.WRITE, "Rewrite", Help.REWRITE,
            "Packager", Help.PACKAGE, "Blobs", Help.BLOBS);

    @Test
    void everyFlagInTheSynopsisHasItsLine() {
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, String> one : HELP.entrySet()) {
            String text = one.getValue();
            String synopsis = text.substring(0, text.indexOf("\n\n"));
            Matcher flag = Pattern.compile("\\[(-[a-z]+)").matcher(synopsis);
            while (flag.find()) {
                if (!text.contains("\n  " + flag.group(1))) {
                    wrong.add(one.getKey() + ": " + flag.group(1)
                            + " is in the synopsis and has no line");
                }
            }
            if (!text.contains("\n  -help ")) {
                wrong.add(one.getKey() + ": -help is not listed");
            }
            if (!text.endsWith(".\n")) {
                wrong.add(one.getKey() + ": the text does not end on the"
                        + " doc/tools.md section");
            }
            for (String line : text.split("\n")) {
                if (line.length() > 78) {
                    wrong.add(one.getKey() + ": a line runs to "
                            + line.length());
                }
            }
        }
        assertTrue(wrong.isEmpty(), () -> String.join("\n", wrong));
    }

    @Test
    void everySectionNamedIsAHeadingOfToolsMd() throws IOException {
        String tools = Files.readString(Rig.root().resolve("doc/tools.md"));
        for (Map.Entry<String, String> one : HELP.entrySet()) {
            String text = one.getValue().strip();
            String section = text.substring(text.lastIndexOf(", ") + 2,
                    text.length() - 1);
            assertTrue(tools.contains("\n## " + section + "\n"),
                    one.getKey() + " points at doc/tools.md, " + section
                            + ", which is not a heading there");
        }
    }

    @Test
    void everyToolPrintsItsHelpAndNothingElse() {
        for (Map.Entry<String, String> one : HELP.entrySet()) {
            String said = Rig.run(List.of("java", "-cp",
                    Rig.root().resolve("target/classes").toString(),
                    "org.dtx." + one.getKey(), "-help"));
            assertEquals(one.getValue(), said, one.getKey() + " -help");
        }
    }
}
