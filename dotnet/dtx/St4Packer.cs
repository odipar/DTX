namespace Dtx;

using System.Diagnostics;
using System.Globalization;

/// <summary>
/// An <see cref="IPacker"/> that packs with the copy of ST4 in this
/// repository.
///
/// <para>dotnet/nt4 is that copy, taken from odipar/ST4@498aa25 and not
/// edited here. So a tool writes DTX2 with no packer beside it, and
/// <see cref="St4Beside"/> runs one where a caller names it.</para>
///
/// <para>The packer takes what <c>st4 -f -kK -mN -l65535</c> gives it, and <c>-c</c> beside them where the columns contain copies.
/// The ring is bytes and the packer counts units, so <c>-m</c> is the ring
/// divided by the unit, at most what a word offset can give.</para>
///
/// <para><c>-l65535</c> meets ST4_wrap's assumption 4: no operation is
/// longer than the 65535 units the 68000 decoders count in a word.</para>
/// </summary>
public sealed class St4Packer : IPacker
{
    /// <summary>The longest operation, ST4_wrap assumption 4.</summary>
    private const int MaxOp = 65535;

    private readonly bool copies;
    private readonly double seconds;

    /// <summary>A packer that does not pack copies from the literal stream.</summary>
    public St4Packer() : this(false, 0)
    {
    }

    /// <summary>
    /// A packer that so that a match beyond the ring copies from the column's own
    /// literal stream, which packs a small ring far smaller.
    /// </summary>
    /// <param name="copies">whether to pack copies</param>
    /// <param name="seconds">how long to search beyond the opening passes
    /// for a better parse, or zero for those passes alone. A search of no
    /// seconds is the same parse every run; one of some seconds is not
    /// </param>
    public St4Packer(bool copies, double seconds)
    {
        this.copies = copies;
        this.seconds = seconds;
    }

    /// <inheritdoc/>
    public bool Copies => copies;

    /// <inheritdoc/>
    public byte[] Pack(byte[] column, int unit, int ring, int loop)
    {
        string problem = Nt4.Format.CheckUnit(unit);
        if (problem.Length != 0)
        {
            throw new ArgumentException(problem);
        }
        // A word offset is stored scaled to bytes, so the limit is a byte
        // figure: 32512 units at k=4 would not fit the word.
        int offsetLimit = Math.Min(ring / unit, Nt4.Format.MaxOffsetUnits(unit));
        int[] units = Nt4.Units.Split(column, unit);
        if (loop < -1 || loop >= units.Length)
        {
            throw new ArgumentException($"the loop is unit -1 to"
                    + $" {units.Length - 1} of the column, not {loop}");
        }
        // ST4 packs a loop two ways, and this makes the same test its own
        // packer makes: the end marker's endless match where a back
        // reference reaches the loop's first unit, and a replayed pass
        // where it does not.
        return Nt4.Nt4.Container(
                loop >= 0 && units.Length - loop > offsetLimit
                        ? Replayed(units, unit, offsetLimit, loop)
                        : Nt4.Compressor.Compress(Parse(units, unit,
                                offsetLimit), units, unit, MaxOp, loop,
                                offsetLimit));
    }

    /// <summary>
    /// A column whose loop is longer than a back reference reaches. The run
    /// before the loop and the loop are parsed apart, so nothing in the loop
    /// reaches before the loop's first unit and every pass reads the same
    /// history. The data set records that unit, and a reader puts the
    /// decoder's registers away there and back at the column's end, every
    /// pass, which ST4's decoders leave to the caller (abi.md 4).
    /// </summary>
    private Nt4.Compressor.Result Replayed(int[] units, int unit, int limit,
            int loop)
    {
        int[] before = units[..loop];
        int[] over = units[loop..];
        return Nt4.Compressor.CompressRewinding(
                before.Length == 0 ? null : Parse(before, unit, limit),
                Parse(over, unit, limit), units, unit, MaxOp, loop, limit);
    }

    /// <summary>
    /// One parse of units, with the copy code where this packs it. Neither
    /// optimizer reports progress: a tool writes what it wrote, and a meter
    /// on standard output would stand in the middle of it.
    /// </summary>
    private Nt4.Block Parse(int[] units, int unit, int limit) => copies
            ? Nt4.LiteralCopySearch.Optimize(units, unit, limit, MaxOp,
                    seconds, false)
            : Nt4.EventOptimizer.Optimize(units, unit, limit, false);
}

/// <summary>
/// An <see cref="IPacker"/> that runs an ST4 packer beside this one.
///
/// <para><see cref="St4Packer"/> packs with the copy in this repository,
/// and a tool takes it where none is named. This runs another: an ST4
/// build of its own, named by <c>-p</c>, so a packer newer than the copy
/// here is used through this.</para>
/// </summary>
public sealed class St4Beside : IPacker
{
    private readonly string packer;
    private readonly string copies;

    /// <param name="copies"><c>-c</c>, or <c>-cS</c> for a search of S
    /// seconds, or empty for none</param>
    public St4Beside(string packer, string copies)
    {
        this.packer = packer;
        this.copies = copies;
    }

    /// <inheritdoc/>
    public bool Copies => copies.Length != 0;

    /// <inheritdoc/>
    public byte[] Pack(byte[] column, int unit, int ring, int loop)
    {
        string work = Directory.CreateTempSubdirectory("dtx").FullName;
        try
        {
            string in_ = Path.Combine(work, "column");
            string out_ = Path.Combine(work, "column.st4");
            File.WriteAllBytes(in_, column);
            ProcessStartInfo start = new(packer)
            {
                RedirectStandardOutput = true,
                RedirectStandardError = true,
            };
            // The offset limit is capped as St4Packer caps it, so the two
            // packers are given one limit: a word offset is stored scaled to
            // bytes, and 32512 units at k=4 would not fit the word.
            int offsetLimit = Math.Min(ring / unit,
                    Nt4.Format.MaxOffsetUnits(unit));
            foreach (string one in new[]
            {
                "-f",
                "-k" + unit.ToString(CultureInfo.InvariantCulture),
                "-m" + offsetLimit.ToString(CultureInfo.InvariantCulture),
                "-l65535",
            })
            {
                start.ArgumentList.Add(one);
            }
            if (loop >= 0)
            {
                // st4 -r takes the loop's own unit, and works out for itself
                // whether a back reference reaches the loop's first unit or
                // the pass has to be replayed
                start.ArgumentList.Add(
                        "-r" + loop.ToString(CultureInfo.InvariantCulture));
            }
            if (copies.Length != 0)
            {
                start.ArgumentList.Add(copies);
            }
            start.ArgumentList.Add(in_);
            start.ArgumentList.Add(out_);
            using Process run = Process.Start(start)
                    ?? throw new InvalidOperationException(
                            $"{packer} did not start");
            string given = run.StandardOutput.ReadToEnd()
                    + run.StandardError.ReadToEnd();
            run.WaitForExit();
            if (run.ExitCode != 0 || !File.Exists(out_))
            {
                throw new InvalidOperationException(
                        $"{packer} gave {given.Trim()}");
            }
            return File.ReadAllBytes(out_);
        }
        finally
        {
            Directory.Delete(work, true);
        }
    }
}
