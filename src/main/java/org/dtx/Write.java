package org.dtx;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The tool that writes a table: {@code dtx-write in out}.
 *
 * <p>The table comes from the first file, a DTX file of any variant or comma
 * separated text, and goes to the second as a DTX file of the variant
 * {@code -v} gives, or as text where the name ends in {@code .csv}. The
 * table is the same under every variant (R1.3), so one tool writes text as
 * DTX, rewrites a DTX file at another variant, unit or ring, and reads a DTX
 * file out as text. doc/tools.md, Write.
 */
public final class Write {

    private Write() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IOException | RuntimeException failed) {
            Help.stopped(failed);
        }
    }

    static void run(String[] args) throws IOException {
        if (Help.among(args)) {
            System.out.print(Help.WRITE);
            return;
        }
        if (args.length < 2) {
            System.err.print(Help.WRITE);
            System.exit(2);
            return;
        }
        int variant = -1;
        String width = "";
        int repeat = -1;
        int unit = 1;
        int ring = 960;
        String packer = "";
        String copies = "";
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("-v")) {
                variant = number(arg);
            } else if (arg.startsWith("-w")) {
                width = arg;
            } else if (arg.startsWith("-r")) {
                repeat = number(arg);
            } else if (arg.startsWith("-k")) {
                unit = number(arg);
            } else if (arg.startsWith("-m")) {
                ring = number(arg);
            } else if (arg.startsWith("-copies")) {
                // the packer's own: a match beyond the ring copies from the
                // literal stream, and -copiesS searches S seconds for a
                // better parse. YMX spells it the same way.
                copies = "-c" + arg.substring(7);
            } else if (arg.startsWith("-p")) {
                packer = arg.substring(2);
            } else {
                notRead(arg);
                return;
            }
        }
        boolean toText = args[1].endsWith(".csv");
        if (toText && variant >= 0) {
            System.err.println("-v" + variant + " names a DTX variant, and "
                    + args[1] + " is text");
            System.exit(2);
            return;
        }
        byte[] in = Files.readAllBytes(Path.of(args[0]));
        Table table;
        if (isDtx(in)) {
            if (!width.isEmpty()) {
                System.err.println(width + " gives text its width,"
                        + " and " + args[0] + " is a DTX file with its own");
                System.exit(2);
                return;
            }
            table = Dtx.read(in);
            if (repeat >= 0) {
                table = repeating(table, repeat);
            }
            if (variant < 0) {
                variant = Dtx.header(in).variant();
            }
        } else {
            String text = new String(in, StandardCharsets.UTF_8);
            int given = width.isEmpty() ? Csv.width(text) : number(width);
            table = repeat < 0 ? Csv.table(text, given)
                    : Csv.table(text, given, repeat);
            if (variant < 0) {
                variant = Dtx.DTX0;
            }
        }
        byte[] out;
        if (toText) {
            out = Csv.text(table).getBytes(StandardCharsets.UTF_8);
        } else {
            out = switch (variant) {
                case Dtx.DTX0 -> Dtx0.write(table);
                case Dtx.DTX1 -> Dtx1.write(table);
                case Dtx.DTX2 -> Dtx2.write(table, packer(packer, copies),
                        unit, ring);
                default -> throw new IllegalArgumentException(
                        "the variant is 0, 1 or 2, not " + variant);
            };
        }
        Files.write(Path.of(args[1]), out);
        System.out.printf("%s -> %s %d bytes, %d rows, %d columns,"
                + " width %d, RR=%d%s%n", args[0],
                toText ? "text" : "DTX" + variant, out.length,
                table.rows(), table.columns(), table.width(), table.repeat(),
                !toText && variant == Dtx.DTX2
                        ? ", k=" + unit + ", N=" + ring : "");
    }

    /** Whether {@code file} opens with {@code DTX}. */
    static boolean isDtx(byte[] file) {
        if (file.length < Dtx.MAGIC.length) {
            return false;
        }
        for (int i = 0; i < Dtx.MAGIC.length; i++) {
            if (file[i] != Dtx.MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /** {@code table} repeating at {@code repeat}, the rest as it is. */
    static Table repeating(Table table, int repeat) {
        byte[][] column = new byte[table.columns()][];
        for (int i = 0; i < table.columns(); i++) {
            column[i] = table.column(i);
        }
        return Table.of(table.rows(), repeat, table.width(), column);
    }

    /**
     * What packs a column: the copy of ST4 in this repository, or the
     * executable {@code -p} names.
     */
    private static Packer packer(String named, String copies) {
        if (named.isEmpty()) {
            return copies.isEmpty() ? new St4()
                    : new St4(true, seconds(copies));
        }
        return new St4Beside(Path.of(named), copies);
    }

    /** The seconds {@code -copiesS} searches for, or zero. */
    private static double seconds(String copies) {
        try {
            return copies.length() > 2
                    ? Double.parseDouble(copies.substring(2)) : 0;
        } catch (NumberFormatException notANumber) {
            notRead("-copies" + copies.substring(2));
            return 0;
        }
    }

    /**
     * The whole number an argument gives behind its two letter flag, or the
     * tool's line and exit 2 where what stands there is not one.
     */
    private static int number(String arg) {
        try {
            return Integer.parseInt(arg.substring(2).strip());
        } catch (NumberFormatException notANumber) {
            notRead(arg);
            return -1;
        }
    }

    /** The tool's line for an argument it does not read, and exit 2. */
    private static void notRead(String arg) {
        System.err.println("dtx-write does not read " + arg);
        System.exit(2);
    }
}
