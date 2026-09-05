package st4

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
)

// Beside is a Packer that runs the ST4 executable at Path.
//
// CopiesFlag reaches it as -c, or -cS for a search of S seconds, or is
// empty for none. A column packed that way lets a match beyond the ring
// copy from its own literal stream, which packs a small ring far smaller;
// the payload then states it (R5.10) and the reader of it takes a decoder
// built with the copy code.
type Beside struct {
	Path       string
	CopiesFlag string
}

// Copies says whether this packer packs copies from the literal stream.
func (p Beside) Copies() bool {
	return p.CopiesFlag != ""
}

// Pack gives column as one complete ST4 data set.
func (p Beside) Pack(column []byte, unit, ring int) ([]byte, error) {
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
	argv := []string{"-f", "-k" + strconv.Itoa(unit),
		"-m" + strconv.Itoa(ring/unit), "-l65535"}
	if p.CopiesFlag != "" {
		argv = append(argv, p.CopiesFlag)
	}
	argv = append(argv, in, out)
	said, err := exec.Command(p.Path, argv...).CombinedOutput()
	if err != nil {
		return nil, fmt.Errorf("%s gave %s", p.Path, said)
	}
	packed, err := os.ReadFile(out)
	if err != nil {
		return nil, fmt.Errorf("%s wrote no data set: %s", p.Path, said)
	}
	return packed, nil
}
