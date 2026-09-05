; -----------------------------------------------------------------------------
; DTX2.S - a DTX2 table read on the 68000: six calls, one table, PC relative
;
; By Robbert van Dalen. Written by Claude (Anthropic's Claude Code) under
; Robbert's direction. Use it freely, including commercially, as long as your
; documentation says you used DTX (see LICENSE).
;
; doc/abi.md states the calls, the format block and the state block. One
; template a variant: this file reads DTX2 and nothing else, so no call in it
; tests which variant it contains. org.dtx.Packager takes the template the
; table's variant needs.
;
; DTX2 packs a column with ST4, and this reader follows YMX. Every column
; decodes through its own ring; init fills every ring with P rows; and after
; that one column is refilled a row, the one whose turn it is. So a read takes
; one value from each ring and never enters a decoder. ST4_wrap.S, the copy
; carried into this repository, is the decoder, built at the unit the payload
; states.
;
; THE CODE DOES NOT MOVE WITH THE TABLE. R, C, RR, P, N and the widths reach
; it at run time. R, RR, P, N and C stand in the state block, which init reads
; out of the column table; the read's three counts and the row's bytes are
; written into the instructions that take them. What still moves the code is
; the decoder built into it: k and the copy code are ST4's own assembly
; parameters, so a table packed at another unit, or with copies, contains other
; instructions.
;
; ONE FILL AND ONE REFILL FOR EVERY COLUMN. The column table contains a stream
; record a column at a fixed stride, so the fill is a loop over the records
; and the refill indexes them by the turn. ST4 leaves only d6, d7 and a6
; alone, so the loop's state is in neither: the record now being
; filled stands in the block, and a6 reaches it.
;
; THE COLUMN TABLE stands behind the code, and the format block states where.
; Its header and read entries are DTX1's, and the records follow them:
;
;   +0   4  stream A, from the payload    +16  4  the ring, in the block
;   +4   4  stream B                      +20  4  the decoder state, in the block
;   +8   4  stream C                      +24  2  the width's shift
;   +12  4  stream D                      +26  2  the unit's shift
;
; a6 IS THE STATE BLOCK through init, jump and advance, and the caller's
; a6, d6 and d7 stand parked in the block for the length of the call: ST4
; leaves those three alone, and they are the only registers that stand across
; an ST4_resume. Read takes the block in a0 and parks nothing.
;
; NOTHING IS CHECKED. No call checks the table, the state block, the row
; number or the address a row goes to. doc/abi.md 7 lists what each call
; takes without checking, ST4_wrap.S its own seven.
; -----------------------------------------------------------------------------

        .68000
        .text

        include "DTX_table.i"

; The column table's own header, and where a read's entries begin.
DTX_COL_COUNT       equ     0               ; three words, one a width
DTX_COL_ROWBYTES    equ     6
DTX_COL_BASE        equ     8               ; three longs, one a width
DTX_COL_PERIOD      equ     22
DTX_COL_STREAMS     equ     24              ; the records, from the table
DTX_COL_N           equ     28
DTX_COL_ENTRIES     equ     32

; One stream record.
DTX_S_A             equ     0
DTX_S_B             equ     4
DTX_S_C             equ     8
DTX_S_D             equ     12
DTX_S_RING          equ     16
DTX_S_DECODER       equ     20
DTX_S_WSHIFT        equ     24
DTX_S_KSHIFT        equ     26
DTX_S_SIZE          equ     32

; The state block beyond what doc/abi.md 3 states: three cursors, the three
; rings their classes begin at, the record a fill is at, and the five figures
; the table gives. The decoder states stand at DTX_DECODERS, one a column,
; and the rings behind them.
DTX_PAYLOAD         equ     36
DTX_RECORD          equ     40
DTX_LEFT            equ     44              ; the columns a fill has left
DTX_CLASSBASE       equ     48              ; three longs, one a width
DTX_ROWSFIG         equ     60              ; R
DTX_REPEATFIG       equ     64              ; RR
DTX_PERIODFIG       equ     68              ; P
DTX_NFIG            equ     72              ; N
DTX_CFIG            equ     76              ; C, a word
DTX_DECODERS        equ     80

; -----------------------------------------------------------------------------
        include "DTX_image.S"
        DTX_IMAGE 2,DTX_PERIOD,DTX_N,ST4_UNIT

; -----------------------------------------------------------------------------
; What a value is installed with. A 68000 reaches PC relative for a source
; and never for a destination, so a site is lea'd into a4 and written through
; it, and the assembler works out where each site stands.

        .macro  DTX_INSTALL_W   value,site
        lea     \site+2(pc),a4
        move.w  \value,(a4)
        .endm

; -----------------------------------------------------------------------------
; Where things stand, worked out rather than assembled in: the payload and the
; column table both move with C.

; The payload's first byte in a1 and the table's header in a2.
DTX_payload:
        lea     _fmt(pc),a2
        movea.l a2,a1
        suba.w  #_fmt-_base,a1
        adda.l  8(a2),a1
        movea.l a1,a2                   ; the header
        moveq   #0,d1
        move.w  8(a2),d1                ; C
        addi.l  #14+3,d1
        andi.l  #-4,d1
        adda.l  d1,a1                   ; the payload
        rts

; The column table's first byte in a3.
DTX_columns:
        lea     _fmt(pc),a3
        adda.l  20(a3),a3               ; the field, while a3 is still the block
        suba.w  #_fmt-_base,a3          ; and the offset counts from _base
        rts

; The first stream record in a3.
DTX_streams:
        bsr     DTX_columns
        adda.l  DTX_COL_STREAMS(a3),a3
        rts

; -----------------------------------------------------------------------------
; The budget one refill takes, in units: the rows in d3 times the column's
; width, divided by k. Both are shifts, and both stand at or above zero. The
; record stands in a3, and d4 counts: a seed works a budget out between
; ST4_init and ST4_resume, and d0, d1, d2, a0, a1, a2, a4 and a5 compare the
; decoder's state across that.

DTX_budget:
        move.w  DTX_S_WSHIFT(a3),d4
        bra.s   .up_test
.up:
        add.l   d3,d3
.up_test:
        dbf     d4,.up
        move.w  DTX_S_KSHIFT(a3),d4
        bra.s   .down_test
.down:
        lsr.l   #1,d3
.down_test:
        dbf     d4,.down
        rts

; The write pointer goes back to the ring start where a refill landed on the
; ring end. N divides by P times the width, so a full refill lands on the end
; or short of it and the compare is exact: no call is counted anywhere. The
; record stands in a3, and d3 and d4 are ST4's to clobber.

DTX_wrap:
        move.l  a1,d3
        sub.l   a6,d3                   ; the pointer's place in the block
        move.l  DTX_S_RING(a3),d4
        add.l   DTX_NFIG(a6),d4
        cmp.l   d4,d3
        bne.s   .held
        movea.l a6,a1
        adda.l  DTX_S_RING(a3),a1
.held:
        rts

; The decoder state of the record in a3, written from the decoder's registers.
; d4 has the offset while a3 becomes the state: nothing else is free.

DTX_store:
        move.l  DTX_S_DECODER(a3),d4
        movea.l a6,a3
        adda.l  d4,a3
        movem.l d0-d2/a0-a2/a4-a5,(a3)
        rts

; -----------------------------------------------------------------------------
; DTX_seed. Every decoder seeded, every ring filled with P rows, the cursors a
; row below row 0. Init takes it, and so does a jump that runs from row 0.
;
; One loop over the stream records. ST4 clobbers a3, so the record is read
; back out of the block after every call into it.

DTX_seed:
        move.l  #-1,DTX_ROW(a6)
        clr.w   DTX_TURN(a6)
        move.l  DTX_PERIODFIG(a6),DTX_DECODED(a6)
        bsr     DTX_streams
        move.l  a3,DTX_RECORD(a6)
        move.w  DTX_CFIG(a6),DTX_LEFT(a6)
seed_column:
        movea.l DTX_RECORD(a6),a3
        movea.l DTX_PAYLOAD(a6),a0
        movea.l a0,a2
        movea.l a0,a4
        movea.l a0,a5
        adda.l  DTX_S_A(a3),a0
        adda.l  DTX_S_B(a3),a2
        adda.l  DTX_S_C(a3),a4
        adda.l  DTX_S_D(a3),a5
        movea.l a6,a1
        adda.l  DTX_S_RING(a3),a1
        move.w  DTX_NFIG+2(a6),d3       ; N, a ring's bytes, in a word
        bsr     ST4_init
        movea.l DTX_RECORD(a6),a3
        move.l  DTX_PERIODFIG(a6),d3
        bsr     DTX_budget
        bsr     ST4_resume
        movea.l DTX_RECORD(a6),a3
        bsr     DTX_wrap
        movea.l DTX_RECORD(a6),a3
        bsr     DTX_store
        move.l  DTX_RECORD(a6),d4
        addi.l  #DTX_S_SIZE,d4
        move.l  d4,DTX_RECORD(a6)
        subq.w  #1,DTX_LEFT(a6)
        bne.s   seed_column

; A class cursor a row below its ring's first row, so the advance into row 0
; steps like any other advance.
        move.l  DTX_CLASSBASE+0(a6),d0
        subq.l  #1,d0
        move.l  d0,DTX_CURSOR+0(a6)
        move.l  DTX_CLASSBASE+4(a6),d0
        subq.l  #2,d0
        move.l  d0,DTX_CURSOR+4(a6)
        move.l  DTX_CLASSBASE+8(a6),d0
        subq.l  #4,d0
        move.l  d0,DTX_CURSOR+8(a6)
        rts

; -----------------------------------------------------------------------------
; DTX_init. The five figures into the block, the three ring bases with them,
; the read's counts and the row's bytes into the instructions that take them,
; and then a seed.

DTX_init:
        movem.l d6-d7/a6,DTX_PARK(a0)
        movea.l a0,a6

        bsr     DTX_payload             ; a1 the payload, a2 the header
        move.l  a1,DTX_PAYLOAD(a6)
        move.l  4(a2),d0                ; R
        move.l  d0,DTX_ROWSFIG(a6)
        move.l  10(a2),d0               ; RR
        move.l  d0,DTX_REPEATFIG(a6)
        move.w  8(a2),DTX_CFIG(a6)      ; C

        bsr     DTX_columns
        moveq   #0,d0
        move.w  DTX_COL_PERIOD(a3),d0
        move.l  d0,DTX_PERIODFIG(a6)
        move.l  DTX_COL_N(a3),DTX_NFIG(a6)

; Where each class begins: a ring in the block, so a6 makes it an address.
        move.l  a6,d1
        move.l  DTX_COL_BASE+0(a3),d0
        add.l   d1,d0
        move.l  d0,DTX_CLASSBASE+0(a6)
        move.l  DTX_COL_BASE+4(a3),d0
        add.l   d1,d0
        move.l  d0,DTX_CLASSBASE+4(a6)
        move.l  DTX_COL_BASE+8(a3),d0
        add.l   d1,d0
        move.l  d0,DTX_CLASSBASE+8(a6)

        move.w  DTX_COL_ROWBYTES(a3),d2
        DTX_INSTALL_W d2,read_over
        DTX_INSTALL_W d2,take_over

; The counts, one a width, into the six loops that walk them. A count of zero
; leaves its loop with -1 in the counter, which the branch below it takes as
; none.
        move.w  DTX_COL_COUNT+0(a3),d0
        subq.w  #1,d0
        DTX_INSTALL_W d0,read_bytes
        DTX_INSTALL_W d0,take_bytes
        move.w  DTX_COL_COUNT+2(a3),d0
        subq.w  #1,d0
        DTX_INSTALL_W d0,read_words
        DTX_INSTALL_W d0,take_words
        move.w  DTX_COL_COUNT+4(a3),d0
        subq.w  #1,d0
        DTX_INSTALL_W d0,read_longs
        DTX_INSTALL_W d0,take_longs

        bsr     DTX_seed
        movem.l DTX_PARK(a6),d6-d7/a6
        rts

; -----------------------------------------------------------------------------
; DTX_step. One row: the cursors move, the column whose turn it is is
; refilled, and the turn steps.

DTX_step:
        lea     DTX_CURSOR(a6),a3
        moveq   #1,d2
        bsr     DTX_move                ; the width-1 class
        addq.l  #4,a3
        add.w   d2,d2
        bsr     DTX_move                ; the width-2 class
        addq.l  #4,a3
        add.w   d2,d2
        bsr     DTX_move                ; the width-4 class

; DTX_take steps the cursors from the block itself, and comes in here for the
; turn and the refill. At the turn's wrap a period's rows join the rows
; decoded, and that shortens a column's last refill and stops the one
; after it.
DTX_refill:
        move.l  DTX_ROWSFIG(a6),d3
        sub.l   DTX_DECODED(a6),d3      ; the rows this period's refills take
        ble.w   DTX_turn                ; every row decoded: no refill
        cmp.l   DTX_PERIODFIG(a6),d3
        bcs.s   .rows
        move.l  DTX_PERIODFIG(a6),d3
.rows:
        move.w  DTX_TURN(a6),d1
        cmp.w   DTX_CFIG(a6),d1
        bcc.w   DTX_turn                ; a turn past the last column
        mulu.w  #DTX_S_SIZE,d1
        bsr     DTX_streams
        adda.l  d1,a3
        move.l  a3,DTX_RECORD(a6)
        bsr     DTX_budget
        movea.l DTX_RECORD(a6),a3
        move.l  DTX_S_DECODER(a3),d4
        movea.l a6,a3
        adda.l  d4,a3
        movem.l (a3)+,d0-d2/a0-a2/a4-a5
        bsr     ST4_resume
        movea.l DTX_RECORD(a6),a3
        bsr     DTX_wrap
        movea.l DTX_RECORD(a6),a3
        bsr     DTX_store

DTX_turn:
        move.w  DTX_TURN(a6),d1
        addq.w  #1,d1
        cmp.w   DTX_PERIODFIG+2(a6),d1
        bcs.s   .stored
        clr.w   d1
        move.l  DTX_ROWSFIG(a6),d3      ; a period ended, so every column took
        sub.l   DTX_DECODED(a6),d3      ; its refill: those rows are decoded
        ble.s   .stored
        cmp.l   DTX_PERIODFIG(a6),d3
        bcs.s   .grew
        move.l  DTX_PERIODFIG(a6),d3
.grew:
        add.l   d3,DTX_DECODED(a6)
.stored:
        move.w  d1,DTX_TURN(a6)
        rts

; One class cursor a row on, back to its ring start where it reaches the end.
; The cursor's place in the block stands in a3, its width in d2.
DTX_move:
        move.l  (a3),d0
        add.l   d2,d0
        move.l  DTX_CLASSBASE-DTX_CURSOR(a3),d1
        add.l   DTX_NFIG(a6),d1
        cmp.l   d1,d0
        bne.s   .held
        move.l  DTX_CLASSBASE-DTX_CURSOR(a3),d0
.held:
        move.l  d0,(a3)
        rts

; -----------------------------------------------------------------------------
; DTX_jump. ST4_resume clobbers d3, d4, d5 and a3, so nothing but a6 and the
; stack stands across a step: the target is on the stack.

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

; -----------------------------------------------------------------------------
; DTX_advance. The move that leaves d0 sets N and Z, so bmi after the call.

DTX_advance:
        movem.l d6-d7/a6,DTX_PARK(a0)
        movea.l a0,a6
        move.l  DTX_ROW(a6),d0
        addq.l  #1,d0
        cmp.l   DTX_ROWSFIG(a6),d0
        bne.s   .step
        move.l  DTX_REPEATFIG(a6),d0
        cmp.l   DTX_ROWSFIG(a6),d0
        bcc.s   .over                   ; RR at R: the end, and it is sticky
        movem.l DTX_PARK(a6),d6-d7/a6
        bra.w   DTX_jump                ; the repeat is a jump
.over:
        moveq   #-1,d0
        movem.l DTX_PARK(a6),d6-d7/a6
        rts
.step:
        move.l  d0,DTX_ROW(a6)
        bsr     DTX_step
        move.l  DTX_ROW(a6),d0          ; a step clobbers d0 to d5
        movem.l DTX_PARK(a6),d6-d7/a6
        rts

; -----------------------------------------------------------------------------
; DTX_read. The row as DTX0 lays it out, and nothing between the values. One
; loop a width, over the run of entries of that width: a column's value
; stands at a displacement off its class cursor, and the ring the class walks
; puts every column of it a whole ring apart.
;
; A read moves no cursor, refills nothing and decodes nothing.

DTX_read:
        tst.l   DTX_ROW(a0)
        bmi.w   read_none
        bsr     DTX_columns
        lea     DTX_COL_ENTRIES(a3),a3
        movea.l DTX_CURSOR+0(a0),a2
read_bytes:
        move.w  #0,d0                   ; installed: the width-1 columns, less one
        bmi.s   read_word_class
read_byte_loop:
        move.w  (a3)+,d1
        move.w  (a3)+,d2
        move.b  0(a2,d1.w),0(a1,d2.w)
        dbf     d0,read_byte_loop
read_word_class:
        movea.l DTX_CURSOR+4(a0),a2
read_words:
        move.w  #0,d0                   ; installed: the width-2 columns, less one
        bmi.s   read_long_class
read_word_loop:
        move.w  (a3)+,d1
        move.w  (a3)+,d2
        btst    #0,d2                   ; odd in the row: bytes, or a 68000
        bne.s   read_word_odd            ; takes an address error
        move.w  0(a2,d1.w),0(a1,d2.w)
        dbf     d0,read_word_loop
        bra.s   read_long_class
read_word_odd:
        move.b  0(a2,d1.w),0(a1,d2.w)
        move.b  1(a2,d1.w),1(a1,d2.w)
        dbf     d0,read_word_loop
read_long_class:
        movea.l DTX_CURSOR+8(a0),a2
read_longs:
        move.w  #0,d0                   ; installed: the width-4 columns, less one
        bmi.s   read_done
read_long_loop:
        move.w  (a3)+,d1
        move.w  (a3)+,d2
        btst    #0,d2                   ; odd in the row: bytes, as above
        bne.s   read_long_odd
        move.l  0(a2,d1.w),0(a1,d2.w)
        dbf     d0,read_long_loop
        bra.s   read_done
read_long_odd:
        move.b  0(a2,d1.w),0(a1,d2.w)
        move.b  1(a2,d1.w),1(a1,d2.w)
        move.b  2(a2,d1.w),2(a1,d2.w)
        move.b  3(a2,d1.w),3(a1,d2.w)
        dbf     d0,read_long_loop
read_done:
read_over:
        adda.w  #0,a1                   ; installed: the row's bytes
read_none:
        rts

; -----------------------------------------------------------------------------
; DTX_take. A read and an advance in one call. a1 goes on the stack for the
; length of the step: a1 is the decoder's write pointer, and ST4_resume takes
; it.

DTX_take:
        tst.l   DTX_ROW(a0)
        bmi.w   DTX_advance             ; no row: step to row 0 and write none
        bsr     DTX_columns
        lea     DTX_COL_ENTRIES(a3),a3
        movea.l DTX_CURSOR+0(a0),a2
take_bytes:
        move.w  #0,d0                   ; installed: the width-1 columns, less one
        bmi.s   take_word_class
take_byte_loop:
        move.w  (a3)+,d1
        move.w  (a3)+,d2
        move.b  0(a2,d1.w),0(a1,d2.w)
        dbf     d0,take_byte_loop
take_word_class:
        movea.l DTX_CURSOR+4(a0),a2
take_words:
        move.w  #0,d0                   ; installed: the width-2 columns, less one
        bmi.s   take_long_class
take_word_loop:
        move.w  (a3)+,d1
        move.w  (a3)+,d2
        btst    #0,d2                   ; odd in the row: bytes, or a 68000
        bne.s   take_word_odd            ; takes an address error
        move.w  0(a2,d1.w),0(a1,d2.w)
        dbf     d0,take_word_loop
        bra.s   take_long_class
take_word_odd:
        move.b  0(a2,d1.w),0(a1,d2.w)
        move.b  1(a2,d1.w),1(a1,d2.w)
        dbf     d0,take_word_loop
take_long_class:
        movea.l DTX_CURSOR+8(a0),a2
take_longs:
        move.w  #0,d0                   ; installed: the width-4 columns, less one
        bmi.s   take_done
take_long_loop:
        move.w  (a3)+,d1
        move.w  (a3)+,d2
        btst    #0,d2                   ; odd in the row: bytes, as above
        bne.s   take_long_odd
        move.l  0(a2,d1.w),0(a1,d2.w)
        dbf     d0,take_long_loop
        bra.s   take_done
take_long_odd:
        move.b  0(a2,d1.w),0(a1,d2.w)
        move.b  1(a2,d1.w),1(a1,d2.w)
        move.b  2(a2,d1.w),2(a1,d2.w)
        move.b  3(a2,d1.w),3(a1,d2.w)
        dbf     d0,take_long_loop
take_done:
take_over:
        adda.w  #0,a1                   ; installed: the row's bytes
        move.l  a1,-(a7)                ; the decoder takes a1
        movem.l d6-d7/a6,DTX_PARK(a0)
        movea.l a0,a6
        move.l  DTX_ROW(a6),d0
        addq.l  #1,d0
        cmp.l   DTX_ROWSFIG(a6),d0
        bne.s   take_step
        move.l  DTX_REPEATFIG(a6),d0
        cmp.l   DTX_ROWSFIG(a6),d0
        bcc.s   take_over_end           ; RR at R: the end, and it is sticky
        movem.l DTX_PARK(a6),d6-d7/a6
        bsr     DTX_jump                ; a0 is the block still
        movea.l (a7)+,a1
        rts
take_over_end:
        moveq   #-1,d0
        movem.l DTX_PARK(a6),d6-d7/a6
        movea.l (a7)+,a1
        rts
take_step:
        move.l  d0,DTX_ROW(a6)
        bsr     DTX_step
        move.l  DTX_ROW(a6),d0
        movem.l DTX_PARK(a6),d6-d7/a6
        movea.l (a7)+,a1
        rts

; -----------------------------------------------------------------------------
; The carried decoder, ST4_wrap.S at this payload's unit.

        .align  4
        include "ST4_wrap.S"

; rmac cuts its listing's symbol table off before ST4_resume, which sorts
; late. The emulation rig counts the decoder's calls, and reaches
; it through this name instead.
DTX_resume      equ     ST4_resume

; -----------------------------------------------------------------------------
        DTX_TABLE_FOLLOWS

        end
