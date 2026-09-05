; -----------------------------------------------------------------------------
; DTX_image.S - what every variant's image opens and ends with
;
; By Robbert van Dalen. Written by Claude (Anthropic's Claude Code) under
; Robbert's direction. Use it freely, including commercially, as long as your
; documentation says you used DTX (see LICENSE).
;
; Included by DTX0.S, DTX1.S and DTX2.S, never assembled on its own. It contains
; what doc/abi.md 1 defines for every image and what no variant changes: the
; six slots, the format block, and the one call that reads neither the state
; block nor the table's layout.
;
; A template includes this after DTX_table.i, invokes DTX_IMAGE where the
; image begins, and DTX_TABLE_FOLLOWS where its code ends.
; -----------------------------------------------------------------------------

; The move one value takes. The width every value of the table takes reaches
; the assembler as an equate, so a read moves a value in one instruction and
; no call tests the width. One build a width is what that costs.
        .macro  DTX_VALUE from,to
        .if     DTX_WIDTH=1
        move.b  \from,\to
        .else
        .if     DTX_WIDTH=2
        move.w  \from,\to
        .else
        move.l  \from,\to
        .endif
        .endif
        .endm

; The six slots, the format block and DTX_metadata. The four figures a
; variant defines for itself reach it as arguments: P, N and k stand at 1, 0
; and 0 where nothing is packed, and the width at 0 where the code does not
; move with it.
        .macro  DTX_IMAGE variant,period,ring,unit,width
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
        dc.l    0                       ; the packager writes it: the table
                                        ; stands behind the column table,
                                        ; whose size moves with C
        dc.w    DTX_ROWBYTES
        dc.w    \period
        dc.w    \ring
        dc.b    \unit,\width
        dc.l    _columns-_base          ; where the column table begins

; DTX_metadata. The format block is at a fixed place in the image, so one
; lea reaches it; the table's own header is behind a column table whose size
; moves with C, so its place is read out of the format block rather than
; assembled in. R, C and RR then come out of the header.
; SPEC.md 1 lays it out: R at +4, C at +8 and RR at +10.
DTX_metadata:
        lea     _fmt(pc),a0
        movea.l a0,a1
        suba.w  #_fmt-_base,a1          ; the image's first byte
        adda.l  8(a0),a1                ; the table's header
        move.l  4(a1),d0                ; R
        move.w  8(a1),d1                ; C
        move.l  10(a1),d2               ; RR
        rts
        .endm

; What follows the code. Nothing stands after this label: the packager
; appends the column table and then the .dtx file to what rmac writes, so
; _columns is where the first of them lands and the code ends on a long to
; put it there. DTX0 and DTX1 do not have a column table, so under those two
; the table's own header is the first byte there. The format block defines
; where each begins, and no instruction reaches either by a displacement the
; assembler works out: both move with C, and the code does not.
        .macro  DTX_TABLE_FOLLOWS
        .align  4
_columns:
        .endm
