#!/bin/sh
# MANIFEST.txt for a release directory: what the release contains, by name,
# size and sha256, so a caller can tell one release's file from another's
# without opening it.
#
#   release/manifest.sh VERSION DIR
#   COMMIT=e39c110 release/manifest.sh VERSION DIR
#
# The images are read out of the zip that packs them and listed one a line
# with the four figures that identify one: the variant, the width every
# value of a table takes, the unit its decoder decodes at, and whether that
# decoder has the copy code. The zips are listed by what each contains. The
# source commit is HEAD unless COMMIT gives the commit DIR was built from.
set -e
VERSION=$1
DIR=$2
if [ -z "$VERSION" ] || [ ! -d "$DIR" ]; then
    echo "manifest: release/manifest.sh VERSION DIR" >&2
    exit 1
fi
COMMIT=${COMMIT:-$(git rev-parse --short HEAD 2>/dev/null || echo unknown)}
IMAGES="dtx-images-v$VERSION.zip"
if [ ! -f "$DIR/$IMAGES" ]; then
    echo "manifest: $DIR does not contain $IMAGES" >&2
    exit 1
fi

sha() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | cut -d' ' -f1
    else
        shasum -a 256 "$1" | cut -d' ' -f1
    fi
}
size() {
    wc -c < "$1" | tr -d ' '
}

unpacked=$(mktemp -d)
unzip -q "$DIR/$IMAGES" -d "$unpacked"
count=$(ls "$unpacked"/*.bin | wc -l | tr -d ' ')
MANIFEST="$DIR/MANIFEST.txt"
{
    echo "DTX images and tools - release $VERSION"
    echo "source commit $COMMIT"
    echo "doc/abi.md is the calling convention; doc/tools.md the tools"
    echo
    echo "the images, in $IMAGES"
    echo "name  bytes  sha256  variant  w  k  copies"
    for image in "$unpacked"/*.bin; do
        name=$(basename "$image")
        # The name gives the width as -wW and the unit as -kK. DTX0's code
        # does not move with the width, and DTX0 and DTX1 do not have a
        # decoder, so those columns read - rather than a figure.
        w=$(echo "$name" | sed -n 's/.*-w\([0-9]*\)[-.].*/\1/p')
        k=$(echo "$name" | sed -n 's/.*-k\([0-9]*\)[-.].*/\1/p')
        [ -n "$w" ] || w=-
        [ -n "$k" ] || k=-
        case "$name" in
            DTX2-w*-copies-*) variant=2; copies=yes ;;
            DTX2-w*)          variant=2; copies=no ;;
            DTX0-*)           variant=0; copies=- ;;
            DTX1-w*)          variant=1; copies=- ;;
            *)                variant=-; copies=- ;;
        esac
        echo "$name  $(size "$image")  $(sha "$image")  $variant  $w  $k  $copies"
    done
    echo
    echo "the zips"
    echo "name  bytes  sha256  contents"
    echo "$IMAGES  $(size "$DIR/$IMAGES")  $(sha "$DIR/$IMAGES")  the $count images"
    for zip in "$DIR"/dtx-tools-*.zip; do
        name=$(basename "$zip")
        platform=$(echo "$name" | sed "s/^dtx-tools-//; s/-v$VERSION\.zip$//")
        echo "$name  $(size "$zip")  $(sha "$zip")  the tools for $platform"
    done
} > "$MANIFEST"
rm -rf "$unpacked"
echo "$MANIFEST: $(grep -c . "$MANIFEST") lines"
