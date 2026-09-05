namespace Dtx;

/// <summary>
/// The header every DTX variant shares, and the numbers the variants take.
///
/// <para>doc/SPEC.md section 1: DTX, the variant, R, C, RR, a width a
/// column, and zero bytes up to the next long, so the payload begins on
/// one. Every field of more than one byte is most significant byte
/// first.</para>
/// </summary>
public static class Format
{
    /// <summary>The variant byte of a table laid out row by row.</summary>
    public const int Dtx0 = 0;

    /// <summary>The variant byte of a table laid out column by column.</summary>
    public const int Dtx1 = 1;

    /// <summary>Column by column and packed.</summary>
    public const int Dtx2 = 2;

    /// <summary>The three bytes a file opens with.</summary>
    public static readonly byte[] Magic = { (byte)'D', (byte)'T', (byte)'X' };

    /// <summary>The largest ring a payload can define: N is two bytes.</summary>
    public const int MaxRing = 65535;

    /// <summary>
    /// The flags bit at payload byte 3 that marks every column was packed
    /// with copies from its own literal stream, R5.10.
    /// </summary>
    public const int CopiesFlag = 1;

    /// <summary>{@code at} up to the next multiple of {@code to}.</summary>
    public static int Align(int at, int to) => (at + to - 1) / to * to;

    /// <summary>What a header runs to: 14 plus C, up to the next long.</summary>
    public static int HeaderLength(int columns) => Align(14 + columns, 4);

    /// <summary>The header at the start of a file.</summary>
    /// <exception cref="ArgumentException">where the file is short of a
    /// header, does not open with DTX, or breaks a bound R6 sets</exception>
    public static Header ReadHeader(byte[] file)
    {
        if (file.Length < 16)
        {
            throw new ArgumentException(
                    $"a file of {file.Length} bytes does not contain a header");
        }
        for (int i = 0; i < Magic.Length; i++)
        {
            if (file[i] != Magic[i])
            {
                throw new ArgumentException("the file does not open with DTX");
            }
        }
        int rows = GetLong(file, 4);
        int columns = GetWord(file, 8);
        int repeat = GetLong(file, 10);
        if (rows < 1)
        {
            throw new ArgumentException($"R is 1 upward, not {rows}");
        }
        if (columns < 1 || columns > 256)
        {
            throw new ArgumentException($"C is 1 to 256, not {columns}");
        }
        if (repeat < 0 || repeat > rows)
        {
            throw new ArgumentException($"RR is 0 to R, not {repeat}");
        }
        int length = HeaderLength(columns);
        if (file.Length < length)
        {
            throw new ArgumentException($"a file of {file.Length} bytes is"
                    + $" short of a header of {length}");
        }
        int[] width = new int[columns];
        for (int i = 0; i < columns; i++)
        {
            width[i] = file[14 + i];
            if (width[i] != 1 && width[i] != 2 && width[i] != 4)
            {
                throw new ArgumentException(
                        $"column {i} is {width[i]} bytes wide, not 1, 2 or 4");
            }
        }
        return new Header(file[3], rows, repeat, width, length);
    }

    /// <summary>
    /// Where each column begins in a DTX1 payload. A column of two or four
    /// bytes begins on an even offset, so a wide value is read whole.
    /// </summary>
    public static int[] Offsets(int rows, int[] width)
    {
        int[] at = new int[width.Length];
        int next = 0;
        for (int i = 0; i < width.Length; i++)
        {
            next = Align(next, 2);
            at[i] = next;
            next += rows * width[i];
        }
        return at;
    }

    // The four byte order helpers. A DTX file and a 68000 image are both
    // most significant byte first.

    public static int GetWord(byte[] file, int at) =>
            file[at] << 8 | file[at + 1];

    public static int GetLong(byte[] file, int at) =>
            file[at] << 24 | file[at + 1] << 16 | file[at + 2] << 8 | file[at + 3];

    public static void PutWord(byte[] file, int at, int value)
    {
        file[at] = (byte)(value >> 8);
        file[at + 1] = (byte)value;
    }

    public static void PutLong(byte[] file, int at, int value)
    {
        file[at] = (byte)(value >> 24);
        file[at + 1] = (byte)(value >> 16);
        file[at + 2] = (byte)(value >> 8);
        file[at + 3] = (byte)value;
    }
}

/// <summary>
/// What a file's header defines. Length is the header's end, the payload's
/// first byte.
/// </summary>
public sealed record Header(int Variant, int Rows, int Repeat, int[] Width,
        int Length)
{
    /// <summary>C, the column count.</summary>
    public int Columns => Width.Length;

    /// <summary>A row's bytes, the sum of the widths.</summary>
    public int RowBytes
    {
        get
        {
            int out_ = 0;
            foreach (int w in Width)
            {
                out_ += w;
            }
            return out_;
        }
    }
}
