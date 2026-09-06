; -----------------------------------------------------------------------------
; DTX_image.S - what every variant's image opens and ends with
;
; By Robbert van Dalen. Written by Claude (Anthropic's Claude Code) under
; Robbert's direction. Use it freely, including commercially, as long as your
; documentation says you used DTX (see LICENSE).
;
; Included by DTX0.S, DTX1.S and DTX2.S, never assembled on its own. It contains
; the four slots, the format block, DTX_metadata and DTX_payload, which
; doc/abi.md 1 defines for every image and no variant changes, and the macro
; DTX0 and DTX1 install a long with.
;
; A template includes this after DTX_table.i, invokes DTX_IMAGE where the
; image begins, and DTX_TABLE_FOLLOWS where its code ends.
; -----------------------------------------------------------------------------

; What a value is installed with. A 68000 reaches PC relative for a source
; and never for a destination, so a site is lea'd into a4 and written
; through it. The assembler works out where each site stands: nothing here
; counts an offset, and every reach into the image is PC relative.
;
; The operand of an instruction whose opcode is one word stands at +2 of it,
; and the L form adds that.
        .macro  DTX_INSTALL_L   value,site
        lea     \site+2(pc),a4
        move.l  \value,(a4)
        .endm

; The four slots, the format block, DTX_metadata and DTX_payload. Six fields
; of the block stand at zero here and the packager writes them at a combine
; (doc/abi.md 5): the assembler does not read the table they describe. The
; two figures a variant defines for itself reach this macro as arguments: k
; stands at 0 where nothing is packed, and the width at 0 where the code does
; not move with it.
        .macro  DTX_IMAGE variant,unit,width
_base:
        bra.w   DTX_init
        bra.w   DTX_metadata
        bra.w   DTX_jump
        bra.w   DTX_advance

_fmt:
        dc.b    'D','T','X',\variant
        dc.l    0                       ; the state block's bytes
        dc.l    0                       ; where the table stands
        dc.w    0                       ; a row's bytes
        dc.w    0                       ; P
        dc.w    0                       ; N
        dc.b    \unit,\width
        dc.l    _columns-_base          ; where the column table begins
        dc.l    0                       ; the stride from one column's value
                                        ; to the next

; DTX_metadata. The format block is at a fixed place in the image, so one
; lea reaches it; the table's own header is behind a column table whose size
; moves with C, so its place is read out of the format block rather than
; assembled in. R, C and RR then come out of the header, and the stride out
; of the block itself.
; SPEC.md 1 lays it out: R at +4, C at +8 and RR at +10.
DTX_metadata:
        lea     _fmt(pc),a0
        lea     _base(pc),a1            ; the image's first byte
        adda.l  8(a0),a1                ; the table's header
        move.l  4(a1),d0                ; R
        move.w  8(a1),d1                ; C
        move.l  10(a1),d2               ; RR
        move.l  24(a0),d3               ; the stride
        rts

; DTX_payload. The payload's first byte in a1 and the table's header in a2.
; Where the table stands is read out of the format block; the header is 16
; bytes under every variant and every C (SPEC.md 1), so the payload is 16
; past it.
DTX_payload:
        lea     _base(pc),a1
        adda.l  _fmt-_base+8(a1),a1     ; where the table's header stands
        movea.l a1,a2                   ; the header
        adda.w  #16,a1                  ; the payload
        rts
        .endm

; What follows the code. Nothing stands after this label: the packager
; appends the column table and then the .dtx file to what rmac writes, so
; _columns is where the first of them lands and the code ends on a long to
; put it there. DTX0 and DTX1 do not have a column table, so under those two
; the table's own header is the first byte there. Where _columns stands is
; assembled in and the format block repeats it; the table's header stands
; behind a column table of 16C bytes, so it moves with C and no instruction
; reaches it by a displacement the assembler works out.
        .macro  DTX_TABLE_FOLLOWS
        .align  4
_columns:
        .endm
