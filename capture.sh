#!/usr/bin/env bash
# Capture CAPTURE_URL in a browser: desktop + mobile screenshots into CAPTURE_DIR.
# Exit 75 = transient navigation/browser infrastructure failure (retry later).
# Exit 1  = script or rendering defect.
set -u
cd "$(dirname "$0")"

CAPTURE_URL="${CAPTURE_URL:?Set CAPTURE_URL env.}"
CAPTURE_DIR="${CAPTURE_DIR:?Set CAPTURE_DIR env.}"
PW="$HOME/.local/share/omgithub-playwright"
SESSION="pg-capture"
export DISPLAY="${DISPLAY:-:$(cat "$PW/display" 2>/dev/null || echo 0)}"
export PLAYWRIGHT_MCP_OUTPUT_DIR="$PW/output"

timed() { echo "[capture] + $*"; /usr/bin/time -p "$@"; }
fail() {
  local code="$1"; shift
  echo "[capture] FAIL($code): $*" >&2
  timed playwright-cli -s=$SESSION close >/dev/null 2>&1 || true
  exit "$code"
}

/usr/bin/time -p mkdir -p "$CAPTURE_DIR"

# Open the exact URL.
RC=0
/usr/bin/time -p playwright-cli -s=$SESSION open "$CAPTURE_URL" > "$CAPTURE_DIR/open.log" 2>&1 || RC=$?
if [[ $RC -ne 0 ]]; then
  grep -qiE "ERR_CONNECTION|ERR_NAME|ERR_TIMED_OUT|net::|timeout|ECONNREFUSED|timed out|refused" "$CAPTURE_DIR/open.log" \
    && fail 75 "navigation/browser infrastructure failure" \
    || fail 1 "browser failed to open: $(tail -3 "$CAPTURE_DIR/open.log")"
fi

# Wait for rendered content (document ready + marker text exists).
for i in $(seq 1 30); do
  ready="$(/usr/bin/time -p playwright-cli -s=$SESSION eval "() => document.readyState" 2>/dev/null | tr -d '"' || true)"
  [[ "$ready" == *"complete"* ]] && break
  sleep 1
done
[[ "$ready" == *"complete"* ]] || fail 75 "page never reached readyState=complete (navigation infra)"

marker="$(/usr/bin/time -p playwright-cli -s=$SESSION --raw eval "() => (document.querySelector('h1')?.innerText || '') + '|' + (document.body?.innerText || '').length" 2>/dev/null || true)"
echo "[capture] marker=$marker"
title="$(/usr/bin/time -p playwright-cli -s=$SESSION --raw eval "() => document.querySelectorAll('h2').length" 2>/dev/null || echo 0)"
[[ "${title}" =~ ^[0-9]+$ ]] && [[ "${title}" -ge 4 ]] || fail 1 "rendering defect: page content missing ($title sections, marker=$marker)"

# Desktop view.
/usr/bin/time -p playwright-cli -s=$SESSION screenshot --filename="$CAPTURE_DIR/final-desktop.png" > /dev/null 2>&1 \
  || fail 1 "desktop screenshot failed"

# Mobile view.
/usr/bin/time -p playwright-cli -s=$SESSION resize 390 844 > /dev/null 2>&1 || true
/usr/bin/time -p playwright-cli -s=$SESSION reload > /dev/null 2>&1 || fail 75 "transient reload failure"
sleep 2
/usr/bin/time -p playwright-cli -s=$SESSION screenshot --filename="$CAPTURE_DIR/final-mobile.png" > /dev/null 2>&1 \
  || fail 1 "mobile screenshot failed"

# Close own browser; leave the app running.
/usr/bin/time -p playwright-cli -s=$SESSION close > /dev/null 2>&1

# Validate screenshots.
python3 - "$CAPTURE_DIR" <<'PY'
import sys, os
from struct import unpack
def png_size(p):
    with open(p, 'rb') as f:
        if f.read(8) != b'\x89PNG\r\n\x1a\n': return None
        return unpack('>II', f.read(20)[4:12])
bad = []
for name in ("final-desktop.png", "final-mobile.png"):
    p = os.path.join(sys.argv[1], name)
    size = png_size(p) if os.path.exists(p) else None
    if size is None or size[0] < 300: bad.append(name)
if bad: sys.exit(f"invalid screenshots: {bad}")
PY
[[ $? -eq 0 ]] || fail 1 "invalid screenshots"

echo "[capture] OK -> $CAPTURE_DIR/final-desktop.png, final-mobile.png"
exit 0
