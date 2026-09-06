/**
 * DTX: a table of {@code R} rows and {@code C} columns, in one of the
 * variants {@code doc/SPEC.md} defines.
 *
 * <p>{@link org.dtx.Table} is the table in memory, column by column.
 * {@link org.dtx.Dtx0}, {@link org.dtx.Dtx1} and {@link org.dtx.Dtx2} write
 * one variant each and read it back; DTX2 unpacks through the copy of ST4
 * under {@code org/st4}. It packs through a {@link org.dtx.Packer}:
 * {@link org.dtx.St4} with that copy, {@link org.dtx.St4Beside} with an
 * executable a caller names, or one the caller supplies. ST4's own
 * repository specifies the format. Its 68000 decoder is carried under
 * {@code 68k/}, which a packaged reader takes and this package does not.
 */
@NullMarked
package org.dtx;

import org.jspecify.annotations.NullMarked;
