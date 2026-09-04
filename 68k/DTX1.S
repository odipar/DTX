; -----------------------------------------------------------------------------
; DTX1.S - a DTX1 table read on the 68000: five calls, one table, PC relative
;
; By Robbert van Dalen. Written by Claude (Anthropic's Claude Code) under
; Robbert's direction. Use it freely, including commercially, as long as your
; documentation says you used DTX (see LICENSE).
;
; doc/abi.md states the calls, the format block and the state block. One
; template a variant: this file reads DTX1 and nothing else, so no call in it
; tests which variant it holds. org.dtx.Packager picks the template the
; table's variant asks for.
;
; DTX1 holds a column's values together, so one cursor a width class reaches
; every column of that width, at a displacement the packager holds. What one
; table settles reaches this file as DTX_table.i: the equates, and the lists
; DTX_SEED_CURSORS, DTX_JUMP_CURSORS, DTX_STEP_CURSORS, DTX_LOAD_CURSORS and
; DTX_READ_ROW, one macro invocation a class or a column.
;
; THE TABLE'S BYTES FOLLOW THE CODE. Nothing here holds them: _table is the
; last label and the code ends on a long, so the packager appends the .dtx
; file to what rmac writes and _table is where it lands.
;
; TRUSTED INPUT ONLY. No call checks the table, the state block, the row
; number or the address a row goes to. doc/abi.md 7 lists what each call
; takes without checking.
; -----------------------------------------------------------------------------

        .68000
        .text

        include "DTX_table.i"

; -----------------------------------------------------------------------------
; The macros the lists are written in.

; A class cursor a row below row 0, so the advance into row 0 steps like any
; other advance.
        .macro  DTX_SEED_CURSOR c,width,base
        lea     _payload+\base-\width(pc),a1
        move.l  a1,DTX_CURSOR+\c(a0)
        .endm

; A class cursor on the row in d0: the class base plus the row times its
; width, which is a shift a width.
        .macro  DTX_JUMP_CURSOR c,shift,base
        move.l  d0,d1
        .rept   \shift
        add.l   d1,d1
        .endr
        lea     _payload+\base(pc),a1
        adda.l  d1,a1
        move.l  a1,DTX_CURSOR+\c(a0)
        .endm

; A class cursor one row on.
        .macro  DTX_STEP_CURSOR c,width,base
        move.l  DTX_CURSOR+\c(a0),d1
        addq.l  #\width,d1
        move.l  d1,DTX_CURSOR+\c(a0)
        .endm

        include "DTX_row.S"

; -----------------------------------------------------------------------------
; The image, doc/abi.md 1: five slots, the format block, then the bodies.

        include "DTX_image.S"
        DTX_IMAGE 1,1,0,0

; -----------------------------------------------------------------------------

DTX_init:
        move.l  #-1,DTX_ROW(a0)
        clr.w   DTX_TURN(a0)
        clr.l   DTX_DECODED(a0)
        DTX_SEED_CURSORS
        rts

DTX_jump:
        move.l  d0,DTX_ROW(a0)
        DTX_JUMP_CURSORS
        rts

; The move that leaves d0 sets N and Z, so bmi after the call.
DTX_advance:
        move.l  DTX_ROW(a0),d0
        addq.l  #1,d0
        DTX_END_OR_REPEAT
.step:
        move.l  d0,DTX_ROW(a0)
        DTX_STEP_CURSORS
        rts

; The row as DTX0 lays it out, and nothing between the values. The class
; cursors take a2, a3 and a4, so a0 still holds the block when this returns.
DTX_read:
        tst.l   DTX_ROW(a0)
        bmi.s   .none
        DTX_LOAD_CURSORS
        DTX_READ_ROW
.none:
        rts

; -----------------------------------------------------------------------------
; DTX_take. A read and an advance in one call. The read leaves every class
; cursor in its register, so the step adds a width to each and stores it,
; rather than loading them out of the block a second time.

DTX_take:
        tst.l   DTX_ROW(a0)
        bmi.w   DTX_advance             ; no row: step to row 0 and write none
        DTX_LOAD_CURSORS
        DTX_READ_ROW
        move.l  DTX_ROW(a0),d0
        addq.l  #1,d0
        DTX_TAKE_END_OR_REPEAT
.step:
        move.l  d0,DTX_ROW(a0)
        DTX_STEP_HELD_CURSORS
        rts

; -----------------------------------------------------------------------------
        DTX_TABLE_FOLLOWS

        end
