package org.dtx;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A DTX0 or DTX1 file as a standalone 68000 image: the code, then the
 * table's bytes, reached PC relative.
 *
 * <p>{@code doc/abi.md} states the five calls, the format block and the
 * state block. This emits the assembly for one table and hands it to rmac,
 * which writes the raw binary. Everything the table settles is folded into
 * the code: {@code R}, {@code RR}, the row's bytes, each width as the size
 * of a move, and every column's displacement off its class cursor.
 */
public final class Packager {

    /** The state block's fields, from doc/abi.md 3. */
    static final int ROW = 0;
    static final int TURN = 4;
    static final int DECODED = 8;
    static final int PARK = 12;
    static final int CURSOR = 24;

    /** What the format block runs to. */
    static final int FORMAT = 24;

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

    /** The state block a reader of this table takes, in bytes. */
    public static int stateBytes(Dtx.Header header) {
        return header.variant() == Dtx.DTX0
                ? CURSOR + 4 : CURSOR + 4 * classes(header.width()).length;
    }

    /**
     * The address register a width class's cursor stands in. {@code a1} is
     * the row's destination and {@code a0} the state block, so a0 takes the
     * third class and is loaded last, which holds the read to the a0 to a3
     * doc/abi.md 2 states it clobbers.
     */
    private static String cursor(int at) {
        return at == 2 ? "a0" : "a" + (2 + at);
    }

    /** {@code file} as the assembly rmac makes an image of. */
    public static String assembly(byte[] file) {
        Dtx.Header header = Dtx.header(file);
        int variant = header.variant();
        if (variant != Dtx.DTX0 && variant != Dtx.DTX1) {
            throw new IllegalArgumentException(
                    "the variant is 0 or 1, not " + variant);
        }
        int[] width = header.width();
        int rows = header.rows();
        if (rows > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("R is at most 2147483647");
        }
        int[] taken = classes(width);
        if (taken.length > 3) {
            throw new IllegalArgumentException("a table holds three widths");
        }
        int rowBytes = 0;
        for (int w : width) {
            rowBytes += w;
        }
        StringBuilder out = new StringBuilder();
        out.append("; DTX").append(variant)
                .append(", packaged by org.dtx.Packager. doc/abi.md states"
                        + " the calls.\n")
                .append("; R = ").append(rows).append(", C = ")
                .append(width.length).append(", RR = ").append(header.repeat())
                .append(", the row's bytes = ").append(rowBytes).append('\n')
                .append("\t.68000\n\t.text\n\n")
                .append("DTX_ROW\t\tequ\t").append(ROW).append('\n')
                .append("DTX_TURN\tequ\t").append(TURN).append('\n')
                .append("DTX_DECODED\tequ\t").append(DECODED).append('\n')
                .append("DTX_PARK\tequ\t").append(PARK).append('\n')
                .append("DTX_CURSOR\tequ\t").append(CURSOR).append('\n')
                .append("DTX_STATE\tequ\t").append(stateBytes(header))
                .append("\t\t; the state block's bytes\n\n")
                .append("_base:\n")
                .append("\tbra.w\t_init\n\tbra.w\t_metadata\n")
                .append("\tbra.w\t_jump\n\tbra.w\t_advance\n\tbra.w\t_read\n\n");

        // The format block, doc/abi.md 1.
        out.append("_fmt:\n")
                .append("\tdc.b\t'D','T','X',").append(variant).append('\n')
                .append("\tdc.l\tDTX_STATE\n")
                .append("\tdc.l\t_table-_base\n")
                .append("\tdc.w\t").append(rowBytes).append("\t\t; the row's bytes\n")
                .append("\tdc.w\t1\t\t; P\n")
                .append("\tdc.w\t0\t\t; N\n")
                .append("\tdc.b\t0,0\t\t; k, and a zero\n")
                .append("\tdc.l\t0\n\n");

        // The DTX1 column offsets; DTX0's bodies do not read them.
        int[] at = Dtx1.offsets(rows, width);
        out.append(init(variant, width, taken, at, rowBytes))
                .append(metadata(header))
                .append(jump(variant, width, taken, at, rowBytes))
                .append(advance(variant, width, taken, header, rowBytes))
                .append(read(variant, width, taken, at, rowBytes));

        out.append("\t.even\n_table:\n");
        for (int i = 0; i < file.length; i += 16) {
            out.append("\tdc.b\t");
            for (int j = i; j < Math.min(i + 16, file.length); j++) {
                out.append(j == i ? "" : ",").append(file[j] & 0xFF);
            }
            out.append('\n');
        }
        out.append("_payload\tequ\t_table+").append(header.length()).append('\n');
        return out.toString();
    }

    private static String init(int variant, int[] width, int[] taken,
            int[] at, int rowBytes) {
        StringBuilder out = new StringBuilder("_init:\n"
                + "\tmove.l\t#-1,DTX_ROW(a0)\n"
                + "\tclr.w\tDTX_TURN(a0)\n"
                + "\tclr.l\tDTX_DECODED(a0)\n");
        if (variant == Dtx.DTX0) {
            out.append("\tlea\t_payload-").append(rowBytes).append("(pc),a1\n")
                    .append("\tmove.l\ta1,DTX_CURSOR(a0)\n");
        } else {
            for (int c = 0; c < taken.length; c++) {
                int base = at[first(width, taken[c])];
                out.append("\tlea\t_payload+").append(base - taken[c])
                        .append("(pc),a1\n")
                        .append("\tmove.l\ta1,DTX_CURSOR+").append(4 * c)
                        .append("(a0)\n");
            }
        }
        return out.append("\trts\n\n").toString();
    }

    private static String metadata(Dtx.Header header) {
        return "_metadata:\n"
                + "\tlea\t_fmt(pc),a0\n"
                + "\tlea\t_table(pc),a1\n"
                + "\tmove.l\t#" + header.rows() + ",d0\n"
                + "\tmove.w\t#" + header.columns() + ",d1\n"
                + "\tmove.l\t#" + header.repeat() + ",d2\n"
                + "\trts\n\n";
    }

    /** {@code d1} takes {@code d0} times {@code by}, a 32 by 16 product. */
    private static String times(int by) {
        if (Integer.bitCount(by) == 1) {
            int shift = Integer.numberOfTrailingZeros(by);
            StringBuilder out = new StringBuilder("\tmove.l\td0,d1\n");
            for (int i = 0; i < shift; i++) {
                out.append("\tadd.l\td1,d1\n");
            }
            return out.toString();
        }
        return "\tmove.l\td0,d1\n"
                + "\tswap\td1\n"
                + "\tmulu.w\t#" + by + ",d1\n"
                + "\tswap\td1\n"
                + "\tclr.w\td1\n"
                + "\tmove.l\td0,d3\n"
                + "\tandi.l\t#$FFFF,d3\n"
                + "\tmulu.w\t#" + by + ",d3\n"
                + "\tadd.l\td3,d1\n";
    }

    private static String jump(int variant, int[] width, int[] taken,
            int[] at, int rowBytes) {
        StringBuilder out = new StringBuilder("_jump:\n"
                + "\tmove.l\td0,DTX_ROW(a0)\n");
        if (variant == Dtx.DTX0) {
            out.append(times(rowBytes))
                    .append("\tlea\t_payload(pc),a1\n")
                    .append("\tadda.l\td1,a1\n")
                    .append("\tmove.l\ta1,DTX_CURSOR(a0)\n");
        } else {
            for (int c = 0; c < taken.length; c++) {
                int base = at[first(width, taken[c])];
                out.append(times(taken[c]))
                        .append("\tlea\t_payload+").append(base).append("(pc),a1\n")
                        .append("\tadda.l\td1,a1\n")
                        .append("\tmove.l\ta1,DTX_CURSOR+").append(4 * c)
                        .append("(a0)\n");
            }
        }
        return out.append("\trts\n\n").toString();
    }

    private static String advance(int variant, int[] width, int[] taken,
            Dtx.Header header, int rowBytes) {
        StringBuilder out = new StringBuilder("_advance:\n"
                + "\tmove.l\tDTX_ROW(a0),d0\n"
                + "\taddq.l\t#1,d0\n"
                + "\tcmpi.l\t#" + header.rows() + ",d0\n"
                + "\tbne.s\t.step\n");
        if (header.repeat() < header.rows()) {
            out.append("\tmove.l\t#").append(header.repeat()).append(",d0\n")
                    .append("\tbra.w\t_jump\n");
        } else {
            out.append("\tmoveq\t#-1,d0\n\trts\n");
        }
        out.append(".step:\n\tmove.l\td0,DTX_ROW(a0)\n");
        if (variant == Dtx.DTX0) {
            out.append("\tmove.l\tDTX_CURSOR(a0),d1\n")
                    .append("\taddi.l\t#").append(rowBytes).append(",d1\n")
                    .append("\tmove.l\td1,DTX_CURSOR(a0)\n");
        } else {
            for (int c = 0; c < taken.length; c++) {
                out.append("\tmove.l\tDTX_CURSOR+").append(4 * c).append("(a0),d1\n")
                        .append("\taddq.l\t#").append(taken[c]).append(",d1\n")
                        .append("\tmove.l\td1,DTX_CURSOR+").append(4 * c)
                        .append("(a0)\n");
            }
        }
        return out.append("\trts\n\n").toString();
    }

    private static String read(int variant, int[] width, int[] taken,
            int[] at, int rowBytes) {
        StringBuilder out = new StringBuilder("_read:\n"
                + "\ttst.l\tDTX_ROW(a0)\n"
                + "\tbmi.s\t.none\n");
        if (variant == Dtx.DTX0) {
            out.append("\tmovea.l\tDTX_CURSOR(a0),a2\n");
            String move = rowBytes % 4 == 0 ? "l" : rowBytes % 2 == 0 ? "w" : "b";
            int size = rowBytes % 4 == 0 ? 4 : rowBytes % 2 == 0 ? 2 : 1;
            int count = rowBytes / size;
            if (count <= 8) {
                for (int i = 0; i < count; i++) {
                    out.append("\tmove.").append(move).append("\t(a2)+,(a1)+\n");
                }
            } else {
                out.append("\tmove.w\t#").append(count - 1).append(",d0\n")
                        .append(".run:\tmove.").append(move)
                        .append("\t(a2)+,(a1)+\n")
                        .append("\tdbf\td0,.run\n");
            }
        } else {
            for (int c = 0; c < taken.length; c++) {
                out.append("\tmovea.l\tDTX_CURSOR+").append(4 * c)
                        .append("(a0),").append(cursor(c)).append('\n');
            }
            int[] base = new int[taken.length];
            for (int c = 0; c < taken.length; c++) {
                base[c] = at[first(width, taken[c])];
            }
            for (int i = 0; i < width.length; i++) {
                int c = classOf(taken, width[i]);
                String move = width[i] == 4 ? "l" : width[i] == 2 ? "w" : "b";
                out.append("\tmove.").append(move).append('\t')
                        .append(at[i] - base[c]).append('(').append(cursor(c))
                        .append("),(a1)+\n");
            }
        }
        return out.append(".none:\trts\n\n").toString();
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

    /**
     * The raw image rmac makes of {@code file}.
     *
     * @param rmac the assembler to run
     */
    public static byte[] image(byte[] file, Path rmac) {
        try {
            Path work = Files.createTempDirectory("dtx68");
            try {
                Path source = work.resolve("image.s");
                Path out = work.resolve("image.bin");
                Files.writeString(source, assembly(file));
                // -fr is the absolute output, the raw bytes at the
                // origin. -fb writes a BSD object with a header on it.
                Process run = new ProcessBuilder(rmac.toString(), "-m68000",
                        "-fr", "+o3", "-o", out.toString(), source.toString())
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

    /** Reads the DTX file named first and writes the image named second. */
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Packager in.dtx out.bin [-aRMAC] [-s]");
            System.exit(2);
            return;
        }
        String rmac = "rmac";
        boolean source = false;
        for (int i = 2; i < args.length; i++) {
            if (args[i].startsWith("-a")) {
                rmac = args[i].substring(2);
            } else if (args[i].equals("-s")) {
                source = true;
            } else {
                System.err.println("Packager does not read " + args[i]);
                System.exit(2);
                return;
            }
        }
        byte[] file = Files.readAllBytes(Path.of(args[0]));
        Dtx.Header header = Dtx.header(file);
        if (source) {
            Files.writeString(Path.of(args[1]), assembly(file));
        } else {
            Files.write(Path.of(args[1]), image(file, Path.of(rmac)));
        }
        long bytes = Files.size(Path.of(args[1]));
        System.out.printf("%s -> DTX%d image %d bytes, table %d bytes,"
                + " %d rows, %d columns, state block %d bytes%n",
                args[0], header.variant(), bytes, file.length, header.rows(),
                header.columns(), stateBytes(header));
    }
}
