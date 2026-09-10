#!/usr/bin/env bash
set -euo pipefail
# shellcheck source=common.sh
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
load_env

if server_running; then
  echo "running pid=$(cat "$PID_FILE") java=${JAVA_PORT} bedrock=${BEDROCK_PORT}"
  exit 0
fi
echo "stopped"
exit 1
