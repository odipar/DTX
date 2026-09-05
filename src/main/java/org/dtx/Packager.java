package org.dtx;

import java.io.IOException;
import java.io.InputStream;
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

    /** The format block's fields, doc/abi.md 1. */
    static final int STATE_BYTES = 4;
    static final int TABLE_AT = 8;
    static final int ROWBYTES_AT = 12;
    static final int PERIOD_AT = 14;
    static final int RING_AT = 16;
    static final int UNIT_AT = 18;
    static final int COLUMNS_AT = 20;

    /** Where the carried code stands on the classpath. */
    private static final String CARRIED = "/org/dtx/68k/";

    /** What one entry of the column table runs to. */
    static final int ENTRY = 4;

    /** What the column table's own header runs to: counts, the row's bytes
     * and where each width class begins. */
    static final int ENTRIES = 32;

    /** What one stream record runs to, one a column under DTX2. */
    static final int STREAM = 32;

    /** What a packed reader's state block holds before its decoder states. */
    static final int PACKED_HEAD = 80;

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

    /**
     * What a DTX2 payload states: the ring, the unit, whether its columns
     * hold copies from the literal stream, and where each data set begins.
     */
    record Packed(int ring, int unit, boolean copies, int[] at) {}

    /**
     * What a DTX2 payload gives, SPEC.md 2.3.
     *
     * <p>It checks the data sets against it. Every set opens with
     * {@code $53 $34 $07 k}, so one compare against the payload's own
     * {@code k} holds ST4's signature, its format version and R5.2 at once.
     *
     * @throws IllegalArgumentException where a data set states another
     *     version or another unit than the payload does
     */
    static Packed packed(byte[] file, Dtx.Header header) {
        int payload = header.length();
        int ring = Dtx.getWord(file, payload);
        int unit = file[payload + 2] & 0xFF;
        boolean copies = (file[payload + 3] & Dtx2.COPIES) != 0;
        int[] at = new int[header.columns()];
        int signature = 0x53340700 + unit;
        for (int i = 0; i < at.length; i++) {
            at[i] = Dtx.getLong(file, payload + 4 + 4 * i);
            int said = Dtx.getLong(file, payload + at[i]);
            if (said != signature) {
                throw new IllegalArgumentException(String.format(
                        "column %d's data set opens %08X and the payload"
                        + " states %08X: an ST4 data set opens with S4, the"
                        + " format version 7 and the payload's own k",
                        i, said, signature));
            }
        }
        return new Packed(ring, unit, copies, at);
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
        // DTX1 holds three cursors and the three places their classes
        // begin, at any widths the table states, so its block does not
        // move with C either.
        return header.variant() == Dtx.DTX1
                ? 48 : CURSOR + 4 * classes(header.width()).length;
    }

    /** The state block a packaged DTX2 reader takes, in bytes. */
    static int stateBytes(Dtx.Header header, Packed packed) {
        return ring(header) + packed.ring() * header.columns();
    }

    /** Where the decoder states stand in the state block, doc/abi.md 3. */
    static int decoders(Dtx.Header header) {
        return PACKED_HEAD;
    }

    /** Where the rings stand in the state block. */
    static int ring(Dtx.Header header) {
        return decoders(header) + 32 * header.columns();
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
        Dtx.Header header = Dtx.header(file);
        int variant = header.variant();
        if (variant != Dtx.DTX0 && variant != Dtx.DTX1
                && variant != Dtx.DTX2) {
            throw new IllegalArgumentException(
                    "the variant is 0, 1 or 2, not " + variant);
        }
        int[] width = header.width();
        int rows = header.rows();
        int rowBytes = 0;
        for (int w : width) {
            rowBytes += w;
        }
        Packed packed = variant == Dtx.DTX2
                ? packed(file, header) : new Packed(0, 0, false, new int[0]);
        int period = variant == Dtx.DTX2 ? period(header, packed) : 1;
        // The payload states whether its columns hold copies (R5.10), so
        // the decoder built for them is settled by the file and not by a
        // word carried beside it. That build writes the reach into two of
        // its own instructions, and a 68030 caller flushes the instruction
        // cache after every call that seeds a decoder.
        boolean copies = packed.copies();

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
                .append(equ("DTX_ROWBYTES", rowBytes))
                .append(equ("DTX_STATE", variant == Dtx.DTX2
                        ? stateBytes(header, packed) : stateBytes(header)));
        if (variant == Dtx.DTX2) {
            out.append(equ("DTX_PERIOD", period))
                    .append(equ("DTX_N", packed.ring()))
                    .append(equ("ST4_UNIT", packed.unit()));
            if (copies) {
                out.append(equ("ST4_WINDOW", 1))
                        .append("; the columns were packed with st4 -c, so"
                                + " the decoder takes the copy code\n");
            }
        }

        return out.toString();
    }

    /**
     * The column table the image holds behind its code: one entry a column,
     * grouped by width so each of a read's three loops walks a run of them.
     *
     * <p>Under DTX1 an entry is four bytes: the column's displacement from
     * the base of its width class, and where its value stands in the row.
     * The reader takes both as words off an index register, so one loop a
     * width serves any number of columns and no code stands a column.
     */
    static byte[] columnTable(byte[] file) {
        Dtx.Header header = Dtx.header(file);
        if (header.variant() == Dtx.DTX0) {
            // A DTX0 row is one run of bytes: no loop walks a column, so
            // there is nothing a column table would state.
            return new byte[0];
        }
        int[] width = header.width();
        int[] at = Dtx1.offsets(header.rows(), width);
        int[] count = counts(width);
        int[] base = bases(header);
        int rowBytes = 0;
        for (int w : width) {
            rowBytes += w;
        }
        boolean packed = header.variant() == Dtx.DTX2;
        Packed given = packed
                ? packed(file, header) : new Packed(0, 0, false, new int[0]);
        int n = given.ring();
        int records = ENTRIES + ENTRY * width.length;
        byte[] out = new byte[records + (packed ? STREAM * width.length : 0)];
        for (int c = 0; c < 3; c++) {
            Dtx.putWord(out, 2 * c, count[c]);
            // Under DTX2 a class begins at its first column's ring in the
            // state block, not at its column's place in the payload.
            Dtx.putLong(out, 8 + 4 * c,
                    packed ? ringOf(header, c, n) : base[c]);
        }
        Dtx.putWord(out, 6, rowBytes);
        Dtx.putWord(out, 20, width.length);
        Dtx.putWord(out, 22, packed ? period(header, given) : 1);
        Dtx.putLong(out, 24, records);
        Dtx.putLong(out, 28, n);
        int wrote = ENTRIES;
        for (int c = 0; c < 3; c++) {
            int w = c == 0 ? 1 : c == 1 ? 2 : 4;
            int first = -1;
            for (int i = 0; i < width.length; i++) {
                if (width[i] == w && first < 0) {
                    first = i;
                }
            }
            int row = 0;
            for (int i = 0; i < width.length; i++) {
                if (width[i] == w) {
                    Dtx.putWord(out, wrote,
                            packed ? (i - first) * n : at[i] - base[c]);
                    Dtx.putWord(out, wrote + 2, row);
                    wrote += ENTRY;
                }
                row += width[i];
            }
        }
        if (packed) {
            int payload = header.length();
            for (int i = 0; i < width.length; i++) {
                int rec = records + STREAM * i;
                int set = given.at()[i];
                Dtx.putLong(out, rec, set + 28);
                Dtx.putLong(out, rec + 4,
                        set + Dtx.getLong(file, payload + set + 8));
                Dtx.putLong(out, rec + 8,
                        set + Dtx.getLong(file, payload + set + 12));
                Dtx.putLong(out, rec + 12,
                        set + Dtx.getLong(file, payload + set + 16));
                Dtx.putLong(out, rec + 16, ring(header) + i * n);
                Dtx.putLong(out, rec + 20, decoders(header) + 32 * i);
                Dtx.putWord(out, rec + 24, shift(width[i]));
                Dtx.putWord(out, rec + 26, shift(given.unit()));
            }
        }
        return out;
    }

    /** Where a width class's ring begins in the state block. */
    static int ringOf(Dtx.Header header, int c, int n) {
        int w = c == 0 ? 1 : c == 1 ? 2 : 4;
        int[] width = header.width();
        for (int i = 0; i < width.length; i++) {
            if (width[i] == w) {
                return ring(header) + i * n;
            }
        }
        return ring(header);
    }

    /** How many columns of each width a table holds, in the order 1, 2, 4. */
    static int[] counts(int[] width) {
        int[] out = new int[3];
        for (int w : width) {
            out[w == 1 ? 0 : w == 2 ? 1 : 2]++;
        }
        return out;
    }

    /** Where each width class's first column begins in the payload. */
    static int[] bases(Dtx.Header header) {
        int[] width = header.width();
        int[] at = Dtx1.offsets(header.rows(), width);
        int[] out = new int[3];
        for (int c = 0; c < 3; c++) {
            int w = c == 0 ? 1 : c == 1 ? 2 : 4;
            out[c] = 0;
            for (int i = 0; i < width.length; i++) {
                if (width[i] == w) {
                    out[c] = at[i];
                    break;
                }
            }
        }
        return out;
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
     * The file the code for one build stands in. A variant assembles to one
     * code any table that follows it, and under DTX2 to one a build of the
     * decoder built into it: the unit it decodes at, with the copy code and
     * without.
     */
    public static String carriedName(int variant, int unit, boolean copies) {
        return variant == Dtx.DTX2
                ? "DTX2-k" + unit + (copies ? "-copies" : "") + ".bin"
                : "DTX" + variant + ".bin";
    }

    /**
     * The code for one build, as the repository holds it.
     *
     * @throws IllegalStateException where no file of that name is carried
     */
    public static byte[] carriedCode(int variant, int unit, boolean copies) {
        String name = carriedName(variant, unit, copies);
        try (InputStream in =
                     Packager.class.getResourceAsStream(CARRIED + name)) {
            if (in == null) {
                throw new IllegalStateException("no code carried at "
                        + CARRIED + name + ": bin/dtx-blobs writes it");
            }
            return in.readAllBytes();
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    /**
     * The five fields a combine writes, zeroed.
     *
     * <p>Carried code states no table. The assembler read one to build it,
     * and what it read stands in the format block: zeroing those five is
     * what makes the file a function of the template alone, and what makes
     * code shipped without a combine read a state block of zero bytes rather
     * than some other table's.
     */
    static void blank(byte[] code) {
        Dtx.putLong(code, FORMAT_AT + STATE_BYTES, 0);
        Dtx.putLong(code, FORMAT_AT + TABLE_AT, 0);
        Dtx.putWord(code, FORMAT_AT + ROWBYTES_AT, 0);
        Dtx.putWord(code, FORMAT_AT + PERIOD_AT, 0);
        Dtx.putWord(code, FORMAT_AT + RING_AT, 0);
    }

    /**
     * rmac's assembly of the variant's template for this table, the code
     * alone.
     *
     * @param rmac the assembler to run
     * @throws IllegalStateException where rmac fails, or where the code it
     *     writes and the format block in it disagree on where the column
     *     table lands
     */
    static byte[] code(byte[] file, Path rmac) {
        return code(file, rmac, carried());
    }

    /**
     * The same, from the templates in {@code templates} rather than from
     * where {@code DTX_68K} puts them. A tool run from outside this
     * repository has no directory to resolve a relative one against.
     */
    static byte[] code(byte[] file, Path rmac, String templates) {
        try {
            Path work = Files.createTempDirectory("dtx68");
            try {
                Path states = work.resolve("DTX_table.i");
                Path out = work.resolve("image.bin");
                Files.writeString(states, table(file));
                Process run = new ProcessBuilder(rmac.toString(), "-m68000",
                        "-fr", "+o3", "-i" + work, "-i" + templates,
                        "-o", out.toString(),
                        Path.of(templates,
                                template(Dtx.header(file).variant())).toString())
                        .redirectErrorStream(true).start();
                byte[] said = run.getInputStream().readAllBytes();
                if (run.waitFor() != 0 || !Files.exists(out)) {
                    throw new IllegalStateException(rmac + " gave "
                            + new String(said).trim());
                }
                return Files.readAllBytes(out);
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

    /**
     * One image: this code, the column table, the table's bytes, and the
     * format block written to state the three.
     *
     * <p>The code is the same bytes any table that follows it, so what a
     * combine writes is the five fields the table settles. It checks the
     * two it cannot write: the variant, and under DTX2 the unit the decoder
     * built into the code decodes at.
     *
     * @throws IllegalStateException where the code is for another variant or
     *     another unit, or where it and its format block disagree on where
     *     the column table lands
     */
    static byte[] combine(byte[] code, byte[] file) {
        Dtx.Header header = Dtx.header(file);
        int variant = header.variant();
        if (code[FORMAT_AT] != 'D' || code[FORMAT_AT + 1] != 'T'
                || code[FORMAT_AT + 2] != 'X') {
            throw new IllegalStateException(
                    "the code opens with no format block");
        }
        if (code[FORMAT_AT + 3] != variant) {
            throw new IllegalStateException("the code reads DTX"
                    + code[FORMAT_AT + 3] + " and the table is DTX" + variant);
        }
        // The code ends where the format block states the column table
        // begins: the two agree, or the image reads its own last
        // instruction as a column.
        int columns = Dtx.getLong(code, FORMAT_AT + COLUMNS_AT);
        if (columns != code.length) {
            throw new IllegalStateException("the code runs to " + code.length
                    + " bytes and the format block puts the column table at "
                    + columns + ": it would not land there");
        }
        Packed given = variant == Dtx.DTX2
                ? packed(file, header) : new Packed(0, 0, false, new int[0]);
        int unit = code[FORMAT_AT + UNIT_AT] & 0xFF;
        if (unit != given.unit()) {
            throw new IllegalStateException("the code decodes at a unit of "
                    + unit + " and the table was packed at " + given.unit());
        }
        int rowBytes = 0;
        for (int w : header.width()) {
            rowBytes += w;
        }
        byte[] entries = columnTable(file);
        byte[] image = new byte[code.length + entries.length + file.length];
        System.arraycopy(code, 0, image, 0, code.length);
        System.arraycopy(entries, 0, image, code.length, entries.length);
        System.arraycopy(file, 0, image, code.length + entries.length,
                file.length);
        Dtx.putLong(image, FORMAT_AT + STATE_BYTES, variant == Dtx.DTX2
                ? stateBytes(header, given) : stateBytes(header));
        // The table stands behind both, and only the packager holds the
        // figure: the column table's size moves with C, so the assembler
        // could not have worked it out.
        Dtx.putLong(image, FORMAT_AT + TABLE_AT, code.length + entries.length);
        Dtx.putWord(image, FORMAT_AT + ROWBYTES_AT, rowBytes);
        Dtx.putWord(image, FORMAT_AT + PERIOD_AT,
                variant == Dtx.DTX2 ? period(header, given) : 1);
        Dtx.putWord(image, FORMAT_AT + RING_AT, given.ring());
        return image;
    }

    /**
     * The image, combined from the code this repository holds.
     *
     * <p>Which of the eight it takes is the file's to state: the variant,
     * and under DTX2 the unit its data sets are packed at and whether they
     * hold copies from the literal stream (R5.10). No word from a caller
     * enters it, so no word can disagree with the bytes.
     */
    public static byte[] image(byte[] file) {
        Dtx.Header header = Dtx.header(file);
        if (header.variant() != Dtx.DTX2) {
            return combine(carriedCode(header.variant(), 0, false), file);
        }
        Packed given = packed(file, header);
        return combine(
                carriedCode(Dtx.DTX2, given.unit(), given.copies()), file);
    }

    /**
     * The image, from rmac's assembly of the template rather than from the
     * carried code. The two give the same bytes; this path checks
     * that, and what a change to a template is tried through.
     *
     * @param rmac the assembler to run
     */
    public static byte[] image(byte[] file, Path rmac) {
        return combine(code(file, rmac), file);
    }

    /** Reads the DTX file named first and writes the image named second. */
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Packager in.dtx out.bin [-aRMAC] [-s]");
            System.exit(2);
            return;
        }
        String rmac = null;
        boolean states = false;
        for (int i = 2; i < args.length; i++) {
            if (args[i].startsWith("-a")) {
                rmac = args[i].substring(2);
            } else if (args[i].equals("-s")) {
                states = true;
            } else {
                System.err.println("Packager does not read " + args[i]);
                System.exit(2);
                return;
            }
        }
        byte[] file = Files.readAllBytes(Path.of(args[0]));
        Dtx.Header header = Dtx.header(file);
        if (states) {
            Files.writeString(Path.of(args[1]), table(file));
        } else if (rmac == null) {
            Files.write(Path.of(args[1]), image(file));
        } else {
            Files.write(Path.of(args[1]), image(file, Path.of(rmac)));
        }
        long bytes = Files.size(Path.of(args[1]));
        int state = header.variant() == Dtx.DTX2
                ? stateBytes(header, packed(file, header)) : stateBytes(header);
        System.out.printf("%s -> DTX%d %s %d bytes, table %d bytes,"
                + " %d rows, %d columns, state block %d bytes%n",
                args[0], header.variant(),
                states ? "figures" : rmac == null ? "image" : "image assembled",
                bytes, file.length, header.rows(), header.columns(), state);
    }
}
