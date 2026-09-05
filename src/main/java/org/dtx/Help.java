package org.dtx;

/**
 * What each tool prints on {@code -help}: its synopsis, a line a flag with
 * the default in parentheses, and the section of doc/tools.md that describes
 * it. The Go and C# trees print the same text, and {@code ParityTest}
 * compares the three.
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
            dtx-write in.csv out.dtx [-vV] [-wW,W,..] [-rRR] [-kK] [-mN] [-pPACKER]
                      [-copies[S]]

            Writes the table in a comma separated text as a DTX file.

              -vV          the variant, 0, 1 or 2 (0)
              -wW,W,..     the width of each column in bytes, 1, 2 or 4 (the narrowest
                           that fits each column's values)
              -rRR         the repeat: the row an advance past the last steps to, R for
                           none (R)
              -kK          DTX2: the unit, 1, 2 or 4 (1)
              -mN          DTX2: the ring, in bytes (960)
              -pPACKER     DTX2: an ST4 executable to pack with (the copy carried here)
              -copies[S]   DTX2: copies from the literal stream, with S seconds of
                           search for a better parse
              -help        this text

            doc/tools.md, Write.
            """;

    static final String REWRITE = """
            dtx-rewrite in.dtx out.dtx [-kK] [-mN] [-pPACKER] [-copies[S]]

            Rewrites a DTX0 or DTX1 file as DTX2: the same rows, widths, R and RR,
            packed.

              -kK          the unit, 1, 2 or 4 (1)
              -mN          the ring, in bytes (960)
              -pPACKER     an ST4 executable to pack with (the copy carried here)
              -copies[S]   copies from the literal stream, with S seconds of search for
                           a better parse
              -help        this text

            doc/tools.md, Rewrite.
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

            doc/tools.md, Build the images.
            """;
}
