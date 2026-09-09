#!/usr/bin/env sh
set -eu

cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

sh ./build.sh

echo Packaging Lide for macOS...
java -cp out lide.AppPackager .

echo "Done. Open dist/Lide.app"
