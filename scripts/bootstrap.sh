#!/usr/bin/env bash
# Install a local JDK 25 if missing (Termux: pkg). Does not use Docker.
set -euo pipefail
# shellcheck source=common.sh
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
load_env

if bin="$(require_java_25 2>/dev/null)"; then
  echo "Java OK: $bin ($("$bin" -version 2>&1 | head -1))"
else
  if is_termux; then
    echo "Installing openjdk-25 via pkg..."
    pkg install -y openjdk-25
  else
    echo "Install JDK 25+ yourself, then re-run. Paper 26.2 will not start on older Java." >&2
    exit 1
  fi
  require_java_25 >/dev/null
  echo "Java OK: $(find_java)"
fi

if [ ! -f "$ROOT/.env" ]; then
  cp "$ROOT/.env.example" "$ROOT/.env"
  echo "Wrote $ROOT/.env — set EULA=true after you agree to the Minecraft EULA."
fi

"$ROOT/scripts/fetch.sh"
"$ROOT/scripts/apply-config.sh"
echo
echo "Ready. Agree to the EULA in .env if you have not, then:"
echo "  $ROOT/scripts/start.sh"
