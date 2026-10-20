# The DTX format

A table of `R` rows and `C` columns, in one of the variants R2 defines.
Every variant is the same table laid out differently, so the
header below is the same under all of them and only the payload differs.

Every field of more than one byte is most significant byte first. A word
is two bytes and a long is four; an offset is on a word where it divides
by 2, and on a long where it divides by 4.

---

## 1. The header

| offset | bytes | contains |
|---|---|---|
| 0 | 3 | `DTX` |
| 3 | 1 | the variant: 0, 1, 2, 3, or a number a later specification assigns |
| 4 | 4 | `R`, the row count, 1 upward |
| 8 | 2 | `C`, the column count, 1 to 256 |
| 10 | 4 | `RR`, the row the table repeats to |
| 14 | 1 | `W`, the bytes every value of the table is written in: 1, 2 or 4 |
| 15 | 1 | zero, so the payload begins on a long |

`RR` names a row, so 0 to `R` minus one. `RR` equal to `R` marks a table
that does not repeat, and a reader of such a table stops at its last row.

One width for the table and not one a column (R6.3). Every value is
`W` bytes, so a row is `C` times `W`, a column is `R` times `W`, and a
reader works both out without reading a width a column.

The header is 16 bytes under every variant and every `C`, and the payload
begins on a long. A long and not a word, because DTX2's data sets begin on
longs (2.3); DTX0 and DTX1 need a word at most, and follow the same
rule so that a header is one shape under every variant.

A reader reads the variant from byte 3, and stops there for a variant it
was not built for (R2.3).

A table of `R` = 3 rows and `C` = 3 columns of two byte values has this
header, and the pictures below lay out that same table:

```
   0     3   4       8    10        14  15  16
  +-----+---+-------+-----+---------+---+---+
  | DTX | v |   R   |  C  |   RR    | 2 | 0 |
  +-----+---+-------+-----+---------+---+---+
     3    1     4      2       4      W  pad
```

---

## 2. The payload

`W` is the width from the header, the bytes every value is written in. The
payload begins on a long. Inside it DTX0 does not pad, DTX1 pads before each
column to a word, DTX2 pads before each data set to a long, and DTX3 before
its stream records and each slot's streams to a long: each variant's
section defines where. A pad byte is zero.

In DTX0 and DTX1 an offset is arithmetic on `R`, `C` and `W` (R3.3, R4.2),
and DTX1's pad enters that arithmetic as a fixed term. DTX2 and DTX3
differ: the payload defines at what offset each packed column begins, and a
value within a packed column is found by unpacking (2.3, 2.4).

### 2.1 DTX0, row by row

`R` rows, each column 0 through column `C` minus one in order.

A row is `C` times `W`, with nothing between its columns. Column `i`
within a row begins at `i` times `W`, row `n` begins at `n` times a row,
and the payload is `R` times a row.

A row begins where the row before it ends, so at a width of 1 and an odd
`C` a row begins on an odd offset and a reader reads its values as bytes
(R3.4). At a width of 2 or 4 the payload begins on a long and every value
is a whole number of them from it, so every value is where a 68000
reads it as one.

```
   a row of the example: 3 columns of 2 bytes, six bytes

  +-----+-----+-----+
  | a0  | b0  | c0  |   row 0, bytes 0 to 5
  +-----+-----+-----+
  | a1  | b1  | c1  |   row 1, bytes 6 to 11
  +-----+-----+-----+
  | a2  | b2  | c2  |   row 2, bytes 12 to 17
  +-----+-----+-----+
     2     2     2

   18 bytes, the table's values
```

### 2.2 DTX1, column by column

`C` columns, each its `R` values in row order. Column `i` is `R` times
`W` bytes and begins on a word: where the column before it ends odd, a
zero byte is between them. Its row `n` is `n` times `W` further on.

Every column is the same length, so they lie at one stride: `R` times `W`,
up to a word. Column `i` begins at `i` strides, and a reader steps from one
column of a row to the next by adding one (R4.2). The payload is `C` minus
one strides and the last column's `R` times `W` bytes, the padding between
one column and the next inside the strides.

DTX1 has that padding over DTX0 (R4.3). It costs a byte between one column
and the next at a width of 1 and an odd `R`, and nothing at a width of 2 or
4, where a column is a whole number of words already. In return every value
is on a word under every `C`, where DTX0 at a width of 1 puts a row on
an odd offset.

The other difference is the reach of one read: a row of DTX0, a column of
DTX1.

```
        column 0            column 1            column 2

  +-----+-----+-----+ +-----+-----+-----+ +-----+-----+-----+
  | a0  | a1  | a2  | | b0  | b1  | b2  | | c0  | c1  | c2  |
  +-----+-----+-----+ +-----+-----+-----+ +-----+-----+-----+
     2     2     2       2     2     2       2     2     2
   \  the stride, 6  /

   18 bytes, the table's values and no pad at a width of 2
```

At a width of 1 and `R` = 3 the same table would run to 11 bytes: three
columns of three, and a pad byte after the first two.

### 2.3 DTX2, column by column and packed

`C` ST4 data sets, one a column. Column `i`'s data set packs the `R`
times `W` bytes of that column as DTX1 lays them out, without DTX1's pad
byte, and is complete: its ST4 header, and the length of what it unpacks
to.

Every data set in a payload is packed at one unit and unpacks through a
ring of one size, so the payload defines both once and then where the data
sets are:

| offset | bytes | contains |
|---|---|---|
| 0 | 2 | `N`, the bytes of the ring a column unpacks through |
| 2 | 1 | `k`, the unit every data set is packed at: 1, 2 or 4 |
| 3 | 1 | the flags: bit 0 marks a payload whose columns contain copies from their literal streams. The other bits are zero |
| 4 | 4·`C` | an offset a column, column 0 first: offset `i` is where column `i`'s data set begins, from the start of the payload |

A writer puts `N` divided by `k` in a data set's `M` (ST4, SPEC.md 2.1),
the same ring in the unit ST4 counts in, and a reader of copies reads `M`
out of the data set to separate a copy from a match (ST4, SPEC.md 7.4): the
two figures describe one ring, and the data set's is the one a decoder
runs on.

Note: `R` in ST4's document is the loop point of a stream (ST4, SPEC.md
6.1), where `R` here is the row count of a table (1). A data set of a
table that repeats loops at the form of ST4, SPEC.md 6.2 where its loop
fits the ring, and at that of 6.3, the rewind point in its header, where
the loop is longer; the columns of a payload have one loop and one
length, so every data set of it has the same form. The loop point of
that stream, in units, is this format's `RR` times `W` over `k` (R5.11):
a reader of both documents meets one figure under the two names, counted
in units there and in rows here.

R5.8 needs `N`. A reader reads it once and has a ring of that many bytes,
and the ring stays one size as `R` grows.

One `N` for the payload does for the rings what one `k` does for the code:
every data set reaches back `N` bytes at most, so one ring size is enough
for them all, the rings are at a fixed stride from one another, and one
pointer arithmetic runs every column (R5.4, R5.5).

`k` need not be `W`: a table of two byte values packs at a unit of 1 or of
2, and one of one byte values at a unit of 4.

**The flags byte.** Bit 0 marks a payload whose every column was packed so
that a match beyond the ring copies from that column's literal stream,
which ST4 packs with `-c`. A copy is an offset above `M`, the ring in
units (ST4, SPEC.md 4.4), which a decoder built without the copy code
reads as a match, so such a decoder reads such a column wrongly: the
payload marks the build a reader needs before it opens a data set
(R5.10). In a payload that sets the bit every column contains copies, and
in one that clears it, none does.

A file written before this byte was used reads zero here, no copies,
and a decoder without the copy code is the one such a file always
needed.

One `k` for the payload means one decoder in a reader. ST4 code is built
for a unit, and a reader of DTX2 runs every column of a payload through
the one build for that unit (R5.3).

`R` times `W` divides by `k` (R5.6). A column is `R` times `W` bytes and
ST4 packs whole units, so a column that is not a whole number of them
unpacks to more bytes than the column has. At a width of 4 that is true at
every `k` and every `R`; at a width of 2 and `k` of 4 it bounds `R` to even
numbers, and at a width of 1 it bounds `R` to multiples of `k`.

Four bytes and `4C` divide by 4, so the first data set begins on a long
where the payload does. The data sets follow, each beginning on a long:
where one ends short of the next boundary, the bytes between are zero.

A reader reads a column from its offset alone: a data set defines the
length of what it unpacks to, and the bits that pack it end on the end
code.

**What an ST4 data set is.** What a reader of DTX2 needs of it; the format
is defined in full in [ST4](https://github.com/odipar/ST4).

- Its first long is `$53 $34 $07 k`: `'S'`, `'4'`, the ST4 format version
  7, and the unit `k`.
- Its ST4 header is twenty-eight bytes, and a data set begins on a long
  so a reader reads that header a long at a time. DTX2 aligns its data
  sets for that (R5.9).
- A reader built for one unit rejects a data set whose fourth byte is
  another. The payload's `k` is that same unit (R5.2), and a reader checks
  the two against each other: one compare of a data set's first long
  against `$53 $34 $07 k` checks the signature, the format version and
  the unit at once.
- A column is a data set at any length, which packs to more bytes than it
  has where it is shorter than the twenty-eight bytes of an ST4 header.

```
   N, k and the flags once, an offset a column, then a data set a column

  +----+--+--+------+------+------+========+=========+=======+
  | N  |k |f | ->b0 | ->b1 | ->b2 | ST4 of | ST4 of  | ST4   |
  +----+--+--+------+------+------+========+=========+=======+
     2  1  1     4      4      4    col 0    col 1     col 2
   \    4     /\  the offsets, 4C /\ each on a long, each with
                                      its header and length
```

A reader unpacks a column through its ring rather than into `R` times `W`
bytes (R5.8).

### 2.4 DTX3, a constant column as its value and the others packed

A column whose `R` values are equal is a **constant column**, and the
payload has its value once. Every other column is a **live column**, packed
as DTX2 packs a column (2.3). A live column's **slot** is its rank among
the live columns, the first being slot 0, and `L` is how many there are,
0 to `C`.

| offset | bytes | contains |
|---|---|---|
| 0 | 2 | `N`, the bytes of the ring a live column unpacks through: a multiple of `k`, from `k` to 32512 |
| 2 | 1 | `k`, the unit every live column is packed at: 1, 2 or 4 |
| 3 | 1 | the flags: bit 0 marks a payload with copies from the literal stream in a live column, and bit 1 a payload whose loop is replayed. The other bits are zero |
| 4 | 2 | `L` |
| 6 | 2 | zero |
| 8 | `C` | the **column map**, a byte a column, column 0 first: a live column's slot, and $FF for a constant column |
| 8 + `C` | `W` times `C` less `L` | the constant columns' values, in column order, each `W` bytes |
| .. | 16`L` | a **stream record** a slot, slot 0 first, beginning on a long |
| .. | .. | each slot's four streams, slot 0's first, each slot's beginning on a long |

The slots follow the column order: a live column's slot is one more than
the slot of the live column before it. At an `L` of 256 every column is
live, the map's last byte is slot 255, and the constant values run to zero
bytes.

A stream record is four longs, where streams A, B, C and D of its slot
begin, counted from the payload's first byte. The four are the streams of
an ST4 data set packed as DTX2 packs a column (2.3), in the order and with
the padding that data set lays them out, and without its twenty-eight byte
header. That header's fields are the table's and the payload's once for
every live column: the length of what a column unpacks to is `R` times `W`,
`M` is `N` over `k`, the rewind point is `RR` times `W` where bit 1 is set
and absent where it is clear, and the signature is ST4's format version 7
at the payload's `k`. `N` at most 32512 keeps `M` exactly `N` over `k` at
every unit: 32512 bytes is the furthest an ST4 offset reaches (ST4, SPEC.md
4.3).

A slot's stream D runs to the next slot's stream A, and the last slot's to
the end of the payload; a reader stops on the end code, as in a data set.
Streams A and D begin on a word, since a 68000 reads both a word at a time.

**The flags byte.** Bit 0 marks the decoder a reader needs: a decoder with
the copy code reads a live column with copies and one without, and a
decoder without it reads the second kind alone (2.3). So a writer packs
each live column as it packs smallest, with copies or without, and one
column with copies sets the bit. Bit 1 marks the form of the loop. Every
live column of a payload has one loop and one length, so one form of it
(ST4, SPEC.md 6.2 and 6.3): the bit is set where the loop is longer than a
back reference reaches and a reader replays it, and clear where the streams
loop by their end code or the table does not repeat.

A reader has a ring of `N` bytes a live column, and slot `j`'s ring is `j`
rings past slot 0's, so one pointer arithmetic runs every live column
(R8.4). Column `i`'s value for a row is the byte the map has for it times
`N` past slot 0's value, where the column is live, and its value in the
payload where it is constant. A constant column has neither ring nor
decoder.

`R` times `W` divides by `k`, and where the table repeats `RR` times `W`
divides by `k` as well, as under DTX2 (R8.9).

```
   C = 3, column 1 constant: N, k and the flags, L, the map, the value,
   a record a slot, then each slot's streams

  +----+--+--+----+----+--+--+--+----+------+------+======+======+
  | N  |k |f | 2  | 0  |0 |FF|1 | v1 | rec0 | rec1 | slot | slot |
  +----+--+--+----+----+--+--+--+----+------+------+======+======+
     2  1  1   2    2   \  map /  W    16     16     0's    1's
                                \ up to a long /     on a long each
```

---

## 3. Not yet written

Where a table defines its length, or whether it defines one.

What a reader reports of a table it will not read: a variant not among
those it reads, an `R` below 1, a `C` outside 1 to 256, a width other than
1, 2 or 4, an `RR` over `R`, or a DTX2 whose column is not a whole number
of units.

Whether a variant may leave some columns plain beside packed ones.
