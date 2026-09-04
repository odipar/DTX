; -----------------------------------------------------------------------------
; DTX_row.S - a row taken through width class cursors
;
; By Robbert van Dalen. Written by Claude (Anthropic's Claude Code) under
; Robbert's direction. Use it freely, including commercially, as long as your
; documentation says you used DTX (see LICENSE).
;
; Included by DTX1.S and DTX2.S, never assembled on its own. Both hold a
; column's values apart from the row's, so both take a row the same way: one
; cursor a width class, and every column of that width at a displacement the
; packager holds. DTX0 needs neither macro, since its row is one run of bytes.
;
; The lists DTX_LOAD_CURSORS and DTX_READ_ROW, in DTX_table.i, are written in
; these two.
; -----------------------------------------------------------------------------

; A class cursor into the register a read takes it from. a0 holds the state
; block, so the class that takes a0 is loaded last.
        .macro  DTX_LOAD_CURSOR c,cur
        movea.l DTX_CURSOR+\c(a0),\cur
        .endm

; One value into the row: the size its width asks for, off its class cursor.
        .macro  DTX_VALUE width,disp,cur
        .if \width=1
        move.b  \disp(\cur),(a1)+
        .endif
        .if \width=2
        move.w  \disp(\cur),(a1)+
        .endif
        .if \width=4
        move.l  \disp(\cur),(a1)+
        .endif
        .endm
