namespace Dtx;

/// <summary>
/// A table in memory: R rows, C columns, and a row RR it repeats to. Every
/// variant holds this same table (R1.3), so a variant reads into one and
/// writes out of one.
///
/// <para>The values are held column by column, R times a column's width
/// bytes each. DTX1 and DTX2 lay them out that way, and DTX0 walks them a
/// row at a time.</para>
/// </summary>
public sealed class Table
{
    private readonly int[] width;
    private readonly byte[][] column;

    private Table(int rows, int repeat, int[] width, byte[][] column)
    {
        Rows = rows;
        Repeat = repeat;
        this.width = width;
        this.column = column;
    }

    /// <summary>R, the rows the table holds.</summary>
    public int Rows { get; }

    /// <summary>RR, the row it repeats to, or R where it does not.</summary>
    public int Repeat { get; }

    /// <summary>C, the column count.</summary>
    public int Columns => width.Length;

    /// <summary>Column i's width in bytes.</summary>
    public int Width(int i) => width[i];

    /// <summary>Every column's width.</summary>
    public int[] Widths() => (int[])width.Clone();

    /// <summary>Column i's R values, in row order.</summary>
    public byte[] Column(int i) => (byte[])column[i].Clone();

    /// <summary>A row's bytes: column 0 through column C minus one.</summary>
    public int RowBytes
    {
        get
        {
            int out_ = 0;
            foreach (int w in width)
            {
                out_ += w;
            }
            return out_;
        }
    }

    /// <summary>
    /// A table of the given columns, each rows times its width bytes. The
    /// arrays are copied, so a later write to the caller's does not reach
    /// this table.
    /// </summary>
    /// <exception cref="ArgumentException">where R6's bounds do not hold, or
    /// a column is not the length its width and rows give</exception>
    public static Table Of(int rows, int repeat, int[] width, byte[][] column)
    {
        if (rows < 1)
        {
            throw new ArgumentException($"R is 1 upward, not {rows}");
        }
        if (width.Length < 1 || width.Length > 256)
        {
            throw new ArgumentException($"C is 1 to 256, not {width.Length}");
        }
        if (width.Length != column.Length)
        {
            throw new ArgumentException(
                    $"{width.Length} widths for {column.Length} columns");
        }
        if (repeat < 0 || repeat > rows)
        {
            throw new ArgumentException($"RR is 0 to R, not {repeat}");
        }
        byte[][] held = new byte[column.Length][];
        for (int i = 0; i < column.Length; i++)
        {
            if (width[i] != 1 && width[i] != 2 && width[i] != 4)
            {
                throw new ArgumentException(
                        $"column {i} is {width[i]} bytes wide, not 1, 2 or 4");
            }
            if (column[i].Length != rows * width[i])
            {
                throw new ArgumentException($"column {i} holds"
                        + $" {column[i].Length} bytes, not {rows * width[i]}");
            }
            held[i] = (byte[])column[i].Clone();
        }
        return new Table(rows, repeat, (int[])width.Clone(), held);
    }

    /// <summary>Whether two tables hold the same rows, widths, R and RR.</summary>
    public bool Same(Table other)
    {
        if (Rows != other.Rows || Repeat != other.Repeat
                || width.Length != other.width.Length)
        {
            return false;
        }
        for (int i = 0; i < width.Length; i++)
        {
            if (width[i] != other.width[i]
                    || !column[i].AsSpan().SequenceEqual(other.column[i]))
            {
                return false;
            }
        }
        return true;
    }

    /// <summary>The header of this table under variant, SPEC.md 1.</summary>
    public byte[] Header(int variant)
    {
        byte[] out_ = new byte[Format.HeaderLength(Columns)];
        Format.Magic.CopyTo(out_, 0);
        out_[3] = (byte)variant;
        Format.PutLong(out_, 4, Rows);
        Format.PutWord(out_, 8, Columns);
        Format.PutLong(out_, 10, Repeat);
        for (int i = 0; i < width.Length; i++)
        {
            out_[14 + i] = (byte)width[i];
        }
        return out_;
    }
}

/// <summary>
/// What packs one column of a DTX2 payload.
///
/// <para>DTX2 states that a column is an ST4 data set (R5.1) and nothing
/// more about how ST4 packs. St4Packer packs with the copy this repository
/// holds, St4Beside runs a packer beside it, and a caller that writes DTX2
/// may supply one of its own.</para>
/// </summary>
public interface IPacker
{
    /// <summary>
    /// column as one complete ST4 data set: its own header, and the length
    /// of what it unpacks to.
    /// </summary>
    byte[] Pack(byte[] column, int unit, int ring);

    /// <summary>
    /// Whether a match beyond the ring copies from the column's own literal
    /// stream, which ST4 packs with -c.
    ///
    /// <para>A decoder built without the copy code reads such a column
    /// wrongly, and nothing in an ST4 data set states which it is. So the
    /// payload states it (R5.10), and the packer states it: a flag carried
    /// beside the file could disagree with the bytes in it, and one the
    /// packer wrote cannot.</para>
    /// </summary>
    bool Copies => false;
}
