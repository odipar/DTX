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
 * <p>The code is the variant's template under {@code 68k/}: DTX0.S, DTX1.S
 * or DTX2.S, which rmac assembles. This class does not write an instruction:
 * it defines what one table gives, as equates in {@code DTX_table.i}. The
 * column table and the table's bytes follow the code, appended to what rmac
 * writes, so {@code _columns} is the last label of the template and the code
 * ends on a long to put them there.
 *
 * <p>{@code doc/abi.md} defines the four calls, the format block and the
 * state block.
 */
public final class Packager {

    /** The state block's fields, from doc/abi.md 3. */
    static final int TURN = 0;
    static final int PARK = 4;
    static final int POINTER = 8;

    /** Where the format block stands: behind the four slots. */
    static final int FORMAT_AT = 16;

    /** What the format block runs to, so where the bodies begin. */
    static final int FORMAT = 28;

    /** The format block's fields, doc/abi.md 1. */
    static final int STATE_BYTES = 4;
    static final int TABLE_AT = 8;
    static final int ROWBYTES_AT = 12;
    static final int PERIOD_AT = 14;
    static final int RING_AT = 16;
    static final int UNIT_AT = 18;
    static final int WIDTH_AT = 19;
    static final int COLUMNS_AT = 20;
    static final int STRIDE_AT = 24;

    /** Where the carried code stands on the classpath. */
    private static final String CARRIED = "/org/dtx/68k/";

    /** What one stream record runs to, one a column under DTX2. */
    static final int STREAM = 16;

    /**
     * What one decoder state takes: the eight registers, the ring's end,
     * where the registers go at a loop, and the budget.
     */
    static final int STATE = 48;

    /** What the copy of a decoder's registers takes, where a pass is replayed. */
    static final int SAVED = 32;

    /** What a packed reader's state block contains before its decoder states. */
    static final int PACKED_HEAD = 72;

    /** The state block DTX0 and DTX1 take: the head, and one pointer. */
    static final int PLAIN = POINTER + 4;

    private Packager() {
    }

    /**
     * What a DTX2 payload defines: the ring, the unit, whether its columns
     * contain copies from the literal stream, and where each data set begins.
     */
    record Packed(int ring, int unit, boolean copies, boolean replayed,
            int[] at) {}

    /**
     * What a DTX2 payload gives, SPEC.md 2.3.
     *
     * <p>It checks the data sets against it. Every set opens with
     * {@code $53 $34 $07 k}, so one compare against the payload's own
     * {@code k} checks ST4's signature, its format version and R5.2 at once.
     *
     * @throws IllegalArgumentException where a data set defines another
     *     version or another unit than the payload does
     */
    static Packed packed(byte[] file, Dtx.Header header) {
        int payload = header.length();
        int ring = Dtx.getWord(file, payload);
        int unit = file[payload + 2] & 0xFF;
        boolean copies = (file[payload + 3] & Dtx2.COPIES) != 0;
        int[] at = new int[header.columns()];
        int signature = 0x53340700 + unit;
        boolean replayed = false;
        for (int i = 0; i < at.length; i++) {
            at[i] = Dtx.getLong(file, payload + 4 + 4 * i);
            // Byte 20 of a data set gives the unit its loop begins at, or
            // $FFFFFFFF where its end marker loops it. A set that records
            // one is replayed by the reader (abi.md 4), and every set of a
            // payload takes the same form: they are one table's columns, so
            // one loop and one length.
            replayed |= Dtx.getLong(file, payload + at[i] + 20) != -1;
            int said = Dtx.getLong(file, payload + at[i]);
            if (said != signature) {
                throw new IllegalArgumentException(String.format(
                        "column %d's data set opens %08X and the payload"
                        + " defines %08X: an ST4 data set opens with S4, the"
                        + " format version 7 and the payload's own k",
                        i, said, signature));
            }
        }
        return new Packed(ring, unit, copies, replayed, at);
    }

    /**
     * The period a table of these takes, the smallest that meets every rule
     * of doc/abi.md 4.
     *
     * @throws IllegalArgumentException naming the rule no period meets
     */
    static int period(Dtx.Header header, Packed packed) {
        int rows = header.rows();
        int width = header.width();
        int n = packed.ring();
        int k = packed.unit();
        int columns = header.columns();
        if ((long) rows * width % k != 0) {
            throw new IllegalArgumentException("a column is " + rows
                    + " times " + width + " bytes, which does not divide by"
                    + " k of " + k);
        }
        // A table shorter than a period takes the period all the same: where
        // its sets end the reader seeds its rows and the first period's budget
        // is 0, and where they loop it seeds a period's rows round the loop
        // (abi.md 4).
        for (int p = columns; ; p++) {
            long budget = (long) p * width / k;
            if (n < 2 * p * width) {
                break;
            }
            if (n % (p * width) == 0 && (long) p * width % k == 0
                    && budget >= 1 && budget <= 65535) {
                // A refill meets one mark at most, so a replayed loop is a
                // period long at least (abi.md 4).
                int loop = rows - header.repeat();
                if (packed.replayed() && loop < p) {
                    throw new IllegalArgumentException("a replayed loop of " + loop
                            + " rows is under the period of " + p + ": a refill holds one"
                            + " mark at most");
                }
                return p;
            }
        }
        throw new IllegalArgumentException("no period from C of " + columns + " up meets N of "
                + n + " and k of " + k + ": N divides by P times the width, is at least"
                + " twice that, and the budget is a whole number of units");
    }

    /**
     * The state block a plain reader of this table takes, in bytes.
     *
     * <p>The same under DTX0 and DTX1, and the same at every {@code C}:
     * every column is one width, so one pointer walks them all.
     */
    public static int stateBytes() {
        return PLAIN;
    }

    /**
     * The state block a packaged DTX2 reader takes, in bytes. A replayed
     * payload takes a copy of the registers a column behind the rings, where
     * the reader puts them at the row its loop begins (abi.md 4).
     */
    static int stateBytes(Dtx.Header header, Packed packed) {
        return ring(header, packed) + packed.ring() * header.columns()
                + (packed.replayed() ? SAVED * header.columns() : 0);
    }

    /**
     * The stride from one column's value to the next, in the row an advance
     * points at: the width under DTX0, where a row's values stand one after
     * another; the length of a column under DTX1, where the columns lie at
     * one stride; and {@code N} under DTX2, where every column has a ring of
     * that size. {@code DTX_metadata} gives it, out of the format block.
     */
    static int stride(Dtx.Header header, Packed packed) {
        return switch (header.variant()) {
            case Dtx.DTX0 -> header.width();
            case Dtx.DTX1 -> Dtx1.stride(header.rows(), header.width());
            default -> packed.ring();
        };
    }

    /** Where the decoder states stand in the state block, doc/abi.md 3. */
    static int decoders() {
        return PACKED_HEAD;
    }

    /** Where the rings stand in the state block: behind a decoder state a turn. */
    static int ring(Dtx.Header header, Packed packed) {
        return decoders() + STATE * period(header, packed);
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
     * What one table gives, as the variant's template reads it: the
     * equates DTX_table.i defines.
     *
     * @throws IllegalArgumentException where R6's bounds are not met, or
     *     where no period meets every rule of doc/abi.md 4
     */
    public static String table(byte[] file) {
        Dtx.Header header = Dtx.header(file);
        int variant = header.variant();
        if (variant != Dtx.DTX0 && variant != Dtx.DTX1
                && variant != Dtx.DTX2) {
            throw new IllegalArgumentException(
                    "the variant is 0, 1 or 2, not " + variant);
        }
        int width = header.width();
        int rows = header.rows();
        int rowBytes = header.rowBytes();
        Packed packed = variant == Dtx.DTX2
                ? packed(file, header) : new Packed(0, 0, false, false, new int[0]);
        int period = variant == Dtx.DTX2 ? period(header, packed) : 1;
        // The payload defines whether its columns contain copies (R5.10), so
        // the decoder built for them is fixed by the file and not by a
        // word carried beside it. That build writes the reach into two of
        // its own instructions, and a 68030 caller flushes the instruction
        // cache after every call that seeds a decoder.
        boolean copies = packed.copies();

        StringBuilder out = new StringBuilder();
        out.append("; What org.dtx.Packager writes of one table, for"
                        + " 68k/DTX").append(variant).append(".S to read.\n")
                .append("; DTX").append(variant).append(", R = ").append(rows)
                .append(", C = ").append(header.columns())
                .append(", W = ").append(width)
                .append(", RR = ").append(header.repeat()).append('\n')
                .append("; Every instruction is the template's; nothing here"
                        + " is one.\n\n")
                .append("; The state block, doc/abi.md 3.\n")
                .append(equ("DTX_TURN", TURN))
                .append(equ("DTX_PARK", PARK))
                .append(equ("DTX_POINTER", POINTER));
        if (variant != Dtx.DTX2) {
            // Nothing parks a6 under the plain variants, so the payload
            // init was given stands in that long instead, which a jump
            // reaches (abi.md 3). Under DTX2 the template names its own.
            out.append(equ("DTX_PAYLOAD", PARK));
        }
        out
                .append("\n; What the code takes at assembly time.\n")
                .append(equ("DTX_WIDTH", width));
        if (variant == Dtx.DTX2) {
            out.append(equ("ST4_UNIT", packed.unit()));
        }
        out.append("\n; The rest of what the table gives. A combine writes"
                        + " these into the format\n; block and the code"
                        + " reads them from there (doc/abi.md 1), so they"
                        + " stand\n; here for a caller reading the figures"
                        + " rather than for the assembler.\n")
                .append(equ("DTX_ROWBYTES", rowBytes))
                .append(equ("DTX_STATE", variant == Dtx.DTX2
                        ? stateBytes(header, packed) : stateBytes()));
        if (variant == Dtx.DTX2) {
            out.append(equ("DTX_PERIOD", period))
                    .append(equ("DTX_N", packed.ring()));
            if (copies) {
                out.append(equ("ST4_WINDOW", 1))
                        .append("; the columns were packed with st4 -c, so"
                                + " the decoder takes the copy code\n");
            }
        }

        return out.toString();
    }

    /**
     * The column table behind the image's code: under DTX2 one stream record
     * a column, four longs at a stride of 16. The plain variants do not have
     * one.
     *
     * <p>A record gives where the column's four ST4 streams begin, from the
     * payload. Its ring and its decoder state are strides rather than
     * fields: every ring is {@code N} bytes and every decoder state 32, so
     * column {@code i}'s stand {@code i} strides past column 0's.
     */
    static byte[] columnTable(byte[] file) {
        Dtx.Header header = Dtx.header(file);
        if (header.variant() != Dtx.DTX2) {
            // Every column is one width, so a pointer and a stride walk them
            // all: what a plain read takes is arithmetic on R, C and the
            // width, and no column table is written.
            return new byte[0];
        }
        Packed given = packed(file, header);
        int payload = header.length();
        byte[] out = new byte[STREAM * header.columns()];
        for (int i = 0; i < header.columns(); i++) {
            int rec = STREAM * i;
            int set = given.at()[i];
            Dtx.putLong(out, rec, set + 28);
            Dtx.putLong(out, rec + 4,
                    set + Dtx.getLong(file, payload + set + 8));
            Dtx.putLong(out, rec + 8,
                    set + Dtx.getLong(file, payload + set + 12));
            Dtx.putLong(out, rec + 12,
                    set + Dtx.getLong(file, payload + set + 16));
        }
        return out;
    }

    /** Where the templates and the carried decoder stand. */
    static String carried() {
        String named = System.getenv("DTX_68K");
        return named == null ? "68k" : named;
    }

    /** The template a variant is read by: one a variant, no call in it
     * testing which it contains. */
    static String template(int variant) {
        return "DTX" + variant + ".S";
    }

    /**
     * The file the code for one build stands in. A build assembles to one
     * code for any table it reads: DTX0 one, DTX1 one a width, DTX2 one a
     * width, a unit and the copy code or not.
     */
    public static String carriedName(int variant, int width, int unit,
            boolean copies) {
        if (variant == Dtx.DTX0) {
            // DTX0 reads a row as one run of bytes, and the move that run
            // takes comes from the row's bytes: its code does not move with
            // the width.
            return "DTX0.bin";
        }
        return variant == Dtx.DTX2
                ? "DTX2-w" + width + "-k" + unit
                        + (copies ? "-copies" : "") + ".bin"
                : "DTX1-w" + width + ".bin";
    }

    /**
     * The code for one build, as the repository has it.
     *
     * @throws IllegalStateException where no file of that name is carried
     */
    public static byte[] carriedCode(int variant, int width, int unit,
            boolean copies) {
        String name = carriedName(variant, width, unit, copies);
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
     * The six fields a combine writes, zeroed.
     *
     * <p>Carried code does not define a table. The assembler read one to build
     * it, and what it read stands in the format block: zeroing those six
     * makes the file a function of the template alone, and makes code shipped
     * without a combine read a state block of zero bytes rather than some
     * other table's.
     */
    static void blank(byte[] code) {
        Dtx.putLong(code, FORMAT_AT + STATE_BYTES, 0);
        Dtx.putLong(code, FORMAT_AT + TABLE_AT, 0);
        Dtx.putWord(code, FORMAT_AT + ROWBYTES_AT, 0);
        Dtx.putWord(code, FORMAT_AT + PERIOD_AT, 0);
        Dtx.putWord(code, FORMAT_AT + RING_AT, 0);
        Dtx.putLong(code, FORMAT_AT + STRIDE_AT, 0);
    }

    /**
     * rmac's assembly of the variant's template for this table, the code
     * alone.
     *
     * @param rmac the assembler to run
     * @throws IllegalStateException where rmac fails
     */
    static byte[] code(byte[] file, Path rmac) {
        return code(file, rmac, carried());
    }

    /**
     * The same, from the templates in {@code templates} rather than from
     * where {@code DTX_68K} puts them. A tool run from outside this
     * repository does not have a directory to resolve a relative one against.
     */
    static byte[] code(byte[] file, Path rmac, String templates) {
        try {
            Path work = Files.createTempDirectory("dtx68");
            try {
                Path defines = work.resolve("DTX_table.i");
                Path out = work.resolve("image.bin");
                Files.writeString(defines, table(file));
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

    /** An image and where each of its tables' headers stands in it, from
     *  the image's first byte: what a caller hands {@code DTX_init} in
     *  a1, one a table, in the order the tables were given. */
    public record Packaged(byte[] image, int[] headers) {
    }

    /**
     * One image: this code, then a column table and a table's bytes for
     * each file, and the format block written to define the code and the
     * first of them.
     *
     * <p>The code is the same bytes any table that follows it, so what a
     * combine writes is the six fields the first table gives. It checks
     * the three it cannot write: the variant, the width the code reads
     * values at, and under DTX2 the unit the decoder built into the code
     * decodes at. Every table past the first meets the first as well, on
     * the figures an image gives once (doc/abi.md 1): the variant, the
     * width, the unit and, under DTX2, {@code P} and {@code N}.
     *
     * @throws IllegalStateException where the code is for another variant,
     *     another width or another unit, where it and its format block
     *     differ on where the column table lands, or where two tables
     *     differ on a figure the image gives once
     */
    static byte[] combine(byte[] code, byte[] file) {
        return combine(code, List.of(file)).image();
    }

    /** The same, of one table or several. */
    static Packaged combine(byte[] code, List<byte[]> files) {
        if (files.isEmpty()) {
            throw new IllegalStateException("no table: an image contains one"
                    + " at least");
        }
        byte[] file = files.get(0);
        Dtx.Header header = Dtx.header(file);
        int variant = header.variant();
        if (code[FORMAT_AT] != 'D' || code[FORMAT_AT + 1] != 'T'
                || code[FORMAT_AT + 2] != 'X') {
            throw new IllegalStateException(
                    "the code does not contain a format block at +16");
        }
        if (code[FORMAT_AT + 3] != variant) {
            throw new IllegalStateException("the code reads DTX"
                    + code[FORMAT_AT + 3] + " and the table is DTX" + variant);
        }
        // The code ends where the format block's column table offset
        // points: the two match, or the image reads its own last
        // instruction as a column.
        int columns = Dtx.getLong(code, FORMAT_AT + COLUMNS_AT);
        if (columns != code.length) {
            throw new IllegalStateException("the code runs to " + code.length
                    + " bytes and the format block puts the column table at "
                    + columns + ": it would not land there");
        }
        Packed given = variant == Dtx.DTX2
                ? packed(file, header) : new Packed(0, 0, false, false, new int[0]);
        int unit = code[FORMAT_AT + UNIT_AT] & 0xFF;
        if (unit != given.unit()) {
            throw new IllegalStateException("the code decodes at a unit of "
                    + unit + " and the table was packed at " + given.unit());
        }
        int width = code[FORMAT_AT + WIDTH_AT] & 0xFF;
        if (variant != Dtx.DTX0 && width != header.width()) {
            throw new IllegalStateException("the code reads values of "
                    + width + " bytes and the table's are "
                    + header.width());
        }
        int rowBytes = header.rowBytes();
        // Each table's column table stands immediately before it, and the
        // pair begins on a long: init reaches the records at the header
        // less 16C, and a header on a long is what SPEC.md 1 asks of a
        // table's own bytes.
        int state = variant == Dtx.DTX2 ? stateBytes(header, given) : stateBytes();
        int at = code.length;
        int[] headers = new int[files.size()];
        byte[][] entries = new byte[files.size()][];
        for (int i = 0; i < files.size(); i++) {
            byte[] next = files.get(i);
            Dtx.Header its = Dtx.header(next);
            if (i > 0) {
                same(header, given, next, its);
                state = Math.max(state, its.variant() == Dtx.DTX2
                        ? stateBytes(its, packed(next, its)) : stateBytes());
                // A pair begins on a long. The code ends on one and a
                // column table is a multiple of 16, so the first pair lands
                // on a long without this and a one table image comes out
                // the bytes it always was.
                at = Dtx.align(at, 4);
            }
            entries[i] = columnTable(next);
            headers[i] = at + entries[i].length;
            at = headers[i] + next.length;
        }
        byte[] image = new byte[at];
        System.arraycopy(code, 0, image, 0, code.length);
        for (int i = 0; i < files.size(); i++) {
            System.arraycopy(entries[i], 0, image, headers[i] - entries[i].length,
                    entries[i].length);
            System.arraycopy(files.get(i), 0, image, headers[i],
                    files.get(i).length);
        }
        Dtx.putLong(image, FORMAT_AT + STATE_BYTES, state);
        // The first table stands behind both, and only the packager has
        // the figure: the column table's size moves with C, so the
        // assembler could not have worked it out.
        Dtx.putLong(image, FORMAT_AT + TABLE_AT, headers[0]);
        Dtx.putWord(image, FORMAT_AT + ROWBYTES_AT, rowBytes);
        Dtx.putWord(image, FORMAT_AT + PERIOD_AT,
                variant == Dtx.DTX2 ? period(header, given) : 1);
        Dtx.putWord(image, FORMAT_AT + RING_AT, given.ring());
        Dtx.putLong(image, FORMAT_AT + STRIDE_AT, stride(header, given));
        return new Packaged(image, headers);
    }

    /** One image gives the variant, the width, the unit and, under DTX2,
     *  {@code P} and {@code N} once (doc/abi.md 1), so every table past
     *  the first gives what the first gives.
     *
     *  @throws IllegalStateException naming the figure two tables differ on
     */
    private static void same(Dtx.Header first, Packed given, byte[] file,
                             Dtx.Header header) {
        apart("the variant", first.variant(), header.variant());
        if (first.variant() != Dtx.DTX0) {
            apart("the width", first.width(), header.width());
        }
        if (first.variant() != Dtx.DTX2) {
            return;
        }
        Packed its = packed(file, header);
        apart("the unit k", given.unit(), its.unit());
        apart("the copies flag", given.copies() ? 1 : 0, its.copies() ? 1 : 0);
        apart("the ring N", given.ring(), its.ring());
        apart("the period P", period(first, given), period(header, its));
    }

    private static void apart(String what, int first, int next) {
        if (first != next) {
            throw new IllegalStateException("one image gives " + what
                    + " once, and the first table gives " + first
                    + " where another gives " + next);
        }
    }

    /**
     * The image, combined from the code in this repository.
     *
     * <p>The file defines which of the twenty-two it takes: the variant,
     * the width every value takes, and under DTX2 the unit its data sets are
     * packed at and whether they contain copies from the literal stream
     * (R5.10). No word from a caller enters it, so no word can differ from
     * the bytes.
     */
    public static byte[] image(byte[] file) {
        return packaged(List.of(file)).image();
    }

    /**
     * The same, of one table or several: the code every one of them takes,
     * then a column table and a table's bytes for each, in the order given.
     * What comes back names where each table's header stands, the address
     * a caller hands {@code DTX_init} (doc/abi.md 2).
     *
     * <p>The first file names the code, and every other meets it: an image
     * gives the variant, the width, the unit and, under DTX2, the period
     * and the ring once.
     */
    public static Packaged packaged(List<byte[]> files) {
        if (files.isEmpty()) {
            throw new IllegalArgumentException("no table: an image contains"
                    + " one at least");
        }
        byte[] file = files.get(0);
        Dtx.Header header = Dtx.header(file);
        if (header.variant() != Dtx.DTX2) {
            return combine(carriedCode(header.variant(), header.width(), 0,
                    false), files);
        }
        Packed given = packed(file, header);
        return combine(carriedCode(Dtx.DTX2, header.width(), given.unit(),
                given.copies()), files);
    }

    /**
     * The image, from rmac's assembly of the template rather than from the
     * carried code. The two give the same bytes, which BlobTest checks; a
     * change to a template is tried through this path.
     *
     * @param rmac the assembler to run
     */
    public static byte[] image(byte[] file, Path rmac) {
        return combine(code(file, rmac), List.of(file)).image();
    }

    /** Reads the DTX file named first and writes the image named second. */
    public static void main(String[] args) {
        try {
            run(args);
        } catch (IOException | RuntimeException failed) {
            Help.stopped(failed);
        }
    }

    static void run(String[] args) throws IOException {
        if (Help.among(args)) {
            System.out.print(Help.PACKAGE);
            return;
        }
        String rmac = null;
        boolean defines = false;
        List<String> named = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("-a") && arg.length() > 2) {
                rmac = arg.substring(2);
            } else if (arg.equals("-s")) {
                defines = true;
            } else if (arg.startsWith("-")) {
                System.err.println("dtx-package does not read " + arg);
                System.exit(2);
                return;
            } else {
                named.add(arg);
            }
        }
        if (defines && named.size() > 1) {
            System.err.println("dtx-package -s reads the figures of one"
                    + " table, and " + named.size() + " were named");
            System.exit(2);
            return;
        }
        // A name is a table, in the order the image lays them out. Where
        // no name is given, one table comes in on standard input.
        List<byte[]> files = new ArrayList<>();
        if (named.isEmpty()) {
            files.add(System.in.readAllBytes());
            named.add("standard input");
        } else {
            for (String name : named) {
                files.add(Files.readAllBytes(Path.of(name)));
            }
        }
        byte[] file = files.get(0);
        Dtx.Header header = Dtx.header(file);
        int[] headers = {0};
        byte[] image;
        if (defines) {
            image = table(file).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } else if (rmac == null) {
            Packaged made = packaged(files);
            headers = made.headers();
            image = made.image();
        } else {
            Packaged made = combine(code(file, Path.of(rmac)), files);
            headers = made.headers();
            image = made.image();
        }
        System.out.write(image);
        System.out.flush();
        if (System.out.checkError()) {
            System.err.println("cannot write standard output");
            System.exit(2);
            return;
        }
        long bytes = image.length;
        int state = header.variant() == Dtx.DTX2
                ? stateBytes(header, packed(file, header)) : stateBytes();
        System.err.printf("%s -> DTX%d %s %d bytes, table %d bytes,"
                + " %d rows, %d columns, state block %d bytes%n",
                named.get(0), header.variant(),
                defines ? "figures"
                        : rmac == null ? "image" : "image assembled",
                bytes, file.length, header.rows(), header.columns(), state);
        // A caller hands init the header of the table to read (abi.md 2),
        // so the image says where each one stands.
        for (int i = 1; !defines && i < named.size(); i++) {
            byte[] next = files.get(i);
            Dtx.Header its = Dtx.header(next);
            System.err.printf("%s -> table %d at image+%d, %d bytes,"
                    + " %d rows, %d columns, state block %d bytes%n",
                    named.get(i), i + 1, headers[i], next.length, its.rows(),
                    its.columns(), its.variant() == Dtx.DTX2
                            ? stateBytes(its, packed(next, its)) : stateBytes());
        }
        if (!defines && named.size() > 1) {
            System.err.printf("table 1 stands at image+%d%n", headers[0]);
        }
    }
}
