package org.dtx;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A DTX file as a standalone 68000 image: the code, then the table's bytes,
 * reached PC relative.
 *
 * <p>The code is {@code 68k/DTX.S}, which rmac assembles. This writes no
 * instruction: it states what one table settles, as equates and as lists of
 * macro invocations in {@code DTX_table.i}, and the template turns those
 * into the bodies. The table's bytes follow the code, appended to what rmac
 * writes, so {@code _table} is the last label of the template and the code
 * ends on a long to put the table there.
 *
 * <p>{@code doc/abi.md} states the five calls, the format block and the
 * state block.
 */
public final class Packager {

    /** The state block's fields, from doc/abi.md 3. */
    static final int ROW = 0;
    static final int TURN = 4;
    static final int DECODED = 8;
    static final int PARK = 12;
    static final int CURSOR = 24;

    /** What the format block runs to, doc/abi.md 1. */
    static final int FORMAT = 20;

    /** Where it stands: behind the six slots. */
    static final int FORMAT_AT = 24;

    private Packager() {
    }

    /** The widths a table of these holds, in the order 1, 2, 4. */
    private static int[] classes(int[] width) {
        List<Integer> out = new ArrayList<>();
        for (int w : new int[] {1, 2, 4}) {
            for (int held : width) {
                if (held == w) {
                    out.add(w);
                    break;
                }
            }
        }
        int[] taken = new int[out.size()];
        for (int i = 0; i < taken.length; i++) {
            taken[i] = out.get(i);
        }
        return taken;
    }

    /** What a DTX2 payload states: the ring, the unit and the offsets. */
    record Packed(int ring, int unit, int[] at) {}

    /** The {@code N}, {@code k} and data set offsets a DTX2 payload gives. */
    static Packed packed(byte[] file, Dtx.Header header) {
        int payload = header.length();
        int ring = Dtx.getWord(file, payload);
        int unit = file[payload + 2] & 0xFF;
        int[] at = new int[header.columns()];
        for (int i = 0; i < at.length; i++) {
            at[i] = Dtx.getLong(file, payload + 4 + 4 * i);
        }
        return new Packed(ring, unit, at);
    }

    /**
     * The period a table of these takes, the smallest that meets every rule
     * of doc/abi.md 4.
     *
     * @throws IllegalArgumentException naming the rule no period meets
     */
    static int period(Dtx.Header header, Packed packed) {
        int[] width = header.width();
        int rows = header.rows();
        int n = packed.ring();
        int k = packed.unit();
        int columns = width.length;
        int widest = 0;
        for (int w : width) {
            widest = Math.max(widest, w);
        }
        if ((long) (columns - 1) * n > 32767) {
            throw new IllegalArgumentException("a read reaches column "
                    + (columns - 1) + " at " + (long) (columns - 1) * n
                    + ", past the 32767 a 68000 displacement holds:"
                    + " C is at most " + (32767 / n + 1) + " at N of " + n);
        }
        if (rows % k != 0) {
            throw new IllegalArgumentException(
                    "R is " + rows + ", which does not divide by k of " + k);
        }
        for (int p = columns; p <= rows; p++) {
            if (n < 2 * p * widest) {
                break;
            }
            boolean holds = true;
            for (int w : width) {
                long budget = (long) p * w / k;
                holds &= n % (p * w) == 0 && (long) p * w % k == 0
                        && budget >= 1 && budget <= 65535;
            }
            if (holds) {
                return p;
            }
        }
        throw new IllegalArgumentException("no period from C of " + columns
                + " to R of " + rows + " holds N of " + n + " and k of " + k
                + ": N divides by P times every width, is at least twice"
                + " that, and every budget is a whole number of units");
    }

    /** The state block a plain reader of this table takes, in bytes. */
    public static int stateBytes(Dtx.Header header) {
        if (header.variant() == Dtx.DTX0) {
            return CURSOR + 4;
        }
        return CURSOR + 4 * classes(header.width()).length;
    }

    /** The state block a packaged DTX2 reader takes, in bytes. */
    static int stateBytes(Dtx.Header header, Packed packed) {
        return ring(header) + packed.ring() * header.columns();
    }

    /** Where the slots stand in the state block. */
    static int slot(Dtx.Header header) {
        return CURSOR + 4 * classes(header.width()).length;
    }

    /** Where the rings stand in the state block. */
    static int ring(Dtx.Header header) {
        return slot(header) + 32 * header.columns();
    }

    /**
     * The address register a width class's cursor stands in: a2, a3 and a4.
     * {@code a1} is the row's destination and {@code a0} the state block,
     * and a0 stays the block through a read so that {@code DTX_take} reaches
     * the block afterwards without loading it again (doc/abi.md 2).
     */
    private static String cursor(int at) {
        return "a" + (2 + at);
    }

    /** log2 of {@code of}, where it is a power of two, or -1. */
    private static int shift(int of) {
        return Integer.bitCount(of) == 1
                ? Integer.numberOfTrailingZeros(of) : -1;
    }

    /** One {@code NAME equ VALUE} line. */
    private static String equ(String name, long value) {
        return name + (name.length() < 8 ? "\t" : "") + "\tequ\t" + value + "\n";
    }

    /**
     * What one table settles, as 68k/DTX.S reads it: the equates, and one
     * macro invocation a class or a column.
     *
     * @throws IllegalArgumentException where R6's bounds do not hold, or
     *     where no period holds every rule of doc/abi.md 4
     */
    public static String table(byte[] file) {
        return table(file, false);
    }

    /**
     * The same, for a table whose columns were packed with copies from the
     * literal stream. The decoder then takes {@code ST4_WINDOW equ 1}, and
     * the image is code in RAM: {@code ST4_init} writes the reach into two
     * of its own instructions, so a 68030 caller flushes the instruction
     * cache after every call that seeds a decoder.
     *
     * @param copies whether the columns were packed with {@code st4 -c}
     */
    public static String table(byte[] file, boolean copies) {
        Dtx.Header header = Dtx.header(file);
        int variant = header.variant();
        if (variant != Dtx.DTX0 && variant != Dtx.DTX1
                && variant != Dtx.DTX2) {
            throw new IllegalArgumentException(
                    "the variant is 0, 1 or 2, not " + variant);
        }
        int[] width = header.width();
        int rows = header.rows();
        int[] taken = classes(width);
        int rowBytes = 0;
        for (int w : width) {
            rowBytes += w;
        }
        Packed packed = variant == Dtx.DTX2
                ? packed(file, header) : new Packed(0, 0, new int[0]);
        int period = variant == Dtx.DTX2 ? period(header, packed) : 1;
        int n = packed.ring();
        int readSize = rowBytes % 4 == 0 ? 4 : rowBytes % 2 == 0 ? 2 : 1;

        // The base of a class: where its first column's bytes begin, in the
        // payload under DTX1 and in the ring area under DTX2.
        int[] at = Dtx1.offsets(rows, width);
        int[] base = new int[taken.length];
        for (int c = 0; c < taken.length; c++) {
            int firstOfClass = first(width, taken[c]);
            base[c] = variant == Dtx.DTX2 ? firstOfClass * n : at[firstOfClass];
        }

        StringBuilder out = new StringBuilder();
        out.append("; What org.dtx.Packager states of one table, for"
                        + " 68k/DTX.S to read.\n")
                .append("; DTX").append(variant).append(", R = ").append(rows)
                .append(", C = ").append(width.length)
                .append(", RR = ").append(header.repeat()).append('\n')
                .append("; Every instruction is the template's; nothing here"
                        + " is one.\n\n")
                .append("; The state block, doc/abi.md 3.\n")
                .append(equ("DTX_ROW", ROW))
                .append(equ("DTX_TURN", TURN))
                .append(equ("DTX_DECODED", DECODED))
                .append(equ("DTX_PARK", PARK))
                .append(equ("DTX_CURSOR", CURSOR))
                .append('\n')
                .append(equ("DTX_VARIANT", variant))
                .append(equ("DTX_ROWS", rows))
                .append(equ("DTX_REPEAT", header.repeat()))
                .append(equ("DTX_COLUMNS", width.length))
                .append(equ("DTX_CLASSES", taken.length))
                .append(equ("DTX_ROWBYTES", rowBytes))
                .append(equ("DTX_HEADER", header.length()))
                // A negative repeat count crashes rmac even inside a
                // conditional it never takes, so the shift stays at or
                // above zero and a flag says whether it is the one to use.
                .append(equ("DTX_ROWPOW", shift(rowBytes) >= 0 ? 1 : 0))
                .append(equ("DTX_ROWSHIFT", Math.max(0, shift(rowBytes))))
                .append(equ("DTX_READSIZE", readSize))
                .append(equ("DTX_READCOUNT", rowBytes / readSize))
                .append(equ("DTX_STATE", variant == Dtx.DTX2
                        ? stateBytes(header, packed) : stateBytes(header)));
        if (variant == Dtx.DTX2) {
            out.append(equ("DTX_PERIOD", period))
                    .append(equ("DTX_N", n))
                    .append(equ("DTX_SLOT", slot(header)))
                    .append(equ("DTX_RING", ring(header)))
                    .append(equ("ST4_UNIT", packed.unit()));
            if (copies) {
                out.append(equ("ST4_WINDOW", 1))
                        .append("; the columns were packed with st4 -c, so"
                                + " the decoder takes the copy code\n");
            }
        }

        out.append("\n; One a width class: the cursor's place in the block,"
                + " its width and its base.\n");
        out.append(list("DTX_SEED_CURSORS", taken.length, c ->
                "DTX_SEED_CURSOR " + 4 * c + "," + taken[c] + "," + base[c]));
        if (variant == Dtx.DTX1) {
            out.append(list("DTX_JUMP_CURSORS", taken.length, c ->
                    "DTX_JUMP_CURSOR " + 4 * c + ","
                            + shift(taken[c]) + "," + base[c]));
        }
        out.append(list("DTX_STEP_CURSORS", taken.length, c ->
                "DTX_STEP_CURSOR " + 4 * c + "," + taken[c] + "," + base[c]));

        if (variant != Dtx.DTX0) {
            out.append(list("DTX_LOAD_CURSORS", taken.length, c ->
                    "DTX_LOAD_CURSOR " + 4 * c + "," + cursor(c)));
            out.append(list("DTX_STEP_HELD_CURSORS", taken.length, c ->
                    variant == Dtx.DTX2
                            ? "DTX_STEP_HELD_RING " + 4 * c + "," + taken[c]
                                    + "," + base[c] + "," + cursor(c)
                            : "DTX_STEP_HELD " + 4 * c + "," + taken[c] + ","
                                    + cursor(c)));
            out.append("\n; One a column: its width, its displacement off its"
                    + " class cursor, and\n; the register that cursor stands"
                    + " in.\n");
            out.append(list("DTX_READ_ROW", width.length, i -> {
                int c = classOf(taken, width[i]);
                int away = variant == Dtx.DTX2
                        ? (i - first(width, width[i])) * n : at[i] - base[c];
                return "DTX_VALUE " + width[i] + "," + away + "," + cursor(c);
            }));
        }

        if (variant == Dtx.DTX2) {
            int payload = header.length();
            int kshift = shift(packed.unit());
            out.append("\n; One a column: the four streams of its data set,"
                    + " and the shifts that\n; take a refill's rows to a"
                    + " budget in units.\n");
            out.append(list("DTX_FILL_COLUMNS", width.length, i -> {
                int set = packed.at()[i];
                return "DTX_FILL " + i + "," + shift(width[i]) + "," + kshift
                        + "," + (set + 28)
                        + "," + (set + Dtx.getLong(file, payload + set + 8))
                        + "," + (set + Dtx.getLong(file, payload + set + 12))
                        + "," + (set + Dtx.getLong(file, payload + set + 16));
            }));
            out.append(list("DTX_REFILL_TABLE", width.length, i ->
                    "DTX_REFILL_SLOT " + i));
            out.append(list("DTX_REFILL_BODIES", width.length, i ->
                    "DTX_REFILL " + i + "," + shift(width[i]) + "," + kshift));
        }
        return out.toString();
    }

    /** What one entry of a list gives. */
    private interface Entry {
        String at(int index);
    }

    /** A list macro of {@code count} invocations, one an index. */
    private static String list(String name, int count, Entry entry) {
        StringBuilder out = new StringBuilder("\t.macro\t" + name + "\n");
        for (int i = 0; i < count; i++) {
            out.append("\t").append(entry.at(i)).append('\n');
        }
        return out.append("\t.endm\n").toString();
    }

    /** The first column of {@code w} bytes. */
    private static int first(int[] width, int w) {
        for (int i = 0; i < width.length; i++) {
            if (width[i] == w) {
                return i;
            }
        }
        throw new IllegalArgumentException("no column is " + w + " bytes wide");
    }

    /** Where {@code w} stands among the widths a table holds. */
    private static int classOf(int[] taken, int w) {
        for (int i = 0; i < taken.length; i++) {
            if (taken[i] == w) {
                return i;
            }
        }
        throw new IllegalArgumentException("no class holds " + w);
    }

    /** Where the templates and the carried decoder stand. */
    static String carried() {
        String named = System.getenv("DTX_68K");
        return named == null ? "68k" : named;
    }

    /** The template a variant is read by: one a variant, no call in it
     * testing which it holds. */
    static String template(int variant) {
        return "DTX" + variant + ".S";
    }

    /**
     * The raw image: rmac's assembly of 68k/DTX.S for this table, with the
     * table's bytes behind it.
     *
     * @param rmac the assembler to run
     */
    public static byte[] image(byte[] file, Path rmac) {
        return image(file, rmac, false);
    }

    /** The same, for a table packed with copies from the literal stream. */
    public static byte[] image(byte[] file, Path rmac, boolean copies) {
        try {
            Path work = Files.createTempDirectory("dtx68");
            try {
                Path states = work.resolve("DTX_table.i");
                Path out = work.resolve("image.bin");
                Files.writeString(states, table(file, copies));
                Process run = new ProcessBuilder(rmac.toString(), "-m68000",
                        "-fr", "+o3", "-i" + work, "-i" + carried(),
                        "-o", out.toString(),
                        Path.of(carried(),
                                template(Dtx.header(file).variant())).toString())
                        .redirectErrorStream(true).start();
                byte[] said = run.getInputStream().readAllBytes();
                if (run.waitFor() != 0 || !Files.exists(out)) {
                    throw new IllegalStateException(rmac + " gave "
                            + new String(said).trim());
                }
                byte[] code = Files.readAllBytes(out);
                // The format block states where the table stands, and the
                // table is appended here: the two agree or the image would
                // read its own code as a header.
                int table = Dtx.getLong(code, FORMAT_AT + 8);
                if (table != code.length) {
                    throw new IllegalStateException("the code runs to "
                            + code.length + " bytes and the format block puts"
                            + " the table at " + table
                            + ": it would not land there");
                }
                byte[] image = new byte[code.length + file.length];
                System.arraycopy(code, 0, image, 0, code.length);
                System.arraycopy(file, 0, image, code.length, file.length);
                return image;
            } finally {
                try (var walk = Files.walk(work)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException gone) {
                                    throw new UncheckedIOException(gone);
                                }
                            });
                }
            }
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(stopped);
        }
    }

    /** Reads the DTX file named first and writes the image named second. */
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println(
                    "Packager in.dtx out.bin [-aRMAC] [-s] [-copies]");
            System.exit(2);
            return;
        }
        String rmac = "rmac";
        boolean states = false;
        boolean copies = false;
        for (int i = 2; i < args.length; i++) {
            if (args[i].startsWith("-a")) {
                rmac = args[i].substring(2);
            } else if (args[i].equals("-s")) {
                states = true;
            } else if (args[i].equals("-copies")) {
                copies = true;
            } else {
                System.err.println("Packager does not read " + args[i]);
                System.exit(2);
                return;
            }
        }
        byte[] file = Files.readAllBytes(Path.of(args[0]));
        Dtx.Header header = Dtx.header(file);
        if (states) {
            Files.writeString(Path.of(args[1]), table(file, copies));
        } else {
            Files.write(Path.of(args[1]), image(file, Path.of(rmac), copies));
        }
        long bytes = Files.size(Path.of(args[1]));
        int state = header.variant() == Dtx.DTX2
                ? stateBytes(header, packed(file, header)) : stateBytes(header);
        System.out.printf("%s -> DTX%d %s %d bytes, table %d bytes,"
                + " %d rows, %d columns, state block %d bytes%n",
                args[0], header.variant(), states ? "figures" : "image",
                bytes, file.length, header.rows(), header.columns(), state);
    }
}
