#!/usr/bin/env bash
# Shared helpers for fetch/start/stop. Sourced, not executed.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DATA="$ROOT/data"
PLUGINS="$DATA/plugins"
PID_FILE="$DATA/server.pid"
LOG_FILE="$DATA/logs/latest.log"
USER_AGENT="MinecraftInfinite/0.1.0 (https://github.com/FunMan1995/minecraft-infinite)"

load_env() {
  if [ -f "$ROOT/.env" ]; then
    set -a
    # shellcheck disable=SC1091
    . "$ROOT/.env"
    set +a
  fi
  EULA="${EULA:-false}"
  MEMORY="${MEMORY:-auto}"
  JAVA_PORT="${JAVA_PORT:-25565}"
  BEDROCK_PORT="${BEDROCK_PORT:-19132}"
  MAX_PLAYERS="${MAX_PLAYERS:-20}"
  MOTD="${MOTD:-Minecraft Infinite}"
  VIEW_DISTANCE="${VIEW_DISTANCE:-8}"
  SIMULATION_DISTANCE="${SIMULATION_DISTANCE:-6}"
  ONLINE_MODE="${ONLINE_MODE:-true}"
  PAPER_VERSION="${PAPER_VERSION:-26.2}"
  PAPER_BUILD="${PAPER_BUILD:-latest}"
  GEYSER_VERSION="${GEYSER_VERSION:-latest}"
  FLOODGATE_VERSION="${FLOODGATE_VERSION:-latest}"
  VIAVERSION="${VIAVERSION:-1}"
}

is_termux() {
  [ -n "${PREFIX:-}" ] && [ -d "/data/data/com.termux/files" ]
}

find_java() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    printf '%s\n' "${JAVA_HOME}/bin/java"
    return 0
  fi
  if command -v java >/dev/null 2>&1; then
    command -v java
    return 0
  fi
  local candidates=(
    "${PREFIX:-/usr}/lib/jvm/java-25-openjdk/bin/java"
    "${PREFIX:-/usr}/lib/jvm/java-21-openjdk/bin/java"
    /usr/lib/jvm/java-25-openjdk/bin/java
    /usr/lib/jvm/java-25-openjdk-amd64/bin/java
  )
  local c
  for c in "${candidates[@]}"; do
    if [ -x "$c" ]; then
      printf '%s\n' "$c"
      return 0
    fi
  done
  return 1
}

java_major() {
  local bin="$1"
  "$bin" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -1
}

require_java_25() {
  local bin
  if ! bin="$(find_java)"; then
    echo "Java 25+ not found." >&2
    if is_termux; then
      echo "Install it with:  pkg install openjdk-25" >&2
      echo "Or run:           $ROOT/scripts/bootstrap.sh" >&2
    else
      echo "Install a JDK 25+ and put java on PATH (Paper 26.2 requires it)." >&2
    fi
    return 1
  fi
  local major
  if ! major="$(java_major "$bin")"; then
    echo "Could not read Java version from $bin" >&2
    return 1
  fi
  if [ "$major" -lt 25 ]; then
    echo "Found Java $major at $bin — Paper 26.2 needs 25+." >&2
    if is_termux; then
      echo "Install it with:  pkg install openjdk-25" >&2
    fi
    return 1
  fi
  printf '%s\n' "$bin"
}

resolve_memory() {
  local spec="$1"
  if [ "$spec" != "auto" ]; then
    printf '%s\n' "$spec"
    return 0
  fi
  python3 - <<'PY'
import os
kb = 0
with open("/proc/meminfo", encoding="utf-8") as f:
    for line in f:
        if line.startswith("MemTotal:"):
            kb = int(line.split()[1])
            break
# ~20% of RAM, floor 1G, cap 4G — leaves headroom for the OS and Geyser.
mb = max(1024, min(4096, int(kb / 1024 * 0.20)))
print(f"{mb}M")
PY
}

server_running() {
  if [ ! -f "$PID_FILE" ]; then
    return 1
  fi
  local pid
  pid="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [ -z "$pid" ]; then
    return 1
  fi
  if kill -0 "$pid" 2>/dev/null; then
    return 0
  fi
  rm -f "$PID_FILE"
  return 1
}
