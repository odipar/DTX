namespace Dtx;

using System.Reflection;

/// <summary>
/// The 68000 images a DTX table is packaged behind.
///
/// <para>Twenty-two of them. DTX0 reads a row as one run of bytes, so its
/// code does not move with the width and one file is every DTX0 table's.
/// DTX1 moves a value a column, so it has one a width. DTX2 has one a width
/// and a build of the decoder built into it: a unit of 1, 2 or 4, with the
/// copy code and without. They are built once and an assembly that packages
/// a table contains them rather than assembling one. doc/tools.md defines
/// how they are built and doc/abi.md what each of them does.</para>
///
/// <para>They are build output, embedded from build/68k. An assembly built
/// without them does not contain one: Read gives null back and the caller
/// resolves an image as it otherwise would, through DTX_68K.</para>
/// </summary>
public static class Images
{
    /// <summary>
    /// One build of the code: a variant, the width its values take, and
    /// under DTX2 a decoder.
    /// </summary>
    public readonly record struct Build(int Variant, int Width, int Unit,
            bool Copies);

    /// <summary>Every build, in the order the build writes them.</summary>
    public static Build[] Builds()
    {
        List<Build> out_ = new() { new Build(Format.Dtx0, 1, 0, false) };
        foreach (int width in new[] { 1, 2, 4 })
        {
            out_.Add(new Build(Format.Dtx1, width, 0, false));
        }
        foreach (int width in new[] { 1, 2, 4 })
        {
            foreach (int unit in new[] { 1, 2, 4 })
            {
                out_.Add(new Build(Format.Dtx2, width, unit, false));
                out_.Add(new Build(Format.Dtx2, width, unit, true));
            }
        }
        return out_.ToArray();
    }

    /// <summary>
    /// The file one build stands in. A variant assembles to one code any
    /// table that follows it, and under DTX2 to one a build of the decoder
    /// built into it: the unit it decodes at, with the copy code and
    /// without.
    /// </summary>
    public static string Name(int variant, int width, int unit, bool copies)
    {
        if (variant == Format.Dtx0)
        {
            // DTX0 reads a row as one run of bytes, and the move that run
            // takes comes from the row's bytes: its code does not move with
            // the width.
            return "DTX0.bin";
        }
        return variant == Format.Dtx2
                ? $"DTX2-w{width}-k{unit}{(copies ? "-copies" : "")}.bin"
                : $"DTX1-w{width}.bin";
    }

    /// <summary>One image's bytes, or null where the build does not contain
    /// one.</summary>
    public static byte[]? Read(int variant, int width, int unit, bool copies)
    {
        string name = Name(variant, width, unit, copies);
        Assembly assembly = typeof(Images).Assembly;
        foreach (string one in assembly.GetManifestResourceNames())
        {
            if (!one.EndsWith(name, StringComparison.Ordinal))
            {
                continue;
            }
            using Stream? stream = assembly.GetManifestResourceStream(one);
            if (stream == null)
            {
                return null;
            }
            using MemoryStream into = new();
            stream.CopyTo(into);
            return into.ToArray();
        }
        return null;
    }

    /// <summary>
    /// One image's bytes, from what this build contains or, where it
    /// does not contain one, from the directory DTX_68K names.
    /// </summary>
    public static byte[] Code(int variant, int width, int unit, bool copies)
    {
        string name = Name(variant, width, unit, copies);
        byte[]? embedded = Read(variant, width, unit, copies);
        if (embedded != null)
        {
            return embedded;
        }
        string? at = Environment.GetEnvironmentVariable("DTX_68K");
        if (string.IsNullOrEmpty(at))
        {
            throw new InvalidOperationException($"this build does not contain"
                    + $" {name}, and DTX_68K does not name a directory"
                    + " with one");
        }
        return File.ReadAllBytes(Path.Combine(at, name));
    }
}
