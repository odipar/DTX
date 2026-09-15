// Package st4 packs a DTX2 column, over the ST4 library of the repository
// that defines the format.
//
// DTX carried a copy of that library until this: the copy drifted, missing
// the -pN parse and forty-two constructs of the house style before it was
// noticed. What stands here belongs to DTX alone - a Packer that packs in
// this process and a Beside that runs an ST4 executable - and the names the
// library defines are re-exported, so a caller writes st4.Packer and
// st4.Result as it did.
package st4

import upstream "github.com/odipar/st4/go/st4"

// The library's names, as this package's, so nothing that packs a column
// names two packages.
type (
	// Block is one block of a parse: the bits to its end, the unit it ends
	// on, its offset, and the block before it.
	Block = upstream.Block
	// Container is the parts of one ST4 file.
	Container = upstream.Container
	// Result is the four streams and their figures.
	Result = upstream.Result
)

// The library's values and functions, as this package's.
const (
	// NoRewind is the rewind field of a stream that ends or loops by itself.
	NoRewind = upstream.NoRewind
	// HeaderSize is the twenty-eight bytes before stream A.
	HeaderSize = upstream.HeaderSize
	// InitialOffset is the offset a parse opens at.
	InitialOffset = upstream.InitialOffset
)

var (
	// CheckUnit is the reason unit cannot be used, or an empty string.
	CheckUnit = upstream.CheckUnit
	// MaxOffsetUnits is the widest ring a unit size reaches.
	MaxOffsetUnits = upstream.MaxOffsetUnits
	// Split is the input as units of the unit size, zero padded.
	Split = upstream.Split
	// Read reads an ST4 file apart.
	Read = upstream.Read
	// Decode rebuilds a stream and reports whether it repeats.
	Decode = upstream.Decode
	// CompressRepeating writes a parse out, repeating from a unit.
	CompressRepeating = upstream.CompressRepeating
	// CompressRewinding writes a parse out that loops by rewind.
	CompressRewinding = upstream.CompressRewinding
	// OptimizeEvents is the parser the tools use.
	OptimizeEvents = upstream.OptimizeEvents
	// OptimizeCopies searches for a parse with copies from the literals.
	OptimizeCopies = upstream.OptimizeCopies
)
