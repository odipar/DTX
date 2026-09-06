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
; reads three fields and compares once. ST4 leaves only d6, d7 and a6
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
; gives, the count a replayed pass keeps, the state whose turn the next row
; is, and column 0's ring. The decoder states stand at DTX_DECODERS, 48 a
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
                                            ; no budget, and R plus P where
                                            ; they loop, which no period
                                            ; reaches
DTX_SAVEAT          equ     44              ; the row a loop begins at, or -1
DTX_ENDAT           equ     48              ; the row a pass ends at, or -1
DTX_SAVED           equ     52              ; where the decoders' registers
                                            ; go, in the block, or 0 where
                                            ; nothing is put away
DTX_NEXT           equ     56              ; the decoder state whose turn
                                            ; the next row is
DTX_RING0           equ     60              ; column 0's ring, the pointer's
DTX_RINGEND0        equ     64              ; one past its last byte
DTX_MODE            equ     68              ; a word: what a marked state does
                                            ; before its refill, 0 to put its
                                            ; registers away and 1 to take
                                            ; them back
DTX_DECODERS        equ     72

; One decoder state: the eight registers in movem's order, then the ring's
; end, where the registers go at a loop, and the budget. A budget of 0 is a
; turn with no refill: a turn past the last column, or a column whose set
; has ended. A negative budget marks the state for the period: the refill
; puts the registers away first, or takes them back, as DTX_MODE says.
DTX_D_END           equ     32
DTX_D_SAVED         equ     36
DTX_D_BUDGET        equ     40
DTX_D_SIZE          equ     48

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
; DTX_seed. Every decoder seeded, every ring filled with P rows, the pointer
; a row below row 0, and the first period's budget in every state. Init
; takes it, and so does a jump that runs from row 0.
;
; One loop over the columns. ST4 clobbers a3, so where the column stands is
; worked out again after every call into it. The state's three fields are
; written before the decoder runs, and the write pointer goes back to the
; ring start where the fill landed on the ring end: N divides by P times
; the width, so a full refill lands on the end or short of it and the
; compare is exact.

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
        DTX_BUDGET
        move.w  d3,DTX_D_BUDGET(a3)     ; a period's rows in units
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
        bsr     DTX_first
        move.l  DTX_PERIODFIG(a6),d3
        DTX_BUDGET
        bsr     ST4_resume
        bsr.s   DTX_state               ; a3 the state again
        cmpa.l  DTX_D_END(a3),a1
        bne.s   .short
        suba.l  DTX_NFIG(a6),a1
.short:
        movem.l d0-d2/a0-a2/a4-a5,(a3)
        addq.w  #1,DTX_FILL(a6)
        move.w  DTX_FILL(a6),d4
        cmp.w   DTX_CFIG(a6),d4
        bcs.s   seed_column

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

; The seed gave every column a period's rows, which the count starts from;
; the first turn is column 0's, and a period of turns is to come. The
; pointer stands a row below column 0's first row, so the advance into row
; 0 steps like any other advance. Then the first period's budget, which is
; short where R is under 2P, and the marks a loop puts on the period.
        move.l  DTX_PERIODFIG(a6),DTX_DONE(a6)
        move.w  DTX_PERIODFIG+2(a6),DTX_TURN(a6)
        lea     DTX_DECODERS(a6),a3
        move.l  a3,DTX_NEXT(a6)
        clr.w   DTX_MODE(a6)
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

; What a period's budget counts its rows against, and the two rows a pass
; turns on. Where RR is below R the sets loop at it (R5.11): the count comes
; round there, and R plus P is a figure no period reaches, so every refill
; takes a whole period. Where the table does not repeat the sets end at R:
; the count stops there, the last period of refills is short and the one
; after it gets a budget of 0, and neither row is ever reached.
        move.l  4(a2),d3                ; R
        move.l  10(a2),d4               ; RR
        move.l  d4,DTX_SAVEAT(a6)
        move.l  d3,DTX_ENDAT(a6)
        cmp.l   d3,d4
        bcs.s   .loops
        move.l  d3,DTX_LEFTFIG(a6)      ; the sets end at R
        moveq   #-1,d3
        move.l  d3,DTX_SAVEAT(a6)       ; a row the count never stands on
        move.l  d3,DTX_ENDAT(a6)
        bra.s   .counted
.loops:
        add.l   DTX_PERIODFIG(a6),d3
        move.l  d3,DTX_LEFTFIG(a6)      ; R plus P, which no period reaches
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
; pointer moves. The state's budget says what the turn does: 0 nothing, a
; positive budget a refill of that many units, a negative one the same after
; the registers are put away or taken back. ST4 clobbers a3, so the state
; is read out of the block again after the decoder for the write pointer's
; wrap and the registers' store. Then the next state's address goes into
; the block, and the last turn of a period ends it.
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
        bmi.s   .marked\~
        movem.l (a3),d0-d2/a0-a2/a4-a5
.refill\~:
        bsr     ST4_resume
        movea.l DTX_NEXT(a6),a3
        cmpa.l  DTX_D_END(a3),a1
        bne.s   .short\~
        suba.l  DTX_NFIG(a6),a1
.short\~:
        movem.l d0-d2/a0-a2/a4-a5,(a3)
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
        bra.s   .done\~
.marked\~:
        bsr     DTX_marked
        bra.s   .refill\~
.done\~:
        .endm

; The marked refill's first half: the registers put away, or taken back.
; a3 is the state and d3 its budget, negated; d3 comes back the budget and
; the registers come back loaded, as an unmarked refill loads them.
;
; ST4 leaves a1, the write pointer, out of a replay: it walks the ring on
; and the pass it belongs to is the caller's, not the stream's. So the copy
; back takes seven registers out of the saved eight and a1 out of the state,
; and the decoder keeps the ring position its last refill left.

DTX_marked:
        neg.w   d3
        tst.w   DTX_MODE(a6)
        bne.s   .back
        movem.l (a3),d0-d2/a0-a2/a4-a5
        movea.l DTX_D_SAVED(a3),a3
        movem.l d0-d2/a0-a2/a4-a5,(a3)
        rts
.back:
        movea.l DTX_D_SAVED(a3),a4
        movem.l (a4),d0-d2/a0
        movea.l 16(a3),a1
        movem.l 20(a4),a2/a4-a5
        rts

; -----------------------------------------------------------------------------
; A period ended: every column took its refill and those rows are decoded.
; The next turn is column 0's, and the rows the period decoded go onto the
; count. Where the sets end the count stops at R, so the last period is
; short and the one after it is empty; where they loop the count comes
; round at the row a pass ends on, and the two rows a replayed pass turns
; on fall on a period (abi.md 4), so DTX_next's compares find them exactly.

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

; The period to come: its budget, where it is short or 0, and its
; marks. A whole period keeps the budget the seed wrote, and a pass that
; is replayed never has a short one, so a budget is written only where the
; sets end.
DTX_next:
        move.l  DTX_LEFTFIG(a6),d3
        sub.l   DTX_DONE(a6),d3
        cmp.l   DTX_PERIODFIG(a6),d3
        bge.s   DTX_marks
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
        rts

; The marks. A marked period just ended where the budgets stand negative,
; and they go back first. Then the count against the two rows: the row a
; loop begins at marks the period to put the registers away, and the row a
; pass ends at marks it to take them back and puts the count back to the
; loop's row. Where nothing is put away the count still comes round, and no
; state is marked.

DTX_marks:
        tst.w   DTX_DECODERS+DTX_D_BUDGET(a6)
        bpl.s   .plain
        bsr.s   DTX_flip
.plain:
        move.l  DTX_DONE(a6),d3
        cmp.l   DTX_SAVEAT(a6),d3
        beq.s   .away
        cmp.l   DTX_ENDAT(a6),d3
        bne.s   .none
        move.l  DTX_SAVEAT(a6),DTX_DONE(a6)
        move.w  #1,DTX_MODE(a6)
        bra.s   DTX_flip
.away:
        clr.w   DTX_MODE(a6)
        bra.s   DTX_flip
.none:
        rts

; Every column's budget negated: onto a marked period, and off it.
DTX_flip:
        tst.l   DTX_SAVED(a6)
        beq.s   .none
        lea     DTX_DECODERS+DTX_D_BUDGET(a6),a3
        move.w  DTX_CFIG(a6),d4
        subq.w  #1,d4
.column:
        neg.w   (a3)
        lea     DTX_D_SIZE(a3),a3
        dbra    d4,.column
.none:
        rts

; -----------------------------------------------------------------------------
; A replayed pass whose loop begins at row 0 begins where ST4_init leaves
; the decoder, so those are the registers to put away, at the seed. Every
; other loop begins at a period, and the marks find it.

DTX_first:
        tst.l   DTX_SAVED(a6)
        beq.s   .none
        tst.l   DTX_SAVEAT(a6)
        bne.s   .none
        movea.l a6,a3
        adda.l  DTX_SAVED(a6),a3
        moveq   #0,d4
        move.w  DTX_FILL(a6),d4
        lsl.l   #5,d4
        adda.l  d4,a3
        movem.l d0-d2/a0-a2/a4-a5,(a3)
.none:
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
        bsr.s   DTX_step
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
