#!/usr/bin/env bash
# Copy committed configs into data/ and refresh ports/MOTD from .env.
set -euo pipefail
# shellcheck source=common.sh
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
load_env

mkdir -p "$DATA/plugins/Geyser-Spigot" "$DATA/plugins/floodgate" "$DATA/logs"

python3 - "$ROOT" "$DATA" "$JAVA_PORT" "$BEDROCK_PORT" "$MAX_PLAYERS" \
  "$MOTD" "$VIEW_DISTANCE" "$SIMULATION_DISTANCE" "$ONLINE_MODE" <<'PY'
import pathlib, re, shutil, sys, uuid

root, data, java_port, bedrock_port, max_players, motd, view, sim, online = sys.argv[1:]
cfg = pathlib.Path(root) / "config"
dest = pathlib.Path(data)

def upsert_props(path, updates):
    existing = {}
    order = []
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.strip() and not line.lstrip().startswith("#") and "=" in line:
                k, _, v = line.partition("=")
                existing[k] = v
                order.append(("kv", k))
            else:
                order.append(("raw", line))
    for k, v in updates.items():
        if k not in existing:
            order.append(("kv", k))
        existing[k] = v
    out = []
    seen = set()
    for kind, val in order:
        if kind == "raw":
            out.append(val)
        else:
            if val in seen:
                continue
            seen.add(val)
            out.append(f"{val}={existing[val]}")
    path.write_text("\n".join(out) + "\n", encoding="utf-8")

# server.properties: seed once from template, then keep .env keys current.
props = dest / "server.properties"
if not props.exists():
    props.write_text((cfg / "server.properties").read_text(encoding="utf-8"), encoding="utf-8")
upsert_props(props, {
    "server-port": java_port,
    "max-players": max_players,
    "motd": motd,
    "view-distance": view,
    "simulation-distance": sim,
    "online-mode": online.lower(),
    "hardcore": "true",
    "difficulty": "hard",
    "gamemode": "survival",
    "level-name": "0",
    "pvp": "true",
    "spawn-protection": "0",
})

geyser_dst = dest / "plugins" / "Geyser-Spigot" / "config.yml"
if not geyser_dst.exists():
    src = (cfg / "geyser.yml").read_text(encoding="utf-8")
    src = re.sub(r"(?m)^(  uuid: ).*", rf"\g<1>{uuid.uuid4()}", src)
    geyser_dst.write_text(src, encoding="utf-8")

text = geyser_dst.read_text(encoding="utf-8")
text = re.sub(r"(?m)^(  port: )\d+", rf"\g<1>{bedrock_port}", text, count=1)
text = re.sub(r'(?m)^(  motd1: ).*', rf'\1"{motd}"', text, count=1)
text = re.sub(r"(?m)^(  auth-type: ).*", r"\1floodgate", text, count=1)
text = re.sub(r"(?m)^(  clone-remote-port: ).*", r"\1false", text, count=1)
if re.search(r"(?m)^  address:", text):
    text = re.sub(r"(?m)^  address:.*", "  address: 0.0.0.0", text, count=1)
else:
    text = re.sub(r"(?m)^bedrock:\n", "bedrock:\n  address: 0.0.0.0\n", text, count=1)
geyser_dst.write_text(text, encoding="utf-8")

flood_dst = dest / "plugins" / "floodgate" / "config.yml"
if not flood_dst.exists():
    flood_dst.write_text((cfg / "floodgate.yml").read_text(encoding="utf-8"), encoding="utf-8")

bukkit_src = cfg / "bukkit.yml"
if bukkit_src.exists():
    shutil.copy(bukkit_src, dest / "bukkit.yml")

hub_src = pathlib.Path(root) / "worlds" / "0"
hub_dst = dest / "0"
if hub_src.exists() and not (hub_dst / "level.dat").exists():
    shutil.copytree(hub_src, hub_dst, dirs_exist_ok=True)
    print(f"Installed blank hub world -> {hub_dst}")

print(f"Config applied: Java TCP {java_port}, Bedrock UDP {bedrock_port}")
PY
