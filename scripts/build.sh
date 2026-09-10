#!/usr/bin/env bash
# Build MinecraftInfinite.jar and copy it into data/plugins.
set -euo pipefail
# shellcheck source=common.sh
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

JAVA_BIN="$(require_java_25)"
export JAVA_HOME="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"

cd "$ROOT"
chmod +x "$ROOT/gradlew"
"$ROOT/gradlew" --no-daemon :plugin:jar

mkdir -p "$PLUGINS"
jar="$(ls -1 "$ROOT/plugin/build/libs"/MinecraftInfinite-*.jar | tail -1)"
cp -f "$jar" "$PLUGINS/MinecraftInfinite.jar"
echo "Installed $jar -> $PLUGINS/MinecraftInfinite.jar"
