; -----------------------------------------------------------------------------
; DTX2.S - a DTX2 table read on the 68000: six calls, one table, PC relative
;
; By Robbert van Dalen. Written by Claude (Anthropic's Claude Code) under
; Robbert's direction. Use it freely, including commercially, as long as your
; documentation says you used DTX (see LICENSE).
;
; doc/abi.md defines the calls, the format block and the state block. One
; template a variant: this file reads only DTX2, so no call in it tests which
; variant it contains. org.dtx.Packager takes the template the table's
; variant needs.
;
; DTX2 packs a column with ST4, and this reader follows YMX. Every column
; decodes through its own ring; init fills every ring with P rows; and after
; that one column is refilled a row, the one whose turn it is. So a read takes
; one value from each ring and never enters a decoder. ST4_wrap.S, the copy
; carried into this repository, is the decoder, built at the unit the payload
; defines.
;
; THE CODE DOES NOT MOVE WITH THE TABLE. R, C, RR, P and N reach it at run
; time and stand in the state block, and the count a read walks and the
; stride between two rings are written into the instructions that take them.
;
; WHAT MOVES THE CODE is the width and the decoder: the width every value
; takes (R6.3) and k and the copy code are assembly-time constants, so a
; table of another width, or packed at another unit, or with copies, contains
; other instructions. One build a width a unit is what that costs.
;
; ONE FILL AND ONE REFILL FOR EVERY COLUMN. Every ring is N bytes and every
; decoder state 32, so the ring, the state and the stream record of column i
; are each i strides from the first: the column being filled stands in the
; block as a number, and the three follow from it. ST4 leaves only d6, d7
; and a6 alone, so nothing else stands across a call into it.
;
; THE COLUMN TABLE stands behind the code, and the format block defines where.
; It is one record a column, four longs, and nothing else:
;
;   +0   4  stream A, from the payload    +8   4  stream C
;   +4   4  stream B                      +12  4  stream D
;
; a6 IS THE STATE BLOCK through init, jump and advance, and the caller's
; a6, d6 and d7 stand parked in the block for the length of the call: ST4
; leaves those three alone, and they are the only registers that stand across
; an ST4_resume. Read takes the block in a0 and does not park.
;
; NOTHING IS CHECKED. No call checks the table, the state block, the row
; number or the address a row goes to. doc/abi.md 7 lists what each call
; takes without checking, ST4_wrap.S its own seven.
; -----------------------------------------------------------------------------

        .68000
        .text

        include "DTX_table.i"

; One stream record, and what a run of them is.
DTX_S_A             equ     0
DTX_S_B             equ     4
DTX_S_C             equ     8
DTX_S_D             equ     12
DTX_S_SIZE          equ     16

; The state block beyond what doc/abi.md 3 defines: the payload, the column
; a fill is at, the five figures the table gives, and where the rings begin.
; The decoder states stand at DTX_DECODERS, 32 a column, and the rings behind
; them, N a column.
DTX_PAYLOAD         equ     28
DTX_FILL            equ     32              ; a word: the column a fill is at
DTX_CFIG            equ     34              ; C, a word
DTX_RINGS           equ     36              ; where the rings begin, in the block
DTX_ROWSFIG         equ     40              ; R
DTX_REPEATFIG       equ     44              ; RR
DTX_PERIODFIG       equ     48              ; P
DTX_NFIG            equ     52              ; N
DTX_DECODERS        equ     56

; -----------------------------------------------------------------------------
        include "DTX_image.S"
        DTX_IMAGE 2,DTX_PERIOD,DTX_N,ST4_UNIT,DTX_WIDTH

; -----------------------------------------------------------------------------
; What a value is installed with. A 68000 reaches PC relative for a source
; and never for a destination, so a site is lea'd into a4 and written through
; it, and the assembler works out where each site stands.

        .macro  DTX_INSTALL_W   value,site
        lea     \site+2(pc),a4
        move.w  \value,(a4)
        .endm

        .macro  DTX_INSTALL_L   value,site
        lea     \site+2(pc),a4
        move.l  \value,(a4)
        .endm

; -----------------------------------------------------------------------------
; Where things stand, worked out rather than assembled in: the payload and the
; column table both move with C.

; The payload's first byte in a1 and the table's header in a2. The header is
; 16 bytes under every variant and every C (SPEC.md 1).
DTX_payload:
        lea     _fmt(pc),a2
        movea.l a2,a1
        suba.w  #_fmt-_base,a1
        adda.l  8(a2),a1
        movea.l a1,a2                   ; the header
        adda.w  #16,a1                  ; the payload
        rts

; The first stream record in a3.
DTX_columns:
        lea     _fmt(pc),a3
        adda.l  20(a3),a3               ; the field, while a3 is still the block
        suba.w  #_fmt-_base,a3          ; and the offset counts from _base
        rts

; -----------------------------------------------------------------------------
; The column a fill is at, in three places. Every record is 16 bytes, every
; decoder state 32 and every ring N, so each is that column's number times a
; stride. d4 works them out: ST4 clobbers it, and it stands in none of the
; decoder's state.

; The stream record in a3.
DTX_record:
        bsr     DTX_columns
        move.w  DTX_FILL(a6),d4
        mulu.w  #DTX_S_SIZE,d4
        adda.l  d4,a3
        rts

; The ring's first byte in a1. N reaches the multiply in a register: d5 is
; ST4's to clobber like d4, and a multiply of two registers is one shape the
; rig times.
DTX_ring:
        move.w  DTX_FILL(a6),d4
        move.w  DTX_NFIG+2(a6),d5
        mulu.w  d5,d4
        movea.l a6,a1
        adda.l  DTX_RINGS(a6),a1
        adda.l  d4,a1
        rts

; The decoder state in a3.
DTX_state:
        move.w  DTX_FILL(a6),d4
        mulu.w  #32,d4
        movea.l a6,a3
        adda.w  #DTX_DECODERS,a3
        adda.l  d4,a3
        rts

; -----------------------------------------------------------------------------
; The budget one refill takes, in units: the rows in d3 times the width,
; divided by k. Both are assembly-time constants, so this is a shift either
; way and nothing at all where they are equal. d3 and d4 are ST4's to
; clobber, and a seed works a budget out between ST4_init and ST4_resume.

DTX_budget:
        .if     DTX_WIDTH>1
        add.l   d3,d3
        .endif
        .if     DTX_WIDTH>2
        add.l   d3,d3
        .endif
        .if     ST4_UNIT>1
        lsr.l   #1,d3
        .endif
        .if     ST4_UNIT>2
        lsr.l   #1,d3
        .endif
        rts

; The write pointer goes back to the ring start where a refill landed on the
; ring end. N divides by P times the width, so a full refill lands on the end
; or short of it and the compare is exact: no call is counted anywhere. The
; pointer stands in a1, and d3 and d4 are ST4's to clobber.

DTX_wrap:
        move.l  a1,d3
        bsr     DTX_ring                ; a1 the ring's first byte
        move.l  a1,d4
        add.l   DTX_NFIG(a6),d4         ; one past its last
        cmp.l   d4,d3
        beq.s   .back                   ; a1 is the ring's first byte already
        movea.l d3,a1
.back:
        rts

; The decoder state of the column a fill is at, written from the decoder's
; registers. a3 becomes the state and d4 works out where: neither stands in
; the state.

DTX_store:
        bsr     DTX_state
        movem.l d0-d2/a0-a2/a4-a5,(a3)
        rts

; -----------------------------------------------------------------------------
; DTX_seed. Every decoder seeded, every ring filled with P rows, the read
; pointer a row below row 0. Init takes it, and so does a jump that runs
; from row 0.
;
; One loop over the columns. ST4 clobbers a3, so where the column stands is
; worked out again after every call into it.

DTX_seed:
        move.l  #-1,DTX_ROW(a6)
        clr.w   DTX_TURN(a6)
        move.l  DTX_PERIODFIG(a6),DTX_DECODED(a6)
        clr.w   DTX_FILL(a6)
seed_column:
        bsr     DTX_record              ; a3 the record
        movea.l DTX_PAYLOAD(a6),a0
        movea.l a0,a2
        movea.l a0,a4
        movea.l a0,a5
        adda.l  DTX_S_A(a3),a0
        adda.l  DTX_S_B(a3),a2
        adda.l  DTX_S_C(a3),a4
        adda.l  DTX_S_D(a3),a5
        bsr     DTX_ring                ; a1 the ring's first byte
        move.w  DTX_NFIG+2(a6),d3       ; N, a ring's bytes, in a word
        bsr     ST4_init
        move.l  DTX_PERIODFIG(a6),d3
        bsr     DTX_budget
        bsr     ST4_resume
        bsr     DTX_wrap
        bsr     DTX_store
        move.w  DTX_FILL(a6),d4
        addq.w  #1,d4
        move.w  d4,DTX_FILL(a6)
        cmp.w   DTX_CFIG(a6),d4
        bcs.s   seed_column

; The read pointer a row below column 0's first row, so the advance into row 0
; steps like any other advance.
        movea.l a6,a1
        adda.l  DTX_RINGS(a6),a1
        move.l  a1,d0
        subq.l  #DTX_WIDTH,d0
        move.l  d0,DTX_POINTER(a6)
        rts

; -----------------------------------------------------------------------------
; DTX_init. The five figures into the block, where the rings begin with them,
; the count a read walks and the stride between two rings into the
; instructions that take them, and then a seed.

DTX_init:
        movem.l d6-d7/a6,DTX_PARK(a0)
        movea.l a0,a6

        bsr     DTX_payload             ; a1 the payload, a2 the header
        move.l  a1,DTX_PAYLOAD(a6)
        move.l  4(a2),d0                ; R
        move.l  d0,DTX_ROWSFIG(a6)
        move.l  10(a2),d0               ; RR
        move.l  d0,DTX_REPEATFIG(a6)
        move.w  8(a2),d1                ; C
        move.w  d1,DTX_CFIG(a6)

; P and N, out of the format block.
        lea     _fmt(pc),a3
        moveq   #0,d0
        move.w  14(a3),d0               ; P
        move.l  d0,DTX_PERIODFIG(a6)
        moveq   #0,d0
        move.w  16(a3),d0               ; N
        move.l  d0,DTX_NFIG(a6)

; Where the rings begin: behind the decoder states, one of 32 bytes a column.
        moveq   #0,d2
        move.w  d1,d2
        lsl.l   #5,d2
        addi.l  #DTX_DECODERS,d2
        move.l  d2,DTX_RINGS(a6)

; The columns a read walks, one less: a dbf counts that way. The stride from
; one ring to the next is N, since every ring is that size (R5.5).
        subq.w  #1,d1
        DTX_INSTALL_W d1,read_count
        DTX_INSTALL_W d1,take_count
        DTX_INSTALL_L d0,read_stride
        DTX_INSTALL_L d0,take_stride

        bsr     DTX_seed
        movem.l DTX_PARK(a6),d6-d7/a6
        rts

; -----------------------------------------------------------------------------
; DTX_step. One row: the read pointer moves, the column whose turn it is is
; refilled, and the turn steps.

DTX_step:
        move.l  DTX_POINTER(a6),d0
        addq.l  #DTX_WIDTH,d0
        move.l  a6,d1
        add.l   DTX_RINGS(a6),d1        ; column 0's ring, the pointer's own
        move.l  d1,d2
        add.l   DTX_NFIG(a6),d2         ; one past its last byte
        cmp.l   d2,d0
        bne.s   .kept
        move.l  d1,d0
.kept:
        move.l  d0,DTX_POINTER(a6)

; The refill of the row's turn, which a jump and an advance reach through
; the step above. At the turn's wrap a period's rows join the rows decoded,
; and that shortens a column's last refill and stops the one after it.
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
        move.w  d1,DTX_FILL(a6)
        bsr     DTX_budget
        bsr     DTX_state
        movem.l (a3)+,d0-d2/a0-a2/a4-a5
        bsr     ST4_resume
        bsr     DTX_wrap
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
; loop over the columns: every ring is N bytes, so column i's value for this
; row stands i rings past column 0's.
;
; A read does not move a pointer, refill or decode.

DTX_read:
        tst.l   DTX_ROW(a0)
        bmi.s   read_none
        movea.l DTX_POINTER(a0),a2
read_count:
        move.w  #0,d0                   ; installed: C, less one
read_loop:
        DTX_VALUE (a2),(a1)+
read_stride:
        adda.l  #0,a2                   ; installed: N
        dbf     d0,read_loop
read_none:
        rts

; -----------------------------------------------------------------------------
; DTX_take. A read and an advance in one call. a1 goes on the stack for the
; length of the step: a1 is the decoder's write pointer, and ST4_resume takes
; it.

DTX_take:
        tst.l   DTX_ROW(a0)
        bmi.w   DTX_advance             ; no row: step to row 0 without a write
        movea.l DTX_POINTER(a0),a2
take_count:
        move.w  #0,d0                   ; installed: C, less one
take_loop:
        DTX_VALUE (a2),(a1)+
take_stride:
        adda.l  #0,a2                   ; installed: N
        dbf     d0,take_loop
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
