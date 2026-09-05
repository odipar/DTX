namespace Dtx;

/// <summary>The three variants read and written.</summary>
public static class Variants
{
    /// <summary>
    /// table as a DTX0 file: R rows, each column 0 through column C
    /// minus one in order, with nothing between them.
    ///
    /// <para>A value falls where the widths put it, so a two or four byte
    /// column can fall on an odd offset and a reader takes it as bytes
    /// (R3.4).</para>
    /// </summary>
    public static byte[] WriteDtx0(Table table)
    {
        byte[] head = table.Header(Format.Dtx0);
        byte[] out_ = new byte[head.Length + table.Rows * table.RowBytes];
        head.CopyTo(out_, 0);
        int at = head.Length;
        for (int n = 0; n < table.Rows; n++)
        {
            for (int i = 0; i < table.Columns; i++)
            {
                int w = table.Width(i);
                Array.Copy(table.Column(i), n * w, out_, at, w);
                at += w;
            }
        }
        return out_;
    }

    /// <summary>The table in a DTX0 file.</summary>
    public static Table ReadDtx0(byte[] file)
    {
        Header header = Format.ReadHeader(file);
        if (header.Variant != Format.Dtx0)
        {
            throw new ArgumentException($"variant {header.Variant} is not DTX0");
        }
        int payload = header.Rows * header.RowBytes;
        if (file.Length - header.Length < payload)
        {
            throw new ArgumentException($"a payload of"
                    + $" {file.Length - header.Length} bytes is short of {payload}");
        }
        byte[][] column = new byte[header.Columns][];
        for (int i = 0; i < column.Length; i++)
        {
            column[i] = new byte[header.Rows * header.Width[i]];
        }
        int at = header.Length;
        for (int n = 0; n < header.Rows; n++)
        {
            for (int i = 0; i < header.Columns; i++)
            {
                int w = header.Width[i];
                Array.Copy(file, at, column[i], n * w, w);
                at += w;
            }
        }
        return Table.Of(header.Rows, header.Repeat, header.Width, column);
    }

    /// <summary>What a DTX1 payload runs to.</summary>
    public static int PayloadLengthDtx1(int rows, int[] width)
    {
        int[] at = Format.Offsets(rows, width);
        int last = width.Length - 1;
        return at[last] + rows * width[last];
    }

    /// <summary>
    /// table as a DTX1 file: column by column, each column beginning on a
    /// word so a wide value is read whole.
    /// </summary>
    public static byte[] WriteDtx1(Table table)
    {
        byte[] head = table.Header(Format.Dtx1);
        int[] width = table.Widths();
        int[] at = Format.Offsets(table.Rows, width);
        byte[] out_ = new byte[head.Length + PayloadLengthDtx1(table.Rows, width)];
        head.CopyTo(out_, 0);
        for (int i = 0; i < width.Length; i++)
        {
            byte[] column = table.Column(i);
            column.CopyTo(out_, head.Length + at[i]);
        }
        return out_;
    }

    /// <summary>The table in a DTX1 file.</summary>
    public static Table ReadDtx1(byte[] file)
    {
        Header header = Format.ReadHeader(file);
        if (header.Variant != Format.Dtx1)
        {
            throw new ArgumentException($"variant {header.Variant} is not DTX1");
        }
        int payload = PayloadLengthDtx1(header.Rows, header.Width);
        if (file.Length - header.Length < payload)
        {
            throw new ArgumentException($"a payload of"
                    + $" {file.Length - header.Length} bytes is short of {payload}");
        }
        int[] at = Format.Offsets(header.Rows, header.Width);
        byte[][] column = new byte[header.Columns][];
        for (int i = 0; i < column.Length; i++)
        {
            column[i] = new byte[header.Rows * header.Width[i]];
            Array.Copy(file, header.Length + at[i], column[i], 0, column[i].Length);
        }
        return Table.Of(header.Rows, header.Repeat, header.Width, column);
    }

    /// <summary>The table in a file, under either plain variant.</summary>
    public static Table Read(byte[] file)
    {
        Header header = Format.ReadHeader(file);
        return header.Variant switch
        {
            Format.Dtx0 => ReadDtx0(file),
            Format.Dtx1 => ReadDtx1(file),
            _ => throw new ArgumentException(
                    $"variant {header.Variant} is not read here"),
        };
    }

    /// <summary>table as a DTX2 file, every column packed at one unit.</summary>
    public static byte[] WriteDtx2(Table table, IPacker packer, int unit, int ring)
    {
        if (unit != 1 && unit != 2 && unit != 4)
        {
            throw new ArgumentException($"k is 1, 2 or 4, not {unit}");
        }
        if (ring < 1 || ring > Format.MaxRing)
        {
            throw new ArgumentException(
                    $"N is 1 to {Format.MaxRing}, not {ring}");
        }
        if (table.Rows % unit != 0)
        {
            throw new ArgumentException(
                    $"R is {table.Rows}, which does not divide by k of {unit}");
        }
        byte[][] set = new byte[table.Columns][];
        for (int i = 0; i < set.Length; i++)
        {
            set[i] = packer.Pack(table.Column(i), unit, ring);
        }

        // 2.3: N, k, the flags, then an offset a column
        int[] at = new int[table.Columns];
        int next = 4 + 4 * table.Columns;
        for (int i = 0; i < set.Length; i++)
        {
            next = Format.Align(next, 4);
            at[i] = next;
            next += set[i].Length;
        }
        byte[] head = table.Header(Format.Dtx2);
        byte[] out_ = new byte[head.Length + next];
        head.CopyTo(out_, 0);
        Format.PutWord(out_, head.Length, ring);
        out_[head.Length + 2] = (byte)unit;
        if (packer.Copies)
        {
            out_[head.Length + 3] = Format.CopiesFlag;
        }
        for (int i = 0; i < set.Length; i++)
        {
            Format.PutLong(out_, head.Length + 4 + 4 * i, at[i]);
            set[i].CopyTo(out_, head.Length + at[i]);
        }
        return out_;
    }

    /// <summary>
    /// The DTX2 file of the table in a DTX0 or DTX1 file. The table is
    /// the same under every variant (R1.3), so what comes back has the
    /// same rows, widths, R and RR as what went in.
    /// </summary>
    public static byte[] Dtx2From(byte[] plain, IPacker packer, int unit, int ring)
            => WriteDtx2(Read(plain), packer, unit, ring);
}
