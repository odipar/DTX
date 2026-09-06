; -----------------------------------------------------------------------------
; DTX2.S - a DTX2 table read on the 68000: four calls, one table, PC relative
;
; By Robbert van Dalen. Written by Claude (Anthropic's Claude Code) under
; Robbert's direction. Use it freely, including commercially, as long as your
; documentation says you used DTX (see LICENSE).
;
; doc/abi.md defines the calls, the format block and the state block. One
; template a variant: this file reads only DTX2, so no call in it tests which
; variant it contains. dtx-blobs assembles this once a width and a decoder,
; and a packager combines that code with the table (doc/abi.md 5).
;
; DTX2 packs a column with ST4, and this reader follows YMX. Every column
; decodes through its own ring; init fills every ring with P rows; and after
; that one column is refilled a row, the one whose turn it is. So a read takes
; one value from each ring and never enters a decoder. ST4_wrap.S, the copy
; carried into this repository, is the decoder, built at the unit the payload
; defines.
;
; THE CODE DOES NOT MOVE WITH THE TABLE. R, C, RR, P and N reach it at run
; time and stand in the state block.
;
; A READ IS THE CALLER'S. An advance gives the pointer at column 0's value
; for the row, and every ring is N bytes, so column i's value stands i rings
; further on. DTX_metadata gives that stride, and nothing here copies a row
; anywhere.
;
; WHAT MOVES THE CODE is the width and the decoder: the width every value
; takes (R6.3) and k and the copy code are assembly-time constants, so a
; table of another width, or packed at another unit, or with copies, contains
; other instructions. That costs one build a width a unit.
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
; a6 IS THE STATE BLOCK through init, jump and advance, and the caller's a6
; stands parked in the block for the length of the call. ST4 leaves d6, d7
; and a6 alone, and nothing here touches d6 or d7, so a6 is the one register
; to put back.
;
; NOTHING IS CHECKED. No call checks the table, the state block, the row
; number or the address a row goes to. doc/abi.md 7 lists what each call
; takes without checking, ST4_wrap.S its own seven.
; -----------------------------------------------------------------------------

        .68000
        .text

        include "DTX_table.i"

; One stream record.
DTX_S_A             equ     0
DTX_S_B             equ     4
DTX_S_C             equ     8
DTX_S_D             equ     12

; The state block beyond what doc/abi.md 3 defines: the payload, where the
; stream records stand, the column a fill is at, the five figures the table
; gives, and where the rings begin. The decoder states stand at
; DTX_DECODERS, 32 a column, and the rings behind them, N a column.
DTX_PAYLOAD         equ     12
DTX_RECORDS         equ     16              ; where the stream records stand
DTX_FILL            equ     20              ; a word: the column a fill is at
DTX_CFIG            equ     22              ; C, a word
DTX_RINGS           equ     24              ; where the rings begin, in the block
DTX_PERIODFIG       equ     28              ; P
DTX_NFIG            equ     32              ; N
DTX_DECODERS        equ     36

; -----------------------------------------------------------------------------
        include "DTX_image.S"
        DTX_IMAGE 2,ST4_UNIT,DTX_WIDTH

; -----------------------------------------------------------------------------
; The first stream record in a3. Where the column table stands is read out of
; the format block, which repeats what the assembler worked out.

DTX_columns:
        lea     _base(pc),a3
        adda.l  _fmt-_base+20(a3),a3
        rts

; -----------------------------------------------------------------------------
; The column a fill is at, in three places. Every record is 16 bytes, every
; decoder state 32 and every ring N, so each is that column's number times a
; stride. d4 works them out: ST4 clobbers it, and it stands in none of the
; decoder's state.

; The stream record in a3. Where the records stand is worked out once, at
; init, and a record is 16 bytes, so the multiply is a shift.
DTX_record:
        movea.l DTX_RECORDS(a6),a3
        moveq   #0,d4
        move.w  DTX_FILL(a6),d4
        lsl.l   #4,d4
        adda.l  d4,a3
        rts

; The ring's first byte in a1. N is any size a word takes, so this one is a
; multiply, and N reaches it out of the block rather than through a register.
DTX_ring:
        move.w  DTX_FILL(a6),d4
        mulu.w  DTX_NFIG+2(a6),d4
        movea.l a6,a1
        adda.l  DTX_RINGS(a6),a1
        adda.l  d4,a1
        rts

; The decoder state in a3. A state is 32 bytes, so the multiply is a shift.
DTX_state:
        moveq   #0,d4
        move.w  DTX_FILL(a6),d4
        lsl.l   #5,d4
        lea     DTX_DECODERS(a6),a3
        adda.l  d4,a3
        rts

; The budget one refill takes, in units: the rows in d3 times the width,
; divided by k. Both are assembly-time constants, so the two are one shift
; of what the width is over the unit, and nothing where they are equal. A
; macro rather than a subroutine: at a ratio of one the macro is empty, and
; at any other one instruction is cheaper than reaching a subroutine and
; coming back.

        .macro  DTX_BUDGET
        .if     (DTX_WIDTH)=(2*ST4_UNIT)
        add.l   d3,d3
        .endif
        .if     (DTX_WIDTH)=(4*ST4_UNIT)
        lsl.l   #2,d3
        .endif
        .if     (2*DTX_WIDTH)=(ST4_UNIT)
        lsr.l   #1,d3
        .endif
        .if     (4*DTX_WIDTH)=(ST4_UNIT)
        lsr.l   #2,d3
        .endif
        .endm

; -----------------------------------------------------------------------------
; The write pointer goes back to the ring start where a refill landed on the
; ring end. N divides by P times the width, so a full refill lands on the end
; or short of it and the compare is exact: no call is counted anywhere. The
; pointer stands in a1, and d3 and d4 are ST4's to clobber.

DTX_wrap:
        move.l  a1,d3
        bsr.s   DTX_ring                ; a1 the ring's first byte
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
        bsr.s   DTX_state
        movem.l d0-d2/a0-a2/a4-a5,(a3)
        rts

; -----------------------------------------------------------------------------
; DTX_seed. Every decoder seeded, every ring filled with P rows, the pointer
; a row below row 0. Init takes it, and so does a jump that runs from row 0.
;
; One loop over the columns. ST4 clobbers a3, so where the column stands is
; worked out again after every call into it.

DTX_seed:
        clr.w   DTX_TURN(a6)
        clr.w   DTX_FILL(a6)
seed_column:
        bsr.s   DTX_record              ; a3 the record
        movea.l DTX_PAYLOAD(a6),a0
        movea.l a0,a2
        movea.l a0,a4
        movea.l a0,a5
        adda.l  DTX_S_A(a3),a0
        adda.l  DTX_S_B(a3),a2
        adda.l  DTX_S_C(a3),a4
        adda.l  DTX_S_D(a3),a5
        bsr.s   DTX_ring                ; a1 the ring's first byte
        move.w  DTX_NFIG+2(a6),d3       ; N, a ring's bytes, in a word
        bsr     ST4_init
        move.l  DTX_PERIODFIG(a6),d3
        DTX_BUDGET
        bsr     ST4_resume
        bsr.s   DTX_wrap
        bsr.s   DTX_store
        addq.w  #1,DTX_FILL(a6)
        move.w  DTX_FILL(a6),d4
        cmp.w   DTX_CFIG(a6),d4
        bcs.s   seed_column

; The pointer a row below column 0's first row, so the advance into row 0
; steps like any other advance.
        move.l  a6,d0
        add.l   DTX_RINGS(a6),d0
        subq.l  #DTX_WIDTH,d0
        move.l  d0,DTX_POINTER(a6)
        rts

; -----------------------------------------------------------------------------
; DTX_init. The five figures into the block, where the rings begin with
; them, and then a seed.

DTX_init:
        move.l  a6,DTX_PARK(a0)
        movea.l a0,a6

        bsr     DTX_payload             ; a1 the payload, a2 the header
        move.l  a1,DTX_PAYLOAD(a6)
        bsr     DTX_columns
        move.l  a3,DTX_RECORDS(a6)
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
        addi.w  #DTX_DECODERS,d2
        move.l  d2,DTX_RINGS(a6)

        bsr     DTX_seed
        movea.l DTX_PARK(a6),a6
        rts

; -----------------------------------------------------------------------------
; DTX_step. One row: the pointer moves, the column whose turn it is is
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
; the step above. Every data set of a DTX2 payload loops (R5.11), so no set
; ends, no refill is the last and none is shortened: a refill is a whole
; period, every period, and nothing here counts the rows decoded.
DTX_refill:
        move.w  DTX_TURN(a6),d1
        cmp.w   DTX_CFIG(a6),d1
        bcc.s   DTX_turn                ; a turn past the last column
        move.l  DTX_PERIODFIG(a6),d3
        move.w  d1,DTX_FILL(a6)
        DTX_BUDGET
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
.stored:
        move.w  d1,DTX_TURN(a6)
        rts

; -----------------------------------------------------------------------------
; DTX_jump. ST4_resume clobbers d3, d4, d5 and a3, so nothing but a6 and the
; stack stands across a step: the target is on the stack.

DTX_jump:
        move.l  a6,DTX_PARK(a0)
        movea.l a0,a6
        move.l  d0,-(a7)                ; the rows still to step
        bsr     DTX_seed                ; the cursor a row below row 0
.run:
        bsr     DTX_step
        subq.l  #1,(a7)
        bpl.s   .run
        addq.l  #4,a7
        movea.l DTX_POINTER(a6),a1
        movea.l DTX_PARK(a6),a6
        rts

; -----------------------------------------------------------------------------
; DTX_advance. The move that leaves d0 sets N and Z, so bmi after the call.
; a1 comes back the pointer at column 0's value for the row.

DTX_advance:
        move.l  a6,DTX_PARK(a0)
        movea.l a0,a6
        bsr     DTX_step
        movea.l DTX_POINTER(a6),a1
        movea.l DTX_PARK(a6),a6
        rts

; -----------------------------------------------------------------------------
; The carried decoder, ST4_wrap.S at this payload's unit.

        .align  4
        include "ST4_wrap.S"

; -----------------------------------------------------------------------------
        DTX_TABLE_FOLLOWS

        end
