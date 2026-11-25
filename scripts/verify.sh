#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
python3 "$ROOT/scripts/test_weft.py"
python3 "$ROOT/scripts/weft.py" verify
