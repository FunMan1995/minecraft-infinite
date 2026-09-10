#!/usr/bin/env bash
# Graceful stop (SIGTERM). Paper saves worlds on TERM.
set -euo pipefail
# shellcheck source=common.sh
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
load_env

if ! server_running; then
  echo "Server is not running."
  exit 0
fi

pid="$(cat "$PID_FILE")"
echo "Stopping pid $pid ..."
kill -TERM "$pid" 2>/dev/null || true

for _ in $(seq 1 40); do
  if ! kill -0 "$pid" 2>/dev/null; then
    rm -f "$PID_FILE"
    echo "Stopped."
    exit 0
  fi
  sleep 1
done

echo "Still running after 40s, sending KILL." >&2
kill -KILL "$pid" 2>/dev/null || true
rm -f "$PID_FILE"
