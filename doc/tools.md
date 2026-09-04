# tools

## Write

A DTX file of any variant out of comma separated text.

```
mvn -q compile exec:exec@write -Dargs="in.csv out.dtx -v2 -k1 -m960 -pst4"
```

| flag | gives |
|---|---|
| `-vV` | the variant: 0, 1 or 2. The default is 0 |
| `-wW,W,..` | one width a column: 1, 2 or 4 each. The default is the narrowest width that holds every value of the column |
| `-rRR` | the row the table repeats to, 0 to `R`. The default is `R`, where the table does not repeat |
| `-kK` | the unit a DTX2 column is packed at, and `R` divides by it (R5.6). The default is 1 |
| `-mN` | the ring a DTX2 column unpacks through, in bytes, 1 to 65535 (R5.4). The default is 960 |
| `-pPACKER` | the ST4 executable a DTX2 file is packed by. The default is `st4` on the path |

`-k`, `-m` and `-p` reach a DTX2 file alone: no other variant packs.

### The text

One row a line, one value a column. The first row that holds values gives
`C`, and every row after it holds that many. A line that is blank, or whose
first character other than a space is `#`, is not a row.

A value is decimal, or hexadecimal where it opens with `$`, and negative
where it opens with `-`. A value of `W` bytes is stored most significant
byte first, as every field of the header is, and a negative one in two's
complement. A value fits `W` bytes where it lies from -2^(8W-1) to
2^(8W)-1, so one width takes what a signed column holds and what an
unsigned one holds alike. DTX states no more of a column than its width
(R6.3), so which of the two a column holds is stated elsewhere or not at
all.

```
# a clock, a note and a step
0, $0100, -2
1, $0101, -1
2, $0102,  0
```

Those three columns take widths 1, 2 and 1: column 1 holds 256 upward, and
column 2 holds a negative value.

`org.dtx.Csv` is the same reader as a library, and `Table`, `Dtx0`,
`Dtx1` and `Dtx2` write a table a caller builds itself.

## Rewrite

A DTX0 or DTX1 file into a DTX2 one. The table is the same under every
variant (R1.3), so what comes out holds the same rows, widths, `R` and `RR`
as what went in.

```
mvn -q compile exec:exec@rewrite -Dargs="in.dtx out.dtx -k1 -m960 -pst4"
```

| flag | gives |
|---|---|
| `-kK` | the unit every column is packed at: 1, 2 or 4, and `R` divides by it (R5.6). The default is 1 |
| `-mN` | the ring in bytes, 1 to 65535 (R5.4). The default is 960 |
| `-pPACKER` | the ST4 executable to run. The default is `st4` on the path |

Rewrite keeps no packer of its own. `-p` names the one ST4's own repository
builds, and a column reaches it as a file.
