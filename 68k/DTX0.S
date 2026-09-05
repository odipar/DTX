; -----------------------------------------------------------------------------
; DTX0.S - a DTX0 table read on the 68000: six calls, one table, PC relative
;
; By Robbert van Dalen. Written by Claude (Anthropic's Claude Code) under
; Robbert's direction. Use it freely, including commercially, as long as your
; documentation says you used DTX (see LICENSE).
;
; doc/abi.md states the calls, the format block and the state block. One
; template a variant: this file reads DTX0 and nothing else, so no call in it
; tests which variant it holds.
;
; THE CODE DOES NOT MOVE WITH THE TABLE. R, C, RR and the row's bytes reach it
; at run time rather than at assembly time: DTX_init reads them out of the
; format block and the table's own header and writes them into the
; instructions that take them. Every DTX0 image holds the same instructions,
; byte for byte, and the table behind them is what differs.
;
; What that costs is a read-only image. The code writes to itself at init, so
; it stands in RAM rather than ROM, and a 68030 caller flushes the instruction
; cache after DTX_init. A 68000 writes to its own code freely.
;
; DTX0 lays the rows one after another, so one cursor walks them and a read is
; one run of bytes. The move that run takes is installed as well: a long where
; the row's bytes divide by four, a word where they are even, a byte where
; they are odd.
;
; THE TABLE'S BYTES FOLLOW THE CODE. Nothing here holds them: _columns is the
; last label and the code ends on a long, so the packager appends the .dtx
; file to what rmac writes and _columns is where it lands. DTX0 has no column
; table, so under this variant the table's own header is the first byte there.
;
; TRUSTED INPUT ONLY. No call checks the table, the state block, the row
; number or the address a row goes to. doc/abi.md 7 lists what each call takes
; without checking.
; -----------------------------------------------------------------------------

        .68000
        .text

        include "DTX_table.i"

; -----------------------------------------------------------------------------
; The image, doc/abi.md 1: six slots, the format block, then the bodies.

        include "DTX_image.S"
        DTX_IMAGE 0,1,0,0

; -----------------------------------------------------------------------------
; What a value is installed with. A 68000 reaches PC relative for a source
; and never for a destination, so a site is lea'd into a4 and written
; through it. The assembler works out where each site stands: nothing here
; counts an offset, and every reach into the image is PC relative.
;
; The operand of an instruction whose opcode is one word stands at +2 of it,
; and the L and W forms add that; OP writes the opcode itself.

        .macro  DTX_INSTALL_L   value,site
        lea     \site+2(pc),a4
        move.l  \value,(a4)
        .endm

        .macro  DTX_INSTALL_W   value,site
        lea     \site+2(pc),a4
        move.w  \value,(a4)
        .endm

        .macro  DTX_INSTALL_OP  value,site
        lea     \site(pc),a4
        move.w  \value,(a4)
        .endm

; -----------------------------------------------------------------------------
; DTX_init. It writes the table's figures into the instructions below and
; leaves the cursor a row below row 0, so the advance into row 0 steps like
; any other advance.

DTX_init:
        move.l  #-1,DTX_ROW(a0)
        clr.w   DTX_TURN(a0)
        clr.l   DTX_DECODED(a0)

        bsr     DTX_payload             ; a1 the payload, a2 the header
        lea     _fmt(pc),a3
        moveq   #0,d2
        move.w  12(a3),d2               ; the row's bytes
        move.l  4(a2),d3                ; R
        move.l  10(a2),d5               ; RR

; R and RR, into the compares and the moves that take them.
        DTX_INSTALL_L d3,advance_rows
        DTX_INSTALL_L d3,take_rows
        DTX_INSTALL_L d3,advance_end
        DTX_INSTALL_L d3,take_end
        DTX_INSTALL_L d5,advance_repeat
        DTX_INSTALL_L d5,take_repeat

; The row's bytes, into the step and into the two mulu a jump takes.
        DTX_INSTALL_L d2,advance_step
        DTX_INSTALL_W d2,jump_high
        DTX_INSTALL_W d2,jump_low

; The run a read takes: a long where the row's bytes divide by four, a word
; where they are even, a byte where they are odd, and the count that many.
        move.l  d2,d5
        move.w  #$22DA,d0               ; move.l (a2)+,(a1)+
        btst    #0,d2
        beq.s   init_wide
        move.w  #$12DA,d0               ; move.b (a2)+,(a1)+
        bra.s   init_ready
init_wide:
        btst    #1,d2
        beq.s   init_long
        move.w  #$32DA,d0               ; move.w (a2)+,(a1)+
        lsr.l   #1,d5
        bra.s   init_ready
init_long:
        lsr.l   #2,d5
init_ready:
        DTX_INSTALL_OP d0,read_move
        DTX_INSTALL_OP d0,take_move
        subq.l  #1,d5                   ; dbf counts one less
        DTX_INSTALL_W d5,read_count
        DTX_INSTALL_W d5,take_count

; The cursor a row below row 0.
        suba.l  d2,a1
        move.l  a1,DTX_CURSOR(a0)
        rts

; The payload's first byte in a1 and the header in a2, worked out rather than
; assembled in: what the header runs to moves with C.
DTX_payload:
        lea     _fmt(pc),a2
        movea.l a2,a1
        suba.w  #_fmt-_base,a1
        adda.l  8(a2),a1                ; where the table's header stands
        movea.l a1,a2
        moveq   #0,d1
        move.w  8(a2),d1                ; C
        addi.l  #14+3,d1
        andi.l  #-4,d1
        adda.l  d1,a1                   ; the payload
        rts

; -----------------------------------------------------------------------------
; DTX_jump. The cursor is the payload plus the row times the row's bytes: two
; mulu.w make that 32 by 16 product at any row's bytes.

DTX_jump:
        move.l  d0,DTX_ROW(a0)
        move.l  d0,d1
        swap    d1
jump_high:
        mulu.w  #0,d1                   ; installed: the row's bytes
        swap    d1
        clr.w   d1
        move.l  d0,d3
        andi.l  #$FFFF,d3
jump_low:
        mulu.w  #0,d3                   ; installed: the row's bytes
        add.l   d3,d1
        move.l  d1,d5
        bsr     DTX_payload
        adda.l  d5,a1
        move.l  a1,DTX_CURSOR(a0)
        rts

; -----------------------------------------------------------------------------
; DTX_advance. The move that leaves d0 sets N and Z, so bmi after the call.

DTX_advance:
        move.l  DTX_ROW(a0),d0
        addq.l  #1,d0
advance_rows:
        cmpi.l  #0,d0                   ; installed: R
        bne.s   advance_next
advance_repeat:
        move.l  #0,d0                   ; installed: RR
advance_end:
        cmpi.l  #0,d0                   ; installed: R
        bcs.w   DTX_jump                ; RR below R: the repeat is a jump
        moveq   #-1,d0                  ; the end, and it is sticky
        rts
advance_next:
        move.l  d0,DTX_ROW(a0)
        move.l  DTX_CURSOR(a0),d1
advance_step:
        addi.l  #0,d1                   ; installed: the row's bytes
        move.l  d1,DTX_CURSOR(a0)
        rts

; -----------------------------------------------------------------------------
; DTX_read. One run of bytes from the cursor, at the move and the count init
; installed.

DTX_read:
        tst.l   DTX_ROW(a0)
        bmi.s   read_none
        movea.l DTX_CURSOR(a0),a2
read_count:
        move.w  #0,d0                   ; installed: the moves a row takes
read_run:
read_move:
        move.l  (a2)+,(a1)+             ; installed: the move that run takes
        dbf     d0,read_run
read_none:
        rts

; -----------------------------------------------------------------------------
; DTX_take. A read and an advance in one call. The read leaves a2 one past the
; row it took, which is the cursor the step would work out, so the step stores
; a2 and does no arithmetic at all.

DTX_take:
        tst.l   DTX_ROW(a0)
        bmi.w   DTX_advance             ; no row: step to row 0 and write none
        movea.l DTX_CURSOR(a0),a2
take_count:
        move.w  #0,d0                   ; installed: the moves a row takes
take_run:
take_move:
        move.l  (a2)+,(a1)+             ; installed: the move that run takes
        dbf     d0,take_run
        move.l  DTX_ROW(a0),d0
        addq.l  #1,d0
take_rows:
        cmpi.l  #0,d0                   ; installed: R
        bne.s   take_next
        move.l  a1,-(a7)                ; a jump moves a1
take_repeat:
        move.l  #0,d0                   ; installed: RR
take_end:
        cmpi.l  #0,d0                   ; installed: R
        bcc.s   take_over                   ; RR at R: the end, and it is sticky
        bsr     DTX_jump
        movea.l (a7)+,a1
        rts
take_over:
        moveq   #-1,d0
        movea.l (a7)+,a1
        rts
take_next:
        move.l  d0,DTX_ROW(a0)
        move.l  a2,DTX_CURSOR(a0)       ; where the read ended
        rts

; -----------------------------------------------------------------------------
        DTX_TABLE_FOLLOWS

        end
