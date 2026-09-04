/**
 * DTX: a table of {@code R} rows and {@code C} columns, in one of the
 * variants {@code doc/SPEC.md} defines.
 *
 * <p>{@link org.dtx.Table} is the table in memory, column by column.
 * {@link org.dtx.Dtx0}, {@link org.dtx.Dtx1} and {@link org.dtx.Dtx2}
 * write one variant each, and the first two read one back. DTX2 packs
 * through a {@link org.dtx.Packer} the caller supplies: no packer is kept
 * here, so the format ST4 defines stays in ST4's own repository. Its
 * 68000 decoder is carried under {@code 68k/}, which a packaged reader
 * takes and this package does not.
 */
@NullMarked
package org.dtx;

import org.jspecify.annotations.NullMarked;
