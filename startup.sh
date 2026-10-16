#!/usr/bin/env bash
# Once-per-worker prerequisites for start.sh / capture.sh.
set -euo pipefail
PW="$HOME/.local/share/omgithub-playwright"

/usr/bin/time -p bash -c '
  if ! grep -q chromium "'$PW'/linux.json" 2>/dev/null || [[ ! -d "$HOME/.cache/ms-playwright" ]]; then
    bash /home/runner/work/_temp/omgithub-runtime/scripts/setup-linux-playwright.sh
  fi
'
/usr/bin/time -p playwright-cli install-browser chromium
