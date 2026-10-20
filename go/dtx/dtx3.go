package dtx

import (
	"fmt"
	"runtime"
	"sync"

	"github.com/odipar/dtx/go/st4"
)

// MaxRingDtx3 is the largest ring a DTX3 payload can define, in bytes: the
// furthest a back reference reaches, so that M is N over k at every unit
// (R8.6).
var MaxRingDtx3 = st4.MaxOffsetUnits(1)

// The flags bits at DTX3 payload byte 3: some live column was packed with
// copies from its literal stream (R8.7), and the loop is replayed at RR
// (R8.8).
const (
	CopiesDtx3   = 1
	ReplayedDtx3 = 2
)

// Constant is the column map's byte for a constant column.
const Constant = 0xFF

// The column map begins at payload byte 8, and a stream record is four
// longs.
const (
	mapAt  = 8
	record = 16
)

// WriteDtx3 returns table as a DTX3 file. A column whose R values are equal
// is constant and the payload has its value once; each other column is
// live, packed by every packer named, and the smallest data set is kept,
// the first named where two are the same size: so a column packs with
// copies where they pay and without them where they do not.
//
// It is an error where no packer is named, unit or ring is outside what the
// payload defines, a column's bytes do not divide by unit, the table
// repeats at a row whose first byte is not a unit of the column, or a data
// set defines a unit, a length, an M or a loop other than the payload's.
func WriteDtx3(t *Table, packers []Packer, unit, ring int) ([]byte, error) {
	if len(packers) == 0 {
		return nil, fmt.Errorf("no packer: a live column is packed by one" +
			" at least")
	}
	if unit != 1 && unit != 2 && unit != 4 {
		return nil, fmt.Errorf("k is 1, 2 or 4, not %d", unit)
	}
	if ring < unit || ring > MaxRingDtx3 || ring%unit != 0 {
		return nil, fmt.Errorf("N is a multiple of k from %d to %d, not %d",
			unit, MaxRingDtx3, ring)
	}
	bytes := t.Rows() * t.Width()
	if bytes%unit != 0 {
		return nil, fmt.Errorf("a column is %d times %d bytes, which does"+
			" not divide by k of %d", t.Rows(), t.Width(), unit)
	}
	loop, err := Loop(t, unit)
	if err != nil {
		return nil, err
	}
	columns := t.Columns()
	width := t.Width()
	live := make([]bool, columns)
	count := 0
	for i := range live {
		live[i] = !constant(t.Column(i), width)
		if live[i] {
			count++
		}
	}

	// Each live column's data set, the smallest the packers make, and
	// whether the one kept has copies. A column packs apart from every
	// other, so the columns pack at once and the bytes are the same.
	column := make([]int, 0, count)
	for i := 0; i < columns; i++ {
		if live[i] {
			column = append(column, i)
		}
	}
	set := make([][]byte, count)
	withCopies := make([]bool, count)
	failed := make([]error, count)
	var wait sync.WaitGroup
	turns := make(chan struct{}, runtime.GOMAXPROCS(0))
	for j := range column {
		wait.Add(1)
		turns <- struct{}{}
		go func(j int) {
			defer func() { <-turns; wait.Done() }()
			values := t.Column(column[j])
			kept, err := packers[0].Pack(values, unit, ring, loop)
			if err != nil {
				failed[j] = err
				return
			}
			keptCopies := packers[0].Copies()
			for _, packer := range packers[1:] {
				made, err := packer.Pack(values, unit, ring, loop)
				if err != nil {
					failed[j] = err
					return
				}
				if len(made) < len(kept) {
					kept, keptCopies = made, packer.Copies()
				}
			}
			set[j], withCopies[j] = kept, keptCopies
		}(j)
	}
	wait.Wait()
	// Every set of a payload has one loop and one form of it, which the
	// first defines.
	copies := false
	rewind := st4.NoRewind
	for j, i := range column {
		if failed[j] != nil {
			return nil, failed[j]
		}
		if err := checkSet(set[j], i, unit, ring, bytes); err != nil {
			return nil, err
		}
		said := int(int32(GetLong(set[j], 20)))
		if j == 0 {
			rewind = said
		}
		if said != rewind || said != st4.NoRewind &&
			(loop < 0 || said != loop*unit) {
			return nil, fmt.Errorf("column %d's data set records a rewind"+
				" point of %d, where every live column has the loop at unit"+
				" %d and the form of the first", i, said, loop)
		}
		copies = copies || withCopies[j]
	}

	// SPEC.md 2.4: N, k, the flags, L, the map, the constant values up to a
	// long, a record a slot, then each slot's streams on a long.
	records := Align(mapAt+columns+width*(columns-count), 4)
	at := make([]int, count)
	next := records + record*count
	for j := range set {
		next = Align(next, 4)
		at[j] = next
		next += len(set[j]) - st4.HeaderSize
	}
	head := t.Header(DTX3)
	p := len(head)
	out := make([]byte, p+next)
	copy(out, head)
	PutWord(out, p, ring)
	out[p+2] = byte(unit)
	if copies {
		out[p+3] |= CopiesDtx3
	}
	if rewind != st4.NoRewind {
		out[p+3] |= ReplayedDtx3
	}
	PutWord(out, p+4, count)
	value := p + mapAt + columns
	slot := 0
	for i := 0; i < columns; i++ {
		if live[i] {
			out[p+mapAt+i] = byte(slot)
			slot++
		} else {
			out[p+mapAt+i] = Constant
			copy(out[value:value+width], t.Column(i))
			value += width
		}
	}
	// A record's four offsets are from the payload's first byte. The
	// streams are the data set's as it laid them out, so each is the data
	// set's offset of it, less the 28-byte header, past the slot's.
	for j := range set {
		rec := p + records + record*j
		PutLong(out, rec, at[j])
		for s := 1; s < 4; s++ {
			PutLong(out, rec+4*s, at[j]+GetLong(set[j], 4+4*s)-st4.HeaderSize)
		}
		copy(out[p+at[j]:], set[j][st4.HeaderSize:])
	}
	return out, nil
}

// checkSet checks a data set against the payload: its signature, its
// length, and its M, which a reader of the payload reads as N over k.
func checkSet(set []byte, column, unit, ring, bytes int) error {
	read, err := st4.Read(set)
	if err != nil {
		return fmt.Errorf("column %d: %v", column, err)
	}
	if read.Unit != unit {
		return fmt.Errorf("column %d's data set is packed at k of %d, and"+
			" the payload's k is %d", column, read.Unit, unit)
	}
	if read.Size != bytes {
		return fmt.Errorf("column %d's data set unpacks to %d bytes, not the"+
			" %d of R rows at its width", column, read.Size, bytes)
	}
	if read.Window != ring/unit {
		return fmt.Errorf("column %d's data set has an M of %d units, and"+
			" the payload's M is N over k, %d", column, read.Window,
			ring/unit)
	}
	return nil
}

// constant reports whether every value of column is its first.
func constant(column []byte, width int) bool {
	for at := width; at < len(column); at += width {
		for b := 0; b < width; b++ {
			if column[at+b] != column[b] {
				return false
			}
		}
	}
	return true
}

// Dtx3From returns the DTX3 file of the table in a DTX file of any
// variant, packed by the packers named at the unit and ring named here.
func Dtx3From(file []byte, packers []Packer, unit, ring int) ([]byte, error) {
	t, err := Read(file)
	if err != nil {
		return nil, err
	}
	return WriteDtx3(t, packers, unit, ring)
}

// Payload3 is a DTX3 payload's figures: the ring, the unit, the two flags,
// the column map, a constant column's value in its column, and slot j's
// four stream offsets from the payload's first byte.
type Payload3 struct {
	Ring      int // N
	Unit      int // k
	Copies    bool
	Replayed  bool
	Map       []int
	Constants []uint32
	Records   [][4]int
}

// Live returns L, the live columns.
func (p Payload3) Live() int {
	return len(p.Records)
}

// IsLive reports whether column i is live.
func (p Payload3) IsLive(i int) bool {
	return p.Map[i] != Constant || len(p.Records) == 256
}

// Bytes returns the bytes of slot j's four streams in a payload of payload
// bytes: from its stream A to the next slot's, or to the payload's end.
func (p Payload3) Bytes(j, payload int) int {
	if j+1 < len(p.Records) {
		return p.Records[j+1][0] - p.Records[j][0]
	}
	return payload - p.Records[j][0]
}

// ReadPayload3 reads those out of a DTX3 payload, SPEC.md 2.4, checked
// against the rules it lays down: L at most C, the slots of the live
// columns 0 to L minus one in column order, and every stream inside the
// file, in order, A and D on a word.
func ReadPayload3(file []byte, header Header) (Payload3, error) {
	p := header.Length()
	columns := header.Columns
	width := header.Width
	if len(file) < p+mapAt+columns {
		return Payload3{}, fmt.Errorf("a DTX3 payload of %d columns has %d"+
			" bytes before its values, and the file ends at %d", columns,
			mapAt+columns, len(file)-p)
	}
	flags := file[p+3]
	live := GetWord(file, p+4)
	if live > columns {
		return Payload3{}, fmt.Errorf("L is 0 to C of %d, not %d", columns,
			live)
	}
	out := Payload3{
		Ring:      GetWord(file, p),
		Unit:      int(file[p+2]),
		Copies:    flags&CopiesDtx3 != 0,
		Replayed:  flags&ReplayedDtx3 != 0,
		Map:       make([]int, columns),
		Constants: make([]uint32, columns),
		Records:   make([][4]int, live),
	}
	value := p + mapAt + columns
	slot := 0
	for i := 0; i < columns; i++ {
		out.Map[i] = int(file[p+mapAt+i])
		if out.Map[i] == Constant && live < 256 {
			if value+width > len(file) {
				return Payload3{}, fmt.Errorf("column %d's value is past the"+
					" file's end", i)
			}
			v := uint32(0)
			for b := 0; b < width; b++ {
				v = v<<8 | uint32(file[value+b])
			}
			out.Constants[i] = v
			value += width
		} else {
			if out.Map[i] != slot {
				return Payload3{}, fmt.Errorf("column %d maps to slot %d,"+
					" and the live columns' slots are 0 to L minus one in"+
					" column order", i, out.Map[i])
			}
			slot++
		}
	}
	if slot != live {
		return Payload3{}, fmt.Errorf("the map has %d live columns, and L is"+
			" %d", slot, live)
	}
	records := Align(mapAt+columns+width*(columns-live), 4)
	last := records + record*live
	for j := 0; j < live; j++ {
		for s := 0; s < 4; s++ {
			at := p + records + record*j + 4*s
			if at+4 > len(file) {
				return Payload3{}, fmt.Errorf("slot %d's record is past the"+
					" file's end", j)
			}
			r := int(int32(GetLong(file, at)))
			if r < last || p+r > len(file) {
				return Payload3{}, fmt.Errorf("slot %d's stream %c is at %d,"+
					" out of order or outside the payload", j, 'A'+s, r)
			}
			out.Records[j][s] = r
			last = r
		}
		if out.Records[j][0]&1 != 0 || out.Records[j][3]&1 != 0 {
			return Payload3{}, fmt.Errorf("slot %d's streams A and D begin on"+
				" a word, and they are at %d and %d", j, out.Records[j][0],
				out.Records[j][3])
		}
	}
	return out, nil
}

// ReadDtx3 returns the table in a DTX3 file: each constant column its value
// R times, and each live column unpacked with the copy of ST4 carried in
// internal/st4. A slot's stream D runs to the next slot's stream A, or to
// the end of the file.
//
// It is an error where the file is not DTX3, breaks a rule of its payload,
// or a column unpacks to other than R times its width.
func ReadDtx3(file []byte) (*Table, error) {
	header, err := ReadHeader(file)
	if err != nil {
		return nil, err
	}
	if header.Variant != DTX3 {
		return nil, fmt.Errorf("variant %d is not DTX3", header.Variant)
	}
	payload, err := ReadPayload3(file, header)
	if err != nil {
		return nil, err
	}
	p := header.Length()
	width := header.Width
	bytes := header.Rows * width
	unit := payload.Unit
	if unit != 1 && unit != 2 && unit != 4 {
		return nil, fmt.Errorf("k is 1, 2 or 4, not %d", unit)
	}
	rewind := st4.NoRewind
	if payload.Replayed {
		rewind = header.Repeat * width
	}
	column := make([][]byte, header.Columns)
	for i := range column {
		column[i] = make([]byte, bytes)
		if !payload.IsLive(i) {
			for at := 0; at < bytes; at += width {
				for b := 0; b < width; b++ {
					column[i][at+b] = byte(payload.Constants[i] >>
						(8 * (width - 1 - b)))
				}
			}
			continue
		}
		slot := payload.Map[i]
		r := payload.Records[slot]
		end := len(file) - p
		if slot+1 < payload.Live() {
			end = payload.Records[slot+1][0]
		}
		decoded, err := st4.Decode(file[p+r[0]:p+r[1]], file[p+r[1]:p+r[2]],
			file[p+r[2]:p+r[3]], file[p+r[3]:p+end], unit, bytes,
			payload.Ring/unit, rewind)
		if err != nil {
			return nil, fmt.Errorf("column %d: %v", i, err)
		}
		if len(decoded.Output) != bytes {
			return nil, fmt.Errorf("column %d unpacks to %d bytes, not the %d"+
				" of R rows at its width", i, len(decoded.Output), bytes)
		}
		column[i] = decoded.Output
	}
	return NewTable(header.Rows, header.Repeat, width, column)
}
