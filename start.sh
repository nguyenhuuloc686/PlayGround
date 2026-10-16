#!/usr/bin/env bash
# Build the static 2D game and serve it in the foreground on PORT (default 3000).
set -euo pipefail
cd "$(dirname "$0")"
PROJECT_DIR="$(pwd)"
WEB_META_DIR="${OPENCODE_WEB_DIR:-/home/runner/work/_temp/omgithub-web}"
DIST_DIR="$PROJECT_DIR/dist"

timed() {
  local t0 t1
  t0="$(date +%s.%N)"
  "$@"
  local rc=$?
  t1="$(date +%s.%N)"
  printf '#timing %s: %.3fs\n' "$*" "$(awk "BEGIN{print $t1-$t0}")" >&2
  return $rc
}

timed test -d "$PROJECT_DIR/src"
timed rm -rf "$DIST_DIR"
timed mkdir -p "$DIST_DIR"
timed cp src/index.html src/style.css src/game.js "$DIST_DIR/"
timed node --check "$DIST_DIR/game.js"
timed node --check scripts/serve.mjs
mkdir -p "$WEB_META_DIR"
timed env PROJECT_DIR_ABS="$PROJECT_DIR" DIST_DIR_ABS="$DIST_DIR" WEB_META_DIR_ABS="$WEB_META_DIR" \
  node -e '
    const { writeFileSync, mkdirSync } = require("node:fs");
    const path = require("node:path");
    mkdirSync(process.env.WEB_META_DIR_ABS, { recursive: true });
    writeFileSync(path.join(process.env.WEB_META_DIR_ABS, "deployment-output.json"), JSON.stringify({
      project: process.env.PROJECT_DIR_ABS,
      directory: process.env.DIST_DIR_ABS
    }));
  '
timed test -f "$WEB_META_DIR/deployment-output.json"

export PORT="${PORT:-3000}"
echo "Serving $DIST_DIR on port $PORT" >&2
exec /usr/bin/time -p node scripts/serve.mjs "$DIST_DIR"
