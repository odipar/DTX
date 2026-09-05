package st4

import "fmt"

// The longest operation, ST4_wrap assumption 4: no operation is longer than
// the 65535 units the 68000 decoders count in a word.
const maxOp = 65535

// Packer packs a column with the port in this package, so a tool writes
// DTX2 with no packer beside it.
//
// CopiesFlag lets a match beyond the ring copy from the column's own literal
// stream, which packs a small ring far smaller. Seconds searches that long
// beyond the opening passes for a better parse, or zero for those passes
// alone: a search of no seconds is the same parse every run, one of some
// seconds is not.
type Packer struct {
	CopiesFlag bool
	Seconds    float64
}

// Copies says whether this packer packs copies from the literal stream.
func (p Packer) Copies() bool {
	return p.CopiesFlag
}

// Pack gives column as one complete ST4 data set.
func (p Packer) Pack(column []byte, unit, ring int) ([]byte, error) {
	if problem := CheckUnit(unit); problem != "" {
		return nil, fmt.Errorf("%s", problem)
	}
	// A word offset is stored scaled to bytes, so the window is a byte
	// figure: 32512 units at k=4 would not fit the word.
	offsetLimit := min(ring/unit, MaxOffsetUnits(unit))
	units := Split(column, unit)
	var parsed *Block
	if p.CopiesFlag {
		parsed = OptimizeCopies(units, unit, offsetLimit, maxOp, p.Seconds, false)
	} else {
		parsed = OptimizeEvents(units, unit, offsetLimit, false)
	}
	return CompressRepeating(parsed, units, unit, maxOp, -1, offsetLimit).
		Container(), nil
}
