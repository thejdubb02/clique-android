#!/usr/bin/env bash
# Build xterm.js (and the fit / unicode11 / canvas addons) from source and
# copy the UMD bundles into app/src/main/assets/vendor/.
#
# Pin matches the CLIque server vendor dir (clique/web/vendor/README.md):
#   @xterm/xterm 5.5.0
#   @xterm/addon-fit 0.10.0
#   @xterm/addon-unicode11 0.8.0
#   @xterm/addon-canvas 0.7.0
#
# Idempotent: exits 0 without touching the network when the vendor JS already
# exists and was built from this pin.

set -euo pipefail

PIN=5.5.0
REPO=https://github.com/xtermjs/xterm.js.git

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VENDOR="$ROOT/app/src/main/assets/vendor"
STAMP="$VENDOR/.built-from"
SRC="$ROOT/build/xterm.js-$PIN"
OUTPUTS=(xterm.js addon-fit.js addon-unicode11.js addon-canvas.js)

need_rebuild() {
  local f
  for f in "${OUTPUTS[@]}"; do
    if [[ ! -s "$VENDOR/$f" ]]; then
      return 0
    fi
  done
  if [[ ! -f "$STAMP" ]] || [[ "$(tr -d '[:space:]' < "$STAMP")" != "$PIN" ]]; then
    return 0
  fi
  return 1
}

if [[ "${FORCE:-}" != "1" ]] && ! need_rebuild; then
  echo "xterm.js $PIN already present under $VENDOR"
  exit 0
fi

for cmd in git npm; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "build-xterm.sh: $cmd is required" >&2
    exit 1
  fi
done

mkdir -p "$VENDOR" "$ROOT/build"

if [[ -d "$SRC/.git" ]]; then
  current="$(git -C "$SRC" describe --tags --exact-match 2>/dev/null || true)"
  if [[ "$current" != "$PIN" ]]; then
    echo "cached xterm.js is $current, replacing with $PIN"
    rm -rf "$SRC"
  fi
fi

if [[ ! -d "$SRC/.git" ]]; then
  rm -rf "$SRC"
  echo "cloning xterm.js $PIN"
  git clone --depth 1 --branch "$PIN" "$REPO" "$SRC"
fi

cd "$SRC"

if [[ ! -d node_modules ]]; then
  # --ignore-scripts skips node-pty compile and playwright browser download.
  # 5.5.0 ships yarn.lock, not package-lock.json.
  if [[ -f yarn.lock ]] && command -v yarn >/dev/null 2>&1; then
    echo "yarn install --frozen-lockfile --ignore-scripts"
    yarn install --frozen-lockfile --ignore-scripts
  elif [[ -f package-lock.json ]]; then
    echo "npm ci --ignore-scripts"
    npm ci --ignore-scripts --no-audit --no-fund
  else
    echo "npm install --ignore-scripts (no package-lock.json at tag $PIN)"
    npm install --ignore-scripts --no-audit --no-fund
  fi
fi

# tsconfig.all.json also builds image/ligatures/tests, which need extra
# addon-local deps. Compile the core and the three addons we ship.
# skipLibCheck: npm (no yarn.lock consumption) can resolve a minimatch that
# breaks @types/glob, which src/browser tests import.
node -e '
const fs = require("fs");
const p = "src/tsconfig-base.json";
const data = JSON.parse(fs.readFileSync(p, "utf8"));
data.compilerOptions = data.compilerOptions || {};
data.compilerOptions.skipLibCheck = true;
fs.writeFileSync(p, JSON.stringify(data, null, 2) + "\n");
'

echo "tsc core + addon-fit + addon-unicode11 + addon-canvas"
npx tsc -b src/common src/browser \
  addons/addon-fit/src addons/addon-unicode11/src addons/addon-canvas/src

# npm run package re-runs the full tsc (image/ligatures/tests). Call webpack
# on the projects we actually compiled.
echo "webpack UMD bundles"
npx webpack
for addon in addon-fit addon-unicode11 addon-canvas; do
  (cd "addons/$addon" && ../../node_modules/.bin/webpack)
done

test -s lib/xterm.js
test -s css/xterm.css
test -s addons/addon-fit/lib/addon-fit.js
test -s addons/addon-unicode11/lib/addon-unicode11.js
test -s addons/addon-canvas/lib/addon-canvas.js

cp -f lib/xterm.js "$VENDOR/xterm.js"
cp -f css/xterm.css "$VENDOR/xterm.css"
cp -f addons/addon-fit/lib/addon-fit.js "$VENDOR/addon-fit.js"
cp -f addons/addon-unicode11/lib/addon-unicode11.js "$VENDOR/addon-unicode11.js"
cp -f addons/addon-canvas/lib/addon-canvas.js "$VENDOR/addon-canvas.js"
printf '%s\n' "$PIN" > "$STAMP"

echo "installed xterm.js $PIN into $VENDOR"
