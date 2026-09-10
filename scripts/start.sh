#!/usr/bin/env bash
# Start Paper in the foreground (default) or background (-d).
# Native JVM only — no Docker, no extra proxy process.
set -euo pipefail
# shellcheck source=common.sh
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
load_env

BACKGROUND=0
if [ "${1:-}" = "-d" ] || [ "${1:-}" = "--detach" ] || [ "${1:-}" = "--background" ]; then
  BACKGROUND=1
fi

if [ "${EULA,,}" != "true" ]; then
  echo "Set EULA=true in .env (copy .env.example) after you agree to https://aka.ms/MinecraftEULA" >&2
  exit 1
fi

if server_running; then
  echo "Already running (pid $(cat "$PID_FILE"))." >&2
  exit 1
fi

JAVA_BIN="$(require_java_25)"
MEM="$(resolve_memory "$MEMORY")"

mkdir -p "$DATA/logs" "$PLUGINS"
printf 'eula=true\n' > "$DATA/eula.txt"

if [ ! -f "$DATA/paper.jar" ] || [ ! -f "$PLUGINS/Geyser-Spigot.jar" ] || [ ! -f "$PLUGINS/floodgate-spigot.jar" ]; then
  echo "Missing server jars — fetching..."
  "$ROOT/scripts/fetch.sh"
fi
if [ ! -f "$PLUGINS/MinecraftInfinite.jar" ]; then
  echo "Building Minecraft Infinite plugin..."
  "$ROOT/scripts/build.sh"
fi
"$ROOT/scripts/apply-config.sh"

# Paper 26.2 recommended G1 flags (fill.papermc.io), no large pages.
# Plugin-mode Geyser uses a direct in-process connection (no extra TCP hop).
JVM_FLAGS=(
  "-Xms${MEM}"
  "-Xmx${MEM}"
  -XX:+AlwaysPreTouch
  -XX:+DisableExplicitGC
  -XX:+ParallelRefProcEnabled
  -XX:+PerfDisableSharedMem
  -XX:+UnlockExperimentalVMOptions
  -XX:+UseG1GC
  -XX:G1HeapRegionSize=8M
  -XX:G1HeapWastePercent=5
  -XX:G1MaxNewSizePercent=40
  -XX:G1MixedGCCountTarget=4
  -XX:G1MixedGCLiveThresholdPercent=90
  -XX:G1NewSizePercent=30
  -XX:G1RSetUpdatingPauseTimePercent=5
  -XX:G1ReservePercent=20
  -XX:InitiatingHeapOccupancyPercent=15
  -XX:MaxGCPauseMillis=200
  -XX:MaxTenuringThreshold=1
  -XX:SurvivorRatio=32
  -Dfile.encoding=UTF-8
  -Djava.awt.headless=true
)

echo "Java:    $JAVA_BIN"
echo "Heap:    $MEM"
echo "Java:    0.0.0.0:${JAVA_PORT}  (TCP)"
echo "Bedrock: 0.0.0.0:${BEDROCK_PORT} (UDP)"
echo "Data:    $DATA"

cd "$DATA"
if [ "$BACKGROUND" -eq 1 ]; then
  nohup "$JAVA_BIN" "${JVM_FLAGS[@]}" -jar "$DATA/paper.jar" --nogui \
    >> "$DATA/logs/console.log" 2>&1 &
  echo $! > "$PID_FILE"
  echo "Started pid $(cat "$PID_FILE"). Logs: $DATA/logs/latest.log"
  echo "Stop with: $ROOT/scripts/stop.sh"
else
  exec "$JAVA_BIN" "${JVM_FLAGS[@]}" -jar "$DATA/paper.jar" --nogui
fi
