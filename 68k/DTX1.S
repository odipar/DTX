; -----------------------------------------------------------------------------
; DTX1.S - a DTX1 table read on the 68000: six calls, one table, PC relative
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
; so the columns stand at one stride and a read walks them with one pointer:
; the value of column i for the row the cursor stands on is the pointer plus i
; strides. The image does not have a column table or an entry a column: the
; stride, the row's bytes and C are arithmetic on R, C and the width.
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

        .macro  DTX_INSTALL_W   value,site
        lea     \site+2(pc),a4
        move.w  \value,(a4)
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
; DTX_init. It installs R, RR, the count a read walks and the stride between
; two columns, and leaves the pointer a row below row 0.

DTX_init:
        move.l  #-1,DTX_ROW(a0)
        clr.w   DTX_TURN(a0)
        clr.l   DTX_DECODED(a0)

        bsr     DTX_payload             ; a1 the payload, a2 the header
        move.l  4(a2),d3                ; R
        move.l  10(a2),d5               ; RR
        DTX_INSTALL_L d3,advance_rows
        DTX_INSTALL_L d3,take_rows
        DTX_INSTALL_L d3,advance_end
        DTX_INSTALL_L d3,take_end
        DTX_INSTALL_L d5,advance_repeat
        DTX_INSTALL_L d5,take_repeat

; The columns a read walks, one less: a dbf counts that way.
        move.w  8(a2),d0                ; C
        subq.w  #1,d0
        DTX_INSTALL_W d0,read_count
        DTX_INSTALL_W d0,take_count

; The stride from one column to the next: R times the width, up to a word
; (R4.3). A width of 2 or 4 makes that even already.
        move.l  d3,d2
        .if     DTX_WIDTH>1
        add.l   d2,d2
        .endif
        .if     DTX_WIDTH>2
        add.l   d2,d2
        .endif
        .if     DTX_WIDTH=1
        addq.l  #1,d2
        andi.l  #-2,d2
        .endif
        DTX_INSTALL_L d2,read_stride
        DTX_INSTALL_L d2,take_stride

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
        addq.l  #DTX_WIDTH,DTX_POINTER(a0)
        rts

; -----------------------------------------------------------------------------
; DTX_read. One loop over the columns: a value, then a stride to the next
; column's. The row goes down column 0 first, which is the row as DTX0 lays
; it out (SPEC.md 2.1), and a1 ends one past it.

DTX_read:
        tst.l   DTX_ROW(a0)
        bmi.s   read_none
        movea.l DTX_POINTER(a0),a2
read_count:
        move.w  #0,d0                   ; installed: C, less one
read_loop:
        DTX_VALUE (a2),(a1)+
read_stride:
        adda.l  #0,a2                   ; installed: the stride
        dbf     d0,read_loop
read_none:
        rts

; -----------------------------------------------------------------------------
; DTX_take. A read and an advance in one call.

DTX_take:
        tst.l   DTX_ROW(a0)
        bmi.w   DTX_advance             ; no row: step to row 0 without a write
        movea.l DTX_POINTER(a0),a2
take_count:
        move.w  #0,d0                   ; installed: C, less one
take_loop:
        DTX_VALUE (a2),(a1)+
take_stride:
        adda.l  #0,a2                   ; installed: the stride
        dbf     d0,take_loop
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
        bcc.s   take_over_end           ; RR at R: the end, and it is sticky
        bsr     DTX_jump
        movea.l (a7)+,a1
        rts
take_over_end:
        moveq   #-1,d0
        movea.l (a7)+,a1
        rts
take_next:
        move.l  d0,DTX_ROW(a0)
        addq.l  #DTX_WIDTH,DTX_POINTER(a0)
        rts

; -----------------------------------------------------------------------------
        DTX_TABLE_FOLLOWS

        end
