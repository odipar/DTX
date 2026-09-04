; -----------------------------------------------------------------------------
; DTX.S - the DTX reader for the 68000: five calls, one table, PC relative
;
; By Robbert van Dalen. Written by Claude (Anthropic's Claude Code) under
; Robbert's direction. Use it freely, including commercially, as long as your
; documentation says you used DTX (see LICENSE).
;
; doc/abi.md states the calls, the format block and the state block. This is
; the code behind them, for DTX0, DTX1 and DTX2 alike. Everything one table
; settles reaches it as an equate or a list, in DTX_table.i, which the
; packager writes beside this file:
;
;       org.dtx.Packager in.dtx out.bin
;
; THE TABLE'S BYTES FOLLOW THE CODE. Nothing here holds them: _table is the
; last label and the code ends on a long, so the packager appends the .dtx
; file to what rmac writes and _table is where it lands. The image is one
; run of bytes, code then table, and every reference into it is PC relative.
;
; -----------------------------------------------------------------------------
; WHAT THE PACKAGER STATES, in DTX_table.i:
;
;   DTX_VARIANT   0, 1 or 2          DTX_ROWS      R
;   DTX_REPEAT    RR                 DTX_COLUMNS   C
;   DTX_ROWBYTES  the row's bytes    DTX_CLASSES   the widths the table holds
;   DTX_HEADER    what the table's header runs to, so _payload is _table+it
;   DTX_ROWPOW    1 where the row's bytes are a power of two, 0 otherwise
;   DTX_ROWSHIFT  log2 of them where it is, and 0 where it is not: rmac
;                 crashes on a negative repeat count, conditional or not
;   DTX_READSIZE  1, 2 or 4, the move a DTX0 read runs on
;   DTX_READCOUNT how many of them a row takes
;   DTX_STATE     the state block's bytes
;   DTX2 also:    DTX_PERIOD, DTX_N, DTX_SLOT, DTX_RING, ST4_UNIT
;
; and the lists, each one macro invocation a class or a column:
;
;   DTX_SEED_CURSORS  DTX_JUMP_CURSORS  DTX_STEP_CURSORS
;   DTX_LOAD_CURSORS  DTX_READ_ROW
;   DTX2 also:  DTX_FILL_COLUMNS  DTX_REFILL_TABLE  DTX_REFILL_BODIES
;
; -----------------------------------------------------------------------------
; TRUSTED INPUT ONLY. No call checks the table, the state block, the row
; number or the address a row goes to. doc/abi.md 7 lists what each call
; takes without checking.
; -----------------------------------------------------------------------------

        .68000
        .text

        include "DTX_table.i"

; The state block, doc/abi.md 3.
DTX_ROW         equ     0               ; long  the row, or $FFFFFFFF
DTX_TURN        equ     4               ; word  the turn, 0 to P-1
DTX_DECODED     equ     8               ; long  the rows decoded
DTX_PARK        equ     12              ; 12    the caller's a6, d6 and d7
DTX_CURSOR      equ     24              ; long  one a width class

; -----------------------------------------------------------------------------
; The macros the lists are written in.

; A class cursor into the register a read takes it from. a0 holds the state
; block, so the class that takes a0 is loaded last.
        .macro  DTX_LOAD_CURSOR c,cur
        movea.l DTX_CURSOR+\c(a0),\cur
        .endm

; One value into the row: the size its width asks for, off its class cursor.
        .macro  DTX_VALUE       width,disp,cur
        .if \width=1
        move.b  \disp(\cur),(a1)+
        .endif
        .if \width=2
        move.w  \disp(\cur),(a1)+
        .endif
        .if \width=4
        move.l  \disp(\cur),(a1)+
        .endif
        .endm

; A class cursor a row below row 0, so the advance into row 0 steps like any
; other advance.
        .macro  DTX_SEED_CURSOR c,width,base
        .if DTX_VARIANT=2
        lea     DTX_RING+\base-\width(a6),a1
        move.l  a1,DTX_CURSOR+\c(a6)
        .else
        lea     _payload+\base-\width(pc),a1
        move.l  a1,DTX_CURSOR+\c(a0)
        .endif
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

; A class cursor one row on. Under DTX2 it wraps at the ring end.
        .macro  DTX_STEP_CURSOR c,width,base
        .if DTX_VARIANT=2
        movea.l DTX_CURSOR+\c(a6),a1
        lea     \width(a1),a1
        lea     DTX_RING+\base+DTX_N(a6),a3
        cmpa.l  a3,a1
        bne.s   .cursor\~
        lea     DTX_RING+\base(a6),a1
.cursor\~:
        move.l  a1,DTX_CURSOR+\c(a6)
        .else
        move.l  DTX_CURSOR+\c(a0),d1
        addq.l  #\width,d1
        move.l  d1,DTX_CURSOR+\c(a0)
        .endif
        .endm

        .if DTX_VARIANT=2

; The budget one refill of this column asks for, in units: the rows in d3
; times the column's width, divided by k.
        .macro  DTX_BUDGET      wshift,kshift
        .rept   \wshift
        add.l   d3,d3
        .endr
        .if \kshift>0
        lsr.l   #\kshift,d3
        .endif
        .endm

; The write pointer goes back to the ring start where a refill landed on the
; ring end. N divides by P times the width, so the compare is exact and no
; call is counted.
        .macro  DTX_WRAP        i
        lea     DTX_RING+(\i*DTX_N)+DTX_N(a6),a3
        cmpa.l  a3,a1
        bne.s   .wrapped\~
        lea     DTX_RING+(\i*DTX_N)(a6),a1
.wrapped\~:
        .endm

; One column's decoder seeded and its ring filled with P rows.
        .macro  DTX_FILL        i,wshift,kshift,streamA,streamB,streamC,streamD
        lea     _payload+\streamA(pc),a0
        lea     DTX_RING+(\i*DTX_N)(a6),a1
        lea     _payload+\streamB(pc),a2
        lea     _payload+\streamC(pc),a4
        lea     _payload+\streamD(pc),a5
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
; the slot written back.
        .macro  DTX_REFILL      i,wshift,kshift
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

        .endif

; -----------------------------------------------------------------------------
; The image, doc/abi.md 1: five slots, the format block, then the bodies.

_base:
        bra.w   DTX_init
        bra.w   DTX_metadata
        bra.w   DTX_jump
        bra.w   DTX_advance
        bra.w   DTX_read

_fmt:
        dc.b    'D','T','X',DTX_VARIANT
        dc.l    DTX_STATE
        dc.l    _table-_base
        dc.w    DTX_ROWBYTES
        .if DTX_VARIANT=2
        dc.w    DTX_PERIOD
        dc.w    DTX_N
        dc.b    ST4_UNIT,0
        .else
        dc.w    1                       ; P, where nothing is packed
        dc.w    0                       ; N
        dc.b    0,0                     ; k, and a zero
        .endif

; -----------------------------------------------------------------------------
; 1. DTX_metadata. The one call that may be made before init.

DTX_metadata:
        lea     _fmt(pc),a0
        lea     _table(pc),a1
        move.l  #DTX_ROWS,d0
        move.w  #DTX_COLUMNS,d1
        move.l  #DTX_REPEAT,d2
        rts

        .if DTX_VARIANT=2

; -----------------------------------------------------------------------------
; The packed reader. a6 holds the state block through all of it, and the
; caller's a6, d6 and d7 stand parked in the block.

; Every decoder seeded, every ring filled, the cursors a row below row 0.
DTX_seed:
        move.l  #-1,DTX_ROW(a6)
        clr.w   DTX_TURN(a6)
        move.l  #DTX_PERIOD,DTX_DECODED(a6)
        DTX_FILL_COLUMNS
        DTX_SEED_CURSORS
        rts

; One row: the cursors move, the column whose turn it is is refilled, and
; the turn steps. At the turn's wrap a period's rows join the rows decoded.
DTX_step:
        DTX_STEP_CURSORS
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
        moveq   #-1,d0
        movem.l DTX_PARK(a6),d6-d7/a6
        rts
        .endif
.step:
        move.l  d0,DTX_ROW(a6)
        bsr     DTX_step
        move.l  DTX_ROW(a6),d0          ; a step clobbers d0 to d5
        movem.l DTX_PARK(a6),d6-d7/a6
        rts

        .else

; -----------------------------------------------------------------------------
; The plain reader. No decoder, no ring, and no register parked.

DTX_init:
        move.l  #-1,DTX_ROW(a0)
        clr.w   DTX_TURN(a0)
        clr.l   DTX_DECODED(a0)
        .if DTX_VARIANT=0
        lea     _payload-DTX_ROWBYTES(pc),a1
        move.l  a1,DTX_CURSOR(a0)
        .else
        DTX_SEED_CURSORS
        .endif
        rts

DTX_jump:
        move.l  d0,DTX_ROW(a0)
        .if DTX_VARIANT=0
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
        .else
        DTX_JUMP_CURSORS
        .endif
        rts

DTX_advance:
        move.l  DTX_ROW(a0),d0
        addq.l  #1,d0
        cmpi.l  #DTX_ROWS,d0
        bne.s   .step
        .if DTX_REPEAT<DTX_ROWS
        move.l  #DTX_REPEAT,d0
        bra.w   DTX_jump                ; the repeat is a jump
        .else
        moveq   #-1,d0
        rts
        .endif
.step:
        move.l  d0,DTX_ROW(a0)
        .if DTX_VARIANT=0
        move.l  DTX_CURSOR(a0),d1
        addi.l  #DTX_ROWBYTES,d1
        move.l  d1,DTX_CURSOR(a0)
        .else
        DTX_STEP_CURSORS
        .endif
        rts

        .endif

; -----------------------------------------------------------------------------
; 4. DTX_read. The row as DTX0 lays it out, and nothing between the values.

DTX_read:
        tst.l   DTX_ROW(a0)
        bmi.s   .none
        .if DTX_VARIANT=0
        movea.l DTX_CURSOR(a0),a2
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
.run:
        .if DTX_READSIZE=4
        move.l  (a2)+,(a1)+
        .endif
        .if DTX_READSIZE=2
        move.w  (a2)+,(a1)+
        .endif
        .if DTX_READSIZE=1
        move.b  (a2)+,(a1)+
        .endif
        dbf     d0,.run
        .endif
        .else
        DTX_LOAD_CURSORS
        DTX_READ_ROW
        .endif
.none:
        rts

; -----------------------------------------------------------------------------
; The carried decoder, under DTX2 alone.

        .if DTX_VARIANT=2
        .align  4
        include "ST4_wrap.S"
        .endif

; -----------------------------------------------------------------------------
; The table's bytes follow. Nothing stands after this label: the packager
; appends the .dtx file to what rmac writes, so _table is where it lands and
; the code ends on a long to put it there.

        .align  4
_table:
_payload        equ     _table+DTX_HEADER

        end
