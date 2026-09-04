package org.dtx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A DTX0 or DTX1 file into a DTX2 one.
 *
 * <p>{@code Rewrite in.dtx out.dtx [-kK] [-mN] [-pPACKER]}: {@code K} is the
 * unit every column is packed at, {@code N} the ring in bytes, and
 * {@code PACKER} the ST4 executable to run.
 */
public final class Rewrite {

    private Rewrite() {
    }

    /** Reads the file named first and writes the DTX2 file named second. */
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Rewrite in.dtx out.dtx"
                    + " [-kK] [-mN] [-pPACKER]"
                    + " [-copies[S]]");
            System.exit(2);
            return;
        }
        int unit = 1;
        int ring = 960;
        String packer = "st4";
        String copies = "";
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("-k")) {
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
                System.err.println("Rewrite does not read " + arg);
                System.exit(2);
                return;
            }
        }
        byte[] in = Files.readAllBytes(Path.of(args[0]));
        byte[] out = Dtx2.from(in, new St4(Path.of(packer), copies), unit, ring);
        Files.write(Path.of(args[1]), out);
        Dtx.Header header = Dtx.header(in);
        System.out.printf("DTX%d %d bytes -> DTX2 %d bytes, %d rows,"
                + " %d columns, k=%d, N=%d%n", header.variant(), in.length,
                out.length, header.rows(), header.columns(), unit, ring);
    }
}
