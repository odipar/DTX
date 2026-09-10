package org.dtx;

/**
 * What each tool prints on {@code -help}: its synopsis, a line a flag with
 * the default in parentheses, examples, and the section of doc/tools.md that
 * describes it. The C# tree prints the same text, and the Go tree the same
 * but for dtx-package, which combines and does not assemble, so its help
 * lists neither -a nor -s; {@code ParityTest} compares them.
 *
 * <p>A tool reads its input on standard input, so {@code -help} is what
 * prints this text.
 */
final class Help {

    private Help() {}

    /**
     * Prints why a tool stopped, and exits with 1. A file it cannot read, a
     * header outside the bounds R6 sets, a value no width takes: the caller
     * is given the one line the Go and C# trees give, and not a stack trace.
     * A misuse of the command line exits with 2 instead.
     */
    static void stopped(Exception failed) {
        String why = failed.getMessage();
        System.err.println(failed instanceof java.io.IOException && why != null
                && !why.contains(" ") ? "cannot read " + why : why);
        System.exit(1);
    }

    /** Whether {@code -help} or {@code -h} is among the arguments. */
    static boolean among(String[] args) {
        for (String arg : args) {
            if (arg.equals("-help") || arg.equals("-h")) {
                return true;
            }
        }
        return false;
    }

    static final String WRITE = """
            dtx-write [-vV] [-wW] [-rRR] [-kK] [-mN] [-pPACKER] [-copies[S]] [-text]

            Writes the table on standard input to standard output. The input is a DTX
            file of any variant, or comma separated text; the output is a DTX file of
            variant V, or comma separated text under -text.

              -vV          the variant to write, 0, 1 or 2 (the one read, or 0 for
                           text)
              -wW          text: the bytes every value takes, 1, 2 or 4 (what the
                           text's first comment gives, or else the narrowest that
                           fits every value)
              -rRR         the repeat: the row an advance past the last steps to, R for
                           none (what the file or the text's first comment gives, or R)
              -kK          DTX2: the unit, 1, 2 or 4 (1)
              -mN          DTX2: the ring, in bytes (960)
              -pPACKER     DTX2: an ST4 executable to pack with (the copy carried here)
              -copies[S]   DTX2: copies from the literal stream, with S seconds of
                           search for a better parse
              -text        writes the table as comma separated text
              -help        this text

            Examples

              dtx-write -v1 -w2 < t.csv > t.dtx
                  text into a DTX1 file of two byte values
              dtx-write -v1 < t.csv > t.dtx
                  the same, at the narrowest width every value of the text fits
              dtx-write -v2 -w1 -k1 -m960 < t.csv > t.dtx
                  text into a DTX2 file of one byte values, at a unit of 1 and
                  a ring of 960 bytes
              dtx-write -v0 -w4 -r32 < t.csv > t.dtx
                  text into a DTX0 file of four byte values, repeating at row 32
              dtx-write -k2 -copies < t.dtx > again.dtx
                  a DTX2 file repacked at a unit of 2, with copies from the
                  literal stream. The width is the file's own
              dtx-write -text < t.dtx > t.csv
                  a DTX file of any variant read out as text

            doc/tools.md, Write.
            """;

    static final String PACKAGE = """
            dtx-package [in.dtx...] [-aRMAC] [-s] < in.dtx > out.bin

            Packages one DTX file or several as a 68000 image, on standard output:
            the code for their variant and, under DTX1 and DTX2, their width, then a
            column table and a file for each. One table comes in on standard input,
            and several are named. doc/abi.md gives the four calls into the image.

              -aRMAC       assembles the code with the rmac at RMAC, in place of the
                           image the build made. The templates are read from 68k
                           beside the caller, or from what DTX_68K names
              -s           writes the table's figures as assembler equates, in place of
                           an image
              -help        this text

            Examples

              dtx-package < t.dtx > t.bin
                  the image of a table, from the code the build made
              dtx-package a.dtx b.dtx > both.bin
                  one image of two tables, the code in it once, with a line
                  saying where each table stands: a caller hands that to
                  DTX_init
              dtx-package -a/usr/local/bin/rmac < t.dtx > t.bin
                  the same, with the code assembled from the templates by that
                  rmac
              dtx-package -s < t.dtx > t.i
                  the table's figures as assembler equates, for a build of your
                  own

            doc/tools.md, Package.
            """;

    static final String BLOBS = """
            dtx-blobs DIR [DIR..] [-aRMAC] [-tTEMPLATES]

            Builds the twenty-two images from the 68k/ templates with rmac and writes
            them into each DIR: DTX0, DTX1 at each width, and DTX2 at each width and a
            unit of 1, 2 and 4, with the copy code and without.

              -aRMAC       the assembler (rmac, on the path)
              -tTEMPLATES  the directory the templates are read from (68k, or what
                           DTX_68K names)
              -help        this text

            Examples

              dtx-blobs build/68k
                  the twenty-two images into build/68k, with the rmac on the path
              dtx-blobs build/68k go/image/data -a/usr/local/bin/rmac
                  into two directories, with that rmac

            doc/tools.md, Build the images.
            """;
}
