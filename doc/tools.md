# tools

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
