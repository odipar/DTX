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
; decoder state 48, so the ring, the state and the stream record of column i
; are each i strides from the first. A seed works the three out of the
; column's number; a refill reads its state's address out of the block,
; where the last refill left the next state's, and the state gives its
; ring's end, its budget and where its registers go at a loop: a refill
; reads its fields and compares once. ST4 leaves only d6, d7 and a6
; alone, so nothing else stands across a call into it.
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
; stream records stand, the column a seed is at, the five figures the table
; gives, the count kept where the sets end, the state whose turn the next
; row is, and column 0's ring. The decoder states stand at DTX_DECODERS, 48 a
; turn, and the rings behind them, N a column.
DTX_PAYLOAD         equ     12
DTX_RECORDS         equ     16              ; where the stream records stand
DTX_FILL            equ     20              ; a word: the column a seed is at
DTX_CFIG            equ     22              ; C, a word
DTX_RINGS           equ     24              ; where the rings begin, in the block
DTX_PERIODFIG       equ     28              ; P
DTX_NFIG            equ     32              ; N
DTX_DONE            equ     36              ; the rows every column has
                                            ; produced
DTX_LEFTFIG         equ     40              ; what a period's budget counts
                                            ; its rows against: R where the
                                            ; sets end, so the last period is
                                            ; short and the one after it has
                                            ; no budget, and $7FFFFFFF where
                                            ; they loop, which no count
                                            ; reaches
DTX_SAVEAT          equ     44              ; the units a column decodes
                                            ; before the row its loop begins
                                            ; at, where a pass is replayed
DTX_LOOPFIG         equ     48              ; the units of the loop, RR to R
DTX_SAVED           equ     52              ; where the decoders' registers
                                            ; go, in the block, or 0 where
                                            ; nothing is put away
DTX_NEXT            equ     56              ; the decoder state whose turn
                                            ; the next row is
DTX_RING0           equ     60              ; column 0's ring, the pointer's
DTX_RINGEND0        equ     64              ; one past its last byte
DTX_DECODERS        equ     72

; One decoder state: the eight registers in movem's order, then the ring's
; end, where the registers go at a loop, the budget, the phase and the
; mark. A budget of 0 is a turn with no refill: a turn past the last
; column, or a column whose set has ended. The mark is the units the
; decoder has left before the row its registers are put away at, or taken
; back at, and 0 where no such row is ahead; the phase says which of the
; two, 0 to put them away and 1 to take them back. A refill the mark falls
; inside is split there.
DTX_D_END           equ     32
DTX_D_SAVED         equ     36
DTX_D_BUDGET        equ     40
DTX_D_PHASE         equ     42
DTX_D_MARK          equ     44
DTX_D_SIZE          equ     48

; -----------------------------------------------------------------------------
        include "DTX_image.S"
        DTX_IMAGE 2,ST4_UNIT,DTX_WIDTH

; -----------------------------------------------------------------------------
; The first stream record in a3, out of the header in a2. A table's column
; table stands immediately before it, one record of 16 bytes a column, so
; the records begin 16C below the header. The format block's field gives the
; first table's and is not read here: an image contains several tables and
; init is given the header of the one to read.

DTX_columns:
        move.w  8(a2),d0                ; C
        lsl.w   #4,d0                   ; 16 bytes a record
        movea.l a2,a3
        suba.w  d0,a3
        rts

; -----------------------------------------------------------------------------
; The column a seed is at, in three places. Every record is 16 bytes, every
; decoder state 48 and every ring N, so each is that column's number times a
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

; The decoder state in a3.
DTX_state:
        move.w  DTX_FILL(a6),d4
        mulu.w  #DTX_D_SIZE,d4
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
; One refill of the state in a3, whose budget is in d3: the registers
; loaded, the decoder run for the budget, the write pointer taken back to
; the ring start where it landed on the ring end, and the registers stored.
; N divides by P times the width, so a full refill lands on the end or
; short of it and the compare is exact. ST4 clobbers a3, so the state is
; read out of the block again after the decoder.
;
; Where the state's mark falls inside the budget the refill is split at
; it: the decoder runs to the mark, its registers are put away or taken
; back as the phase says, and it runs for the rest. A pass that is
; replayed puts the registers away where its loop begins and takes them
; back where it ends, every pass, so the next mark is the loop's units on;
; the first mark puts them away, and every mark after takes them back.
; ST4 leaves a1, the write pointer, out of a replay: it walks the ring on
; and the pass it belongs to is the caller's, not the stream's, so the
; copy back takes seven registers and a1 stands as it is.
;
; A macro rather than a subroutine: the advance runs it in line, and the
; seed once a column.

        .macro  DTX_REFILL
        move.l  DTX_D_MARK(a3),d4
        beq.s   .whole\~
        moveq   #0,d5
        move.w  d3,d5
        sub.l   d5,d4                   ; the mark past this refill
        ble.s   .split\~
        move.l  d4,DTX_D_MARK(a3)
.whole\~:
        movem.l (a3),d0-d2/a0-a2/a4-a5
        bsr     ST4_resume
        bra.s   .filled\~
.split\~:
        neg.l   d4                      ; the rest, past the mark
        move.w  d4,-(a7)
        sub.w   d4,d3                   ; up to the mark
        movem.l (a3),d0-d2/a0-a2/a4-a5
        bsr     ST4_resume
        movea.l DTX_NEXT(a6),a3
        tst.w   DTX_D_PHASE(a3)
        bne.s   .back\~
        move.w  #1,DTX_D_PHASE(a3)
        movea.l DTX_D_SAVED(a3),a3
        movem.l d0-d2/a0-a2/a4-a5,(a3)
        bra.s   .rest\~
.back\~:
        movea.l DTX_D_SAVED(a3),a4
        movem.l (a4),d0-d2/a0
        movem.l 20(a4),a2/a4-a5
.rest\~:
        movea.l DTX_NEXT(a6),a3
        moveq   #0,d3
        move.w  (a7)+,d3
        move.l  DTX_LOOPFIG(a6),d4
        sub.l   d3,d4
        move.l  d4,DTX_D_MARK(a3)       ; the next mark, the loop on
        tst.w   d3
        beq.s   .filled\~
        bsr     ST4_resume
.filled\~:
        movea.l DTX_NEXT(a6),a3
        cmpa.l  DTX_D_END(a3),a1
        bne.s   .short\~
        suba.l  DTX_NFIG(a6),a1
.short\~:
        movem.l d0-d2/a0-a2/a4-a5,(a3)
        .endm

; -----------------------------------------------------------------------------
; DTX_seed. Every decoder seeded, every ring filled with a period's rows,
; or R where the table is shorter, the pointer a row below row 0, and the
; first period's budget in every state. Init takes it, and so does a jump
; that runs from row 0.
;
; One loop over the columns. ST4 clobbers a3, so where the column stands is
; worked out again after every call into it. The state's fields are
; written before the decoder runs: a loop that begins at row 0 begins where
; ST4_init leaves the decoder, so those registers are put away here, and
; any other loop's row is the state's first mark.

DTX_seed:
        clr.w   DTX_FILL(a6)
seed_column:
        bsr.s   DTX_state               ; a3 the state
        bsr.s   DTX_ring                ; a1 the ring's first byte
        move.l  a1,d4
        add.l   DTX_NFIG(a6),d4
        move.l  d4,DTX_D_END(a3)        ; one past its last byte
        moveq   #0,d4
        move.w  DTX_FILL(a6),d4
        lsl.l   #5,d4
        add.l   DTX_SAVED(a6),d4
        add.l   a6,d4
        move.l  d4,DTX_D_SAVED(a3)      ; where the registers go, 32 a column
        move.l  DTX_PERIODFIG(a6),d3
        cmp.l   DTX_LEFTFIG(a6),d3
        ble.s   .period
        move.l  DTX_LEFTFIG(a6),d3      ; a table shorter than a period
.period:
        DTX_BUDGET
        move.w  d3,DTX_D_BUDGET(a3)
        clr.w   DTX_D_PHASE(a3)
        clr.l   DTX_D_MARK(a3)
        move.l  a3,-(a7)
        bsr.s   DTX_record              ; a3 the record
        movea.l DTX_PAYLOAD(a6),a0
        movea.l a0,a2
        movea.l a0,a4
        movea.l a0,a5
        adda.l  DTX_S_A(a3),a0
        adda.l  DTX_S_B(a3),a2
        adda.l  DTX_S_C(a3),a4
        adda.l  DTX_S_D(a3),a5
        move.w  DTX_NFIG+2(a6),d3       ; N, a ring's bytes, in a word
        bsr     ST4_init
        movea.l (a7)+,a3                ; the state
        move.l  a3,DTX_NEXT(a6)         ; the refill reads the state here
        movem.l d0-d2/a0-a2/a4-a5,(a3)
        tst.l   DTX_SAVED(a6)
        beq.s   .marked                 ; nothing is put away
        move.l  DTX_SAVEAT(a6),d4
        bne.s   .ahead
        movea.l DTX_D_SAVED(a3),a3      ; the loop begins at row 0: put away
        movem.l d0-d2/a0-a2/a4-a5,(a3)
        movea.l DTX_NEXT(a6),a3
        move.w  #1,DTX_D_PHASE(a3)
        move.l  DTX_LOOPFIG(a6),d4
.ahead:
        move.l  d4,DTX_D_MARK(a3)
.marked:
        move.w  DTX_D_BUDGET(a3),d3
        DTX_REFILL
        addq.w  #1,DTX_FILL(a6)
        move.w  DTX_FILL(a6),d4
        cmp.w   DTX_CFIG(a6),d4
        bcs     seed_column

; A turn past the last column is idle: every decoder state from C to P
; minus one gets a budget of 0, and nothing else in it is read.
        bsr     DTX_state               ; the state of column C
        move.w  DTX_PERIODFIG+2(a6),d4
        sub.w   DTX_CFIG(a6),d4
        beq.s   .idled
        subq.w  #1,d4
.idle:
        clr.w   DTX_D_BUDGET(a3)
        lea     DTX_D_SIZE(a3),a3
        dbra    d4,.idle
.idled:

; The seed gave every column a period's rows, or R, which the count
; starts from; the first turn is column 0's, and a period of turns is to
; come. The pointer stands a row below column 0's first row, so the
; advance into row 0 steps like any other advance. Then the first period's
; budget, which is short where R is under 2P.
        move.l  DTX_PERIODFIG(a6),d3
        cmp.l   DTX_LEFTFIG(a6),d3
        ble.s   .counted
        move.l  DTX_LEFTFIG(a6),d3
.counted:
        move.l  d3,DTX_DONE(a6)
        move.w  DTX_PERIODFIG+2(a6),DTX_TURN(a6)
        lea     DTX_DECODERS(a6),a3
        move.l  a3,DTX_NEXT(a6)
        move.l  DTX_RING0(a6),d0
        subq.l  #DTX_WIDTH,d0
        move.l  d0,DTX_POINTER(a6)
        bra     DTX_next

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
        clr.l   DTX_DONE(a6)
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

; Where the rings begin: behind the decoder states, one of 48 bytes a turn.
; Column 0's ring is the pointer's own, so its two ends stand in the block.
        move.w  DTX_PERIODFIG+2(a6),d2
        mulu.w  #DTX_D_SIZE,d2
        addi.w  #DTX_DECODERS,d2
        move.l  d2,DTX_RINGS(a6)
        add.l   a6,d2
        move.l  d2,DTX_RING0(a6)
        add.l   d0,d2
        move.l  d2,DTX_RINGEND0(a6)

; What a period's budget counts its rows against, and where a pass that
; is replayed turns. Where RR is below R the sets loop at it (R5.11) and
; no count is kept: $7FFFFFFF is a figure no count reaches, so every
; refill takes a whole period. Where the table does not repeat the sets
; end at R: the count stops there, the last period of refills is short and
; the one after it gets a budget of 0. The two figures a replay turns on
; are units of a column: the units before its loop's first row, and the
; units of the loop.
        move.l  4(a2),d3                ; R
        move.l  10(a2),d4               ; RR
        cmp.l   d3,d4
        bcs.s   .loops
        move.l  d3,DTX_LEFTFIG(a6)      ; the sets end at R
        bra.s   .counted
.loops:
        move.l  #$7FFFFFFF,DTX_LEFTFIG(a6)
        sub.l   d4,d3                   ; the loop's rows
        DTX_BUDGET
        move.l  d3,DTX_LOOPFIG(a6)
        move.l  d4,d3                   ; the rows before the loop
        DTX_BUDGET
        move.l  d3,DTX_SAVEAT(a6)
.counted:

; Where the decoders' registers go, behind the rings, and 0 where nothing is
; put away. Byte 20 of a data set gives the unit its loop begins at, or
; $FFFFFFFF where the set's end marker loops it and no pass is replayed;
; every set of a payload takes the same form, so column 0 defines it.
        clr.l   DTX_SAVED(a6)
        movea.l DTX_PAYLOAD(a6),a3
        movea.l a3,a4
        adda.l  4(a3),a4                ; column 0's data set
        move.l  20(a4),d3
        addq.l  #1,d3                   ; $FFFFFFFF and nothing else gives 0
        beq.s   .marker
        move.l  DTX_NFIG(a6),d3
        moveq   #0,d4
        move.w  d1,d4
        mulu.w  d3,d4                   ; the rings, N a column
        add.l   DTX_RINGS(a6),d4
        move.l  d4,DTX_SAVED(a6)
.marker:

        bsr     DTX_seed
        movea.l DTX_PARK(a6),a6
        rts

; -----------------------------------------------------------------------------
; One row: the column whose turn it is is refilled, the turn steps, and the
; pointer moves. The state's budget says what the turn does: 0 nothing, and
; any other a refill of that many units. Then the next state's address goes
; into the block, and the last turn of a period ends it.
;
; The pointer moves after the refill: the row it moves onto is not one the
; refill writes (doc/abi.md 4), and a1 comes out of the step the pointer
; an advance gives.
;
; A macro rather than a subroutine: the advance runs it in line, and a jump
; runs it through DTX_step once a row.

        .macro  DTX_ROW
        movea.l DTX_NEXT(a6),a3
        move.w  DTX_D_BUDGET(a3),d3
        beq.s   .idle\~
        DTX_REFILL
.idle\~:
        lea     DTX_D_SIZE(a3),a3
        move.l  a3,DTX_NEXT(a6)
        subq.w  #1,DTX_TURN(a6)
        bne.s   .stepped\~
        bsr     DTX_period
.stepped\~:
        movea.l DTX_POINTER(a6),a1
        addq.l  #DTX_WIDTH,a1
        cmpa.l  DTX_RINGEND0(a6),a1
        bne.s   .kept\~
        movea.l DTX_RING0(a6),a1
.kept\~:
        move.l  a1,DTX_POINTER(a6)
        .endm

; -----------------------------------------------------------------------------
; A period ended: every column took its refill and those rows are decoded.
; The next turn is column 0's, and the rows the period decoded go onto the
; count. Where the sets end the count stops at R, so the last period is
; short and the one after it is empty; where they loop no count is kept.

DTX_period:
        lea     DTX_DECODERS(a6),a3
        move.l  a3,DTX_NEXT(a6)
        move.w  DTX_PERIODFIG+2(a6),DTX_TURN(a6)
        move.l  DTX_LEFTFIG(a6),d3
        sub.l   DTX_DONE(a6),d3
        ble.s   .over                   ; every row decoded already
        cmp.l   DTX_PERIODFIG(a6),d3
        ble.s   .grew
        move.l  DTX_PERIODFIG(a6),d3
.grew:
        add.l   d3,DTX_DONE(a6)
        bra.s   DTX_next
.over:
        rts

; The period to come: its budget. A whole period keeps the budget the seed
; wrote, and a pass that loops never has a short one, so a budget is
; written only where the sets end, short or 0.

DTX_next:
        move.l  DTX_LEFTFIG(a6),d3
        sub.l   DTX_DONE(a6),d3
        cmp.l   DTX_PERIODFIG(a6),d3
        bge.s   .kept
        tst.l   d3
        bge.s   .some
        moveq   #0,d3
.some:
        DTX_BUDGET
        lea     DTX_DECODERS+DTX_D_BUDGET(a6),a3
        move.w  DTX_CFIG(a6),d4
        subq.w  #1,d4
.column:
        move.w  d3,(a3)
        lea     DTX_D_SIZE(a3),a3
        dbra    d4,.column
.kept:
        rts

; -----------------------------------------------------------------------------
; DTX_step: one row for a jump, which runs it once a row.

DTX_step:
        DTX_ROW
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
; DTX_advance. One row in line, and a1 comes out of it the pointer at
; column 0's value for the row.

DTX_advance:
        move.l  a6,DTX_PARK(a0)
        movea.l a0,a6
        DTX_ROW
        movea.l DTX_PARK(a6),a6
        rts

; -----------------------------------------------------------------------------
; The carried decoder, ST4_wrap.S at this payload's unit.

        .align  4
        include "ST4_wrap.S"

; -----------------------------------------------------------------------------
        DTX_TABLE_FOLLOWS

        end
