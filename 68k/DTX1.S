; -----------------------------------------------------------------------------
; DTX1.S - a DTX1 table read on the 68000: four calls, one table, PC relative
;
; By Robbert van Dalen. Written by Claude (Anthropic's Claude Code) under
; Robbert's direction. Use it freely, including commercially, as long as your
; documentation says you used DTX (see LICENSE).
;
; doc/abi.md defines the calls, the format block and the state block. One
; template a variant: this file reads only DTX1, so no call in it tests which
; variant it contains.
;
; THE CODE DOES NOT MOVE WITH THE TABLE. R, C and RR reach it at run time
; rather than at assembly time, so every DTX1 image of one width contains the
; same instructions and the table behind them is what differs. What that
; costs is a read-only image: the code writes to itself at init, so it stands
; in RAM and a 68030 caller flushes the instruction cache after DTX_init.
;
; THE WIDTH DOES MOVE THE CODE. Every value of a table is one width (R6.3),
; and it reaches the assembler as an equate, so a read moves a value in one
; instruction. One build a width is what that costs, and the packager takes
; the build the table's header names.
;
; DTX1 lays a column's values together and every column is the same length,
; so the columns stand at one stride: the value of column i for the row the
; cursor stands on is the pointer an advance gives plus i strides. The image
; does not have a column table or an entry a column, and it does not copy a
; row anywhere: the caller reads the values where they stand.
;
; DTX_metadata gives the stride, which is R times the width rounded up to a
; word (R4.3). It stands in the format block, so a caller that builds against
; a given image reads it out of the file instead.
;
; NOTHING IS CHECKED. No call checks the table, the state block, the row
; number or the address a row goes to.
; -----------------------------------------------------------------------------

        .68000
        .text

        include "DTX_table.i"

; -----------------------------------------------------------------------------
        include "DTX_image.S"
        DTX_IMAGE 1,1,0,0,DTX_WIDTH

; -----------------------------------------------------------------------------
; What a value is installed with. A 68000 reaches PC relative for a source
; and never for a destination, so a site is lea'd into a4 and written through
; it, and the assembler works out where each site stands.

        .macro  DTX_INSTALL_L   value,site
        lea     \site+2(pc),a4
        move.l  \value,(a4)
        .endm

; -----------------------------------------------------------------------------
; DTX_payload. The payload's first byte in a1 and the table's header in a2.
; Where the table stands is read out of the format block; the header is 16
; bytes under every variant and every C (SPEC.md 1), so the payload is 16
; past it.

DTX_payload:
        lea     _fmt(pc),a2
        movea.l a2,a1
        suba.w  #_fmt-_base,a1
        adda.l  8(a2),a1
        movea.l a1,a2                   ; the header
        adda.w  #16,a1                  ; the payload
        rts

; -----------------------------------------------------------------------------
; DTX_init. It installs R and RR, and leaves the pointer a row below row 0.

DTX_init:
        move.l  #-1,DTX_ROW(a0)
        clr.w   DTX_TURN(a0)
        clr.l   DTX_DECODED(a0)

        bsr     DTX_payload             ; a1 the payload, a2 the header
        move.l  4(a2),d3                ; R
        move.l  10(a2),d5               ; RR
        DTX_INSTALL_L d3,advance_rows
        DTX_INSTALL_L d3,advance_end
        DTX_INSTALL_L d5,advance_repeat

; The pointer a row below row 0, so the advance into row 0 steps like any
; other advance.
        suba.w  #DTX_WIDTH,a1
        move.l  a1,DTX_POINTER(a0)
        rts

; -----------------------------------------------------------------------------
; DTX_jump. The pointer is the payload plus the row times the width, which is
; a shift a width.

DTX_jump:
        move.l  d0,DTX_ROW(a0)
        move.l  d0,d1
        .if     DTX_WIDTH>1
        add.l   d1,d1
        .endif
        .if     DTX_WIDTH>2
        add.l   d1,d1
        .endif
        bsr     DTX_payload
        adda.l  d1,a1
        move.l  a1,DTX_POINTER(a0)
        rts

; -----------------------------------------------------------------------------
; DTX_advance. The move that leaves d0 sets N and Z, so bmi after the call.
; a1 comes back the row's first value, and the caller takes column i at i
; strides from it.

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
        movea.l DTX_POINTER(a0),a1      ; the last row stands where it stood
        rts
advance_next:
        move.l  d0,DTX_ROW(a0)
        addq.l  #DTX_WIDTH,DTX_POINTER(a0)
        movea.l DTX_POINTER(a0),a1
        rts

; -----------------------------------------------------------------------------
        DTX_TABLE_FOLLOWS

        end
