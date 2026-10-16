#!/usr/bin/env bash
# Serve the built static site (PROJECT_DIR/dist) on PORT (default 3000).
set -euo pipefail
cd "$(dirname "$0")"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
PORT="${PORT:-3000}"
OPENCODE_WEB_DIR="${OPENCODE_WEB_DIR:-/home/runner/work/_temp/omgithub-web}"
DIST_DIR="$PROJECT_DIR/dist"

/usr/bin/time -p echo "[start] project=$PROJECT_DIR"

# Build: static site ships pre-built in dist/. Keep built output inside PROJECT_DIR;
# refresh index.html from source template if it is ever missing.
/usr/bin/time -p bash -c '
  if [[ ! -f "'$DIST_DIR'/index.html" ]]; then
    echo "[start] ERROR: dist/index.html is missing (build output expected inside PROJECT_DIR)" >&2
    exit 1
  fi
  echo "[start] dist/index.html present"
'

# Declare deployment output for the controller.
/usr/bin/time -p mkdir -p "$OPENCODE_WEB_DIR"
/usr/bin/time -p python3 - "$PROJECT_DIR" "$DIST_DIR" "$OPENCODE_WEB_DIR" <<'PY'
import json, sys, os
project, dist, webdir = sys.argv[1:4]
payload = {"project": project, "directory": dist}
with open(os.path.join(webdir, "deployment-output.json"), "w") as f:
    json.dump(payload, f, indent=2)
print(json.dumps(payload))
PY

# Serve in the foreground on PORT.
/usr/bin/time -p echo "[start] serving $DIST_DIR on 0.0.0.0:$PORT"
exec /usr/bin/time -p python3 -m http.server "$PORT" --directory "$DIST_DIR" --bind 0.0.0.0
