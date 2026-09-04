; -----------------------------------------------------------------------------
; DTX1.S - a DTX1 table read on the 68000: six calls, one table, PC relative
;
; By Robbert van Dalen. Written by Claude (Anthropic's Claude Code) under
; Robbert's direction. Use it freely, including commercially, as long as your
; documentation says you used DTX (see LICENSE).
;
; doc/abi.md states the calls, the format block and the state block. One
; template a variant: this file reads DTX1 and nothing else, so no call in it
; tests which variant it holds.
;
; THE CODE DOES NOT MOVE WITH THE TABLE. R, C, RR and the widths reach it at
; run time rather than at assembly time, so every DTX1 image holds the same
; instructions and the column table and the table behind them are what differ.
; What that costs is a read-only image: the code writes to itself at init, so
; it stands in RAM and a 68030 caller flushes the instruction cache after
; DTX_init.
;
; DTX1 holds a column's values together, so one cursor a width class walks
; them and every column of that width stands at a displacement from it. There
; is one loop a width and none a column: a read runs the byte columns, then
; the word columns, then the long ones, taking each column's displacement and
; its place in the row out of the column table.
;
; THE COLUMN TABLE stands behind the code, and the format block says where.
; The packager writes it, grouped by width so each loop walks a run:
;
;   +0   2  the width-1 columns
;   +2   2  the width-2 columns
;   +4   2  the width-4 columns
;   +6   2  the row's bytes
;   +8   4  where the width-1 class begins, from the payload
;   +12  4  where the width-2 class begins
;   +16  4  where the width-4 class begins
;   +20  2  C
;   +22  2  P, which is 1 here
;   +24  4  where the stream records begin. DTX2 holds those
;   +28  4  N, which is zero here
;   +32  .. one entry a column, four bytes: the column's displacement from
;           its class, and where its value stands in the row
;
; The header runs to 32 bytes under both variants that read one, so a read
; reaches the entries at one displacement whichever wrote the table.
;
; TRUSTED INPUT ONLY. No call checks the table, the state block, the row
; number or the address a row goes to.
; -----------------------------------------------------------------------------

        .68000
        .text

        include "DTX_table.i"

; The column table's own header.
DTX_COL_COUNT       equ     0               ; three words, one a width
DTX_COL_ROWBYTES    equ     6
DTX_COL_BASE        equ     8               ; three longs, one a width
DTX_COL_ENTRIES     equ     32

; The state block beyond what doc/abi.md 3 states: three cursors and the
; three places their classes begin, so a jump reaches any row without the
; column table.
DTX_CLASSBASE       equ     36

; -----------------------------------------------------------------------------
        include "DTX_image.S"
        DTX_IMAGE 1,1,0,0

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
; The table stands behind a column table whose size moves with C, so where it
; begins is read out of the format block rather than assembled in.

DTX_payload:
        lea     _fmt(pc),a2
        movea.l a2,a1
        suba.w  #_fmt-_base,a1
        adda.l  8(a2),a1
        movea.l a1,a2                   ; the header
        moveq   #0,d1
        move.w  8(a2),d1                ; C
        addi.l  #14+3,d1
        andi.l  #-4,d1
        adda.l  d1,a1                   ; the payload
        rts

; The column table's first byte in a3.
DTX_columns:
        lea     _fmt(pc),a3
        adda.l  20(a3),a3               ; the field, while a3 is still the block
        suba.w  #_fmt-_base,a3          ; and the offset counts from _base
        rts

; -----------------------------------------------------------------------------
; DTX_init. It installs the counts, the row's bytes, R and RR, and leaves
; every class cursor a row below row 0.

DTX_init:
        move.l  #-1,DTX_ROW(a0)
        clr.w   DTX_TURN(a0)
        clr.l   DTX_DECODED(a0)

        bsr     DTX_payload             ; a1 the payload, a2 the header
        bsr     DTX_columns             ; a3 the column table

        move.l  4(a2),d3                ; R
        move.l  10(a2),d5               ; RR
        DTX_INSTALL_L d3,advance_rows
        DTX_INSTALL_L d3,take_rows
        DTX_INSTALL_L d3,advance_end
        DTX_INSTALL_L d3,take_end
        DTX_INSTALL_L d5,advance_repeat
        DTX_INSTALL_L d5,take_repeat

        move.w  DTX_COL_ROWBYTES(a3),d2
        DTX_INSTALL_W d2,read_over
        DTX_INSTALL_W d2,take_over

; The counts, one a width, into the six loops that walk them. A count of
; zero leaves its loop with -1 in the counter, which the branch below it
; takes as none.
        move.w  DTX_COL_COUNT+0(a3),d0
        subq.w  #1,d0
        DTX_INSTALL_W d0,read_bytes
        DTX_INSTALL_W d0,take_bytes
        move.w  DTX_COL_COUNT+2(a3),d0
        subq.w  #1,d0
        DTX_INSTALL_W d0,read_words
        DTX_INSTALL_W d0,take_words
        move.w  DTX_COL_COUNT+4(a3),d0
        subq.w  #1,d0
        DTX_INSTALL_W d0,read_longs
        DTX_INSTALL_W d0,take_longs

; Where each class begins, and a cursor a row below row 0.
        move.l  a1,d4
        move.l  DTX_COL_BASE+0(a3),d0
        add.l   d4,d0
        move.l  d0,DTX_CLASSBASE+0(a0)
        subq.l  #1,d0
        move.l  d0,DTX_CURSOR+0(a0)
        move.l  DTX_COL_BASE+4(a3),d0
        add.l   d4,d0
        move.l  d0,DTX_CLASSBASE+4(a0)
        subq.l  #2,d0
        move.l  d0,DTX_CURSOR+4(a0)
        move.l  DTX_COL_BASE+8(a3),d0
        add.l   d4,d0
        move.l  d0,DTX_CLASSBASE+8(a0)
        subq.l  #4,d0
        move.l  d0,DTX_CURSOR+8(a0)
        rts

; -----------------------------------------------------------------------------
; DTX_jump. A class cursor is where its class begins plus the row times its
; width, which is a shift a width.

DTX_jump:
        move.l  d0,DTX_ROW(a0)
        move.l  d0,d1
        add.l   DTX_CLASSBASE+0(a0),d1
        move.l  d1,DTX_CURSOR+0(a0)
        move.l  d0,d1
        add.l   d1,d1
        add.l   DTX_CLASSBASE+4(a0),d1
        move.l  d1,DTX_CURSOR+4(a0)
        move.l  d0,d1
        add.l   d1,d1
        add.l   d1,d1
        add.l   DTX_CLASSBASE+8(a0),d1
        move.l  d1,DTX_CURSOR+8(a0)
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
        addq.l  #1,DTX_CURSOR+0(a0)
        addq.l  #2,DTX_CURSOR+4(a0)
        addq.l  #4,DTX_CURSOR+8(a0)
        rts

; -----------------------------------------------------------------------------
; DTX_read. One loop a width, over the run of entries that width holds.

DTX_read:
        tst.l   DTX_ROW(a0)
        bmi.s   read_none
        bsr     DTX_columns
        lea     DTX_COL_ENTRIES(a3),a3
        movea.l DTX_CURSOR+0(a0),a2
read_bytes:
        move.w  #0,d0                   ; installed: the width-1 columns, less one
        bmi.s   read_word_class
read_byte_loop:
        move.w  (a3)+,d1
        move.w  (a3)+,d2
        move.b  0(a2,d1.w),0(a1,d2.w)
        dbf     d0,read_byte_loop
read_word_class:
        movea.l DTX_CURSOR+4(a0),a2
read_words:
        move.w  #0,d0                   ; installed: the width-2 columns, less one
        bmi.s   read_long_class
read_word_loop:
        move.w  (a3)+,d1
        move.w  (a3)+,d2
        move.w  0(a2,d1.w),0(a1,d2.w)
        dbf     d0,read_word_loop
read_long_class:
        movea.l DTX_CURSOR+8(a0),a2
read_longs:
        move.w  #0,d0                   ; installed: the width-4 columns, less one
        bmi.s   read_done
read_long_loop:
        move.w  (a3)+,d1
        move.w  (a3)+,d2
        move.l  0(a2,d1.w),0(a1,d2.w)
        dbf     d0,read_long_loop
read_done:
read_over:
        adda.w  #0,a1                   ; installed: the row's bytes
read_none:
        rts

; -----------------------------------------------------------------------------
; DTX_take. A read and an advance in one call.

DTX_take:
        tst.l   DTX_ROW(a0)
        bmi.w   DTX_advance             ; no row: step to row 0 and write none
        bsr     DTX_columns
        lea     DTX_COL_ENTRIES(a3),a3
        movea.l DTX_CURSOR+0(a0),a2
take_bytes:
        move.w  #0,d0                   ; installed: the width-1 columns, less one
        bmi.s   take_word_class
take_byte_loop:
        move.w  (a3)+,d1
        move.w  (a3)+,d2
        move.b  0(a2,d1.w),0(a1,d2.w)
        dbf     d0,take_byte_loop
take_word_class:
        movea.l DTX_CURSOR+4(a0),a2
take_words:
        move.w  #0,d0                   ; installed: the width-2 columns, less one
        bmi.s   take_long_class
take_word_loop:
        move.w  (a3)+,d1
        move.w  (a3)+,d2
        move.w  0(a2,d1.w),0(a1,d2.w)
        dbf     d0,take_word_loop
take_long_class:
        movea.l DTX_CURSOR+8(a0),a2
take_longs:
        move.w  #0,d0                   ; installed: the width-4 columns, less one
        bmi.s   take_done
take_long_loop:
        move.w  (a3)+,d1
        move.w  (a3)+,d2
        move.l  0(a2,d1.w),0(a1,d2.w)
        dbf     d0,take_long_loop
take_done:
take_over:
        adda.w  #0,a1                   ; installed: the row's bytes
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
        addq.l  #1,DTX_CURSOR+0(a0)
        addq.l  #2,DTX_CURSOR+4(a0)
        addq.l  #4,DTX_CURSOR+8(a0)
        rts

; -----------------------------------------------------------------------------
        DTX_TABLE_FOLLOWS

        end
