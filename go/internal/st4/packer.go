package st4

import "fmt"

// The longest operation, ST4_wrap assumption 4: no operation is longer than
// the 65535 units the 68000 decoders count in a word.
const maxOp = 65535

// Packer packs a column with the port in this package, so a tool writes
// DTX2 with no packer beside it.
//
// CopiesFlag so that a match beyond the ring copies from the column's own literal
// stream, which packs a small ring far smaller. Seconds searches that long
// beyond the opening passes for a better parse, or zero for those passes
// alone: a search of no seconds is the same parse every run, one of some
// seconds is not.
type Packer struct {
	CopiesFlag bool
	Seconds    float64
}

// Copies gives whether this packer packs copies from the literal stream.
func (p Packer) Copies() bool {
	return p.CopiesFlag
}

// Pack gives column as one complete ST4 data set, looping at unit loop or
// ending where loop is -1.
func (p Packer) Pack(column []byte, unit, ring, loop int) ([]byte, error) {
	if problem := CheckUnit(unit); problem != "" {
		return nil, fmt.Errorf("%s", problem)
	}
	// A word offset is stored scaled to bytes, so the limit is a byte
	// figure: 32512 units at k=4 would not fit the word.
	offsetLimit := min(ring/unit, MaxOffsetUnits(unit))
	units := Split(column, unit)
	if loop < -1 || loop >= len(units) {
		return nil, fmt.Errorf("the loop is unit -1 to %d of the column,"+
			" not %d", len(units)-1, loop)
	}
	// ST4 packs a loop two ways, and this makes the same test its own packer
	// makes: the end marker's endless match where a back reference reaches
	// the loop's first unit, and a replayed pass where it does not.
	if loop >= 0 && len(units)-loop > offsetLimit {
		return p.replayed(units, unit, offsetLimit, loop).Container(), nil
	}
	return CompressRepeating(p.parse(units, unit, offsetLimit), units, unit,
		maxOp, loop, offsetLimit).Container(), nil
}

// replayed gives a column whose loop is longer than a back reference reaches.
// The run before the loop and the loop are parsed apart, so nothing in the
// loop reaches before the loop's first unit and every pass reads the same
// history. The data set records that unit, and a reader puts the decoder's
// registers away there and back at the column's end, every pass, which ST4's
// decoders leave to the caller (abi.md 4).
func (p Packer) replayed(units []uint32, unit, limit, loop int) Result {
	var before *Block
	if loop > 0 {
		before = p.parse(units[:loop:loop], unit, limit)
	}
	return CompressRewinding(before, p.parse(units[loop:], unit, limit),
		units, unit, maxOp, loop, limit)
}

// parse gives one parse of units, with the copy code where this packs it.
// Neither optimizer reports progress: a tool writes what it wrote, and a
// meter on standard output would stand in the middle of it.
func (p Packer) parse(units []uint32, unit, limit int) *Block {
	if p.CopiesFlag {
		return OptimizeCopies(units, unit, limit, maxOp, p.Seconds, false)
	}
	return OptimizeEvents(units, unit, limit, false)
}
