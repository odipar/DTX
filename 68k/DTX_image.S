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
; An image contains one table or several (doc/abi.md 1). Init takes the
; header of the one to read, so nothing here reaches a table by a figure of
; its own: the format block's fields below give the first table's, for a
; caller that has only the image.
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
        dc.l    0                       ; the first table's state block bytes
        dc.l    0                       ; where the first table stands
        dc.w    0                       ; the first table's row bytes
        dc.w    0                       ; P, which every table shares
        dc.w    0                       ; N, which every table shares
        dc.b    \unit,\width
        dc.l    _columns-_base          ; the first table's column table
        dc.l    0                       ; the stride from one column's value
                                        ; to the next

; DTX_metadata. The format block is at a fixed place in the image, so one
; lea reaches it; the first table's header is behind a column table whose
; size moves with C, so its place is read out of the format block rather
; than assembled in. R, C and RR then come out of that header, and the
; stride out of the block itself. This call gives the first table's figures;
; a caller reading another table takes R, C and RR out of that table's own
; header, at the offsets below.
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

; DTX_payload. The table's header in a1 on the way in, and on the way out
; the payload's first byte in a1 and that header in a2. Init is given the
; header of the table to read, so the table's place is not read out of the
; format block: an image contains several and the block names the first. The
; header is 16 bytes under every variant and every C (SPEC.md 1), so the
; payload is 16 past it.
DTX_payload:
        movea.l a1,a2                   ; the header
        adda.w  #16,a1                  ; the payload
        rts
        .endm

; What follows the code. Nothing stands after this label: the packager
; appends a column table and then a .dtx file for each of the image's
; tables, so _columns is where the first pair lands and the code ends on a
; long to put it there. DTX0 and DTX1 do not have a column table, so under
; those two the first table's own header is the first byte there. Where
; _columns stands is assembled in and the format block repeats it; a
; table's header stands behind a column table of 16C bytes, which moves
; with that table's C, so no instruction reaches a header by a displacement
; the assembler works out.
        .macro  DTX_TABLE_FOLLOWS
        .align  4
_columns:
        .endm
