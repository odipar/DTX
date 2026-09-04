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

    /** The state block a reader of this table takes, in bytes. */
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
        if (variant != Dtx.DTX0 && variant != Dtx.DTX1
                && variant != Dtx.DTX2) {
            throw new IllegalArgumentException(
                    "the variant is 0, 1 or 2, not " + variant);
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
        Packed packed = variant == Dtx.DTX2
                ? packed(file, header) : new Packed(0, 0, new int[0]);
        int period = variant == Dtx.DTX2 ? period(header, packed) : 1;
        StringBuilder out = new StringBuilder();
        out.append("; DTX").append(variant)
                .append(", packaged by org.dtx.Packager. doc/abi.md states"
                        + " the calls.\n")
                .append("; R = ").append(rows).append(", C = ")
                .append(width.length).append(", RR = ").append(header.repeat())
                .append(", the row's bytes = ").append(rowBytes).append('\n')
                .append("\t.68000\n\t.text\n\n");
        if (variant == Dtx.DTX2) {
            out.append("ST4_UNIT\tequ\t").append(packed.unit())
                    .append("\t\t; the unit every data set is packed at\n\n");
        }
        out
                .append("DTX_ROW\t\tequ\t").append(ROW).append('\n')
                .append("DTX_TURN\tequ\t").append(TURN).append('\n')
                .append("DTX_DECODED\tequ\t").append(DECODED).append('\n')
                .append("DTX_PARK\tequ\t").append(PARK).append('\n')
                .append("DTX_CURSOR\tequ\t").append(CURSOR).append('\n');
        if (variant == Dtx.DTX2) {
            out.append("DTX_SLOT\tequ\t").append(slot(header)).append('\n')
                    .append("DTX_RING\tequ\t").append(ring(header)).append('\n');
        }
        out.append("DTX_STATE\tequ\t")
                .append(variant == Dtx.DTX2
                        ? stateBytes(header, packed) : stateBytes(header))
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
                .append("\tdc.w\t").append(period).append("\t\t; P\n")
                .append("\tdc.w\t").append(packed.ring()).append("\t\t; N\n")
                .append("\tdc.b\t").append(packed.unit())
                .append(",0\t\t; k, and a zero\n")
                .append("\tdc.l\t0\n\n");

        if (variant == Dtx.DTX2) {
            out.append(packedBodies(file, header, packed, period))
                    .append("\t.even\n")
                    .append("\tinclude\t\"ST4_wrap.S\"\n\n");
        } else {
            // The DTX1 column offsets; DTX0's bodies do not read them.
            int[] at = Dtx1.offsets(rows, width);
            out.append(init(variant, width, taken, at, rowBytes))
                    .append(metadata(header))
                    .append(jump(variant, width, taken, at, rowBytes))
                    .append(advance(variant, width, taken, header, rowBytes))
                    .append(read(variant, width, taken, at, rowBytes));
        }

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

    /** Shifts {@code d3} from rows to a budget in units for column i. */
    private static String budget(int w, int k) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Integer.numberOfTrailingZeros(w); i++) {
            out.append("\tadd.l\td3,d3\n");
        }
        int down = Integer.numberOfTrailingZeros(k);
        if (down > 0) {
            out.append("\tlsr.l\t#").append(down).append(",d3\n");
        }
        return out.toString();
    }

    /** Seeds one column's decoder and fills its ring, doc/abi.md 2. */
    private static String fill(int i, int[] width, Packed packed, byte[] file,
            Dtx.Header header, int period) {
        int payload = header.length();
        int set = packed.at()[i];
        int n = packed.ring();
        StringBuilder out = new StringBuilder("; column " + i + ", "
                + width[i] + " bytes a row\n");
        out.append("\tlea\t_payload+").append(set + 28).append("(pc),a0\n")
                .append("\tlea\tDTX_RING+").append(i * n).append("(a6),a1\n")
                .append("\tlea\t_payload+")
                .append(set + Dtx.getLong(file, payload + set + 8))
                .append("(pc),a2\n")
                .append("\tlea\t_payload+")
                .append(set + Dtx.getLong(file, payload + set + 12))
                .append("(pc),a4\n")
                .append("\tlea\t_payload+")
                .append(set + Dtx.getLong(file, payload + set + 16))
                .append("(pc),a5\n")
                .append("\tmove.w\t#").append(n).append(",d3\n")
                .append("\tbsr\tST4_init\n")
                .append("\tmove.l\t#").append(period).append(",d3\n")
                .append(budget(width[i], packed.unit()))
                .append("\tbsr\tST4_resume\n")
                .append(wrap(i, n))
                .append("\tlea\tDTX_SLOT+").append(32 * i).append("(a6),a3\n")
                .append("\tmovem.l\td0-d2/a0-a2/a4-a5,(a3)\n");
        return out.toString();
    }

    /** Takes the write pointer back where a refill landed on the ring end. */
    private static String wrap(int i, int n) {
        return "\tlea\tDTX_RING+" + (i * n + n) + "(a6),a3\n"
                + "\tcmpa.l\ta3,a1\n"
                + "\tbne.s\t.w" + i + "\n"
                + "\tlea\tDTX_RING+" + (i * n) + "(a6),a1\n"
                + ".w" + i + ":\n";
    }

    /** The DTX2 bodies. {@code a6} holds the block through all of them. */
    private static String packedBodies(byte[] file, Dtx.Header header,
            Packed packed, int period) {
        int[] width = header.width();
        int[] taken = classes(width);
        int n = packed.ring();
        int rows = header.rows();
        StringBuilder out = new StringBuilder();

        // _seed: every decoder, every ring filled, the cursors a row below
        // row 0. Init takes it, and so does a jump that runs from row 0.
        out.append("_seed:\n")
                .append("\tmove.l\t#-1,DTX_ROW(a6)\n")
                .append("\tclr.w\tDTX_TURN(a6)\n")
                .append("\tmove.l\t#").append(period)
                .append(",DTX_DECODED(a6)\n");
        for (int i = 0; i < width.length; i++) {
            out.append(fill(i, width, packed, file, header, period));
        }
        for (int c = 0; c < taken.length; c++) {
            int base = first(width, taken[c]) * n;
            out.append("\tlea\tDTX_RING+").append(base - taken[c])
                    .append("(a6),a1\n")
                    .append("\tmove.l\ta1,DTX_CURSOR+").append(4 * c)
                    .append("(a6)\n");
        }
        out.append("\trts\n\n");

        // _step: one row. The cursors move, the column whose turn it is is
        // refilled, and the turn steps; at the turn's wrap a period's rows
        // join the rows decoded.
        out.append("_step:\n");
        for (int c = 0; c < taken.length; c++) {
            int base = first(width, taken[c]) * n;
            out.append("\tmovea.l\tDTX_CURSOR+").append(4 * c)
                    .append("(a6),a1\n")
                    .append("\tlea\t").append(taken[c]).append("(a1),a1\n")
                    .append("\tlea\tDTX_RING+").append(base + n)
                    .append("(a6),a3\n")
                    .append("\tcmpa.l\ta3,a1\n")
                    .append("\tbne.s\t.c").append(c).append('\n')
                    .append("\tlea\tDTX_RING+").append(base).append("(a6),a1\n")
                    .append(".c").append(c).append(":\n")
                    .append("\tmove.l\ta1,DTX_CURSOR+").append(4 * c)
                    .append("(a6)\n");
        }
        out.append("; the rows this period's refills take: what is left of R,\n")
                .append("; and no refill at all once every row is decoded\n")
                .append("\tmove.l\t#").append(rows).append(",d3\n")
                .append("\tsub.l\tDTX_DECODED(a6),d3\n")
                .append("\tble.w\t.turn\n")
                .append("\tcmpi.l\t#").append(period).append(",d3\n")
                .append("\tbcs.s\t.rows\n")
                .append("\tmove.l\t#").append(period).append(",d3\n")
                .append(".rows:\n")
                .append("\tmove.w\tDTX_TURN(a6),d1\n")
                .append("\tcmpi.w\t#").append(width.length).append(",d1\n")
                .append("\tbcc.w\t.turn\t\t; a turn past the last column\n")
                .append("\tadd.w\td1,d1\n\tadd.w\td1,d1\n")
                .append("\tlea\t.table(pc),a3\n")
                .append("\tjmp\t0(a3,d1.w)\n")
                .append(".table:\n");
        for (int i = 0; i < width.length; i++) {
            out.append("\tbra.w\t.f").append(i).append('\n');
        }
        for (int i = 0; i < width.length; i++) {
            out.append(".f").append(i).append(":\n")
                    .append(budget(width[i], packed.unit()))
                    .append("\tlea\tDTX_SLOT+").append(32 * i)
                    .append("(a6),a3\n")
                    .append("\tmovem.l\t(a3)+,d0-d2/a0-a2/a4-a5\n")
                    .append("\tbsr\tST4_resume\n")
                    .append(wrap(i, n))
                    .append("\tlea\tDTX_SLOT+").append(32 * i)
                    .append("(a6),a3\n")
                    .append("\tmovem.l\td0-d2/a0-a2/a4-a5,(a3)\n")
                    .append("\tbra.w\t.turn\n");
        }
        out.append(".turn:\n")
                .append("\tmove.w\tDTX_TURN(a6),d1\n")
                .append("\taddq.w\t#1,d1\n")
                .append("\tcmpi.w\t#").append(period).append(",d1\n")
                .append("\tbcs.s\t.stored\n")
                .append("\tclr.w\td1\n")
                .append("; a period ended, so every column took its refill:\n")
                .append("; the rows it asked for are decoded now\n")
                .append("\tmove.l\t#").append(rows).append(",d3\n")
                .append("\tsub.l\tDTX_DECODED(a6),d3\n")
                .append("\tble.s\t.stored\n")
                .append("\tcmpi.l\t#").append(period).append(",d3\n")
                .append("\tbcs.s\t.grew\n")
                .append("\tmove.l\t#").append(period).append(",d3\n")
                .append(".grew:\n")
                .append("\tadd.l\td3,DTX_DECODED(a6)\n")
                .append(".stored:\n")
                .append("\tmove.w\td1,DTX_TURN(a6)\n")
                .append("\trts\n\n");

        // The five calls. Each parks the caller's a6, d6 and d7 in the
        // block and puts them back, so all three stand across a call.
        String park = "\tmovem.l\td6-d7/a6,DTX_PARK(a0)\n\tmovea.l\ta0,a6\n";
        String unpark = "\tmovem.l\tDTX_PARK(a6),d6-d7/a6\n";

        out.append("_init:\n").append(park)
                .append("\tbsr\t_seed\n").append(unpark).append("\trts\n\n");

        out.append(metadata(header));

        // _jump: a target at or below the row standing seeds afresh, then
        // both run the step body up to the target.
        // ST4_resume clobbers d3, d4, d5 and a3, so nothing but a6 and the
        // stack stands across a step: the target is held on the stack.
        out.append("_jump:\n").append(park)
                .append("\tmove.l\td0,-(a7)\t; the target\n")
                .append("\tmove.l\tDTX_ROW(a6),d4\n")
                .append("\tbmi.s\t.afresh\n")
                .append("\tcmp.l\t(a7),d4\n")
                .append("\tbcs.s\t.run\t\t; the row stands below the target\n")
                .append(".afresh:\n")
                .append("\tbsr\t_seed\n")
                .append(".run:\n")
                .append("\tmove.l\tDTX_ROW(a6),d4\n")
                .append("\tcmp.l\t(a7),d4\n")
                .append("\tbeq.s\t.there\n")
                .append("\taddq.l\t#1,d4\n")
                .append("\tmove.l\td4,DTX_ROW(a6)\n")
                .append("\tbsr\t_step\n")
                .append("\tbra.s\t.run\n")
                .append(".there:\n")
                .append("\tmove.l\t(a7)+,d0\n")
                .append(unpark).append("\trts\n\n");

        out.append("_advance:\n").append(park)
                .append("\tmove.l\tDTX_ROW(a6),d0\n")
                .append("\taddq.l\t#1,d0\n")
                .append("\tcmpi.l\t#").append(rows).append(",d0\n")
                .append("\tbne.s\t.step\n");
        if (header.repeat() < rows) {
            out.append("\tmove.l\t#").append(header.repeat()).append(",d0\n")
                    .append(unpark)
                    .append("\tbra.w\t_jump\t\t; the repeat is a jump\n");
        } else {
            out.append("\tmoveq\t#-1,d0\n").append(unpark).append("\trts\n");
        }
        out.append(".step:\n")
                .append("\tmove.l\td0,DTX_ROW(a6)\n")
                .append("\tbsr\t_step\n")
                .append("\tmove.l\tDTX_ROW(a6),d0\t; a step clobbers d0 to d5\n")
                .append(unpark).append("\trts\n\n");

        // _read: one move a column, off at most three ring cursors.
        out.append("_read:\n")
                .append("\ttst.l\tDTX_ROW(a0)\n")
                .append("\tbmi.s\t.none\n");
        for (int c = 0; c < taken.length; c++) {
            out.append("\tmovea.l\tDTX_CURSOR+").append(4 * c).append("(a0),")
                    .append(cursor(c)).append('\n');
        }
        for (int i = 0; i < width.length; i++) {
            int c = classOf(taken, width[i]);
            String move = width[i] == 4 ? "l" : width[i] == 2 ? "w" : "b";
            out.append("\tmove.").append(move).append('\t')
                    .append((i - first(width, width[i])) * n).append('(')
                    .append(cursor(c)).append("),(a1)+\n");
        }
        out.append(".none:\trts\n\n");
        return out.toString();
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
                        "-fr", "+o3", "-i" + carried(), "-o", out.toString(),
                        source.toString())
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

    /** Where the carried ST4 decoder stands, for rmac to include. */
    static String carried() {
        String named = System.getenv("DTX_68K");
        return named == null ? "68k" : named;
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
        int state = header.variant() == Dtx.DTX2
                ? stateBytes(header, packed(file, header)) : stateBytes(header);
        System.out.printf("%s -> DTX%d %s %d bytes, table %d bytes,"
                + " %d rows, %d columns, state block %d bytes%n",
                args[0], header.variant(), source ? "assembly" : "image",
                bytes, file.length, header.rows(), header.columns(), state);
    }
}
