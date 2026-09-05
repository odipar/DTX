package org.dtx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A DTX file of any variant out of comma separated text.
 *
 * <p>{@code Write in.csv out.dtx [-vV] [-wW,W,..] [-rRR] [-kK] [-mN]
 * [-pPACKER]}: {@code V} is the variant, {@code W} a column's width,
 * {@code RR} the row the table repeats to, and {@code K}, {@code N} and
 * {@code PACKER} what DTX2 takes to pack.
 *
 * <p>Without {@code -w} each column takes the narrowest width of 1, 2 and 4
 * bytes that takes every value of it. Without {@code -r} the table does not
 * repeat, so {@code RR} is {@code R}.
 */
public final class Write {

    private Write() {
    }

    /** Reads the text named first and writes the DTX file named second. */
    public static void main(String[] args) throws IOException {
        if (Help.among(args)) {
            System.out.print(Help.WRITE);
            return;
        }
        if (args.length < 2) {
            System.err.print(Help.WRITE);
            System.exit(2);
            return;
        }
        int variant = Dtx.DTX0;
        String widths = "";
        int repeat = -1;
        int unit = 1;
        int ring = 960;
        String packer = "";
        String copies = "";
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("-v")) {
                variant = Integer.parseInt(arg.substring(2));
            } else if (arg.startsWith("-w")) {
                widths = arg.substring(2);
            } else if (arg.startsWith("-r")) {
                repeat = Integer.parseInt(arg.substring(2));
            } else if (arg.startsWith("-k")) {
                unit = Integer.parseInt(arg.substring(2));
            } else if (arg.startsWith("-m")) {
                ring = Integer.parseInt(arg.substring(2));
            } else if (arg.startsWith("-copies")) {
                // the packer's own: a match beyond the ring copies from the
                // literal stream, and -copiesS searches S seconds for a
                // better parse. YMX spells it the same way.
                copies = "-c" + arg.substring(7);
            } else if (arg.startsWith("-p")) {
                packer = arg.substring(2);
            } else {
                System.err.println("dtx-write does not read " + arg);
                System.exit(2);
                return;
            }
        }
        String text = Files.readString(Path.of(args[0]));
        int[] width = widths.isEmpty() ? Csv.widths(text) : widths(widths);
        Table table = repeat < 0
                ? Csv.table(text, width) : Csv.table(text, width, repeat);
        byte[] out = switch (variant) {
            case Dtx.DTX0 -> Dtx0.write(table);
            case Dtx.DTX1 -> Dtx1.write(table);
            case Dtx.DTX2 -> Dtx2.write(table, packer(packer, copies),
                    unit, ring);
            default -> throw new IllegalArgumentException(
                    "the variant is 0, 1 or 2, not " + variant);
        };
        Files.write(Path.of(args[1]), out);
        StringBuilder drawn = new StringBuilder();
        for (int i = 0; i < table.columns(); i++) {
            drawn.append(i == 0 ? "" : ",").append(table.width(i));
        }
        System.out.printf("%s -> DTX%d %d bytes, %d rows, %d columns,"
                + " widths %s, RR=%d%s%n", args[0], variant, out.length,
                table.rows(), table.columns(), drawn, table.repeat(),
                variant == Dtx.DTX2 ? ", k=" + unit + ", N=" + ring : "");
    }

    /**
     * What packs a column: the copy in this repository, or a packer
     * beside it where {@code -p} names one.
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
        return copies.length() > 2 ? Double.parseDouble(copies.substring(2)) : 0;
    }

    /** The widths {@code -w} gives, one a column. */
    private static int[] widths(String given) {
        String[] cell = given.split(",", -1);
        int[] width = new int[cell.length];
        for (int i = 0; i < cell.length; i++) {
            width[i] = Integer.parseInt(cell[i].strip());
        }
        return width;
    }
}
