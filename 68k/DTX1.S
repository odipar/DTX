; -----------------------------------------------------------------------------
; DTX1.S - a DTX1 table read on the 68000: four calls, one table, PC relative
;
; By Robbert van Dalen. Written by Claude (Anthropic's Claude Code) under
; Robbert's direction. Use it freely, including commercially, as long as your
; documentation says you used DTX (see LICENSE).
;
; doc/abi.md defines the calls, the format block and the state block. One
; template a variant: this file reads only DTX1, so no call in it tests which
; variant it contains.
;
; THE CODE DOES NOT MOVE WITH THE TABLE. R, C and RR reach it at run time
; rather than at assembly time, so every DTX1 image of one width contains the
; same instructions and the table behind them differs. That costs a read-only
; image: the code writes to itself at init, so it stands in RAM and a 68030
; caller flushes the instruction cache after DTX_init.
;
; THE WIDTH DOES MOVE THE CODE. Every value of a table is one width (R6.3),
; and it reaches the assembler as an equate, so a read moves a value in one
; instruction. That costs one build a width, and the packager takes the build
; the table's header names.
;
; DTX1 lays a column's values together and every column is the same length,
; so the columns stand at one stride: the value of column i for the row the
; cursor stands on is the pointer an advance gives plus i strides. The image
; does not have a column table or an entry a column, and it does not copy a
; row anywhere: the caller reads the values where they stand.
;
; DTX_metadata gives the stride, which is R times the width rounded up to a
; word (R4.3). It stands in the format block, so a caller that builds against
; a given image reads it out of the file instead.
;
; NOTHING IS CHECKED. No call checks the table, the state block, the row
; number or the address a row goes to.
; -----------------------------------------------------------------------------

        .68000
        .text

        include "DTX_table.i"

; -----------------------------------------------------------------------------
        include "DTX_image.S"
        DTX_IMAGE 1,0,DTX_WIDTH

; -----------------------------------------------------------------------------
; A value times the width, in one instruction: add.l dn,dn is the cheapest
; double a 68000 has, so a width of 2 takes that and a width of 4 a shift.
; At a width of 1 the value stands as it is and this macro is empty.

        .macro  DTX_SCALE value
        .if     DTX_WIDTH=2
        add.l   \value,\value
        .endif
        .if     DTX_WIDTH=4
        lsl.l   #2,\value
        .endif
        .endm

; -----------------------------------------------------------------------------
; DTX_init. It leaves the pointer a row below row 0.

DTX_init:
        clr.w   DTX_TURN(a0)

        bsr.s   DTX_payload             ; a1 the payload
; The pointer a row below row 0, so the advance into row 0 steps like any
; other advance.
        subq.l  #DTX_WIDTH,a1
        move.l  a1,DTX_POINTER(a0)
        rts

; -----------------------------------------------------------------------------
; DTX_jump. The pointer is the payload plus the row times the width, which is
; a shift a width.

DTX_jump:
        move.l  d0,d1
        DTX_SCALE d1
        bsr.s   DTX_payload
        adda.l  d1,a1
        move.l  a1,DTX_POINTER(a0)
        rts

; -----------------------------------------------------------------------------
; DTX_advance. One row on: a1 comes back the row's first value, and the
; caller takes column i at i strides from it. Which row that is the caller
; counts (abi.md 2): no call keeps a row number, and an advance past row R
; minus one reads past the column.

DTX_advance:
        addq.l  #DTX_WIDTH,DTX_POINTER(a0)
        movea.l DTX_POINTER(a0),a1
        rts

; -----------------------------------------------------------------------------
        DTX_TABLE_FOLLOWS

        end
