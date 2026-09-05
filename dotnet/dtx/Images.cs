namespace Dtx;

using System.Reflection;

/// <summary>
/// The 68000 images a DTX table is packaged behind.
///
/// <para>One variant is one code, and under DTX2 one a build of the decoder
/// built into it, so the eight are built once and an assembly that packages
/// a table holds them rather than assembling one. doc/tools.md states how
/// they are built and doc/abi.md what each of them answers.</para>
///
/// <para>They are build output, embedded from build/68k. An assembly built
/// without them holds none: Read gives nothing back and the caller resolves
/// an image as it otherwise would, through DTX_68K.</para>
/// </summary>
public static class Images
{
    /// <summary>One build of the code: a variant, and under DTX2 a decoder.</summary>
    public readonly record struct Build(int Variant, int Unit, bool Copies);

    /// <summary>Every build, in the order the build writes them.</summary>
    public static Build[] Builds()
    {
        List<Build> out_ = new()
        {
            new Build(Format.Dtx0, 0, false),
            new Build(Format.Dtx1, 0, false),
        };
        foreach (int unit in new[] { 1, 2, 4 })
        {
            out_.Add(new Build(Format.Dtx2, unit, false));
            out_.Add(new Build(Format.Dtx2, unit, true));
        }
        return out_.ToArray();
    }

    /// <summary>The file one build stands in.</summary>
    public static string Name(int variant, int unit, bool copies)
    {
        if (variant != Format.Dtx2)
        {
            return $"DTX{variant}.bin";
        }
        return copies ? $"DTX2-k{unit}-copies.bin" : $"DTX2-k{unit}.bin";
    }

    /// <summary>One image's bytes, or null where this build holds none.</summary>
    public static byte[]? Read(int variant, int unit, bool copies)
    {
        string name = Name(variant, unit, copies);
        Assembly held = typeof(Images).Assembly;
        foreach (string one in held.GetManifestResourceNames())
        {
            if (!one.EndsWith(name, StringComparison.Ordinal))
            {
                continue;
            }
            using Stream? stream = held.GetManifestResourceStream(one);
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
    /// One image's bytes, from what this build holds or, where it holds
    /// none, from the directory DTX_68K names.
    /// </summary>
    public static byte[] Code(int variant, int unit, bool copies)
    {
        string name = Name(variant, unit, copies);
        byte[]? held = Read(variant, unit, copies);
        if (held != null)
        {
            return held;
        }
        string? at = Environment.GetEnvironmentVariable("DTX_68K");
        if (string.IsNullOrEmpty(at))
        {
            throw new InvalidOperationException($"this build holds no {name},"
                    + " and DTX_68K names no directory holding one");
        }
        return File.ReadAllBytes(Path.Combine(at, name));
    }

    /// <summary>How many of the builds this one holds: eight, or none.</summary>
    public static int Held()
    {
        int held = 0;
        foreach (Build build in Builds())
        {
            if (Read(build.Variant, build.Unit, build.Copies) != null)
            {
                held++;
            }
        }
        return held;
    }
}
