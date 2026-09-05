package org.dtx;

/**
 * What each tool prints on {@code -help}: its synopsis, a line a flag with
 * the default in parentheses, examples, and the section of doc/tools.md that
 * describes it. The Go and C# trees print the same text, and
 * {@code ParityTest} compares the three.
 *
 * <p>A tool given no file to work on prints the same text to standard error
 * and exits with 2.
 */
final class Help {

    private Help() {}

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
            dtx-write in out [-vV] [-wW,W,..] [-rRR] [-kK] [-mN] [-pPACKER] [-copies[S]]

            Writes the table in the first file to the second. The first is a DTX file
            of any variant, or comma separated text; the second is a DTX file of
            variant V, or text where its name ends in .csv.

              -vV          the variant to write, 0, 1 or 2 (the one read, or 0 for
                           text)
              -wW,W,..     text: the width of each column in bytes, 1, 2 or 4 (what the
                           text's first comment gives, or else the narrowest that fits
                           each column's values)
              -rRR         the repeat: the row an advance past the last steps to, R for
                           none (what the file or the text's first comment gives, or R)
              -kK          DTX2: the unit, 1, 2 or 4 (1)
              -mN          DTX2: the ring, in bytes (960)
              -pPACKER     DTX2: an ST4 executable to pack with (the copy carried here)
              -copies[S]   DTX2: copies from the literal stream, with S seconds of
                           search for a better parse
              -help        this text

            Examples

              dtx-write t.csv t.dtx -v2 -k1 -m960
                  text into a DTX2 file, at a unit of 1 and a ring of 960 bytes
              dtx-write t.dtx again.dtx -k2 -copies
                  a DTX2 file repacked at a unit of 2, with copies from the
                  literal stream
              dtx-write t.dtx t.csv
                  a DTX file of any variant read out as text
              dtx-write t.csv t.dtx -v1 -w1,2,4 -r32
                  text into a DTX1 file at the widths given, repeating at row 32

            doc/tools.md, Write.
            """;

    static final String PACKAGE = """
            dtx-package in.dtx out.bin [-aRMAC] [-s]

            Packages a DTX file as a 68000 image: the code for its variant, the column
            table and the file. doc/abi.md gives the six calls into the image.

              -aRMAC       assembles the code from the 68k/ templates with the rmac at
                           RMAC, in place of the image the build made
              -s           writes the table's figures as assembler equates, in place of
                           an image
              -help        this text

            Examples

              dtx-package t.dtx t.bin
                  the image of a table, from the code the build made
              dtx-package t.dtx t.bin -a/usr/local/bin/rmac
                  the same, with the code assembled from the templates by that
                  rmac
              dtx-package t.dtx t.i -s
                  the table's figures as assembler equates, for a build of your
                  own

            doc/tools.md, Package.
            """;

    static final String BLOBS = """
            dtx-blobs DIR [DIR..] [-aRMAC] [-tTEMPLATES]

            Builds the eight images from the 68k/ templates with rmac and writes them
            into each DIR: DTX0, DTX1, and DTX2 at a unit of 1, 2 and 4, with the copy
            code and without.

              -aRMAC       the assembler (rmac, on the path)
              -tTEMPLATES  the directory the templates are read from (68k, or what
                           DTX_68K names)
              -help        this text

            Examples

              dtx-blobs build/68k
                  the eight images into build/68k, with the rmac on the path
              dtx-blobs build/68k go/internal/image/data -a/usr/local/bin/rmac
                  into two directories, with that rmac

            doc/tools.md, Build the images.
            """;
}
