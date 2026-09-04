; -----------------------------------------------------------------------------
; DTX0.S - a DTX0 table read on the 68000: five calls, one table, PC relative
;
; By Robbert van Dalen. Written by Claude (Anthropic's Claude Code) under
; Robbert's direction. Use it freely, including commercially, as long as your
; documentation says you used DTX (see LICENSE).
;
; doc/abi.md states the calls, the format block and the state block. One
; template a variant: this file reads DTX0 and nothing else, so no call in it
; tests which variant it holds. org.dtx.Packager picks the template the
; table's variant asks for.
;
; DTX0 lays the rows one after another, so one cursor walks them and a read
; is one run of bytes. There is no width class here and no list: what one
; table settles reaches this file as equates alone, in DTX_table.i.
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
; The image, doc/abi.md 1: five slots, the format block, then the bodies.

        include "DTX_image.S"
        DTX_IMAGE 0,1,0,0

; -----------------------------------------------------------------------------
; DTX_init. The cursor stands a row below row 0, so the advance into row 0
; steps like any other advance.

DTX_init:
        move.l  #-1,DTX_ROW(a0)
        clr.w   DTX_TURN(a0)
        clr.l   DTX_DECODED(a0)
        lea     _payload-DTX_ROWBYTES(pc),a1
        move.l  a1,DTX_CURSOR(a0)
        rts

; -----------------------------------------------------------------------------
; DTX_jump. The cursor is the payload plus the row times the row's bytes.

DTX_jump:
        move.l  d0,DTX_ROW(a0)
        .if DTX_ROWPOW=1
        move.l  d0,d1
        .rept   DTX_ROWSHIFT
        add.l   d1,d1
        .endr
        .else
        move.l  d0,d1                   ; the row's bytes are no power of two,
        swap    d1                      ; so two mulu.w make the 32 by 16
        mulu.w  #DTX_ROWBYTES,d1        ; product
        swap    d1
        clr.w   d1
        move.l  d0,d3
        andi.l  #$FFFF,d3
        mulu.w  #DTX_ROWBYTES,d3
        add.l   d3,d1
        .endif
        lea     _payload(pc),a1
        adda.l  d1,a1
        move.l  a1,DTX_CURSOR(a0)
        rts

; -----------------------------------------------------------------------------
; DTX_advance. The move that leaves d0 sets N and Z, so bmi after the call.

DTX_advance:
        move.l  DTX_ROW(a0),d0
        addq.l  #1,d0
        DTX_END_OR_REPEAT
.step:
        move.l  d0,DTX_ROW(a0)
        move.l  DTX_CURSOR(a0),d1
        addi.l  #DTX_ROWBYTES,d1
        move.l  d1,DTX_CURSOR(a0)
        rts

; -----------------------------------------------------------------------------
; DTX_read. One run of bytes: longs where the row's bytes divide by four,
; words where they are even, bytes where they are odd. A row that takes more
; than eight moves takes a loop instead.

; The run of moves a row takes. It walks a2 from the row's first byte to one
; past its last, which is the next row's cursor: DTX_take stores that and
; adds nothing.
        .macro  DTX_READ_RUN
        .if DTX_READCOUNT<=8
        .rept   DTX_READCOUNT
        .if DTX_READSIZE=4
        move.l  (a2)+,(a1)+
        .endif
        .if DTX_READSIZE=2
        move.w  (a2)+,(a1)+
        .endif
        .if DTX_READSIZE=1
        move.b  (a2)+,(a1)+
        .endif
        .endr
        .else
        move.w  #DTX_READCOUNT-1,d0
.run\~:
        .if DTX_READSIZE=4
        move.l  (a2)+,(a1)+
        .endif
        .if DTX_READSIZE=2
        move.w  (a2)+,(a1)+
        .endif
        .if DTX_READSIZE=1
        move.b  (a2)+,(a1)+
        .endif
        dbf     d0,.run\~
        .endif
        .endm

DTX_read:
        tst.l   DTX_ROW(a0)
        bmi.s   .none
        movea.l DTX_CURSOR(a0),a2
        DTX_READ_RUN
.none:
        rts

; -----------------------------------------------------------------------------
; DTX_take. A read and an advance in one call. The read leaves a2 one past
; the row it took, which is the cursor the step would work out, so the step
; stores a2 and does no arithmetic at all.

DTX_take:
        tst.l   DTX_ROW(a0)
        bmi.w   DTX_advance             ; no row: step to row 0 and write none
        movea.l DTX_CURSOR(a0),a2
        DTX_READ_RUN
        move.l  DTX_ROW(a0),d0
        addq.l  #1,d0
        DTX_TAKE_END_OR_REPEAT
.step:
        move.l  d0,DTX_ROW(a0)
        move.l  a2,DTX_CURSOR(a0)       ; where the read ended
        rts

; -----------------------------------------------------------------------------
        DTX_TABLE_FOLLOWS

        end
