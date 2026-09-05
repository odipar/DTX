namespace Dtx;

/// <summary>
/// What each tool prints on -help: its synopsis, a line a flag with the
/// default in parentheses, and the section of doc/tools.md that describes
/// it. The Java and Go trees print the same text, and ParityTest compares
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
              "dtx-write in.csv out.dtx [-vV] [-wW,W,..] [-rRR] [-kK] [-mN] [-pPACKER]\n"
            + "          [-copies[S]]\n"
            + "\n"
            + "Writes the table in a comma separated text as a DTX file.\n"
            + "\n"
            + "  -vV          the variant, 0, 1 or 2 (0)\n"
            + "  -wW,W,..     the width of each column in bytes, 1, 2 or 4 (the narrowest\n"
            + "               that fits each column's values)\n"
            + "  -rRR         the repeat: the row an advance past the last steps to, R for\n"
            + "               none (R)\n"
            + "  -kK          DTX2: the unit, 1, 2 or 4 (1)\n"
            + "  -mN          DTX2: the ring, in bytes (960)\n"
            + "  -pPACKER     DTX2: an ST4 executable to pack with (the copy carried here)\n"
            + "  -copies[S]   DTX2: copies from the literal stream, with S seconds of\n"
            + "               search for a better parse\n"
            + "  -help        this text\n"
            + "\n"
            + "doc/tools.md, Write.\n";

    public const string Rewrite =
              "dtx-rewrite in.dtx out.dtx [-kK] [-mN] [-pPACKER] [-copies[S]]\n"
            + "\n"
            + "Rewrites a DTX0 or DTX1 file as DTX2: the same rows, widths, R and RR,\n"
            + "packed.\n"
            + "\n"
            + "  -kK          the unit, 1, 2 or 4 (1)\n"
            + "  -mN          the ring, in bytes (960)\n"
            + "  -pPACKER     an ST4 executable to pack with (the copy carried here)\n"
            + "  -copies[S]   copies from the literal stream, with S seconds of search for\n"
            + "               a better parse\n"
            + "  -help        this text\n"
            + "\n"
            + "doc/tools.md, Rewrite.\n";

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
            + "doc/tools.md, Build the images.\n";
}
