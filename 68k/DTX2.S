; -----------------------------------------------------------------------------
; DTX2.S - a DTX2 table read on the 68000: five calls, one table, PC relative
;
; By Robbert van Dalen. Written by Claude (Anthropic's Claude Code) under
; Robbert's direction. Use it freely, including commercially, as long as your
; documentation says you used DTX (see LICENSE).
;
; doc/abi.md states the calls, the format block and the state block. One
; template a variant: this file reads DTX2 and nothing else, so no call in it
; tests which variant it holds. org.dtx.Packager picks the template the
; table's variant asks for.
;
; DTX2 packs a column with ST4, and this reader follows YMX. Every column
; decodes through its own ring; init fills every ring with P rows; and after
; that one column is refilled a row, the one whose turn it is. So a read
; takes one value from each ring and never enters a decoder. ST4_wrap.S, the
; copy carried into this repository, is the decoder, built at the unit the
; payload states.
;
; What one table settles reaches this file as DTX_table.i: the equates, and
; the lists DTX_SEED_CURSORS, DTX_STEP_CURSORS, DTX_LOAD_CURSORS,
; DTX_READ_ROW, DTX_FILL_COLUMNS, DTX_REFILL_TABLE and DTX_REFILL_BODIES.
;
; a6 HOLDS THE STATE BLOCK through init, jump and advance, and the caller's
; a6, d6 and d7 stand parked in the block for the length of the call: ST4
; leaves those three alone, and they are the only registers that stand across
; an ST4_resume. Read takes the block in a0 and parks nothing.
;
; WHAT FOLLOWS THE CODE. The packager appends the column table and then the
; .dtx file to what rmac writes, and the format block says where each begins.
; This variant's code still stands a column: one template a variant is done,
; and one blob a variant is not, so R, C and RR still move it.
;
; TRUSTED INPUT ONLY. No call checks the table, the state block, the row
; number or the address a row goes to. doc/abi.md 7 lists what each call
; takes without checking, ST4_wrap.S its own seven.
; -----------------------------------------------------------------------------

        .68000
        .text

        include "DTX_table.i"

; -----------------------------------------------------------------------------
; The macros the lists are written in.

; A class cursor a row below its ring's first row, so the advance into row 0
; steps like any other advance.
        .macro  DTX_SEED_CURSOR c,width,base
        lea     DTX_RING+\base-\width(a6),a1
        move.l  a1,DTX_CURSOR+\c(a6)
        .endm

; A class cursor one row on, back to the ring start where it reaches the end.
        .macro  DTX_STEP_CURSOR c,width,base
        movea.l DTX_CURSOR+\c(a6),a1
        lea     \width(a1),a1
        lea     DTX_RING+\base+DTX_N(a6),a3
        cmpa.l  a3,a1
        bne.s   .cursor\~
        lea     DTX_RING+\base(a6),a1
.cursor\~:
        move.l  a1,DTX_CURSOR+\c(a6)
        .endm

        include "DTX_row.S"

; A class cursor one row on, taken from the register a read left it in. The
; ring end is worked out in d1: a3 holds a class cursor here, so no address
; register is free to lea it into.
        .macro  DTX_STEP_HELD_RING c,width,base,cur
        lea     \width(\cur),\cur
        move.l  \cur,d1
        sub.l   a6,d1                   ; the cursor's place in the block
        cmpi.l  #DTX_RING+\base+DTX_N,d1
        bne.s   .held\~
        lea     DTX_RING+\base(a6),\cur
.held\~:
        move.l  \cur,DTX_CURSOR+\c(a6)
        .endm

; The budget one refill asks for, in units: the rows in d3 times the column's
; width, divided by k. Both are shifts, and both stand at or above zero.
        .macro  DTX_BUDGET wshift,kshift
        .rept   \wshift
        add.l   d3,d3
        .endr
        .if \kshift>0
        lsr.l   #\kshift,d3
        .endif
        .endm

; The write pointer goes back to the ring start where a refill landed on the
; ring end. N divides by P times the width, so a full refill lands on the end
; or short of it and the compare is exact: no call is counted anywhere.
        .macro  DTX_WRAP i
        lea     DTX_RING+(\i*DTX_N)+DTX_N(a6),a3
        cmpa.l  a3,a1
        bne.s   .wrapped\~
        lea     DTX_RING+(\i*DTX_N)(a6),a1
.wrapped\~:
        .endm

; One column's decoder seeded and its ring filled with P rows. ST4_init takes
; a0 = stream A, a1 = the first ring byte, a2 = B, a4 = C, a5 = D and d3.w = N.
;
; The payload stands behind a column table whose size moves with C, so its
; place is not a displacement the assembler works out: DTX_seed leaves it in
; d6, which ST4 does not touch, and every stream is reached off it.
        .macro  DTX_FILL i,wshift,kshift,streamA,streamB,streamC,streamD
        movea.l d6,a3
        lea     \streamA(a3),a0
        lea     DTX_RING+(\i*DTX_N)(a6),a1
        lea     \streamB(a3),a2
        lea     \streamC(a3),a4
        lea     \streamD(a3),a5
        move.w  #DTX_N,d3
        bsr     ST4_init
        move.l  #DTX_PERIOD,d3
        DTX_BUDGET \wshift,\kshift
        bsr     ST4_resume
        DTX_WRAP \i
        lea     DTX_SLOT+(\i*32)(a6),a3
        movem.l d0-d2/a0-a2/a4-a5,(a3)
        .endm

; One column refilled: its slot loaded whole, one ST4_resume, the wrap, and
; the slot written back. The rows this refill takes stand in d3.
        .macro  DTX_REFILL i,wshift,kshift
DTX_refill\i:
        DTX_BUDGET \wshift,\kshift
        lea     DTX_SLOT+(\i*32)(a6),a3
        movem.l (a3)+,d0-d2/a0-a2/a4-a5
        bsr     ST4_resume
        DTX_WRAP \i
        lea     DTX_SLOT+(\i*32)(a6),a3
        movem.l d0-d2/a0-a2/a4-a5,(a3)
        bra.w   DTX_turn
        .endm

        .macro  DTX_REFILL_SLOT i
        bra.w   DTX_refill\i
        .endm

; -----------------------------------------------------------------------------
; The image, doc/abi.md 1: five slots, the format block, then the bodies.

        include "DTX_image.S"
        DTX_IMAGE 2,DTX_PERIOD,DTX_N,ST4_UNIT

; -----------------------------------------------------------------------------
; DTX_payload. The payload's first byte in a1. The table stands behind a
; column table whose size moves with C, so where it begins is read out of the
; format block rather than assembled in.

DTX_payload:
        lea     _fmt(pc),a2
        movea.l a2,a1
        suba.w  #_fmt-_base,a1
        adda.l  8(a2),a1
        movea.l a1,a2
        moveq   #0,d1
        move.w  8(a2),d1
        addi.l  #14+3,d1
        andi.l  #-4,d1
        adda.l  d1,a1
        rts

; -----------------------------------------------------------------------------
; Every decoder seeded, every ring filled, the cursors a row below row 0.
; Init takes it, and so does a jump that runs from row 0.

DTX_seed:
        move.l  #-1,DTX_ROW(a6)
        clr.w   DTX_TURN(a6)
        move.l  #DTX_PERIOD,DTX_DECODED(a6)
        bsr     DTX_payload             ; the payload in d6, which ST4 keeps
        move.l  a1,d6
        DTX_FILL_COLUMNS
        DTX_SEED_CURSORS
        rts

; -----------------------------------------------------------------------------
; One row: the cursors move, the column whose turn it is is refilled, and the
; turn steps. At the turn's wrap a period's rows join the rows decoded, which
; is what shortens a column's last refill and stops the one after it.

DTX_step:
        DTX_STEP_CURSORS
; DTX_take steps the cursors from the registers a read left them in, and
; comes in here for the turn and the refill.
DTX_refill:
        move.l  #DTX_ROWS,d3
        sub.l   DTX_DECODED(a6),d3      ; the rows this period's refills take
        ble.w   DTX_turn                ; every row decoded: no refill at all
        cmpi.l  #DTX_PERIOD,d3
        bcs.s   .rows
        move.l  #DTX_PERIOD,d3
.rows:
        move.w  DTX_TURN(a6),d1
        cmpi.w  #DTX_COLUMNS,d1
        bcc.w   DTX_turn                ; a turn past the last column
        add.w   d1,d1
        add.w   d1,d1
        lea     DTX_table(pc),a3
        jmp     0(a3,d1.w)
DTX_table:
        DTX_REFILL_TABLE
        DTX_REFILL_BODIES

DTX_turn:
        move.w  DTX_TURN(a6),d1
        addq.w  #1,d1
        cmpi.w  #DTX_PERIOD,d1
        bcs.s   .stored
        clr.w   d1
        move.l  #DTX_ROWS,d3            ; a period ended, so every column took
        sub.l   DTX_DECODED(a6),d3      ; its refill: those rows are decoded
        ble.s   .stored
        cmpi.l  #DTX_PERIOD,d3
        bcs.s   .grew
        move.l  #DTX_PERIOD,d3
.grew:
        add.l   d3,DTX_DECODED(a6)
.stored:
        move.w  d1,DTX_TURN(a6)
        rts

; -----------------------------------------------------------------------------

DTX_init:
        movem.l d6-d7/a6,DTX_PARK(a0)
        movea.l a0,a6
        bsr     DTX_seed
        movem.l DTX_PARK(a6),d6-d7/a6
        rts

; ST4_resume clobbers d3, d4, d5 and a3, so nothing but a6 and the stack
; stands across a step: the target is held on the stack.
DTX_jump:
        movem.l d6-d7/a6,DTX_PARK(a0)
        movea.l a0,a6
        move.l  d0,-(a7)
        move.l  DTX_ROW(a6),d4
        bmi.s   .afresh
        cmp.l   (a7),d4
        bcs.s   .run                    ; the row stands below the target
.afresh:
        bsr     DTX_seed
.run:
        move.l  DTX_ROW(a6),d4
        cmp.l   (a7),d4
        beq.s   .there
        addq.l  #1,d4
        move.l  d4,DTX_ROW(a6)
        bsr     DTX_step
        bra.s   .run
.there:
        move.l  (a7)+,d0
        movem.l DTX_PARK(a6),d6-d7/a6
        rts

; The move that leaves d0 sets N and Z, so bmi after the call.
DTX_advance:
        movem.l d6-d7/a6,DTX_PARK(a0)
        movea.l a0,a6
        move.l  DTX_ROW(a6),d0
        addq.l  #1,d0
        cmpi.l  #DTX_ROWS,d0
        bne.s   .step
        .if DTX_REPEAT<DTX_ROWS
        move.l  #DTX_REPEAT,d0
        movem.l DTX_PARK(a6),d6-d7/a6
        bra.w   DTX_jump                ; the repeat is a jump
        .else
        moveq   #-1,d0                  ; the end, and it is sticky
        movem.l DTX_PARK(a6),d6-d7/a6
        rts
        .endif
.step:
        move.l  d0,DTX_ROW(a6)
        bsr     DTX_step
        move.l  DTX_ROW(a6),d0          ; a step clobbers d0 to d5
        movem.l DTX_PARK(a6),d6-d7/a6
        rts

; The row as DTX0 lays it out, and nothing between the values. A read moves
; no cursor, refills nothing and decodes nothing, and the class cursors take
; a2, a3 and a4, so a0 still holds the block when this returns.
DTX_read:
        tst.l   DTX_ROW(a0)
        bmi.s   .none
        DTX_LOAD_CURSORS
        DTX_READ_ROW
.none:
        rts

; -----------------------------------------------------------------------------
; DTX_take. A read and an advance in one call. The read leaves every class
; cursor in its register and the block in a0, so the step takes both from
; there. a1 goes on the stack for the length of the refill: a1 is the
; decoder's write pointer, and ST4_resume takes it.

DTX_take:
        tst.l   DTX_ROW(a0)
        bmi.w   DTX_advance             ; no row: step to row 0 and write none
        DTX_LOAD_CURSORS
        DTX_READ_ROW
        move.l  a1,-(a7)                ; the decoder takes a1
        movem.l d6-d7/a6,DTX_PARK(a0)
        movea.l a0,a6
        move.l  DTX_ROW(a6),d0
        addq.l  #1,d0
        cmpi.l  #DTX_ROWS,d0
        bne.s   .step
        .if DTX_REPEAT<DTX_ROWS
        move.l  #DTX_REPEAT,d0
        movem.l DTX_PARK(a6),d6-d7/a6
        bsr     DTX_jump                ; a0 holds the block still
        movea.l (a7)+,a1
        rts
        .else
        moveq   #-1,d0                  ; the end, and it is sticky
        movem.l DTX_PARK(a6),d6-d7/a6
        movea.l (a7)+,a1
        rts
        .endif
.step:
        move.l  d0,DTX_ROW(a6)
        DTX_STEP_HELD_CURSORS
        bsr     DTX_refill
        move.l  DTX_ROW(a6),d0
        movem.l DTX_PARK(a6),d6-d7/a6
        movea.l (a7)+,a1
        rts

; -----------------------------------------------------------------------------
; The carried decoder, ST4_wrap.S at this payload's unit.

        .align  4
        include "ST4_wrap.S"

; rmac cuts its listing's symbol table off before ST4_resume, which sorts
; late. The emulation rig counts what the decoder is asked for, and reaches
; it through this name instead.
DTX_resume      equ     ST4_resume

; -----------------------------------------------------------------------------
        DTX_TABLE_FOLLOWS

        end
