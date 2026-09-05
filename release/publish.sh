#!/bin/sh
# The standalone DTX executables: one set per platform, each containing the
# eight 68000 images, so a machine with neither this repository nor a
# toolchain can write a table and package it for a 68000.
#
#   release/publish.sh [version]      # the six platforms below
#   TARGETS="linux-x64" release/publish.sh
#
# They are built from go/, so there are six of them: go build
# cross-compiles to any target from any host with nothing installed for it.
#
# NO JAVA RUNS HERE. The images come from the Go dtx-blobs, which assembles
# 68k/ with rmac, so the only tool this needs beside Go is that assembler.
# The Java tree writes the same bytes and ParityTest checks the two
# to it, but a release is built from one tree.
#
# The executables do not take a wrapper. Go builds a real executable, so
# nothing has to find a runtime or a classpath before one runs.
set -e
cd "$(dirname "$0")/.."
REPO=$(pwd)
OUT=${OUT:-dist}
RMAC=${RMAC:-rmac}
TARGETS=${TARGETS:-"win-x64 win-arm64 osx-x64 osx-arm64 linux-x64 linux-arm64"}
TOOLS="dtx-write dtx-rewrite dtx-package dtx-blobs"

# The version names the images and the zips. The pom is where it is written
# down, and this reads the text rather than running anything.
VERSION=${1:-$(sed -n 's/.*<version>\(.*\)<\/version>.*/\1/p' pom.xml | head -1)}
if [ -z "$VERSION" ]; then
    echo "publish: pom.xml does not name a version" >&2
    exit 1
fi

# What go:embed takes: only this release's images, so an executable cannot
# contain an older release's image by accident.
IMAGES=go/internal/image/data
rm -f "$IMAGES"/*.bin
rm -rf "$OUT/release"
mkdir -p "$OUT/release"

# dtx-blobs first, and from a tree with no image: it builds them, so it
# is the one tool that does not need one.
(cd go && go build -o "$REPO/$OUT/dtx-blobs" ./cmd/dtx-blobs)
"$OUT/dtx-blobs" "$IMAGES" "$OUT/release" -a"$RMAC" -t"$REPO/68k"
rm -f "$OUT/dtx-blobs"

count=$(ls "$IMAGES"/*.bin 2>/dev/null | wc -l | tr -d ' ')
if [ "$count" != 8 ]; then
    echo "publish: $count images built, not the 8 there are" >&2
    exit 1
fi

for target in $TARGETS; do
    case "$target" in
        win-*)   os=windows; ext=.exe ;;
        osx-*)   os=darwin;  ext= ;;
        linux-*) os=linux;   ext= ;;
        *) echo "publish: $target is not a platform this builds" >&2; exit 1 ;;
    esac
    case "$target" in
        *-x64)   arch=amd64 ;;
        *-arm64) arch=arm64 ;;
        *) echo "publish: $target does not name an architecture" >&2; exit 1 ;;
    esac

    # The directory is where a built tool gets tried out, so the build
    # starts from an empty one.
    rm -rf "$OUT/$target"
    mkdir -p "$OUT/$target"
    for tool in $TOOLS; do
        # CGO off makes the binary static and the cross-build runs; -s
        # -w drop the symbol and debug tables, which nothing here reads.
        (cd go && CGO_ENABLED=0 GOOS=$os GOARCH=$arch \
            go build -ldflags="-s -w" -o "$REPO/$OUT/$target/$tool$ext" \
            ./cmd/"$tool")
    done
    zip="dtx-tools-$target-v$VERSION.zip"
    (cd "$OUT/$target" && zip -q -X "../release/$zip" *)
    echo "$OUT/release/$zip: $(wc -c < "$OUT/release/$zip" | tr -d ' ') bytes"
done

# The images a caller may take beside the executables, named by release so
# two of them do not sit in one directory unlabelled.
for image in "$OUT"/release/*.bin; do
    mv "$image" "${image%.bin}-v$VERSION.bin"
done

# What the release contains, by name, size and hash, so a caller can tell one
# release's file from another's without opening it. The images carry the
# three figures that identify one: the variant, the unit its decoder decodes at,
# and whether that decoder has the copy code.
sha() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | cut -d' ' -f1
    else
        shasum -a 256 "$1" | cut -d' ' -f1
    fi
}

MANIFEST="$OUT/release/MANIFEST.txt"
{
    echo "DTX images and tools - release $VERSION"
    echo "source commit $(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
    echo "doc/abi.md is the calling convention; doc/tools.md the tools"
    echo
    echo "the images"
    echo "name  bytes  sha256  variant  k  copies"
    for image in "$OUT"/release/*.bin; do
        name=$(basename "$image")
        # DTX0 and DTX1 do not have a decoder, so neither a unit nor copies.
        case "$name" in
            DTX2-k*-copies-*) variant=2; k=$(echo "$name" | sed 's/.*-k\([0-9]*\)-copies.*/\1/'); copies=yes ;;
            DTX2-k*)          variant=2; k=$(echo "$name" | sed 's/.*-k\([0-9]*\)-v.*/\1/'); copies=no ;;
            DTX0-*)           variant=0; k=-; copies=- ;;
            DTX1-*)           variant=1; k=-; copies=- ;;
            *)                variant=-; k=-; copies=- ;;
        esac
        echo "$name  $(wc -c < "$image" | tr -d ' ')  $(sha "$image")" \
             " $variant  $k  $copies"
    done
    echo
    echo "the tools"
    echo "name  bytes  sha256  platform"
    for zip in "$OUT"/release/*.zip; do
        name=$(basename "$zip")
        platform=$(echo "$name" | sed "s/^dtx-tools-//; s/-v$VERSION\.zip$//")
        echo "$name  $(wc -c < "$zip" | tr -d ' ')  $(sha "$zip")  $platform"
    done
} > "$MANIFEST"
echo "$MANIFEST: $(grep -c . "$MANIFEST") lines"

# The host's executables, tried as a user would: from a directory that is
# not this repository, with nothing beside them. They contain only the images
# they embed, so one that does not contain an image fails here rather than in
# a release.
case "$(uname -s)-$(uname -m)" in
    Darwin-arm64) host=osx-arm64 ;;
    Darwin-x86_64) host=osx-x64 ;;
    Linux-x86_64) host=linux-x64 ;;
    Linux-aarch64) host=linux-arm64 ;;
    *) host= ;;
esac
if [ -n "$host" ] && [ -d "$OUT/$host" ]; then
    try=$(mktemp -d)
    printf '1,2,3\n4,5,6\n7,8,9\n8,7,6\n' > "$try/t.csv"
    "$REPO/$OUT/$host/dtx-write" "$try/t.csv" "$try/t.dtx" -v1 -w1,2,4
    "$REPO/$OUT/$host/dtx-package" "$try/t.dtx" "$try/t.bin"
    echo "tried: $(wc -c < "$try/t.bin" | tr -d ' ') bytes of image from" \
         "$OUT/$host, outside the repository"
    rm -rf "$try"
fi

echo "$OUT/release holds this release: the zips, and the eight images."
