namespace Dtx;

using System.Globalization;
using System.Text;

/// <summary>The three tools doc/tools.md defines, one method each.</summary>
public static class Tools
{
    /// <summary>What packs a column: the copy of ST4 in this repository, or
    /// the executable -p names.</summary>
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

    /// <summary>
    /// The tool that writes a table: dtx-write in out.
    ///
    /// <para>The table comes from the first file, a DTX file of any variant
    /// or comma separated text, and goes to the second as a DTX file of the
    /// variant -v gives, or as text where the name ends in .csv. The table
    /// is the same under every variant (R1.3), so one tool writes text as
    /// DTX, rewrites a DTX file at another variant, unit or ring, and reads
    /// a DTX file out as text. doc/tools.md, Write.</para>
    /// </summary>
    public static int Write(string[] args)
    {
        if (Help.Among(args))
        {
            Console.Write(Help.Write);
            return 0;
        }
        if (args.Length < 2)
        {
            Console.Error.Write(Help.Write);
            return 2;
        }
        string[] named = args[..2];
        int variant = -1, repeat = -1, unit = 1, ring = 960;
        string widths = "", packer = "", copies = "";
        foreach (string arg in args[2..])
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
            else
            {
                Console.Error.WriteLine($"dtx-write does not read {arg}");
                return 2;
            }
        }
        bool toText = named[1].EndsWith(".csv", StringComparison.Ordinal);
        if (toText && variant >= 0)
        {
            Console.Error.WriteLine($"-v{variant} names a DTX variant, and"
                    + $" {named[1]} is text");
            return 2;
        }
        byte[] in_ = File.ReadAllBytes(named[0]);
        Table table;
        if (IsDtx(in_))
        {
            if (widths.Length != 0)
            {
                Console.Error.WriteLine($"-w{widths} gives text its widths,"
                        + $" and {named[0]} is a DTX file with its own");
                return 2;
            }
            table = Variants.Read(in_);
            if (repeat >= 0)
            {
                table = Repeating(table, repeat);
            }
            if (variant < 0)
            {
                variant = Format.ReadHeader(in_).Variant;
            }
        }
        else
        {
            string text = Encoding.UTF8.GetString(in_);
            int[] width = widths.Length == 0 ? Csv.Widths(text) : Widths(widths);
            int given = repeat < 0 ? Csv.Repeat(text) : repeat;
            table = given < 0
                    ? Csv.TableAt(text, width) : Csv.TableAt(text, width, given);
            if (variant < 0)
            {
                variant = Format.Dtx0;
            }
        }
        byte[] out_ = toText ? Encoding.UTF8.GetBytes(Csv.Text(table)) : variant switch
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
        string packing = !toText && variant == Format.Dtx2
                ? $", k={unit}, N={ring}" : "";
        Console.WriteLine($"{named[0]} -> {(toText ? "text" : $"DTX{variant}")}"
                + $" {out_.Length} bytes, {table.Rows} rows, {table.Columns}"
                + $" columns, widths {drawn}, RR={table.Repeat}{packing}");
        return 0;
    }

    /// <summary>Whether file opens with DTX.</summary>
    private static bool IsDtx(byte[] file) =>
            file.Length >= Format.Magic.Length
                    && file.AsSpan(0, Format.Magic.Length).SequenceEqual(Format.Magic);

    /// <summary>table repeating at repeat, the rest as it is.</summary>
    private static Table Repeating(Table table, int repeat)
    {
        byte[][] column = new byte[table.Columns][];
        for (int i = 0; i < column.Length; i++)
        {
            column[i] = table.Column(i);
        }
        return Table.Of(table.Rows, repeat, table.Widths(), column);
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

    /// <summary>A DTX file into a standalone 68000 image.</summary>
    public static int Package(string[] args)
    {
        if (Help.Among(args))
        {
            Console.Write(Help.Package);
            return 0;
        }
        if (args.Length < 2)
        {
            Console.Error.Write(Help.Package);
            return 2;
        }
        string[] named = args[..2];
        string rmac = "";
        bool defines = false;
        foreach (string arg in args[2..])
        {
            if (arg.StartsWith("-a", StringComparison.Ordinal)) rmac = arg[2..];
            else if (arg == "-s") defines = true;
            else
            {
                Console.Error.WriteLine($"dtx-package does not read {arg}");
                return 2;
            }
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
    /// <para>The assembler reads only a data set's four stream offsets, not
    /// the streams, so a data set whose streams are the column itself fixes
    /// every figure the build takes.</para>
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
