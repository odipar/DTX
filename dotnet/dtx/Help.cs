namespace Dtx;

/// <summary>
/// What each tool prints on -help: its synopsis, a line a flag with the
/// default in parentheses, examples, and the section of doc/tools.md that
/// describes it. The Java and Go trees print the same text, and ParityTest compares
/// the three. A tool given no file to work on prints the same text to
/// standard error and exits with 2.
/// </summary>
public static class Help
{
    /// <summary>Whether -help or -h is among the arguments.</summary>
    public static bool Among(string[] args)
    {
        foreach (string arg in args)
        {
            if (arg == "-help" || arg == "-h") return true;
        }
        return false;
    }

    public const string Write =
              "dtx-write in out [-vV] [-wW,W,..] [-rRR] [-kK] [-mN] [-pPACKER] [-copies[S]]\n"
            + "\n"
            + "Writes the table in the first file to the second. The first is a DTX file\n"
            + "of any variant, or comma separated text; the second is a DTX file of\n"
            + "variant V, or text where its name ends in .csv.\n"
            + "\n"
            + "  -vV          the variant to write, 0, 1 or 2 (the one read, or 0 for\n"
            + "               text)\n"
            + "  -wW,W,..     text: the width of each column in bytes, 1, 2 or 4 (what the\n"
            + "               text's first comment gives, or else the narrowest that fits\n"
            + "               each column's values)\n"
            + "  -rRR         the repeat: the row an advance past the last steps to, R for\n"
            + "               none (what the file or the text's first comment gives, or R)\n"
            + "  -kK          DTX2: the unit, 1, 2 or 4 (1)\n"
            + "  -mN          DTX2: the ring, in bytes (960)\n"
            + "  -pPACKER     DTX2: an ST4 executable to pack with (the copy carried here)\n"
            + "  -copies[S]   DTX2: copies from the literal stream, with S seconds of\n"
            + "               search for a better parse\n"
            + "  -help        this text\n"
            + "\n"
            + "Examples\n"
            + "\n"
            + "  dtx-write t.csv t.dtx -v2 -k1 -m960\n"
            + "      text into a DTX2 file, at a unit of 1 and a ring of 960 bytes\n"
            + "  dtx-write t.dtx again.dtx -k2 -copies\n"
            + "      a DTX2 file repacked at a unit of 2, with copies from the\n"
            + "      literal stream\n"
            + "  dtx-write t.dtx t.csv\n"
            + "      a DTX file of any variant read out as text\n"
            + "  dtx-write t.csv t.dtx -v1 -w1,2,4 -r32\n"
            + "      text into a DTX1 file at the widths given, repeating at row 32\n"
            + "\n"
            + "doc/tools.md, Write.\n";

    public const string Package =
              "dtx-package in.dtx out.bin [-aRMAC] [-s]\n"
            + "\n"
            + "Packages a DTX file as a 68000 image: the code for its variant, the column\n"
            + "table and the file. doc/abi.md gives the six calls into the image.\n"
            + "\n"
            + "  -aRMAC       assembles the code from the 68k/ templates with the rmac at\n"
            + "               RMAC, in place of the image the build made\n"
            + "  -s           writes the table's figures as assembler equates, in place of\n"
            + "               an image\n"
            + "  -help        this text\n"
            + "\n"
            + "Examples\n"
            + "\n"
            + "  dtx-package t.dtx t.bin\n"
            + "      the image of a table, from the code the build made\n"
            + "  dtx-package t.dtx t.bin -a/usr/local/bin/rmac\n"
            + "      the same, with the code assembled from the templates by that\n"
            + "      rmac\n"
            + "  dtx-package t.dtx t.i -s\n"
            + "      the table's figures as assembler equates, for a build of your\n"
            + "      own\n"
            + "\n"
            + "doc/tools.md, Package.\n";

    public const string Blobs =
              "dtx-blobs DIR [DIR..] [-aRMAC] [-tTEMPLATES]\n"
            + "\n"
            + "Builds the eight images from the 68k/ templates with rmac and writes them\n"
            + "into each DIR: DTX0, DTX1, and DTX2 at a unit of 1, 2 and 4, with the copy\n"
            + "code and without.\n"
            + "\n"
            + "  -aRMAC       the assembler (rmac, on the path)\n"
            + "  -tTEMPLATES  the directory the templates are read from (68k, or what\n"
            + "               DTX_68K names)\n"
            + "  -help        this text\n"
            + "\n"
            + "Examples\n"
            + "\n"
            + "  dtx-blobs build/68k\n"
            + "      the eight images into build/68k, with the rmac on the path\n"
            + "  dtx-blobs build/68k go/internal/image/data -a/usr/local/bin/rmac\n"
            + "      into two directories, with that rmac\n"
            + "\n"
            + "doc/tools.md, Build the images.\n";
}
