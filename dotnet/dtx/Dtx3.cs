namespace Dtx;

/// <summary>
/// DTX3, column by column, a constant column as its value and every other
/// packed: a column map, the constant values, a stream record a live
/// column, then the live columns' ST4 streams.
///
/// <para>A column whose R values are equal is constant, and the payload has
/// its value once. Every other column is live: its slot is its rank among
/// the live columns, and slot j's ring, decoder state and stream record are
/// j strides past slot 0's (R8.4). A live column is an ST4 data set without
/// its 28-byte header, whose fields the table and the payload define once
/// (R8.6).</para>
/// </summary>
public static class Dtx3
{
    /// <summary>
    /// The largest ring a payload can define, in bytes: the furthest a back
    /// reference reaches, so that M is N over k at every unit (R8.6).
    /// </summary>
    public const int MaxRing = Nt4.Format.MaxOffset;

    /// <summary>The flags bit at payload byte 3 that marks a payload some
    /// live column of which was packed with copies (R8.7).</summary>
    public const int CopiesFlag = 1;

    /// <summary>The flags bit that marks a payload whose loop is replayed
    /// at RR (R8.8).</summary>
    public const int ReplayedFlag = 2;

    /// <summary>The column map's byte for a constant column.</summary>
    public const int Constant = 0xFF;

    /// <summary>Where the column map begins in the payload.</summary>
    internal const int MapAt = 8;

    /// <summary>What one stream record runs to: streams A, B, C and D.</summary>
    internal const int Record = 16;

    /// <summary>
    /// table as a DTX3 file. Each live column is packed by every packer
    /// named, and the smallest data set is kept, the first named where two
    /// are the same size: so a column packs with copies where they pay and
    /// without them where they do not.
    /// </summary>
    /// <exception cref="ArgumentException">where no packer is named, unit
    /// or ring is outside what the payload can define, a column's bytes do
    /// not divide by unit, the table repeats at a row whose first byte is
    /// not a unit of the column, or a data set defines a unit, a length, an
    /// M or a loop other than the payload's</exception>
    public static byte[] Write(Table table, IReadOnlyList<IPacker> packers, int unit, int ring)
    {
        if (packers.Count == 0)
        {
            throw new ArgumentException("no packer: a live column is packed"
                    + " by one at least");
        }
        if (unit != 1 && unit != 2 && unit != 4)
        {
            throw new ArgumentException($"k is 1, 2 or 4, not {unit}");
        }
        if (ring < unit || ring > MaxRing || ring % unit != 0)
        {
            throw new ArgumentException($"N is a multiple of k from {unit} to"
                    + $" {MaxRing}, not {ring}");
        }
        int bytes = table.Rows * table.Width;
        if (bytes % unit != 0)
        {
            throw new ArgumentException($"a column is {table.Rows} times"
                    + $" {table.Width} bytes, which does not divide by k of"
                    + $" {unit}");
        }
        int loop = Variants.Loop(table, unit);
        int columns = table.Columns;
        int width = table.Width;
        bool[] live = new bool[columns];
        int count = 0;
        for (int i = 0; i < columns; i++)
        {
            live[i] = !IsConstant(table.Column(i), width);
            count += live[i] ? 1 : 0;
        }

        // Each live column's data set, the smallest the packers make, and
        // whether the one kept has copies. A column packs apart from every
        // other, so the columns pack at once and the bytes are the same.
        int[] column = new int[count];
        for (int i = 0, slot = 0; i < columns; i++)
        {
            if (live[i])
            {
                column[slot++] = i;
            }
        }
        byte[][] set = new byte[count][];
        bool[] withCopies = new bool[count];
        try
        {
            Parallel.For(0, count, j => Pack(table, packers, column, set,
                    withCopies, unit, ring, loop, j));
        }
        catch (AggregateException failed) when (failed.InnerExceptions.Count > 0)
        {
            // A packer's error reaches the caller as the packer raised it.
            System.Runtime.ExceptionServices.ExceptionDispatchInfo
                    .Capture(failed.InnerExceptions[0]).Throw();
        }
        // Every set of a payload has one loop and one form of it, which the
        // first defines.
        bool copies = false;
        int rewind = Nt4.Format.NoRewind;
        for (int j = 0; j < count; j++)
        {
            int i = column[j];
            Check(set[j], i, unit, ring, bytes);
            int said = Format.GetLong(set[j], 20);
            if (j == 0)
            {
                rewind = said;
            }
            if (said != rewind || said != Nt4.Format.NoRewind
                    && (loop < 0 || said != loop * unit))
            {
                throw new ArgumentException($"column {i}'s data set records a"
                        + $" rewind point of {said}, where every live column"
                        + $" has the loop at unit {loop} and the form of the"
                        + " first");
            }
            copies |= withCopies[j];
        }

        // SPEC.md 2.4: N, k, the flags, L, the map, the constant values up
        // to a long, a record a slot, then each slot's streams on a long.
        int records = Format.Align(MapAt + columns + width * (columns - count), 4);
        int[] at = new int[count];
        int next = records + Record * count;
        for (int j = 0; j < count; j++)
        {
            next = Format.Align(next, 4);
            at[j] = next;
            next += set[j].Length - Nt4.Format.HeaderSize;
        }
        byte[] head = table.Header(Format.Dtx3);
        int p = head.Length;
        byte[] out_ = new byte[p + next];
        head.CopyTo(out_, 0);
        Format.PutWord(out_, p, ring);
        out_[p + 2] = (byte)unit;
        out_[p + 3] = (byte)((copies ? CopiesFlag : 0)
                | (rewind != Nt4.Format.NoRewind ? ReplayedFlag : 0));
        Format.PutWord(out_, p + 4, count);
        int value = p + MapAt + columns;
        for (int i = 0, slot = 0; i < columns; i++)
        {
            if (live[i])
            {
                out_[p + MapAt + i] = (byte)slot++;
            }
            else
            {
                out_[p + MapAt + i] = Constant;
                Array.Copy(table.Column(i), 0, out_, value, width);
                value += width;
            }
        }
        // A record's four offsets are from the payload's first byte. The
        // streams are the data set's as it laid them out, so each is the
        // data set's offset of it, less the 28-byte header, past the slot's.
        for (int j = 0; j < count; j++)
        {
            int rec = p + records + Record * j;
            Format.PutLong(out_, rec, at[j]);
            for (int s = 1; s < 4; s++)
            {
                Format.PutLong(out_, rec + 4 * s, at[j]
                        + Format.GetLong(set[j], 4 + 4 * s) - Nt4.Format.HeaderSize);
            }
            Array.Copy(set[j], Nt4.Format.HeaderSize, out_, p + at[j],
                    set[j].Length - Nt4.Format.HeaderSize);
        }
        return out_;
    }

    /// <summary>Slot j's data set: its column packed by every packer, the
    /// smallest kept.</summary>
    private static void Pack(Table table, IReadOnlyList<IPacker> packers, int[] column,
            byte[][] set, bool[] withCopies, int unit, int ring, int loop, int j)
    {
        byte[] values = table.Column(column[j]);
        byte[] kept = packers[0].Pack(values, unit, ring, loop);
        bool keptCopies = packers[0].Copies;
        for (int n = 1; n < packers.Count; n++)
        {
            byte[] made = packers[n].Pack(values, unit, ring, loop);
            if (made.Length < kept.Length)
            {
                kept = made;
                keptCopies = packers[n].Copies;
            }
        }
        set[j] = kept;
        withCopies[j] = keptCopies;
    }

    /// <summary>
    /// A data set against the payload: its signature, its length, and its
    /// M, which a reader of the payload reads as N over k.
    /// </summary>
    private static void Check(byte[] set, int column, int unit, int ring, int bytes)
    {
        Nt4.Format.Container read = Nt4.Format.Read(set);
        if (read.Unit != unit)
        {
            throw new ArgumentException($"column {column}'s data set is packed"
                    + $" at k of {read.Unit}, and the payload's k is {unit}");
        }
        if (read.Size != bytes)
        {
            throw new ArgumentException($"column {column}'s data set unpacks"
                    + $" to {read.Size} bytes, not the {bytes} of R rows at"
                    + " its width");
        }
        if (read.Window != ring / unit)
        {
            throw new ArgumentException($"column {column}'s data set has an"
                    + $" M of {read.Window} units, and the payload's M is N"
                    + $" over k, {ring / unit}");
        }
    }

    /// <summary>Whether every value of column is its first.</summary>
    internal static bool IsConstant(byte[] column, int width)
    {
        for (int at = width; at < column.Length; at += width)
        {
            for (int b = 0; b < width; b++)
            {
                if (column[at + b] != column[b])
                {
                    return false;
                }
            }
        }
        return true;
    }

    /// <summary>
    /// What a DTX3 payload defines, SPEC.md 2.4: the ring, the unit, the
    /// two flags, the column map, a constant column's value in its column,
    /// and slot j's four stream offsets from the payload's first byte.
    /// </summary>
    public sealed record Payload(int Ring, int Unit, bool Copies, bool Replayed,
            int[] Map, long[] Constants, int[][] Records)
    {
        /// <summary>L, the live columns.</summary>
        public int Live => Records.Length;

        /// <summary>Whether column i is live.</summary>
        public bool IsLive(int i) => Map[i] != Constant || Records.Length == 256;

        /// <summary>The bytes of slot j's four streams in a payload of
        /// payload bytes: from its stream A to the next slot's, or to the
        /// payload's end.</summary>
        public int Bytes(int j, int payload) =>
                (j + 1 < Records.Length ? Records[j + 1][0] : payload) - Records[j][0];
    }

    /// <summary>
    /// What a DTX3 payload defines, checked against the rules SPEC.md 2.4
    /// lays down for it: L at most C, the slots of the live columns 0 to L
    /// minus one in column order, and every stream inside the file, in
    /// order, A and D on a word.
    /// </summary>
    /// <exception cref="ArgumentException">naming the rule the payload
    /// breaks</exception>
    public static Payload ReadPayload(byte[] file, Header header)
    {
        int p = header.Length;
        int columns = header.Columns;
        int width = header.Width;
        if (file.Length < p + MapAt + columns)
        {
            throw new ArgumentException($"a DTX3 payload of {columns} columns"
                    + $" has {MapAt + columns} bytes before its values, and the"
                    + $" file ends at {file.Length - p}");
        }
        int ring = Format.GetWord(file, p);
        int unit = file[p + 2];
        int flags = file[p + 3];
        int live = Format.GetWord(file, p + 4);
        if (live > columns)
        {
            throw new ArgumentException($"L is 0 to C of {columns}, not {live}");
        }
        int[] map = new int[columns];
        long[] constants = new long[columns];
        int value = p + MapAt + columns;
        int slot = 0;
        for (int i = 0; i < columns; i++)
        {
            map[i] = file[p + MapAt + i];
            if (map[i] == Constant && live < 256)
            {
                if (value + width > file.Length)
                {
                    throw new ArgumentException($"column {i}'s value is past"
                            + " the file's end");
                }
                long v = 0;
                for (int b = 0; b < width; b++)
                {
                    v = v << 8 | file[value + b];
                }
                constants[i] = v;
                value += width;
            }
            else if (map[i] != slot++)
            {
                throw new ArgumentException($"column {i} maps to slot {map[i]},"
                        + " and the live columns' slots are 0 to L minus one in"
                        + " column order");
            }
        }
        if (slot != live)
        {
            throw new ArgumentException($"the map has {slot} live columns, and"
                    + $" L is {live}");
        }
        int records = Format.Align(MapAt + columns + width * (columns - live), 4);
        int[][] record = new int[live][];
        int last = records + Record * live;
        for (int j = 0; j < live; j++)
        {
            record[j] = new int[4];
            for (int s = 0; s < 4; s++)
            {
                int at = p + records + Record * j + 4 * s;
                if (at + 4 > file.Length)
                {
                    throw new ArgumentException($"slot {j}'s record is past the"
                            + " file's end");
                }
                record[j][s] = Format.GetLong(file, at);
                if (record[j][s] < last || p + (long)record[j][s] > file.Length)
                {
                    throw new ArgumentException($"slot {j}'s stream"
                            + $" {(char)('A' + s)} is at {record[j][s]}, out of"
                            + " order or outside the payload");
                }
                last = record[j][s];
            }
            if ((record[j][0] & 1) != 0 || (record[j][3] & 1) != 0)
            {
                throw new ArgumentException($"slot {j}'s streams A and D begin"
                        + $" on a word, and they are at {record[j][0]} and"
                        + $" {record[j][3]}");
            }
        }
        return new Payload(ring, unit, (flags & CopiesFlag) != 0,
                (flags & ReplayedFlag) != 0, map, constants, record);
    }

    /// <summary>
    /// The table in a DTX3 file: each constant column its value R times,
    /// and each live column unpacked with the copy of ST4 in this
    /// repository. A slot's stream D runs to the next slot's stream A, or
    /// to the end of the file.
    /// </summary>
    /// <exception cref="ArgumentException">where the file is not DTX3,
    /// breaks a rule of its payload, or a column unpacks to other than R
    /// times its width</exception>
    public static Table Read(byte[] file)
    {
        Header header = Format.ReadHeader(file);
        if (header.Variant != Format.Dtx3)
        {
            throw new ArgumentException($"variant {header.Variant} is not DTX3");
        }
        Payload payload = ReadPayload(file, header);
        int p = header.Length;
        int width = header.Width;
        int bytes = header.Rows * width;
        int unit = payload.Unit;
        if (unit != 1 && unit != 2 && unit != 4)
        {
            throw new ArgumentException($"k is 1, 2 or 4, not {unit}");
        }
        byte[][] column = new byte[header.Columns][];
        for (int i = 0; i < column.Length; i++)
        {
            column[i] = new byte[bytes];
            if (!payload.IsLive(i))
            {
                for (int at = 0; at < bytes; at += width)
                {
                    for (int b = 0; b < width; b++)
                    {
                        column[i][at + b] = (byte)(payload.Constants[i]
                                >> 8 * (width - 1 - b));
                    }
                }
                continue;
            }
            int slot = payload.Map[i];
            int[] r = payload.Records[slot];
            int end = slot + 1 < payload.Live
                    ? payload.Records[slot + 1][0] : file.Length - p;
            byte[] out_ = Nt4.Decompressor.Decode(file[(p + r[0])..(p + r[1])],
                    file[(p + r[1])..(p + r[2])], file[(p + r[2])..(p + r[3])],
                    file[(p + r[3])..(p + end)], unit, bytes, payload.Ring / unit,
                    payload.Replayed ? header.Repeat * width : Nt4.Format.NoRewind)
                    .Output;
            if (out_.Length != bytes)
            {
                throw new ArgumentException($"column {i} unpacks to"
                        + $" {out_.Length} bytes, not the {bytes} of R rows at"
                        + " its width");
            }
            column[i] = out_;
        }
        return Table.Of(header.Rows, header.Repeat, width, column);
    }
}
