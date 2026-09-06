namespace Dtx;

using System.Diagnostics;
using System.Text;

/// <summary>
/// Combines a DTX table with the 68000 image that reads it.
///
/// <para>One build is one code, so packaging combines rather than
/// assembles: it takes the image for the build the table needs, writes the
/// six fields the table gives into the format block, and appends the
/// column table and the table's bytes. Code assembles a template with rmac,
/// which is the one step an assembler is needed for. doc/abi.md defines the
/// image, the format block and the column table.</para>
/// </summary>
public static class Pack
{
    /// <summary>The state block's fields, from doc/abi.md 3.</summary>
    public const int Turn = 0;
    public const int Park = 4;
    public const int Pointer = 8;

    /// <summary>The format block: where it stands, behind the four slots;
    /// what it runs to, so where the bodies begin; and its fields.</summary>
    public const int FormatAt = 16;
    public const int FormatSize = 28;
    public const int StateAt = 4;
    public const int TableAt = 8;
    public const int RowBytesAt = 12;
    public const int PeriodAt = 14;
    public const int RingAt = 16;
    public const int UnitAt = 18;
    public const int WidthAt = 19;
    public const int ColumnsAt = 20;
    public const int StrideAt = 24;

    /// <summary>What one stream record runs to, one a column under DTX2.</summary>
    public const int Stream = 16;

    /// <summary>What one decoder state takes, and the copy of it a replay
    /// puts away.</summary>
    public const int State = 32;

    /// <summary>What a packed reader's state block contains before its
    /// decoder states.</summary>
    public const int PackedHead = 52;

    /// <summary>The state block DTX0 and DTX1 take: the head, and one pointer.</summary>
    public const int Plain = Pointer + 4;

    /// <summary>
    /// What a DTX2 payload defines: the ring, the unit, whether its columns
    /// contain copies from the literal stream, and where each data set begins.
    /// </summary>
    public readonly record struct Packed(int Ring, int Unit, bool Copies,
            bool Replayed, int[] At);

    /// <summary>
    /// What a DTX2 payload gives, SPEC.md 2.3.
    ///
    /// <para>It checks the data sets against it. Every set opens with
    /// <c>$53 $34 $07 k</c>, so one compare against the payload's own k
    /// checks ST4's signature, its format version and R5.2 at once.</para>
    /// </summary>
    /// <exception cref="ArgumentException">where a data set defines another
    /// version or another unit than the payload does</exception>
    public static Packed ReadPacked(byte[] file, Header header)
    {
        int payload = header.Length;
        int unit = file[payload + 2];
        int[] at = new int[header.Columns];
        int signature = 0x53340700 + unit;
        bool replayed = false;
        for (int i = 0; i < at.Length; i++)
        {
            at[i] = Format.GetLong(file, payload + 4 + 4 * i);
            // Byte 20 of a data set gives the unit its loop begins at, or
            // $FFFFFFFF where its end marker loops it. A set that records
            // one is replayed by the reader (abi.md 4), and every set of a
            // payload takes the same form: they are one table's columns, so
            // one loop and one length.
            replayed |= Format.GetLong(file, payload + at[i] + 20) != -1;
            int read = Format.GetLong(file, payload + at[i]);
            if (read != signature)
            {
                throw new ArgumentException(
                        $"column {i}'s data set opens {read:X8} and the"
                        + $" payload defines {signature:X8}: an ST4 data set"
                        + " opens with S4, the format version 7 and the"
                        + " payload's own k");
            }
        }
        return new Packed(Format.GetWord(file, payload), unit,
                (file[payload + 3] & Format.CopiesFlag) != 0, replayed, at);
    }

    /// <summary>Where the decoder states stand in the state block, doc/abi.md 3.</summary>
    public static int Decoders() => PackedHead;

    /// <summary>Where the rings stand in the state block.</summary>
    public static int Ring(Header header) => Decoders() + 32 * header.Columns;

    /// <summary>
    /// The state block a plain reader of this table takes, in bytes.
    ///
    /// <para>The same under DTX0 and DTX1, and the same at every C: every
    /// column is one width, so one pointer walks them all.</para>
    /// </summary>
    public static int StateBytes(Header header) => Plain;

    /// <summary>
    /// The state block a packaged DTX2 reader takes, in bytes. A replayed
    /// payload takes a second decoder state a column behind the rings, where
    /// the reader puts the registers away at the row its loop begins
    /// (abi.md 4).
    /// </summary>
    public static int PackedStateBytes(Header header, Packed given) =>
            Ring(header) + given.Ring * header.Columns
                    + (given.Replayed ? State * header.Columns : 0);

    /// <summary>
    /// The stride from one column's value to the next, in the row an advance
    /// points at: the width under DTX0, where a row's values stand one after
    /// another; the length of a column under DTX1, where the columns lie at
    /// one stride; and N under DTX2, where every column has a ring of that
    /// size. DTX_metadata gives it, out of the format block.
    /// </summary>
    public static int Stride(Header header, Packed given) => header.Variant switch
    {
        Format.Dtx0 => header.Width,
        Format.Dtx1 => Variants.Stride(header.Rows, header.Width),
        _ => given.Ring,
    };

    /// <summary>
    /// The period a table of these takes, the smallest that meets every rule
    /// of doc/abi.md 4.
    /// </summary>
    /// <exception cref="ArgumentException">naming the rule none meets</exception>
    public static int Period(Header header, Packed given)
    {
        int rows = header.Rows;
        int width = header.Width;
        int n = given.Ring;
        int k = given.Unit;
        int columns = header.Columns;
        if ((long)rows * width % k != 0)
        {
            throw new ArgumentException($"a column is {rows} times {width}"
                    + $" bytes, which does not divide by k of {k}");
        }
        // A replayed set puts the decoder's registers away where its loop
        // begins and back at the column's end, and a refill takes a whole
        // period, so both rows fall on a period (abi.md 4).
        int repeat = header.Repeat;
        bool lands = !given.Replayed;
        for (int p = columns; p <= rows; p++)
        {
            long budget = (long)p * width / k;
            if (n < 2 * p * width)
            {
                break;
            }
            if (n % (p * width) == 0 && (long)p * width % k == 0
                    && budget >= 1 && budget <= 65535
                    && (lands || (repeat % p == 0 && (rows - repeat) % p == 0)))
            {
                return p;
            }
        }
        throw new ArgumentException($"no period from C of {columns} to R of"
                + $" {rows} meets N of {n} and k of {k}: N divides by P times"
                + " the width, is at least twice that, the budget is a whole"
                + " number of units"
                + (lands ? "" : $", and RR of {repeat} and the rows from it"
                        + " to R divide by P, since these data sets are"
                        + " replayed"));
    }

    /// <summary>
    /// The column table behind the image's code: under DTX2 one stream
    /// record a column, 16 bytes at a stride of 16, and nothing else.
    ///
    /// <para>A record gives where the column's four ST4 streams begin, from
    /// the payload. Its ring and its decoder state are strides rather than
    /// fields: every ring is N bytes and every decoder state 32, so column
    /// i's stand i strides past column 0's.</para>
    /// </summary>
    public static byte[] ColumnTable(byte[] file, Header header)
    {
        if (header.Variant != Format.Dtx2)
        {
            // Every column is one width, so a pointer and a stride walk them
            // all: what a plain read takes is arithmetic on R, C and the
            // width, and no column table is written.
            return Array.Empty<byte>();
        }
        Packed given = ReadPacked(file, header);
        int payload = header.Length;
        byte[] out_ = new byte[Stream * header.Columns];
        for (int i = 0; i < header.Columns; i++)
        {
            int rec = Stream * i;
            int set = given.At[i];
            Format.PutLong(out_, rec, set + 28);
            Format.PutLong(out_, rec + 4,
                    set + Format.GetLong(file, payload + set + 8));
            Format.PutLong(out_, rec + 8,
                    set + Format.GetLong(file, payload + set + 12));
            Format.PutLong(out_, rec + 12,
                    set + Format.GetLong(file, payload + set + 16));
        }
        return out_;
    }

    /// <summary>
    /// One image: this code, the column table, the table's bytes, and the
    /// format block written to define the three.
    ///
    /// <para>The code is the same bytes any table that follows it, so what a
    /// combine writes is the six fields the table gives. It checks the
    /// three it cannot write: the variant, the width, and under DTX2 the
    /// unit the decoder built into the code decodes at.</para>
    /// </summary>
    public static byte[] Combine(byte[] code, byte[] file, Header header)
    {
        if (code.Length < FormatAt + FormatSize
                || code[FormatAt] != 'D' || code[FormatAt + 1] != 'T'
                || code[FormatAt + 2] != 'X')
        {
            throw new InvalidOperationException(
                    "the code does not contain a format block at +16");
        }
        if (code[FormatAt + 3] != header.Variant)
        {
            throw new InvalidOperationException(
                    $"the code reads DTX{code[FormatAt + 3]} and the table is"
                    + $" DTX{header.Variant}");
        }
        // The code ends where the format block puts the column table:
        // the two match, or the image reads its own last instruction as a
        // column.
        int columns = Format.GetLong(code, FormatAt + ColumnsAt);
        if (columns != code.Length)
        {
            throw new InvalidOperationException($"the code runs to"
                    + $" {code.Length} bytes and the format block puts the"
                    + $" column table at {columns}: it would not land there");
        }
        Packed given = header.Variant == Format.Dtx2
                ? ReadPacked(file, header)
                : new Packed(0, 0, false, false, Array.Empty<int>());
        if (code[FormatAt + UnitAt] != given.Unit)
        {
            throw new InvalidOperationException($"the code decodes at a unit"
                    + $" of {code[FormatAt + UnitAt]} and the table was packed"
                    + $" at {given.Unit}");
        }
        if (header.Variant != Format.Dtx0
                && code[FormatAt + WidthAt] != header.Width)
        {
            throw new InvalidOperationException($"the code reads values of"
                    + $" {code[FormatAt + WidthAt]} bytes and the table's are"
                    + $" {header.Width}");
        }
        byte[] entries = ColumnTable(file, header);
        byte[] out_ = new byte[code.Length + entries.Length + file.Length];
        code.CopyTo(out_, 0);
        entries.CopyTo(out_, code.Length);
        file.CopyTo(out_, code.Length + entries.Length);
        Format.PutLong(out_, FormatAt + StateAt, header.Variant == Format.Dtx2
                ? PackedStateBytes(header, given) : StateBytes(header));
        // The table stands behind both, and only the packager has the
        // figure: the column table's size moves with C, so the assembler
        // could not have worked it out.
        Format.PutLong(out_, FormatAt + TableAt, code.Length + entries.Length);
        Format.PutWord(out_, FormatAt + RowBytesAt, header.RowBytes);
        Format.PutWord(out_, FormatAt + PeriodAt,
                header.Variant == Format.Dtx2 ? Period(header, given) : 1);
        Format.PutWord(out_, FormatAt + RingAt, given.Ring);
        Format.PutLong(out_, FormatAt + StrideAt, Stride(header, given));
        return out_;
    }

    /// <summary>
    /// The image for this table, from the code this build contains.
    ///
    /// <para>Which of the twenty-two it takes is the file's to define: the
    /// variant, the width every value takes, and under DTX2 the unit its
    /// data sets are packed at and whether they contain copies from the
    /// literal stream (R5.10). No word from a caller enters it, so no word
    /// can differ from the bytes.</para>
    /// </summary>
    public static byte[] Image(byte[] file)
    {
        Header header = Format.ReadHeader(file);
        if (header.Variant != Format.Dtx2)
        {
            return Combine(Images.Code(header.Variant, header.Width, 0, false),
                    file, header);
        }
        Packed given = ReadPacked(file, header);
        return Combine(Images.Code(Format.Dtx2, header.Width, given.Unit,
                given.Copies), file, header);
    }

    /// <summary>One NAME equ VALUE line.</summary>
    private static string Equ(string name, int value) =>
            name + (name.Length < 8 ? "\t" : "") + "\tequ\t" + value + "\n";

    /// <summary>
    /// What one table gives, as a template reads it: the equates, and no
    /// instruction. Every figure a loop counts with reaches the code
    /// at run time instead, out of the table's own header (doc/tools.md).
    /// </summary>
    public static string Figures(byte[] file)
    {
        Header header = Format.ReadHeader(file);
        int variant = header.Variant;
        if (variant != Format.Dtx0 && variant != Format.Dtx1
                && variant != Format.Dtx2)
        {
            throw new ArgumentException(
                    $"the variant is 0, 1 or 2, not {variant}");
        }
        Packed given = new(0, 0, false, false, Array.Empty<int>());
        int period = 1;
        int state = StateBytes(header);
        if (variant == Format.Dtx2)
        {
            given = ReadPacked(file, header);
            period = Period(header, given);
            state = PackedStateBytes(header, given);
        }
        StringBuilder out_ = new();
        out_.Append("; What org.dtx.Packager writes of one table, for"
                        + $" 68k/DTX{variant}.S to read.\n")
                .Append($"; DTX{variant}, R = {header.Rows}, C ="
                        + $" {header.Columns}, W = {header.Width}, RR ="
                        + $" {header.Repeat}\n")
                .Append("; Every instruction is the template's; nothing here"
                        + " is one.\n\n")
                .Append("; The state block, doc/abi.md 3.\n")
                .Append(Equ("DTX_TURN", Turn))
                .Append(Equ("DTX_PARK", Park))
                .Append(Equ("DTX_POINTER", Pointer))
                .Append("\n; What the code takes at assembly time.\n")
                .Append(Equ("DTX_WIDTH", header.Width));
        if (variant == Format.Dtx2)
        {
            out_.Append(Equ("ST4_UNIT", given.Unit));
        }
        out_.Append("\n; The rest of what the table gives. A combine writes"
                        + " these into the format\n; block and the code reads"
                        + " them from there (doc/abi.md 1), so they stand\n;"
                        + " here for a caller reading the figures rather than"
                        + " for the assembler.\n")
                .Append(Equ("DTX_ROWBYTES", header.RowBytes))
                .Append(Equ("DTX_STATE", state));
        if (variant == Format.Dtx2)
        {
            out_.Append(Equ("DTX_PERIOD", period))
                    .Append(Equ("DTX_N", given.Ring));
            // The payload defines whether its columns contain copies (R5.10), so
            // the decoder built for them is fixed by the file. That build
            // writes the reach into two of its own instructions, and a 68030
            // caller flushes the instruction cache after every call that
            // seeds a decoder.
            if (given.Copies)
            {
                out_.Append(Equ("ST4_WINDOW", 1))
                        .Append("; the columns were packed with st4 -c, so"
                                + " the decoder takes the copy code\n");
            }
        }
        return out_.ToString();
    }

    /// <summary>Where the templates and the carried decoder stand.</summary>
    public static string Templates()
    {
        string? named = Environment.GetEnvironmentVariable("DTX_68K");
        return string.IsNullOrEmpty(named) ? "68k" : named;
    }

    /// <summary>
    /// rmac's assembly of the variant's template for this table, the code
    /// alone. This is the one step an assembler is needed for.
    /// </summary>
    public static byte[] Code(byte[] file, string rmac, string templates)
    {
        Header header = Format.ReadHeader(file);
        string work = Directory.CreateTempSubdirectory("dtx68").FullName;
        try
        {
            File.WriteAllText(Path.Combine(work, "DTX_table.i"), Figures(file));
            string out_ = Path.Combine(work, "image.bin");
            ProcessStartInfo start = new(rmac)
            {
                RedirectStandardOutput = true,
                RedirectStandardError = true,
            };
            foreach (string one in new[]
            {
                "-m68000", "-fr", "+o3", "-i" + work, "-i" + templates,
                "-o", out_, Path.Combine(templates, $"DTX{header.Variant}.S"),
            })
            {
                start.ArgumentList.Add(one);
            }
            using Process run = Process.Start(start)
                    ?? throw new InvalidOperationException($"{rmac} did not start");
            // Both pipes are read at once: a child that fills the one the
            // parent is not reading blocks on it, and the parent blocks on
            // the other.
            Task<string> error = run.StandardError.ReadToEndAsync();
            string given = run.StandardOutput.ReadToEnd() + error.Result;
            run.WaitForExit();
            if (run.ExitCode != 0 || !File.Exists(out_))
            {
                throw new InvalidOperationException($"{rmac} gave {given.Trim()}");
            }
            return File.ReadAllBytes(out_);
        }
        finally
        {
            Directory.Delete(work, true);
        }
    }

    /// <summary>
    /// The six fields a combine writes, zeroed.
    ///
    /// <para>Built code does not define a table. The assembler read one to
    /// build it, and what it read stands in the format block: zeroing those
    /// six makes the file a function of the template alone, and makes code
    /// shipped without a combine read a state block of zero bytes rather
    /// than some other table's.</para>
    /// </summary>
    public static void Blank(byte[] code)
    {
        Format.PutLong(code, FormatAt + StateAt, 0);
        Format.PutLong(code, FormatAt + TableAt, 0);
        Format.PutWord(code, FormatAt + RowBytesAt, 0);
        Format.PutWord(code, FormatAt + PeriodAt, 0);
        Format.PutWord(code, FormatAt + RingAt, 0);
        Format.PutLong(code, FormatAt + StrideAt, 0);
    }
}
