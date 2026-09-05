using System;

/// <summary>
/// The one entry point: the first argument names the tool, the rest are its
/// arguments - what <c>java -cp target/classes &lt;class&gt;</c> is to the
/// Java tree, <c>dotnet dtx.dll &lt;tool&gt;</c> is to this one.
///
/// <para>Published under a tool's own name, the executable is that tool and
/// every argument is its own.</para>
/// </summary>
public static class Program
{
    public static int Main(string[] args)
    {
        string? self = Environment.ProcessPath;
        string name = self == null ? ""
                : System.IO.Path.GetFileNameWithoutExtension(self);
        string[] rest = args;
        if (!name.StartsWith("dtx-", StringComparison.Ordinal))
        {
            if (args.Length == 0)
            {
                Console.Error.WriteLine("usage: dtx <tool> [arguments...]\n"
                        + "tools: dtx-write dtx-rewrite dtx-package dtx-blobs");
                return 2;
            }
            name = args[0];
            rest = args[1..];
        }
        switch (name)
        {
            case "dtx-write": return Dtx.Tools.Write(rest);
            case "dtx-rewrite": return Dtx.Tools.Rewrite(rest);
            case "dtx-package": return Dtx.Tools.Package(rest);
            case "dtx-blobs": return Dtx.Tools.Blobs(rest);
            default:
                Console.Error.WriteLine($"dtx does not hold a tool named {name}");
                return 2;
        }
    }
}
