; -----------------------------------------------------------------------------
; DTX_image.S - what every variant's image opens and ends with
;
; By Robbert van Dalen. Written by Claude (Anthropic's Claude Code) under
; Robbert's direction. Use it freely, including commercially, as long as your
; documentation says you used DTX (see LICENSE).
;
; Included by DTX0.S, DTX1.S and DTX2.S, never assembled on its own. It holds
; what doc/abi.md 1 states of every image and what no variant changes: the
; six slots, the format block, and the one call that reads neither the state
; block nor the table's layout.
;
; A template includes this after DTX_table.i, invokes DTX_IMAGE where the
; image begins, and DTX_TABLE_FOLLOWS where its code ends.
; -----------------------------------------------------------------------------

; The six slots, the format block and DTX_metadata. The three figures a
; variant states of itself reach it as arguments: P, N and k stand at 1, 0
; and 0 where nothing is packed.
        .macro  DTX_IMAGE variant,period,ring,unit
_base:
        bra.w   DTX_init
        bra.w   DTX_metadata
        bra.w   DTX_jump
        bra.w   DTX_advance
        bra.w   DTX_read
        bra.w   DTX_take

_fmt:
        dc.b    'D','T','X',\variant
        dc.l    DTX_STATE
        dc.l    _table-_base
        dc.w    DTX_ROWBYTES
        dc.w    \period
        dc.w    \ring
        dc.b    \unit,0

; DTX_metadata. Both blocks are bytes in the image, reached with one lea
; each, so this call needs no state and may be made before init.
DTX_metadata:
        lea     _fmt(pc),a0
        lea     _table(pc),a1
        move.l  #DTX_ROWS,d0
        move.w  #DTX_COLUMNS,d1
        move.l  #DTX_REPEAT,d2
        rts
        .endm

; The table's bytes follow. Nothing stands after this label: the packager
; appends the .dtx file to what rmac writes, so _table is where it lands and
; the code ends on a long to put it there.
        .macro  DTX_TABLE_FOLLOWS
        .align  4
_table:
_payload        equ     _table+DTX_HEADER
        .endm

; The end of a table, and the repeat at RR. An advance that steps past the
; last row jumps to RR where the table repeats, and gives $FFFFFFFF where it
; does not, leaving the clock on the last row: the end is sticky, and a
; further advance gives $FFFFFFFF again. The row stands in d0 and the body
; that steps a row stands at .step.
;
; DTX2 states this itself rather than taking it here, since it puts the
; caller's a6, d6 and d7 back before either exit.
        .macro  DTX_END_OR_REPEAT
        cmpi.l  #DTX_ROWS,d0
        bne.s   .step
        .if DTX_REPEAT<DTX_ROWS
        move.l  #DTX_REPEAT,d0
        bra.w   DTX_jump                ; the repeat is a jump
        .else
        moveq   #-1,d0                  ; the end, and it is sticky
        rts
        .endif
        .endm

; The same rule for DTX_take, which gives a1 back one past the row it wrote.
; The repeat runs DTX_jump, and a jump moves a1.
        .macro  DTX_TAKE_END_OR_REPEAT
        cmpi.l  #DTX_ROWS,d0
        bne.s   .step
        .if DTX_REPEAT<DTX_ROWS
        move.l  a1,-(a7)
        move.l  #DTX_REPEAT,d0
        bsr     DTX_jump
        movea.l (a7)+,a1
        rts
        .else
        moveq   #-1,d0                  ; the end, and it is sticky
        rts
        .endif
        .endm
