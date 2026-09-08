namespace Dtx;

/// <summary>
/// What each tool prints on -help: its synopsis, a line a flag with the
/// default in parentheses, examples, and the section of doc/tools.md that
/// describes it. The Java tree prints the same text, and the Go tree the
/// same but for dtx-package, which combines and does not assemble, so its
/// help lists neither -a nor -s; ParityTest compares them.
///
/// <para>A tool given no file to work on prints the same text to standard
/// error and exits with 2.</para>
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
              "dtx-write in out [-vV] [-wW] [-rRR] [-kK] [-mN] [-pPACKER] [-copies[S]]\n"
            + "\n"
            + "Writes the table in the first file to the second. The first is a DTX file\n"
            + "of any variant, or comma separated text; the second is a DTX file of\n"
            + "variant V, or text where its name ends in .csv.\n"
            + "\n"
            + "  -vV          the variant to write, 0, 1 or 2 (the one read, or 0 for\n"
            + "               text)\n"
            + "  -wW          text: the bytes every value takes, 1, 2 or 4 (what the\n"
            + "               text's first comment gives, or else the narrowest that\n"
            + "               fits every value)\n"
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
            + "  dtx-write t.csv t.dtx -v1 -w2\n"
            + "      text into a DTX1 file of two byte values\n"
            + "  dtx-write t.csv t.dtx -v1\n"
            + "      the same, at the narrowest width every value of the text fits\n"
            + "  dtx-write t.csv t.dtx -v2 -w1 -k1 -m960\n"
            + "      text into a DTX2 file of one byte values, at a unit of 1 and\n"
            + "      a ring of 960 bytes\n"
            + "  dtx-write t.csv t.dtx -v0 -w4 -r32\n"
            + "      text into a DTX0 file of four byte values, repeating at row 32\n"
            + "  dtx-write t.dtx again.dtx -k2 -copies\n"
            + "      a DTX2 file repacked at a unit of 2, with copies from the\n"
            + "      literal stream. The width is the file's own\n"
            + "  dtx-write t.dtx t.csv\n"
            + "      a DTX file of any variant read out as text\n"
            + "\n"
            + "doc/tools.md, Write.\n";

    public const string Package =
              "dtx-package in.dtx... out.bin [-aRMAC] [-s]\n"
            + "\n"
            + "Packages one DTX file or several as a 68000 image: the code for their\n"
            + "variant and, under DTX1 and DTX2, their width, then a column table and\n"
            + "a file for each. doc/abi.md gives the four calls into the image.\n"
            + "\n"
            + "  -aRMAC       assembles the code with the rmac at RMAC, in place of the\n"
            + "               image the build made. The templates are read from 68k\n"
            + "               beside the caller, or from what DTX_68K names\n"
            + "  -s           writes the table's figures as assembler equates, in place of\n"
            + "               an image\n"
            + "  -help        this text\n"
            + "\n"
            + "Examples\n"
            + "\n"
            + "  dtx-package t.dtx t.bin\n"
            + "      the image of a table, from the code the build made\n"
            + "  dtx-package a.dtx b.dtx both.bin\n"
            + "      one image of two tables, the code in it once, with a line\n"
            + "      saying where each table stands: a caller hands that to\n"
            + "      DTX_init\n"
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
            + "Builds the twenty-two images from the 68k/ templates with rmac and writes\n"
            + "them into each DIR: DTX0, DTX1 at each width, and DTX2 at each width and a\n"
            + "unit of 1, 2 and 4, with the copy code and without.\n"
            + "\n"
            + "  -aRMAC       the assembler (rmac, on the path)\n"
            + "  -tTEMPLATES  the directory the templates are read from (68k, or what\n"
            + "               DTX_68K names)\n"
            + "  -help        this text\n"
            + "\n"
            + "Examples\n"
            + "\n"
            + "  dtx-blobs build/68k\n"
            + "      the twenty-two images into build/68k, with the rmac on the path\n"
            + "  dtx-blobs build/68k go/internal/image/data -a/usr/local/bin/rmac\n"
            + "      into two directories, with that rmac\n"
            + "\n"
            + "doc/tools.md, Build the images.\n";
}
