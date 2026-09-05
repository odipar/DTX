namespace Dtx;

using System.Globalization;
using System.Text;

/// <summary>The four tools doc/tools.md defines, one method each.</summary>
public static class Tools
{
    /// <summary>What packs a column: the copy in this assembly, or a
    /// packer beside it where -p names one.</summary>
    private static IPacker Packer(string named, string copies)
    {
        if (named.Length != 0)
        {
            return new St4Beside(named, copies);
        }
        return copies.Length == 0
                ? new St4Packer() : new St4Packer(true, Seconds(copies));
    }

    /// <summary>The seconds -copiesS searches for, or zero.</summary>
    private static double Seconds(string copies) => copies.Length > 2
            ? double.Parse(copies[2..], CultureInfo.InvariantCulture) : 0;

    private static int Number(string given) =>
            int.Parse(given, CultureInfo.InvariantCulture);

    /// <summary>Comma separated text into a DTX file of any variant.</summary>
    public static int Write(string[] args)
    {
        if (Help.Among(args))
        {
            Console.Write(Help.Write);
            return 0;
        }
        int variant = Format.Dtx0, repeat = -1, unit = 1, ring = 960;
        string widths = "", packer = "", copies = "";
        List<string> named = new();
        foreach (string arg in args)
        {
            if (arg.StartsWith("-v", StringComparison.Ordinal)) variant = Number(arg[2..]);
            else if (arg.StartsWith("-w", StringComparison.Ordinal)) widths = arg[2..];
            else if (arg.StartsWith("-r", StringComparison.Ordinal)) repeat = Number(arg[2..]);
            else if (arg.StartsWith("-k", StringComparison.Ordinal)) unit = Number(arg[2..]);
            else if (arg.StartsWith("-m", StringComparison.Ordinal)) ring = Number(arg[2..]);
            // the packer's own: a match beyond the ring copies from the
            // literal stream, and -copiesS searches S seconds for a better
            // parse. YMX spells it the same way.
            else if (arg.StartsWith("-copies", StringComparison.Ordinal)) copies = "-c" + arg[7..];
            else if (arg.StartsWith("-p", StringComparison.Ordinal)) packer = arg[2..];
            else if (arg.StartsWith('-'))
            {
                Console.Error.WriteLine($"dtx-write does not read {arg}");
                return 2;
            }
            else named.Add(arg);
        }
        if (named.Count != 2)
        {
            Console.Error.Write(Help.Write);
            return 2;
        }
        string text = File.ReadAllText(named[0]);
        int[] width = widths.Length == 0 ? Csv.Narrowest(text) : Widths(widths);
        Table table = repeat < 0
                ? Csv.TableAt(text, width) : Csv.TableAt(text, width, repeat);
        byte[] out_ = variant switch
        {
            Format.Dtx0 => Variants.WriteDtx0(table),
            Format.Dtx1 => Variants.WriteDtx1(table),
            Format.Dtx2 => Variants.WriteDtx2(table, Packer(packer, copies),
                    unit, ring),
            _ => throw new ArgumentException(
                    $"the variant is 0, 1 or 2, not {variant}"),
        };
        File.WriteAllBytes(named[1], out_);
        StringBuilder drawn = new();
        for (int i = 0; i < table.Columns; i++)
        {
            drawn.Append(i == 0 ? "" : ",").Append(table.Width(i));
        }
        string packing = variant == Format.Dtx2 ? $", k={unit}, N={ring}" : "";
        Console.WriteLine($"{named[0]} -> DTX{variant} {out_.Length} bytes,"
                + $" {table.Rows} rows, {table.Columns} columns, widths"
                + $" {drawn}, RR={table.Repeat}{packing}");
        return 0;
    }

    /// <summary>The widths -w gives, one a column.</summary>
    private static int[] Widths(string given)
    {
        string[] cell = given.Split(',');
        int[] width = new int[cell.Length];
        for (int i = 0; i < cell.Length; i++)
        {
            width[i] = Number(cell[i].Trim());
        }
        return width;
    }

    /// <summary>A DTX0 or DTX1 file into a DTX2 one.</summary>
    public static int Rewrite(string[] args)
    {
        if (Help.Among(args))
        {
            Console.Write(Help.Rewrite);
            return 0;
        }
        int unit = 1, ring = 960;
        string packer = "", copies = "";
        List<string> named = new();
        foreach (string arg in args)
        {
            if (arg.StartsWith("-k", StringComparison.Ordinal)) unit = Number(arg[2..]);
            else if (arg.StartsWith("-m", StringComparison.Ordinal)) ring = Number(arg[2..]);
            else if (arg.StartsWith("-copies", StringComparison.Ordinal)) copies = "-c" + arg[7..];
            else if (arg.StartsWith("-p", StringComparison.Ordinal)) packer = arg[2..];
            else if (arg.StartsWith('-'))
            {
                Console.Error.WriteLine($"dtx-rewrite does not read {arg}");
                return 2;
            }
            else named.Add(arg);
        }
        if (named.Count != 2)
        {
            Console.Error.Write(Help.Rewrite);
            return 2;
        }
        byte[] in_ = File.ReadAllBytes(named[0]);
        byte[] out_ = Variants.Dtx2From(in_, Packer(packer, copies), unit, ring);
        File.WriteAllBytes(named[1], out_);
        Header header = Format.ReadHeader(in_);
        Console.WriteLine($"DTX{header.Variant} {in_.Length} bytes -> DTX2"
                + $" {out_.Length} bytes, {header.Rows} rows,"
                + $" {header.Columns} columns, k={unit}, N={ring}");
        return 0;
    }

    /// <summary>A DTX file into a standalone 68000 image.</summary>
    public static int Package(string[] args)
    {
        if (Help.Among(args))
        {
            Console.Write(Help.Package);
            return 0;
        }
        string rmac = "";
        bool defines = false;
        List<string> named = new();
        foreach (string arg in args)
        {
            if (arg.StartsWith("-a", StringComparison.Ordinal)) rmac = arg[2..];
            else if (arg == "-s") defines = true;
            else if (arg.StartsWith('-'))
            {
                Console.Error.WriteLine($"dtx-package does not read {arg}");
                return 2;
            }
            else named.Add(arg);
        }
        if (named.Count != 2)
        {
            Console.Error.Write(Help.Package);
            return 2;
        }
        byte[] file = File.ReadAllBytes(named[0]);
        Header header = Format.ReadHeader(file);
        if (defines)
        {
            File.WriteAllText(named[1], Pack.Figures(file));
        }
        else if (rmac.Length == 0)
        {
            File.WriteAllBytes(named[1], Pack.Image(file));
        }
        else
        {
            File.WriteAllBytes(named[1], Pack.Combine(
                    Pack.Code(file, rmac, Pack.Templates()), file, header));
        }
        long bytes = new FileInfo(named[1]).Length;
        int state = header.Variant == Format.Dtx2
                ? Pack.PackedStateBytes(header, Pack.ReadPacked(file, header))
                : Pack.StateBytes(header);
        string what = defines ? "figures"
                : rmac.Length == 0 ? "image" : "image assembled";
        Console.WriteLine($"{named[0]} -> DTX{header.Variant} {what} {bytes}"
                + $" bytes, table {file.Length} bytes, {header.Rows} rows,"
                + $" {header.Columns} columns, state block {state} bytes");
        return 0;
    }

    /// <summary>
    /// The eight 68000 images the packager combines from, one file a build.
    ///
    /// <para>The table each is assembled from is made here rather than read:
    /// the code does not move with a table, and Pack.Blank zeroes the five
    /// fields the one used did give, so what comes out is a function of the
    /// template alone.</para>
    /// </summary>
    public static int Blobs(string[] args)
    {
        if (Help.Among(args))
        {
            Console.Write(Help.Blobs);
            return 0;
        }
        string rmac = "rmac", templates = Pack.Templates();
        List<string> into = new();
        foreach (string arg in args)
        {
            if (arg.StartsWith("-a", StringComparison.Ordinal)) rmac = arg[2..];
            else if (arg.StartsWith("-t", StringComparison.Ordinal)) templates = arg[2..];
            else if (arg.StartsWith('-'))
            {
                Console.Error.WriteLine($"dtx-blobs does not read {arg}");
                return 2;
            }
            else into.Add(arg);
        }
        if (into.Count == 0)
        {
            Console.Error.Write(Help.Blobs);
            return 2;
        }
        foreach (string at in into)
        {
            Directory.CreateDirectory(at);
        }
        foreach (Images.Build build in Images.Builds())
        {
            byte[] code = Pack.Code(Seed(build), rmac, templates);
            Pack.Blank(code);
            string name = Images.Name(build.Variant, build.Unit, build.Copies);
            foreach (string at in into)
            {
                File.WriteAllBytes(Path.Combine(at, name), code);
            }
            Console.WriteLine($"{name,-20} {code.Length,5} bytes");
        }
        return 0;
    }

    /// <summary>
    /// A table that fixes the figures one build's assembly reads. Any
    /// table of the kind does, since the code does not move with it: this
    /// one is 64 rows of two columns, at a ring of 960 where it is packed.
    /// </summary>
    private static byte[] Seed(Images.Build build)
    {
        int[] width = build.Variant == Format.Dtx2
                ? new[] { build.Unit, build.Unit } : new[] { 1, 2, 4 };
        const int rows = 64;
        byte[][] column = new byte[width.Length][];
        for (int i = 0; i < width.Length; i++)
        {
            column[i] = new byte[rows * width[i]];
        }
        Table table = Table.Of(rows, rows, width, column);
        return build.Variant switch
        {
            Format.Dtx0 => Variants.WriteDtx0(table),
            Format.Dtx1 => Variants.WriteDtx1(table),
            // The seed defines the build's own copies flag, since that fixes
            // which decoder the template is assembled with.
            _ => Variants.WriteDtx2(table, new Plain(build.Copies),
                    build.Unit, 960),
        };
    }

    /// <summary>
    /// A packer that does not pack: the column comes back as it is, and
    /// defines the build's own copies flag.
    ///
    /// <para>The assembler reads a data set's four stream offsets and
    /// nothing in the streams, so a data set whose streams are the column
    /// itself fixes every figure the build takes.</para>
    /// </summary>
    private sealed class Plain : IPacker
    {
        private readonly bool copies;

        internal Plain(bool copies) => this.copies = copies;

        public bool Copies => copies;

        public byte[] Pack(byte[] column, int unit, int ring)
        {
            byte[] set = new byte[28 + column.Length];
            set[0] = (byte)'S';
            set[1] = (byte)'4';
            set[2] = 7;
            set[3] = (byte)unit;
            Format.PutLong(set, 4, column.Length / unit);
            Format.PutLong(set, 8, 28);
            Format.PutLong(set, 12, 28 + column.Length);
            Format.PutLong(set, 16, 28 + column.Length);
            Format.PutLong(set, 24, ring / unit);
            column.CopyTo(set, 28);
            return set;
        }
    }
}
