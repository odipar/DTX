; -----------------------------------------------------------------------------
; DTX0.S - a DTX0 table read on the 68000: four calls, one table, PC relative
;
; By Robbert van Dalen. Written by Claude (Anthropic's Claude Code) under
; Robbert's direction. Use it freely, including commercially, as long as your
; documentation says you used DTX (see LICENSE).
;
; doc/abi.md defines the calls, the format block and the state block. One
; template a variant: this file reads only DTX0, so no call in it tests which
; variant it contains.
;
; THE CODE DOES NOT MOVE WITH THE TABLE. R, RR and the row's bytes reach it at
; run time rather than at assembly time: DTX_init reads them out of the format
; block and the table's own header and writes them into the instructions that
; take them. Every DTX0 image contains the same instructions, byte for byte;
; the table behind them differs.
;
; That costs a read-only image. The code writes to itself at init, so it
; stands in RAM rather than ROM, and a 68030 caller flushes the instruction
; cache after DTX_init. A 68000 writes to its own code freely.
;
; DTX0 lays the rows one after another, so one pointer walks them and a read is
; one run of bytes. The width every value takes does not reach this file: a row
; is a run either way, so one build reads a DTX0 table of any width.
;
; THE TABLE'S BYTES FOLLOW THE CODE. Nothing here contains them: _columns is the
; last label and the code ends on a long, so the packager appends the .dtx
; file to what rmac writes and _columns is where it lands. DTX0 does not have
; a column table, so under this variant the table's own header is the first
; byte there.
;
; NOTHING IS CHECKED. No call checks the table, the state block, the row
; number or the address a row goes to. doc/abi.md 7 lists what each call takes
; without checking.
; -----------------------------------------------------------------------------

        .68000
        .text

        include "DTX_table.i"

; -----------------------------------------------------------------------------
; The image, doc/abi.md 1: four slots, the format block, then the bodies.

        include "DTX_image.S"
        DTX_IMAGE 0,0,0

; -----------------------------------------------------------------------------
; The word form of DTX_image.S's DTX_INSTALL_L, which that file's comment
; lays out. The row's bytes take a word, and only this file installs one.

        .macro  DTX_INSTALL_W   value,site
        lea     \site+2(pc),a4
        move.w  \value,(a4)
        .endm

; -----------------------------------------------------------------------------
; DTX_init. a1 the header of the table to read (doc/abi.md 2). It writes
; that table's figures into the instructions below, parks its payload for a
; jump to reach, and leaves the pointer a row below row 0, so the advance
; into row 0 steps like any other advance.
;
; The row's bytes come out of the table's own header and not out of the
; format block, which gives the first table's: an image contains several
; tables, and the figure below stands in three instructions, so it is this
; table's or the reads are another's.

DTX_init:
        clr.w   DTX_TURN(a0)

        bsr.s   DTX_payload             ; a1 the payload, a2 the header
        move.l  a1,DTX_PAYLOAD(a0)      ; what a jump reaches it by
        moveq   #0,d2
        move.w  8(a2),d2                ; C
        moveq   #0,d3
        move.b  14(a2),d3               ; W, the bytes a value takes
        mulu.w  d3,d2                   ; the row's bytes

; The row's bytes, into the step and into the two mulu a jump takes. A row
; is C values of the width, so 1024 bytes at the widest table R6 takes,
; which a word gives.
        DTX_INSTALL_W d2,advance_step
        DTX_INSTALL_W d2,jump_high
        DTX_INSTALL_W d2,jump_low

; The pointer a row below row 0.
        suba.l  d2,a1
        move.l  a1,DTX_POINTER(a0)
        rts

; -----------------------------------------------------------------------------
; DTX_jump. The pointer is the payload plus the row times the row's bytes: two
; mulu.w make that 32 by 16 product at any row's bytes. The payload is the
; one init parked, not the format block's: an image contains several tables
; and a jump reads the one its block was seeded on.

DTX_jump:
        move.l  d0,d1
        swap    d1
jump_high:
        mulu.w  #0,d1                   ; installed: the row's bytes
        swap    d1
        clr.w   d1
        move.l  d0,d3
jump_low:
        mulu.w  #0,d3                   ; installed: the row's bytes
        add.l   d3,d1
        movea.l DTX_PAYLOAD(a0),a1      ; the payload init was given
        adda.l  d1,a1
        move.l  a1,DTX_POINTER(a0)
        rts

; -----------------------------------------------------------------------------
; DTX_advance. One row on: a1 comes back the row's first value, and the
; values of a DTX0 row stand one after another, so the caller takes column i
; at i widths from it. Which row that is the caller counts (abi.md 2): no
; call keeps a row number, and an advance past row R minus one reads past
; the table.

DTX_advance:
        movea.l DTX_POINTER(a0),a1
advance_step:
        adda.w  #0,a1                   ; installed: the row's bytes
        move.l  a1,DTX_POINTER(a0)
        rts

; -----------------------------------------------------------------------------
        DTX_TABLE_FOLLOWS

        end
