package org.dtx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The code in this repository, one file a build.
 *
 * <p>A variant assembles to one code any table that follows it, so the
 * packager combines rather than assembles: it takes the file for the build
 * the table needs, writes the five fields the table gives into the
 * format block, and appends the column table and the table's bytes. This
 * writes those files, and it is the one step rmac is needed for.
 *
 * <p>Twenty-two of them. DTX0 reads a row as one run of bytes, so its code
 * does not move with the width and one file is every DTX0 table's. DTX1
 * moves a value a column, so it has one a width. DTX2 has one a width and a
 * build of the decoder built into it, which is a unit of 1, 2 or 4 with the
 * copy code and without.
 *
 * <p>The table each is assembled from fixes only the figures the assembler
 * reads, so it is made here rather than read: the columns do not contain
 * bytes that decode, and {@link Packager#blank} zeroes the five fields the
 * table did give. What comes out is a function of the template alone.
 */
public final class Blobs {

    private Blobs() {
    }

    /**
     * One build of the code: a variant, the width its values take, and
     * under DTX2 a decoder.
     */
    public record Build(int variant, int width, int unit, boolean copies) {

        /** The file this build stands in. */
        public String name() {
            return Packager.carriedName(variant, width, unit, copies);
        }
    }

    /** Every build, in the order the files are written. */
    public static List<Build> all() {
        List<Build> out = new ArrayList<>();
        out.add(new Build(Dtx.DTX0, 1, 0, false));
        for (int width : new int[] {1, 2, 4}) {
            out.add(new Build(Dtx.DTX1, width, 0, false));
        }
        for (int width : new int[] {1, 2, 4}) {
            for (int unit : new int[] {1, 2, 4}) {
                out.add(new Build(Dtx.DTX2, width, unit, false));
                out.add(new Build(Dtx.DTX2, width, unit, true));
            }
        }
        return out;
    }

    /**
     * A table that fixes the figures one build's assembly reads. Any table
     * of the kind does, since the code does not move with it: this one is
     * 64 rows of two columns, at a ring of 960 where the build is packed.
     */
    public static byte[] seed(Build build) {
        int rows = 64;
        int columns = 2;
        byte[][] column = new byte[columns][];
        for (int i = 0; i < columns; i++) {
            column[i] = new byte[rows * build.width()];
        }
        Table table = Table.of(rows, rows, build.width(), column);
        return switch (build.variant()) {
            case Dtx.DTX0 -> Dtx0.write(table);
            case Dtx.DTX1 -> Dtx1.write(table);
            // The seed defines the build's own copies flag, since that is
            // what fixes which decoder the template is assembled with.
            default -> Dtx2.write(table, new Plain(build.copies()),
                    build.unit(), 960);
        };
    }

    /**
     * A packer that does not pack: the column comes back as it is, and defines
     * the build's own copies flag.
     *
     * <p>The assembler reads only a data set's four stream offsets, not the
     * streams, so a data set whose streams are the column itself fixes every
     * figure the build takes. Nothing decodes it, and nothing here runs it:
     * the packer that writes a table a caller reads is ST4's own.
     */
    private record Plain(boolean copies) implements Packer {

        @Override
        public byte[] pack(byte[] column, int unit, int ring) {
            return container(column, unit, ring);
        }
    }

    private static byte[] container(byte[] column, int unit, int ring) {
        byte[] set = new byte[28 + column.length];
        set[0] = 'S';
        set[1] = '4';
        set[2] = 7;
        set[3] = (byte) unit;
        Dtx.putLong(set, 4, column.length / unit);
        Dtx.putLong(set, 8, 28);
        Dtx.putLong(set, 12, 28 + column.length);
        Dtx.putLong(set, 16, 28 + column.length);
        Dtx.putLong(set, 24, ring / unit);
        System.arraycopy(column, 0, set, 28, column.length);
        return set;
    }

    /** The code one build assembles to, with the table's own figures out. */
    public static byte[] code(Build build, Path rmac) {
        return code(build, rmac, Packager.carried());
    }

    /** The same, from the templates in {@code templates}. */
    public static byte[] code(Build build, Path rmac, String templates) {
        byte[] code = Packager.code(seed(build), rmac, templates);
        Packager.blank(code);
        return code;
    }

    /**
     * Writes every build's file into each directory named.
     *
     * <p>More than one because more than one build reads them: the classes
     * a jar is made of, and a plain directory a release attaches and a port
     * in another language builds from. The files are the same bytes in each,
     * and nothing about them is Java's.
     */
    public static void main(String[] args) throws IOException {
        if (Help.among(args)) {
            System.out.print(Help.BLOBS);
            return;
        }
        List<Path> into = new ArrayList<>();
        String rmac = "rmac";
        String templates = Packager.carried();
        for (String arg : args) {
            if (arg.startsWith("-a")) {
                rmac = arg.substring(2);
            } else if (arg.startsWith("-t")) {
                templates = arg.substring(2);
            } else if (arg.startsWith("-")) {
                System.err.println("dtx-blobs does not read " + arg);
                System.exit(2);
                return;
            } else {
                into.add(Path.of(arg));
            }
        }
        if (into.isEmpty()) {
            System.err.print(Help.BLOBS);
            System.exit(2);
            return;
        }
        for (Path at : into) {
            Files.createDirectories(at);
        }
        for (Build build : all()) {
            byte[] code;
            try {
                code = code(build, Path.of(rmac), templates);
            } catch (RuntimeException failed) {
                throw new IllegalStateException("no code built for "
                        + build.name() + " with an assembler at " + rmac
                        + " and templates at " + templates
                        + ": the build runs one, and -Drmac=PATH names"
                        + " another. A release holds what it built, and a"
                        + " caller who takes one does not run an assembler.",
                        failed);
            }
            for (Path at : into) {
                Files.write(at.resolve(build.name()), code);
            }
            System.out.printf("%-20s %5d bytes%n", build.name(), code.length);
        }
    }
}
