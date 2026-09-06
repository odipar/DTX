namespace Dtx;

/// <summary>
/// A table in memory: R rows, C columns of one width, and a row RR it
/// repeats to. Every variant is this same table (R1.3), so a variant reads
/// into one and writes out of one.
///
/// <para>The values are stored column by column, R times the width bytes
/// each. DTX1 and DTX2 lay them out that way, and DTX0 walks them a row at
/// a time.</para>
/// </summary>
public sealed class Table
{
    private readonly byte[][] column;

    private Table(int rows, int repeat, int width, byte[][] column)
    {
        Rows = rows;
        Repeat = repeat;
        Width = width;
        this.column = column;
    }

    /// <summary>R, the rows in the table.</summary>
    public int Rows { get; }

    /// <summary>RR, the row it repeats to, or R where it does not.</summary>
    public int Repeat { get; }

    /// <summary>W, the bytes every value of the table takes.</summary>
    public int Width { get; }

    /// <summary>C, the column count.</summary>
    public int Columns => column.Length;

    /// <summary>Column i's R values, in row order.</summary>
    public byte[] Column(int i) => (byte[])column[i].Clone();

    /// <summary>A row's bytes: C values of W bytes.</summary>
    public int RowBytes => Columns * Width;

    /// <summary>
    /// A table of the given columns, each rows times width bytes. The
    /// arrays are copied, so a later write to the caller's does not reach
    /// this table.
    /// </summary>
    /// <exception cref="ArgumentException">where R6's bounds are not met, or
    /// a column is not the length width and rows give</exception>
    public static Table Of(int rows, int repeat, int width, byte[][] column)
    {
        if (rows < 1)
        {
            throw new ArgumentException($"R is 1 upward, not {rows}");
        }
        if (column.Length < 1 || column.Length > 256)
        {
            throw new ArgumentException($"C is 1 to 256, not {column.Length}");
        }
        if (width != 1 && width != 2 && width != 4)
        {
            throw new ArgumentException(
                    $"the width is 1, 2 or 4 bytes, not {width}");
        }
        if (repeat < 0 || repeat > rows)
        {
            throw new ArgumentException($"RR is 0 to R, not {repeat}");
        }
        byte[][] kept = new byte[column.Length][];
        for (int i = 0; i < column.Length; i++)
        {
            if (column[i].Length != rows * width)
            {
                throw new ArgumentException($"column {i} is"
                        + $" {column[i].Length} bytes, not {rows * width}");
            }
            kept[i] = (byte[])column[i].Clone();
        }
        return new Table(rows, repeat, width, kept);
    }

    /// <summary>The header of this table under variant, SPEC.md 1.</summary>
    public byte[] Header(int variant)
    {
        byte[] out_ = new byte[Format.HeaderLength];
        Format.Magic.CopyTo(out_, 0);
        out_[3] = (byte)variant;
        Format.PutLong(out_, 4, Rows);
        Format.PutWord(out_, 8, Columns);
        Format.PutLong(out_, 10, Repeat);
        out_[14] = (byte)Width;
        return out_;
    }
}

/// <summary>
/// What packs one column of a DTX2 payload.
///
/// <para>DTX2 defines a column as an ST4 data set (R5.1) and does not
/// define how ST4 packs. St4Packer packs with the copy in this
/// repository, St4Beside runs a packer beside it, and a caller that writes DTX2
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
    /// wrongly, and nothing in an ST4 data set defines which it is. So the
    /// payload defines it (R5.10), and the packer defines it: a flag carried
    /// beside the file could differ from the bytes in it, and one the
    /// packer wrote cannot.</para>
    /// </summary>
    bool Copies => false;
}
