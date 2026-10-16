#!/usr/bin/env bash
# Capture desktop and mobile screenshots of CAPTURE_URL into CAPTURE_DIR.
# Exits 75 for temporary navigation/browser infrastructure failures, 1 for defects.
set -euo pipefail
cd "$(dirname "$0")"
RUNTIME_P="${HOME}/.local/share/omgithub-playwright"
CAPTURE_URL="${CAPTURE_URL:?Set CAPTURE_URL}"
CAPTURE_DIR="${CAPTURE_DIR:?Set CAPTURE_DIR}"

if [[ ! -d "${XDG_DATA_HOME:-$HOME/.local/share}/omgithub-playwright/node_modules/playwright" ]]; then
  echo 'Playwright runtime missing; installing (picture-perfect setup).' >&2
  mkdir -p "$RUNTIME_P"
  npm install --prefix "$RUNTIME_P" --no-save --no-package-lock playwright >/dev/null 2>&1 || true
  "$RUNTIME_P/node_modules/.bin/playwright" install chromium >/dev/null 2>&1 || true
fi

if ! (pgrep -x Xvfb >/dev/null 2>&1); then
  echo 'Xvfb missing; starting one for capture.' >&2
  mkdir -p "$RUNTIME_P"
  : > "$RUNTIME_P/display"
  bash -c 'nohup Xvfb -displayfd 3 -screen 0 1440x900x24 -nolisten tcp 3>"$1/display" >"$1/xvfb.log" 2>&1 </dev/null & echo $! >"$1/xvfb.pid"' _ "$RUNTIME_P" || true
  timeout 25 bash -c 'until test -s "$1/display"; do sleep 0.1; done' _ "$RUNTIME_P" || true
fi

if [[ -z "${DISPLAY:-}" && -s "$RUNTIME_P/display" ]]; then
  export DISPLAY=":$(cat "$RUNTIME_P/display")"
fi

capture_start="$(date +%s)"
curl_probe() {
  /usr/bin/time -p curl --fail --silent --max-time 5 --output /dev/null "$CAPTURE_URL" || true
}
curl_probe

rc=0
/usr/bin/time -p node scripts/capture.mjs || rc=$?
curl_probe
capture_end="$(date +%s)"
printf '#timing capture.sh total: %ss\n' "$((capture_end - capture_start))" >&2
exit $rc
