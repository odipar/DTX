// Package st4 runs an ST4 packer beside this one.
//
// No ST4 packer is kept in this repository, so a column is packed by the
// executable ST4's own repository builds. The unit and the ring reach it as
// -kK and -mN, where -m counts units and N is in bytes.
//
// Every column is packed with -l65535, which holds ST4_wrap's assumption 4:
// no operation is longer than the 65535 units the 68000 decoders count in a
// word. ST4's own default already fits them, and this states it rather than
// taking it.
package st4

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
)

// A Packer that runs the executable at Path.
//
// CopiesFlag reaches the packer as -c, or -cS for a search of S seconds, or
// is empty for none. A column packed that way lets a match beyond the ring
// copy from its own literal stream, which packs a small ring far smaller;
// the payload then states it (R5.10) and the reader of it takes a decoder
// built with the copy code.
type Packer struct {
	Path       string
	CopiesFlag string
}

// Copies says whether this packer packs copies from the literal stream.
func (p Packer) Copies() bool {
	return p.CopiesFlag != ""
}

// Pack gives column as one complete ST4 data set.
func (p Packer) Pack(column []byte, unit, ring int) ([]byte, error) {
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
