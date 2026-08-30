# The DTX format

A table of `R` rows and `C` columns, in one of the variants R2 defines.
Every variant holds the same table and lays it out differently, so the
header below is the same under all of them and only the payload differs.

Every field of more than one byte is most significant byte first.

---

## 1. The header

| offset | bytes | gives |
|---|---|---|
| 0 | 3 | `DTX` |
| 3 | 1 | the variant: 0, 1, 2, or a number a later specification assigns |
| 4 | 4 | `R`, the row count, 1 upward |
| 8 | 2 | `C`, the column count, 1 to 256 |
| 10 | 4 | `RR`, the row the table repeats to |
| 14 | `C` | one byte a column, its width: 1, 2 or 4 |

`RR` names a row, so 0 to `R` minus one. `RR` equal to `R` says the table
does not repeat, and a reader that reaches the last row has no next one.

The header is padded with zero bytes so the payload begins on a long. Its
length is 14 plus `C`, rounded up to a multiple of 4.

A long and not a word, because DTX2's data sets begin on longs (2.3).
DTX0 and DTX1 want no more than a word, and take the same rule so that a
header is one shape under every variant.

A reader takes the variant from byte 3 and reads no further where it does
not read that variant (R2.3).

A table of `R` = 3 rows and `C` = 3 columns, of widths 1, 4 and 2, has
this header, and the pictures below lay out that same table:

```
   0     3   4       8    10        14         17   20
  +-----+---+-------+-----+---------+---+---+---+----+
  | DTX | v |   R   |  C  |   RR    | 1 | 4 | 2 |....|
  +-----+---+-------+-----+---------+---+---+---+----+
     3    1     4      2       4      the widths   pad
```


---

## 2. The payload

`W[k]` is column `k`'s width, from the header.

The payload begins on a long. Inside it DTX0 pads nothing, DTX1 pads
before each column to a word, and DTX2 pads before each data set to a
long: each variant's section says where. A pad byte is zero.

Nowhere does padding cost an offset its arithmetic (R3.3, R4.2). Where a value
sits is a multiplication and a sum of the widths, and where a variant pads
the pad is as fixed as the widths are.

### 2.1 DTX0, row by row

`R` rows, each holding column 0 through column `C` minus one in order.

A row is the sum of the widths, with nothing between its columns. Column
`k` within a row begins at the sum of the widths before `k`, row `n`
begins at `n` times a row, and the payload is `R` times a row.

A value falls where the widths put it, so a two or four byte column can
fall on an odd offset and a reader takes it as bytes (R3.4).

```
   a row of the example: 1 + 4 + 2, seven bytes

  +---+---------+-----+
  |a0 |   b0    | c0  |   row 0, bytes 0 to 6
  +---+---------+-----+
  |a1 |   b1    | c1  |   row 1, bytes 7 to 13
  +---+---------+-----+
  |a2 |   b2    | c2  |   row 2, bytes 14 to 20
  +---+---------+-----+
    1      4       2

   21 bytes, which is what the table holds
```

### 2.2 DTX1, column by column

`C` columns, each holding its `R` values in row order. Column `k` is `R`
times `W[k]` bytes and begins on a word: where the column before it ends
odd, a zero byte stands between them. Its row `n` is `n` times `W[k]`
further on.

That padding is what DTX1 has over DTX0 (R4.3). A column begins even and
its values are `W[k]` apart, so every value of a two or four byte column
sits on a word and a 68000 reads it as one. It costs at most a byte a
column, and only where a column of an odd length precedes another.

What else differs is what one read reaches: a row of DTX0, or a column of
DTX1.

```
   column 0   column 1                     column 2

  +---+---+---+-+---------+---------+---------+-----+-----+-----+
  |a0 |a1 |a2 |.|   b0    |   b1    |   b2    | c0  | c1  | c2  |
  +---+---+---+-+---------+---------+---------+-----+-----+-----+
    1   1   1  1     4         4         4       2     2     2
              pad, so column 1 begins on a word

   22 bytes: the table's 21, and one byte of pad
```

### 2.3 DTX2, column by column and packed

`C` ST4 data sets, one a column. A column's data set packs the bytes
DTX1's column `k` holds and is complete: its own header, its own streams,
and the length of what it unpacks to.

Every data set in a payload is packed at one unit and unpacks through a
ring of one size, so the payload states both once and then says where the
data sets are:

| offset | bytes | gives |
|---|---|---|
| 0 | 2 | `N`, the bytes of the ring a column unpacks through |
| 2 | 1 | `k`, the unit every data set is packed at: 1, 2 or 4 |
| 3 | 1 | zero |
| 4 | 4·`C` | one offset a column: where its data set begins, from the start of the payload |

`N` is what R5.5 asks for. A reader takes it once and holds a ring of that
many bytes, and the ring does not grow as `R` does.

`k` need not be `W[k]`: a two byte column packs at a unit of 1 or of 2.
One `k` for the payload is not only shorter than one a column - ST4 asks
it, and rejects a file whose data sets do not share a unit.

`R` divides by `k` (R5.3). A column holds `R` times `W[k]` bytes and ST4
packs whole units, so a column that is not a whole number of them unpacks
to more bytes than the column holds. Where `R` divides by `k` it cannot:
`R` times `W[k]` then divides by `k` at every width.

Four bytes and `4C` divide by 4, so the first data set begins on a long
where the payload does. The data sets follow, each beginning on a long:
where one ends short of the next boundary, the bytes between are zero.

A reader takes a column from its offset alone: how long a data set runs is
the data set's own to state, so no offset is read against the next.

**What an ST4 data set is.** Enough of it to find the way; the format is
stated in full in [ST4](https://github.com/odipar/ST4).

- Its first long is `$53 $34 $04 k`: `'S'`, `'4'`, the ST4 format version
  4, and the unit `k`.
- Its header is twenty bytes, and it begins on a long so a reader takes
  that header a long at a time. That is why DTX2 aligns its data sets.
- A reader built for one unit rejects a data set whose fourth byte gives
  another. The payload's `k` gives that same unit, so a reader knows
  before it opens a data set what it will find there.
- A run of bytes shorter than twenty is smaller stored than packed, which
  is ST4's own to say and not read here.

```
   N and k once, an offset a column, then a data set a column

  +----+--+--+------+------+------+========+=========+=======+
  | N  |k |. | ->b0 | ->b1 | ->b2 | ST4 of | ST4 of  | ST4   |
  +----+--+--+------+------+------+========+=========+=======+
     2  1  1     4      4      4    col 0    col 1     col 2
   \    4     /\  the offsets, 4C /\ each on a long, each with
                                      its own header and length
```

A reader unpacks a column into a buffer of its own choosing rather than
into `R` times `W[k]` bytes (R5.5).

---

## 3. Not yet written

Where a table states its length, or whether it states one at all.

What a reader reports of a table it will not read: a variant it does not
know, a `C` over 256, a width other than 1, 2 or 4, an `RR` over `R`, or a
DTX2 whose `R` does not divide by its `k`.

Whether a variant may hold columns of more than one kind, packing some and
leaving others plain.
