namespace Dtx;

/// <summary>
/// The header every DTX variant shares, and the numbers the variants take.
///
/// <para>doc/SPEC.md section 1: DTX, the variant, R, C, RR, the width every
/// value takes, and one zero byte, so the payload begins on a long. Every
/// field of more than one byte is most significant byte first.</para>
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

    /// <summary>What a header runs to, under every variant and every C.</summary>
    public const int HeaderLength = 16;

    /// <summary>at up to the next multiple of to.</summary>
    public static int Align(int at, int to) => (at + to - 1) / to * to;

    /// <summary>The header at the start of a file.</summary>
    /// <exception cref="ArgumentException">where the file is short of a
    /// header, does not open with DTX, or breaks a bound R6 sets</exception>
    public static Header ReadHeader(byte[] file)
    {
        if (file.Length < HeaderLength)
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
        int width = file[14];
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
        if (width != 1 && width != 2 && width != 4)
        {
            throw new ArgumentException(
                    $"the width is 1, 2 or 4 bytes, not {width}");
        }
        return new Header(file[3], rows, columns, repeat, width);
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
/// What a file's header defines: the byte at offset 3, R, C, RR, and the
/// bytes every value takes.
/// </summary>
public sealed record Header(int Variant, int Rows, int Columns, int Repeat,
        int Width)
{
    /// <summary>What the header runs to, the payload's first byte.</summary>
    public int Length => Format.HeaderLength;

    /// <summary>A row's bytes: C values of W bytes.</summary>
    public int RowBytes => Columns * Width;
}
