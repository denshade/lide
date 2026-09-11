#!/usr/bin/env sh
set -eu

cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

java -jar lib/ladle.jar release build.ini

echo "Done. Run with: java -jar build/lide.jar"
