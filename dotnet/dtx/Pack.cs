namespace Dtx;

using System.Diagnostics;
using System.Text;

/// <summary>
/// Combines a DTX table with the 68000 image that reads it.
///
/// <para>One variant is one code, so nothing here assembles: it takes the
/// image for the build the table asks for, writes the five fields the table
/// settles into the format block, and appends the column table and the
/// table's bytes. doc/abi.md states the image, the format block and the
/// column table.</para>
/// </summary>
public static class Pack
{
    /// <summary>The state block's fields, from doc/abi.md 3.</summary>
    public const int Row = 0;
    public const int Turn = 4;
    public const int Decoded = 8;
    public const int Park = 12;
    public const int Cursor = 24;

    /// <summary>The format block: what it runs to, where it stands, its fields.</summary>
    public const int FormatSize = 24;
    public const int FormatAt = 24;
    public const int StateAt = 4;
    public const int TableAt = 8;
    public const int RowBytesAt = 12;
    public const int PeriodAt = 14;
    public const int RingAt = 16;
    public const int UnitAt = 18;
    public const int ColumnsAt = 20;

    /// <summary>The column table: its header, entries and stream records.</summary>
    public const int Entry = 4;
    public const int Entries = 32;
    public const int Stream = 32;
    public const int PackedHead = 80;

    /// <summary>
    /// What a DTX2 payload states: the ring, the unit, whether its columns
    /// hold copies from the literal stream, and where each data set begins.
    /// </summary>
    public readonly record struct Packed(int Ring, int Unit, bool Copies, int[] At);

    /// <summary>
    /// What a DTX2 payload gives, SPEC.md 2.3.
    ///
    /// <para>It checks the data sets against it. Every set opens with
    /// <c>$53 $34 $07 k</c>, so one compare against the payload's own k
    /// holds ST4's signature, its format version and R5.2 at once.</para>
    /// </summary>
    /// <exception cref="ArgumentException">where a data set states another
    /// version or another unit than the payload does</exception>
    public static Packed ReadPacked(byte[] file, Header header)
    {
        int payload = header.Length;
        int unit = file[payload + 2];
        int[] at = new int[header.Columns];
        int signature = 0x53340700 + unit;
        for (int i = 0; i < at.Length; i++)
        {
            at[i] = Format.GetLong(file, payload + 4 + 4 * i);
            int said = Format.GetLong(file, payload + at[i]);
            if (said != signature)
            {
                throw new ArgumentException(
                        $"column {i}'s data set opens {said:X8} and the"
                        + $" payload states {signature:X8}: an ST4 data set"
                        + " opens with S4, the format version 7 and the"
                        + " payload's own k");
            }
        }
        return new Packed(Format.GetWord(file, payload), unit,
                (file[payload + 3] & Format.CopiesFlag) != 0, at);
    }

    /// <summary>The widths a table of these holds, in the order 1, 2, 4.</summary>
    public static int[] Classes(int[] width)
    {
        List<int> out_ = new();
        foreach (int w in new[] { 1, 2, 4 })
        {
            foreach (int held in width)
            {
                if (held == w)
                {
                    out_.Add(w);
                    break;
                }
            }
        }
        return out_.ToArray();
    }

    /// <summary>How many columns each width class holds.</summary>
    public static int[] Counts(int[] width)
    {
        int[] out_ = new int[3];
        foreach (int w in width)
        {
            out_[w == 1 ? 0 : w == 2 ? 1 : 2]++;
        }
        return out_;
    }

    /// <summary>The column of w bytes that stands first, or -1.</summary>
    private static int First(int[] width, int w)
    {
        for (int i = 0; i < width.Length; i++)
        {
            if (width[i] == w)
            {
                return i;
            }
        }
        return -1;
    }

    /// <summary>Where each width class's first column begins in the payload.</summary>
    public static int[] Bases(Header header)
    {
        int[] at = Format.Offsets(header.Rows, header.Width);
        int[] out_ = new int[3];
        for (int c = 0; c < 3; c++)
        {
            int i = First(header.Width, c == 0 ? 1 : c == 1 ? 2 : 4);
            out_[c] = i < 0 ? 0 : at[i];
        }
        return out_;
    }

    /// <summary>Where the decoder states stand in the state block, doc/abi.md 3.</summary>
    public static int Decoders(Header header) => PackedHead;

    /// <summary>Where the rings stand in the state block.</summary>
    public static int Ring(Header header) => Decoders(header) + 32 * header.Columns;

    /// <summary>Where a width class's ring begins in the state block.</summary>
    public static int RingOf(Header header, int c, int n)
    {
        int i = First(header.Width, c == 0 ? 1 : c == 1 ? 2 : 4);
        return i < 0 ? Ring(header) : Ring(header) + i * n;
    }

    /// <summary>The state block a plain reader of this table takes.</summary>
    public static int StateBytes(Header header)
    {
        if (header.Variant == Format.Dtx0)
        {
            return Cursor + 4;
        }
        // DTX1 holds three cursors and the three places their classes begin,
        // at any widths the table states, so its block does not move with C.
        return header.Variant == Format.Dtx1
                ? 48 : Cursor + 4 * Classes(header.Width).Length;
    }

    /// <summary>The state block a packaged DTX2 reader takes.</summary>
    public static int PackedStateBytes(Header header, Packed given) =>
            Ring(header) + given.Ring * header.Columns;

    /// <summary>
    /// The period a table of these takes, the smallest that meets every rule
    /// of doc/abi.md 4.
    /// </summary>
    /// <exception cref="ArgumentException">naming the rule none meets</exception>
    public static int Period(Header header, Packed given)
    {
        int[] width = header.Width;
        int rows = header.Rows;
        int n = given.Ring;
        int k = given.Unit;
        int columns = width.Length;
        int widest = 0;
        foreach (int w in width)
        {
            widest = Math.Max(widest, w);
        }
        if ((long)(columns - 1) * n > 32767)
        {
            throw new ArgumentException($"a read reaches column {columns - 1}"
                    + $" at {(long)(columns - 1) * n}, past the 32767 a 68000"
                    + $" displacement holds: C is at most {32767 / n + 1} at"
                    + $" N of {n}");
        }
        if (k == 0 || rows % k != 0)
        {
            throw new ArgumentException(
                    $"R is {rows}, which does not divide by k of {k}");
        }
        for (int p = columns; p <= rows; p++)
        {
            if (n < 2 * p * widest)
            {
                break;
            }
            bool holds = true;
            foreach (int w in width)
            {
                long budget = (long)p * w / k;
                holds &= n % (p * w) == 0 && (long)p * w % k == 0
                        && budget >= 1 && budget <= 65535;
            }
            if (holds)
            {
                return p;
            }
        }
        throw new ArgumentException($"no period from C of {columns} to R of"
                + $" {rows} holds N of {n} and k of {k}: N divides by P times"
                + " every width, is at least twice that, and every budget is"
                + " a whole number of units");
    }

    /// <summary>log2 of of where it is a power of two, or -1.</summary>
    private static int Shift(int of)
    {
        for (int s = 0; s < 32; s++)
        {
            if (1 << s == of)
            {
                return s;
            }
        }
        return -1;
    }

    /// <summary>
    /// The table the image holds behind its code: a header, one read entry a
    /// column grouped by width so each of a read's three loops walks a run of
    /// them, and under DTX2 one stream record a column.
    ///
    /// <para>DTX0 has none: a DTX0 row is one run of bytes, so no loop walks
    /// a column.</para>
    /// </summary>
    public static byte[] ColumnTable(byte[] file, Header header)
    {
        if (header.Variant == Format.Dtx0)
        {
            return Array.Empty<byte>();
        }
        int[] width = header.Width;
        int[] at = Format.Offsets(header.Rows, width);
        int[] count = Counts(width);
        int[] base_ = Bases(header);
        bool packed = header.Variant == Format.Dtx2;
        Packed given = packed
                ? ReadPacked(file, header)
                : new Packed(0, 0, false, Array.Empty<int>());
        int period = packed ? Period(header, given) : 1;
        int n = given.Ring;
        int records = Entries + Entry * width.Length;
        byte[] out_ = new byte[records + (packed ? Stream * width.Length : 0)];
        for (int c = 0; c < 3; c++)
        {
            Format.PutWord(out_, 2 * c, count[c]);
            // Under DTX2 a class begins at its first column's ring in the
            // state block, not at its column's place in the payload.
            Format.PutLong(out_, 8 + 4 * c,
                    packed ? RingOf(header, c, n) : base_[c]);
        }
        Format.PutWord(out_, 6, header.RowBytes);
        Format.PutWord(out_, 20, width.Length);
        Format.PutWord(out_, 22, period);
        Format.PutLong(out_, 24, records);
        Format.PutLong(out_, 28, n);
        int wrote = Entries;
        foreach (int w in new[] { 1, 2, 4 })
        {
            int begins = First(width, w);
            int row = 0;
            for (int i = 0; i < width.Length; i++)
            {
                if (width[i] == w)
                {
                    Format.PutWord(out_, wrote, packed
                            ? (i - begins) * n
                            : at[i] - base_[w == 1 ? 0 : w == 2 ? 1 : 2]);
                    Format.PutWord(out_, wrote + 2, row);
                    wrote += Entry;
                }
                row += width[i];
            }
        }
        if (packed)
        {
            int payload = header.Length;
            int kshift = Shift(given.Unit);
            for (int i = 0; i < width.Length; i++)
            {
                int rec = records + Stream * i;
                int set = given.At[i];
                Format.PutLong(out_, rec, set + 28);
                Format.PutLong(out_, rec + 4,
                        set + Format.GetLong(file, payload + set + 8));
                Format.PutLong(out_, rec + 8,
                        set + Format.GetLong(file, payload + set + 12));
                Format.PutLong(out_, rec + 12,
                        set + Format.GetLong(file, payload + set + 16));
                Format.PutLong(out_, rec + 16, Ring(header) + i * n);
                Format.PutLong(out_, rec + 20, Decoders(header) + 32 * i);
                Format.PutWord(out_, rec + 24, Shift(width[i]));
                Format.PutWord(out_, rec + 26, kshift);
            }
        }
        return out_;
    }

    /// <summary>
    /// One image: this code, the column table, the table's bytes, and the
    /// format block written to state the three.
    ///
    /// <para>The code is the same bytes any table that follows it, so what a
    /// combine writes is the five fields the table settles. It checks the two
    /// it cannot write: the variant, and under DTX2 the unit the decoder
    /// built into the code decodes at.</para>
    /// </summary>
    public static byte[] Combine(byte[] code, byte[] file, Header header)
    {
        if (code.Length < FormatAt + FormatSize
                || code[FormatAt] != 'D' || code[FormatAt + 1] != 'T'
                || code[FormatAt + 2] != 'X')
        {
            throw new InvalidOperationException(
                    "the code opens with no format block");
        }
        if (code[FormatAt + 3] != header.Variant)
        {
            throw new InvalidOperationException(
                    $"the code reads DTX{code[FormatAt + 3]} and the table is"
                    + $" DTX{header.Variant}");
        }
        // The code ends where the format block states the column table begins:
        // the two agree, or the image reads its own last instruction as a
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
                : new Packed(0, 0, false, Array.Empty<int>());
        if (code[FormatAt + UnitAt] != given.Unit)
        {
            throw new InvalidOperationException($"the code decodes at a unit"
                    + $" of {code[FormatAt + UnitAt]} and the table was packed"
                    + $" at {given.Unit}");
        }
        byte[] entries = ColumnTable(file, header);
        byte[] out_ = new byte[code.Length + entries.Length + file.Length];
        code.CopyTo(out_, 0);
        entries.CopyTo(out_, code.Length);
        file.CopyTo(out_, code.Length + entries.Length);
        Format.PutLong(out_, FormatAt + StateAt, header.Variant == Format.Dtx2
                ? PackedStateBytes(header, given) : StateBytes(header));
        // The table stands behind both, and only the packager holds the
        // figure: the column table's size moves with C, so the assembler
        // could not have worked it out.
        Format.PutLong(out_, FormatAt + TableAt, code.Length + entries.Length);
        Format.PutWord(out_, FormatAt + RowBytesAt, header.RowBytes);
        Format.PutWord(out_, FormatAt + PeriodAt,
                header.Variant == Format.Dtx2 ? Period(header, given) : 1);
        Format.PutWord(out_, FormatAt + RingAt, given.Ring);
        return out_;
    }

    /// <summary>
    /// The image for this table, from the code this build holds.
    ///
    /// <para>Which of the eight it takes is the file's to state: the variant,
    /// and under DTX2 the unit its data sets are packed at and whether they
    /// hold copies from the literal stream (R5.10). No word from a caller
    /// enters it, so no word can disagree with the bytes.</para>
    /// </summary>
    public static byte[] Image(byte[] file)
    {
        Header header = Format.ReadHeader(file);
        if (header.Variant != Format.Dtx2)
        {
            return Combine(Images.Code(header.Variant, 0, false), file, header);
        }
        Packed given = ReadPacked(file, header);
        return Combine(Images.Code(Format.Dtx2, given.Unit, given.Copies),
                file, header);
    }

    /// <summary>One NAME equ VALUE line.</summary>
    private static string Equ(string name, int value) =>
            name + (name.Length < 8 ? "\t" : "") + "\tequ\t" + value + "\n";

    /// <summary>
    /// What one table settles, as a template reads it: the equates, and no
    /// instruction. Every figure a loop counts with reaches the code
    /// at run time instead, out of the table's own header and the column
    /// table (doc/tools.md).
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
        Packed given = new(0, 0, false, Array.Empty<int>());
        int period = 1;
        int state = StateBytes(header);
        if (variant == Format.Dtx2)
        {
            given = ReadPacked(file, header);
            period = Period(header, given);
            state = PackedStateBytes(header, given);
        }
        StringBuilder out_ = new();
        out_.Append("; What org.dtx.Packager states of one table, for"
                        + " 68k/DTX.S to read.\n")
                .Append($"; DTX{variant}, R = {header.Rows}, C ="
                        + $" {header.Columns}, RR = {header.Repeat}\n")
                .Append("; Every instruction is the template's; nothing here"
                        + " is one.\n\n")
                .Append("; The state block, doc/abi.md 3.\n")
                .Append(Equ("DTX_ROW", Row))
                .Append(Equ("DTX_TURN", Turn))
                .Append(Equ("DTX_DECODED", Decoded))
                .Append(Equ("DTX_PARK", Park))
                .Append(Equ("DTX_CURSOR", Cursor))
                .Append('\n')
                .Append(Equ("DTX_ROWBYTES", header.RowBytes))
                .Append(Equ("DTX_STATE", state));
        if (variant == Format.Dtx2)
        {
            out_.Append(Equ("DTX_PERIOD", period))
                    .Append(Equ("DTX_N", given.Ring))
                    .Append(Equ("ST4_UNIT", given.Unit));
            // The payload states whether its columns hold copies (R5.10), so
            // the decoder built for them is settled by the file. That build
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
            string said = run.StandardOutput.ReadToEnd()
                    + run.StandardError.ReadToEnd();
            run.WaitForExit();
            if (run.ExitCode != 0 || !File.Exists(out_))
            {
                throw new InvalidOperationException($"{rmac} gave {said.Trim()}");
            }
            return File.ReadAllBytes(out_);
        }
        finally
        {
            Directory.Delete(work, true);
        }
    }

    /// <summary>
    /// The five fields a combine writes, zeroed.
    ///
    /// <para>Built code states no table. The assembler read one to build it,
    /// and what it read stands in the format block: zeroing those five is
    /// what makes the file a function of the template alone, and what makes
    /// code shipped without a combine read a state block of zero bytes rather
    /// than some other table's.</para>
    /// </summary>
    public static void Blank(byte[] code)
    {
        Format.PutLong(code, FormatAt + StateAt, 0);
        Format.PutLong(code, FormatAt + TableAt, 0);
        Format.PutWord(code, FormatAt + RowBytesAt, 0);
        Format.PutWord(code, FormatAt + PeriodAt, 0);
        Format.PutWord(code, FormatAt + RingAt, 0);
    }
}
