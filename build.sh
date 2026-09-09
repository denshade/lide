#!/usr/bin/env sh
set -eu

cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

mkdir -p out
echo Compiling Lide...
javac --release 21 -d out -sourcepath src src/lide/*.java

mkdir -p out/lide/icons
if ls src/lide/icons/*.png >/dev/null 2>&1; then
  cp src/lide/icons/*.png out/lide/icons/
fi

echo Build OK.
