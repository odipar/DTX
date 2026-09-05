namespace Dtx;

using System.Collections.Generic;
using System.Globalization;

/// <summary>
/// A table out of comma separated text: one row a line, one value a column.
///
/// <para>The first row that holds values gives C. A line that is blank, or
/// whose first character other than a space is #, is not a row. A value is
/// decimal, or hexadecimal where it opens with $, and negative where it
/// opens with -.</para>
///
/// <para>A value of W bytes is stored most significant byte first, as every
/// field of the header is, and a negative one in two's complement. A value
/// fits W bytes where it lies from -2^(8W-1) to 2^(8W)-1, so one width takes
/// what a signed column holds and what an unsigned one holds alike. DTX
/// states no more of a column than its width, so which of the two a column
/// holds is the caller's to state elsewhere.</para>
/// </summary>
public static class Csv
{
    /// <summary>The rows of text at the given widths, repeating at R.</summary>
    public static Table TableAt(string text, int[] width)
    {
        List<long[]> row = Rows(text);
        return Build(row, width, row.Count);
    }

    /// <summary>The rows of text at the given widths, repeating at repeat.</summary>
    public static Table TableAt(string text, int[] width, int repeat) =>
            Build(Rows(text), width, repeat);

    /// <summary>
    /// The narrowest width of 1, 2 and 4 that takes every value of each
    /// column of text.
    /// </summary>
    public static int[] Narrowest(string text) => Narrowest(Rows(text));

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
            throw new ArgumentException("the text holds no row");
        }
        return out_;
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
