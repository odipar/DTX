package st4

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
)

// Beside is a Packer that runs the ST4 executable at Path.
//
// CopiesFlag reaches it as -c, or -cS for a search of S seconds, or is
// empty for none. A column packed that way so that a match beyond the ring
// copies from its own literal stream, which packs a small ring far smaller;
// the payload then defines it (R5.10) and the reader of it takes a decoder
// built with the copy code.
type Beside struct {
	Path       string
	CopiesFlag string
}

// Copies gives whether this packer packs copies from the literal stream.
func (p Beside) Copies() bool {
	return p.CopiesFlag != ""
}

// Pack gives column as one complete ST4 data set, looping at unit loop or
// ending where loop is -1.
func (p Beside) Pack(column []byte, unit, ring, loop int) ([]byte, error) {
	work, err := os.MkdirTemp("", "dtx")
	if err != nil {
		return nil, err
	}
	defer os.RemoveAll(work)
	in := filepath.Join(work, "column")
	out := filepath.Join(work, "column.st4")
	if err := os.WriteFile(in, column, 0o644); err != nil {
		return nil, err
	}
	// The offset limit is capped as Packer caps it, so the two packers are
	// given one limit: a word offset is stored scaled to bytes, and 32512
	// units at k=4 would not fit the word.
	offsetLimit := min(ring/unit, MaxOffsetUnits(unit))
	argv := []string{"-f", "-k" + strconv.Itoa(unit),
		"-m" + strconv.Itoa(offsetLimit), "-l65535"}
	if loop >= 0 {
		// st4 -r takes the loop's own unit, and works out for itself whether
		// a back reference reaches the loop's first unit or the pass has to
		// be replayed
		argv = append(argv, "-r"+strconv.Itoa(loop))
	}
	if p.CopiesFlag != "" {
		argv = append(argv, p.CopiesFlag)
	}
	argv = append(argv, in, out)
	said, err := exec.Command(p.Path, argv...).CombinedOutput()
	if err != nil {
		// Where the executable is not there its output is empty and the
		// message would end at "gave ", so the error from the run is
		// wrapped into it.
		if trimmed := strings.TrimSpace(string(said)); trimmed != "" {
			return nil, fmt.Errorf("%s gave %s: %w", p.Path, trimmed, err)
		}
		return nil, err
	}
	packed, err := os.ReadFile(out)
	if err != nil {
		return nil, fmt.Errorf("%s did not write a data set: %s", p.Path, said)
	}
	return packed, nil
}
