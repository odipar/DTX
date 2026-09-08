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
    private static double Seconds(string copies)
    {
        if (copies.Length <= 2)
        {
            return 0;
        }
        try
        {
            return double.Parse(copies[2..], CultureInfo.InvariantCulture);
        }
        catch (Exception notANumber) when (notANumber is FormatException
                || notANumber is OverflowException)
        {
            throw new NotRead("-copies" + copies[2..]);
        }
    }

    /// <summary>
    /// The whole number an argument gives behind its two letter flag. An
    /// argument with anything else behind the flag is one the tool does not
    /// read.
    /// </summary>
    private static int Number(string arg)
    {
        try
        {
            return int.Parse(arg[2..].Trim(), CultureInfo.InvariantCulture);
        }
        catch (Exception notANumber) when (notANumber is FormatException
                || notANumber is OverflowException)
        {
            throw new NotRead(arg);
        }
    }

    /// <summary>
    /// A flag whose figure is not a number: an argument dtx-write does not
    /// read. Write gives the line on standard error and exits with 2.
    /// </summary>
    private sealed class NotRead : Exception
    {
        internal NotRead(string arg)
                : base($"dtx-write does not read {arg}")
        {
        }
    }

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
        try
        {
            return Writing(args);
        }
        catch (NotRead notRead)
        {
            Console.Error.WriteLine(notRead.Message);
            return 2;
        }
    }

    /// <summary>The tool's own work, which a flag it does not read
    /// stops.</summary>
    private static int Writing(string[] args)
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
        string width = "", packer = "", copies = "";
        foreach (string arg in args[2..])
        {
            if (arg.StartsWith("-v", StringComparison.Ordinal)) variant = Number(arg);
            else if (arg.StartsWith("-w", StringComparison.Ordinal)) width = arg;
            else if (arg.StartsWith("-r", StringComparison.Ordinal)) repeat = Number(arg);
            else if (arg.StartsWith("-k", StringComparison.Ordinal)) unit = Number(arg);
            else if (arg.StartsWith("-m", StringComparison.Ordinal)) ring = Number(arg);
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
            if (width.Length != 0)
            {
                Console.Error.WriteLine($"{width} gives text its width,"
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
            int given = width.Length == 0
                    ? Csv.Width(text) : Number(width);
            int at = repeat < 0 ? Csv.Repeat(text) : repeat;
            table = at < 0
                    ? Csv.TableAt(text, given) : Csv.TableAt(text, given, at);
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
        string packing = !toText && variant == Format.Dtx2
                ? $", k={unit}, N={ring}" : "";
        Console.WriteLine($"{named[0]} -> {(toText ? "text" : $"DTX{variant}")}"
                + $" {out_.Length} bytes, {table.Rows} rows, {table.Columns}"
                + $" columns, width {table.Width}, RR={table.Repeat}{packing}");
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
        return Table.Of(table.Rows, repeat, table.Width, column);
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
        string rmac = "";
        bool defines = false;
        var named = new List<string>();
        foreach (string arg in args)
        {
            // -aRMAC names the rmac to assemble with. A bare -a does not name
            // one, so it is a flag the tool does not read.
            if (arg.StartsWith("-a", StringComparison.Ordinal) && arg.Length > 2)
            {
                rmac = arg[2..];
            }
            else if (arg == "-s") defines = true;
            else if (arg.StartsWith("-", StringComparison.Ordinal))
            {
                Console.Error.WriteLine($"dtx-package does not read {arg}");
                return 2;
            }
            else named.Add(arg);
        }
        if (named.Count < 2)
        {
            Console.Error.Write(Help.Package);
            return 2;
        }
        // Every name but the last is a table, in the order the image lays
        // them out; the last is what the image is written to.
        string outName = named[^1];
        named.RemoveAt(named.Count - 1);
        if (defines && named.Count != 1)
        {
            Console.Error.WriteLine("dtx-package -s reads the figures of one"
                    + $" table, and {named.Count} were named");
            return 2;
        }
        var files = new List<byte[]>();
        foreach (string name in named)
        {
            files.Add(File.ReadAllBytes(name));
        }
        byte[] file = files[0];
        Header header = Format.ReadHeader(file);
        int[] headers = { 0 };
        if (defines)
        {
            File.WriteAllText(outName, Pack.Figures(file));
        }
        else if (rmac.Length == 0)
        {
            File.WriteAllBytes(outName, Pack.Image(files, out headers));
        }
        else
        {
            File.WriteAllBytes(outName, Pack.Combine(
                    Pack.Code(file, rmac, Pack.Templates()), files, header,
                    out headers));
        }
        long bytes = new FileInfo(outName).Length;
        string what = defines ? "figures"
                : rmac.Length == 0 ? "image" : "image assembled";
        Console.WriteLine($"{named[0]} -> DTX{header.Variant} {what} {bytes}"
                + $" bytes, table {file.Length} bytes, {header.Rows} rows,"
                + $" {header.Columns} columns, state block"
                + $" {StateOf(file, header)} bytes");
        // A caller hands init the header of the table to read (abi.md 2), so
        // the image says where each one stands.
        for (int i = 1; !defines && i < named.Count; i++)
        {
            Header its = Format.ReadHeader(files[i]);
            Console.WriteLine($"{named[i]} -> table {i + 1} at"
                    + $" image+{headers[i]}, {files[i].Length} bytes,"
                    + $" {its.Rows} rows, {its.Columns} columns, state block"
                    + $" {StateOf(files[i], its)} bytes");
        }
        if (!defines && named.Count > 1)
        {
            Console.WriteLine($"table 1 stands at image+{headers[0]}");
        }
        return 0;
    }

    /// <summary>The state block a table's reader takes, in bytes.</summary>
    private static int StateOf(byte[] file, Header header) =>
            header.Variant == Format.Dtx2
                    ? Pack.PackedStateBytes(header, Pack.ReadPacked(file, header))
                    : Pack.StateBytes(header);

    /// <summary>
    /// The twenty-two 68000 images the packager combines from, one file a
    /// build.
    ///
    /// <para>The table each is assembled from is made here rather than read:
    /// the code does not move with a table's shape, and Pack.Blank zeroes
    /// the six fields the one used did give, so what comes out is a
    /// function of the template alone.</para>
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
            string name = Images.Name(build.Variant, build.Width, build.Unit,
                    build.Copies);
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
    /// one is 64 rows of two columns, at the build's width, and at a ring of
    /// 960 where it is packed.
    /// </summary>
    private static byte[] Seed(Images.Build build)
    {
        const int rows = 64;
        const int columns = 2;
        byte[][] column = new byte[columns][];
        for (int i = 0; i < columns; i++)
        {
            column[i] = new byte[rows * build.Width];
        }
        Table table = Table.Of(rows, rows, build.Width, column);
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

        public byte[] Pack(byte[] column, int unit, int ring, int loop)
        {
            byte[] set = new byte[28 + column.Length];
            set[0] = (byte)'S';
            set[1] = (byte)'4';
            set[2] = 7;
            set[3] = (byte)unit;
            Format.PutLong(set, 4, column.Length);
            Format.PutLong(set, 8, 28);
            Format.PutLong(set, 12, 28 + column.Length);
            Format.PutLong(set, 16, 28 + column.Length);
            // Nothing decodes this set, so the loop is left out of it: byte
            // 20 reads $FFFFFFFF, the rewind field of a set the reader does
            // not replay (abi.md 4).
            Format.PutLong(set, 20, Nt4.Format.NoRewind);
            Format.PutLong(set, 24, ring / unit);
            column.CopyTo(set, 28);
            return set;
        }
    }
}
