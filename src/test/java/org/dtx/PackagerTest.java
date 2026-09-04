package org.dtx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The assembly a table is packaged as, and the image rmac makes of it.
 *
 * <p>What the image does is held by {@code 68k/test/emu/test_dtx.py}, which
 * runs it on a 68000. This holds what the packager states of it: the
 * figures the format block gives, the state block's size, and what it will
 * not package.
 */
final class PackagerTest {

    private static byte[] table(int variant) {
        Table table = Csv.table("1,300,-2\n2,301,-1\n3,302,0\n4,303,1\n",
                new int[] {1, 2, 1});
        return variant == Dtx.DTX0 ? Dtx0.write(table) : Dtx1.write(table);
    }

    @Test
    void theFormatBlockStatesWhatTheTableStates() {
        for (int variant : new int[] {Dtx.DTX0, Dtx.DTX1}) {
            String out = Packager.assembly(table(variant));
            assertTrue(out.contains("\tdc.b\t'D','T','X'," + variant),
                    "the variant byte at DTX" + variant);
            assertTrue(out.contains("\tdc.w\t4\t\t; the row's bytes"),
                    "the row's bytes at DTX" + variant);
            assertTrue(out.contains("\tdc.w\t1\t\t; P"), "P is 1 without a ring");
            assertTrue(out.contains("\tdc.w\t0\t\t; N"), "N is 0 without a ring");
        }
    }

    @Test
    void theStateBlockGrowsWithTheWidthClasses() {
        Table one = Csv.table("1\n2\n", new int[] {1});
        Table three = Csv.table("1,2,3\n4,5,6\n", new int[] {1, 2, 4});
        assertEquals(28, Packager.stateBytes(Dtx.header(Dtx0.write(three))),
                "DTX0 holds one cursor whatever the widths");
        assertEquals(28, Packager.stateBytes(Dtx.header(Dtx1.write(one))),
                "DTX1 with one width class");
        assertEquals(36, Packager.stateBytes(Dtx.header(Dtx1.write(three))),
                "DTX1 with three width classes");
    }

    @Test
    void theFiveSlotsStandInTheOrderTheAbiGives() {
        String out = Packager.assembly(table(Dtx.DTX0));
        int at = out.indexOf("_base:");
        assertTrue(at > 0, "the image has no base");
        String slots = out.substring(at, out.indexOf("_fmt:"));
        assertEquals("_base:\n\tbra.w\t_init\n\tbra.w\t_metadata\n"
                + "\tbra.w\t_jump\n\tbra.w\t_advance\n\tbra.w\t_read\n\n",
                slots, "the slots, doc/abi.md 1");
    }

    @Test
    void aPackedTableIsNotPackagedYet() {
        Table table = Csv.table("1\n2\n", new int[] {1});
        byte[] two = Dtx2.write(table, (column, unit, ring) -> {
            byte[] set = new byte[28 + column.length];
            set[0] = 'S';
            set[1] = '4';
            set[2] = 7;
            set[3] = (byte) unit;
            System.arraycopy(column, 0, set, 28, column.length);
            return set;
        }, 1, 960);
        assertEquals("the variant is 0 or 1, not 2",
                assertThrows(IllegalArgumentException.class,
                        () -> Packager.assembly(two)).getMessage());
    }

    @Test
    void rmacMakesAnImageThatOpensWithTheSlotsAndTheFormatBlock()
            throws Exception {
        String named = System.getenv("RMAC");
        Path rmac = Path.of(named == null ? "rmac" : named);
        Assumptions.assumeTrue(named != null || onThePath(rmac),
                "no rmac at " + rmac);
        byte[] image = Packager.image(table(Dtx.DTX0), rmac);
        assertEquals(0x60, image[0] & 0xFF, "the first slot is a bra.w");
        assertEquals("DTX", new String(image, 20, 3), "the format block at 20");
        assertEquals(0, image[23], "the variant the format block states");
        assertEquals(28, Dtx.getLong(image, 24), "the state block's bytes");
        int header = Dtx.getLong(image, 28);
        assertEquals("DTX", new String(image, header, 3),
                "the header the format block points at");
    }

    private static boolean onThePath(Path rmac) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String at : path.split(":")) {
            if (Files.isExecutable(Path.of(at).resolve(rmac.toString()))) {
                return true;
            }
        }
        return false;
    }
}
