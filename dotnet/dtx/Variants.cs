namespace Dtx;

/// <summary>The three variants read and written.</summary>
public static class Variants
{
    /// <summary>
    /// table as a DTX0 file: R rows, each column 0 through column C
    /// minus one in order, with nothing between them.
    ///
    /// <para>A value falls where the width puts it, so at a width of 1 a row
    /// can begin on an odd offset and a reader takes it as bytes (R3.4). At
    /// a width of 2 or 4 every value stands on its own boundary.</para>
    /// </summary>
    public static byte[] WriteDtx0(Table table)
    {
        byte[] head = table.Header(Format.Dtx0);
        int width = table.Width;
        int row = table.RowBytes;
        byte[] out_ = new byte[head.Length + table.Rows * row];
        head.CopyTo(out_, 0);
        for (int i = 0; i < table.Columns; i++)
        {
            byte[] column = table.Column(i);
            for (int n = 0; n < table.Rows; n++)
            {
                Array.Copy(column, n * width, out_,
                        head.Length + n * row + i * width, width);
            }
        }
        return out_;
    }

    /// <summary>The table in a DTX0 file.</summary>
    /// <exception cref="ArgumentException">where the file is not DTX0, or is
    /// short of the rows its header defines</exception>
    public static Table ReadDtx0(byte[] file)
    {
        Header header = Format.ReadHeader(file);
        if (header.Variant != Format.Dtx0)
        {
            throw new ArgumentException($"variant {header.Variant} is not DTX0");
        }
        int width = header.Width;
        int row = header.RowBytes;
        int payload = header.Rows * row;
        if (file.Length - header.Length < payload)
        {
            throw new ArgumentException($"a payload of"
                    + $" {file.Length - header.Length} bytes is short of {payload}");
        }
        byte[][] column = new byte[header.Columns][];
        for (int i = 0; i < column.Length; i++)
        {
            column[i] = new byte[header.Rows * width];
            for (int n = 0; n < header.Rows; n++)
            {
                Array.Copy(file, header.Length + n * row + i * width,
                        column[i], n * width, width);
            }
        }
        return Table.Of(header.Rows, header.Repeat, width, column);
    }

    /// <summary>
    /// The stride from one column to the next: R times the width, up to a
    /// word.
    /// </summary>
    public static int Stride(int rows, int width) =>
            Format.Align(rows * width, 2);

    /// <summary>What a DTX1 payload runs to, for rows of this width.</summary>
    public static int PayloadLengthDtx1(int rows, int columns, int width) =>
            (columns - 1) * Stride(rows, width) + rows * width;

    /// <summary>
    /// table as a DTX1 file: C columns, each its R values in row order.
    ///
    /// <para>A column begins on a word, so at a width of 1 and an odd R a
    /// zero byte stands between one column and the next (R4.3). Every column
    /// is the same length, so they lie at one stride and a reader steps from
    /// one to the next by adding it.</para>
    /// </summary>
    public static byte[] WriteDtx1(Table table)
    {
        byte[] head = table.Header(Format.Dtx1);
        int stride = Stride(table.Rows, table.Width);
        byte[] out_ = new byte[head.Length
                + PayloadLengthDtx1(table.Rows, table.Columns, table.Width)];
        head.CopyTo(out_, 0);
        for (int i = 0; i < table.Columns; i++)
        {
            table.Column(i).CopyTo(out_, head.Length + i * stride);
        }
        return out_;
    }

    /// <summary>The table in a DTX1 file.</summary>
    /// <exception cref="ArgumentException">where the file is not DTX1, or is
    /// short of the columns its header defines</exception>
    public static Table ReadDtx1(byte[] file)
    {
        Header header = Format.ReadHeader(file);
        if (header.Variant != Format.Dtx1)
        {
            throw new ArgumentException($"variant {header.Variant} is not DTX1");
        }
        int width = header.Width;
        int payload = PayloadLengthDtx1(header.Rows, header.Columns, width);
        if (file.Length - header.Length < payload)
        {
            throw new ArgumentException($"a payload of"
                    + $" {file.Length - header.Length} bytes is short of {payload}");
        }
        int stride = Stride(header.Rows, width);
        byte[][] column = new byte[header.Columns][];
        for (int i = 0; i < column.Length; i++)
        {
            column[i] = new byte[header.Rows * width];
            Array.Copy(file, header.Length + i * stride, column[i], 0,
                    column[i].Length);
        }
        return Table.Of(header.Rows, header.Repeat, width, column);
    }

    /// <summary>
    /// The table in a file, under any variant. A DTX2 file is unpacked with
    /// the copy of ST4 in this repository.
    /// </summary>
    /// <exception cref="ArgumentException">where the variant is not 0, 1
    /// or 2</exception>
    public static Table Read(byte[] file)
    {
        Header header = Format.ReadHeader(file);
        return header.Variant switch
        {
            Format.Dtx0 => ReadDtx0(file),
            Format.Dtx1 => ReadDtx1(file),
            Format.Dtx2 => ReadDtx2(file),
            _ => throw new ArgumentException(
                    $"variant {header.Variant} is not 0, 1 or 2"),
        };
    }

    /// <summary>table as a DTX2 file, every column packed at one unit.</summary>
    /// <exception cref="ArgumentException">where unit or ring is outside
    /// what the payload can define, or a column's bytes do not divide by
    /// unit</exception>
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
        if (table.Rows * table.Width % unit != 0)
        {
            throw new ArgumentException($"a column is {table.Rows} times"
                    + $" {table.Width} bytes, which does not divide by k of"
                    + $" {unit}");
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
    /// The table in a DTX2 file, each column unpacked with the copy of ST4
    /// in this repository. A data set runs from its offset to the next
    /// offset above it, or to the end of the file.
    /// </summary>
    /// <exception cref="ArgumentException">where the file is not DTX2, a
    /// data set does not open with the payload's own unit (R5.2), or a
    /// column unpacks to other than R times the width</exception>
    public static Table ReadDtx2(byte[] file)
    {
        Header header = Format.ReadHeader(file);
        if (header.Variant != Format.Dtx2)
        {
            throw new ArgumentException($"variant {header.Variant} is not DTX2");
        }
        int[] at = Pack.ReadPacked(file, header).At;
        byte[][] column = new byte[header.Columns][];
        for (int i = 0; i < column.Length; i++)
        {
            int from = header.Length + at[i];
            int to = file.Length;
            foreach (int other in at)
            {
                int begins = header.Length + other;
                if (begins > from && begins < to)
                {
                    to = begins;
                }
            }
            Nt4.Format.Container set = Nt4.Format.Read(file[from..to]);
            byte[] out_ = Nt4.Decompressor.Decode(set.Control, set.Literal,
                    set.ByteOffsets, set.WordOffsets, set.Unit, set.Size,
                    set.Window, set.Rewind).Output;
            int bytes = header.Rows * header.Width;
            if (out_.Length != bytes)
            {
                throw new ArgumentException($"column {i} unpacks to"
                        + $" {out_.Length} bytes, not the {bytes} of R rows"
                        + " at its width");
            }
            column[i] = out_;
        }
        return Table.Of(header.Rows, header.Repeat, header.Width, column);
    }
}
