namespace Dtx;

using System.Collections.Generic;
using System.Globalization;
using System.Text;

/// <summary>
/// A table out of comma separated text: one row a line, one value a column.
///
/// <para>The first row of numbers gives C. A line that is blank, or whose
/// first character other than a space is #, is not a row, and neither is a
/// line before the first row of numbers in which no cell is a number: a
/// line of column names, or a line describing the table. A value is
/// decimal, or hexadecimal where it opens with $, and negative where it
/// opens with -.</para>
///
/// <para>A value of W bytes is stored most significant byte first, as every
/// field of the header is, and a negative one in two's complement. A value
/// fits W bytes where it lies from -2^(8W-1) to 2^(8W)-1, so one width takes
/// a signed column's values and an unsigned one's alike. DTX does not
/// define more of a column than its width, so which of the two a column
/// is, the caller defines elsewhere.</para>
///
/// <para>A table is written out the other way as well: a comment that gives
/// the shape, a line of column names, then one row a line, each value the
/// unsigned number its bytes give. Read back, the comment gives the widths
/// and the repeat where the caller does not give them, and the names are
/// passed over, so the text a table was written as reads back to that
/// table.</para>
/// </summary>
public static class Csv
{
    /// <summary>
    /// The rows of text, each column at the width the first comment gives
    /// or else the narrowest that takes every value of it, repeating at the
    /// row the first comment gives or else at R.
    /// </summary>
    public static Table TableAt(string text)
    {
        List<long[]> row = Rows(text);
        int repeat = Repeat(text);
        return Build(row, Widths(text), repeat < 0 ? row.Count : repeat);
    }

    /// <summary>
    /// The rows of text at the given widths, repeating at the row the first
    /// comment gives or else at R.
    /// </summary>
    public static Table TableAt(string text, int[] width)
    {
        List<long[]> row = Rows(text);
        int repeat = Repeat(text);
        return Build(row, width, repeat < 0 ? row.Count : repeat);
    }

    /// <summary>The rows of text at the given widths, repeating at repeat.</summary>
    public static Table TableAt(string text, int[] width, int repeat) =>
            Build(Rows(text), width, repeat);

    /// <summary>
    /// The widths the first comment of text gives, or else the narrowest
    /// width of 1, 2 and 4 that takes every value of each column.
    /// </summary>
    public static int[] Widths(string text)
    {
        string given = Comment(text, "widths ");
        if (given.Length == 0)
        {
            return Narrowest(Rows(text));
        }
        string[] cell = given.Split(',');
        int[] width = new int[cell.Length];
        for (int i = 0; i < cell.Length; i++)
        {
            width[i] = int.Parse(cell[i].Trim(), CultureInfo.InvariantCulture);
        }
        return width;
    }

    /// <summary>
    /// The repeat the first comment of text gives, or -1 where it does not
    /// give one.
    /// </summary>
    public static int Repeat(string text)
    {
        string given = Comment(text, "RR ");
        return given.Length == 0
                ? -1 : int.Parse(given, CultureInfo.InvariantCulture);
    }

    /// <summary>
    /// table as text: a comment giving R, C, the widths and RR; a line of
    /// column names, c0 onward; then one row a line, one value a column,
    /// each the unsigned number its bytes give. TableAt(text) reads it back
    /// to the same table.
    /// </summary>
    public static string Text(Table table)
    {
        StringBuilder out_ = new();
        out_.Append("# ").Append(table.Rows).Append(" rows, ")
                .Append(table.Columns).Append(" columns, widths ");
        for (int i = 0; i < table.Columns; i++)
        {
            out_.Append(i == 0 ? "" : ",").Append(table.Width(i));
        }
        out_.Append(", RR ").Append(table.Repeat).Append('\n');
        for (int i = 0; i < table.Columns; i++)
        {
            out_.Append(i == 0 ? "c" : ",c").Append(i);
        }
        out_.Append('\n');
        for (int r = 0; r < table.Rows; r++)
        {
            for (int i = 0; i < table.Columns; i++)
            {
                int width = table.Width(i);
                out_.Append(i == 0 ? "" : ",")
                        .Append(Get(table.Column(i), r * width, width));
            }
            out_.Append('\n');
        }
        return out_.ToString();
    }

    /// <summary>
    /// What follows key in the first comment of text, up to a comma followed
    /// by a space or the end of the line, or empty where the text does not
    /// open with a comment giving it. The comment is the one Text(table)
    /// writes: <c># 3 rows, 3 columns, widths 1,2,1, RR 3</c>.
    /// </summary>
    private static string Comment(string text, string key)
    {
        foreach (string line in text.Split('\n'))
        {
            string read = line.Trim();
            if (read.Length == 0)
            {
                continue;
            }
            if (!read.StartsWith('#'))
            {
                return "";
            }
            int at = read.IndexOf(key, StringComparison.Ordinal);
            if (at < 0)
            {
                return "";
            }
            string rest = read[(at + key.Length)..];
            int end = rest.IndexOf(", ", StringComparison.Ordinal);
            return (end < 0 ? rest : rest[..end]).Trim();
        }
        return "";
    }

    /// <summary>The unsigned number of width bytes at at.</summary>
    private static long Get(byte[] in_, int at, int width)
    {
        long value = 0;
        for (int i = 0; i < width; i++)
        {
            value = value << 8 | in_[at + i];
        }
        return value;
    }

    private static Table Build(List<long[]> row, int[] width, int repeat)
    {
        foreach (int w in width)
        {
            if (w != 1 && w != 2 && w != 4)
            {
                throw new ArgumentException(
                        $"a column is 1, 2 or 4 bytes wide, not {w}");
            }
        }
        if (row[0].Length != width.Length)
        {
            throw new ArgumentException(
                    $"{width.Length} widths for {row[0].Length} columns");
        }
        byte[][] column = new byte[width.Length][];
        for (int i = 0; i < width.Length; i++)
        {
            column[i] = new byte[row.Count * width[i]];
            for (int r = 0; r < row.Count; r++)
            {
                long value = row[r][i];
                if (!Fits(value, width[i]))
                {
                    throw new ArgumentException($"row {r} column {i} gives"
                            + $" {value}, which {width[i]} bytes do not take");
                }
                Put(column[i], r * width[i], value, width[i]);
            }
        }
        return Table.Of(row.Count, repeat, width, column);
    }

    /// <summary>Every row of text, a value a column, in the order read.</summary>
    private static List<long[]> Rows(string text)
    {
        List<long[]> out_ = new();
        int columns = -1;
        string[] line = text.Split('\n');
        for (int at = 0; at < line.Length; at++)
        {
            string read = line[at].Trim();
            if (read.Length == 0 || read.StartsWith('#'))
            {
                continue;
            }
            string[] cell = read.Split(',');
            if (columns < 0 && !ANumberAmong(cell))
            {
                // a line of column names, or one describing the table
                continue;
            }
            if (columns < 0)
            {
                columns = cell.Length;
                if (columns > 256)
                {
                    throw new ArgumentException($"C is 1 to 256, not {columns}");
                }
            }
            else if (cell.Length != columns)
            {
                throw new ArgumentException($"line {at + 1} holds"
                        + $" {cell.Length} values, not {columns}");
            }
            long[] row = new long[columns];
            for (int i = 0; i < columns; i++)
            {
                row[i] = Value(cell[i].Trim(), at + 1, i);
            }
            out_.Add(row);
        }
        if (out_.Count == 0)
        {
            throw new ArgumentException("the text does not contain a row");
        }
        return out_;
    }

    /// <summary>
    /// Whether a cell of the line is a number, as Value reads one.
    /// </summary>
    private static bool ANumberAmong(string[] cell)
    {
        foreach (string one in cell)
        {
            string read = one.Trim();
            bool hex = read.StartsWith('$')
                    || read.StartsWith("-$", StringComparison.Ordinal);
            string digits = read.StartsWith("-$", StringComparison.Ordinal)
                    ? read[2..] : hex || read.StartsWith('-') ? read[1..] : read;
            if (digits.Length == 0)
            {
                continue;
            }
            bool number = true;
            foreach (char c in digits)
            {
                if (hex ? !Uri.IsHexDigit(c) : c < '0' || c > '9')
                {
                    number = false;
                }
            }
            if (number)
            {
                return true;
            }
        }
        return false;
    }

    /// <summary>The narrowest width each column of row takes.</summary>
    private static int[] Narrowest(List<long[]> row)
    {
        int[] width = new int[row[0].Length];
        for (int i = 0; i < width.Length; i++)
        {
            int taken = 1;
            foreach (long[] read in row)
            {
                while (!Fits(read[i], taken))
                {
                    if (taken == 4)
                    {
                        throw new ArgumentException($"column {i} gives"
                                + $" {read[i]}, which no width of 1, 2 or 4"
                                + " bytes takes");
                    }
                    taken = taken == 1 ? 2 : 4;
                }
            }
            width[i] = taken;
        }
        return width;
    }

    /// <summary>The number cell gives, or what it is that is not one.</summary>
    private static long Value(string cell, int line, int column)
    {
        string where = $"line {line} column {column}";
        try
        {
            if (cell.StartsWith('$'))
            {
                return long.Parse(cell[1..], NumberStyles.HexNumber,
                        CultureInfo.InvariantCulture);
            }
            if (cell.StartsWith("-$", StringComparison.Ordinal))
            {
                return -long.Parse(cell[2..], NumberStyles.HexNumber,
                        CultureInfo.InvariantCulture);
            }
            return long.Parse(cell, CultureInfo.InvariantCulture);
        }
        catch (Exception failed) when (failed is FormatException
                || failed is OverflowException)
        {
            throw new ArgumentException(
                    $"{where} gives \"{cell}\", which is not a number");
        }
    }

    /// <summary>Whether value lies from -2^(8W-1) to 2^(8W)-1.</summary>
    private static bool Fits(long value, int width) =>
            value >= -(1L << (8 * width - 1)) && value <= (1L << (8 * width)) - 1;

    /// <summary>value at at, most significant byte first.</summary>
    private static void Put(byte[] out_, int at, long value, int width)
    {
        for (int i = 0; i < width; i++)
        {
            out_[at + i] = (byte)(value >> (8 * (width - 1 - i)));
        }
    }
}
