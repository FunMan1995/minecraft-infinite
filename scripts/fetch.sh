#!/usr/bin/env bash
# Download Paper, Geyser-Spigot, Floodgate, and ViaVersion into data/.
set -euo pipefail
# shellcheck source=common.sh
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"
load_env

mkdir -p "$PLUGINS" "$DATA/logs"

python3 - "$DATA" "$PLUGINS" "$USER_AGENT" "$PAPER_VERSION" "$PAPER_BUILD" \
  "$GEYSER_VERSION" "$FLOODGATE_VERSION" "$VIAVERSION" <<'PY'
import hashlib, json, os, sys, urllib.request

data, plugins, ua, paper_ver, paper_build, geyser_ver, floodgate_ver, via = sys.argv[1:]
headers = {"User-Agent": ua}

def get(url):
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=120) as resp:
        return resp.read()

def get_json(url):
    return json.loads(get(url).decode())

def download(url, dest, sha256=None):
    print(f"→ {os.path.basename(dest)}")
    body = get(url)
    if sha256:
        got = hashlib.sha256(body).hexdigest()
        if got.lower() != sha256.lower():
            raise SystemExit(f"sha256 mismatch for {dest}: {got} != {sha256}")
    tmp = dest + ".part"
    with open(tmp, "wb") as f:
        f.write(body)
    os.replace(tmp, dest)
    print(f"  {len(body)} bytes -> {dest}")

# --- Paper (fill.papermc.io v3; api.papermc.io/v2 is gone) ---
builds = get_json(
    f"https://fill.papermc.io/v3/projects/paper/versions/{paper_ver}/builds"
)
if not isinstance(builds, list) or not builds:
    raise SystemExit(f"No Paper builds for {paper_ver}: {builds!r}")

def is_stable(b):
    return str(b.get("channel", "")).upper() == "STABLE"

if paper_build == "latest":
    chosen = next((b for b in builds if is_stable(b)), builds[0])
else:
    want = int(paper_build)
    chosen = next((b for b in builds if b.get("id") == want), None)
    if chosen is None:
        raise SystemExit(f"Paper build {want} not found for {paper_ver}")

dl = (chosen.get("downloads") or {}).get("server:default") or {}
url = dl.get("url")
if not url:
    raise SystemExit(f"Paper download URL missing: {chosen}")
checksums = dl.get("checksums") if isinstance(dl.get("checksums"), dict) else {}
sha = checksums.get("sha256") or dl.get("sha256")
print(f"Paper {paper_ver} build {chosen.get('id')} ({chosen.get('channel')})")
download(url, os.path.join(data, "paper.jar"), sha)

# --- Geyser-Spigot ---
gver = geyser_ver
gurl_meta = (
    f"https://download.geysermc.org/v2/projects/geyser/versions/{gver}/builds/latest"
)
gmeta = get_json(gurl_meta)
gdl = (gmeta.get("downloads") or {}).get("spigot") or {}
gsha = gdl.get("sha256")
gfile = os.path.join(plugins, "Geyser-Spigot.jar")
print(f"Geyser {gmeta.get('version')} build {gmeta.get('build')}")
download(
    f"https://download.geysermc.org/v2/projects/geyser/versions/{gmeta.get('version')}/builds/{gmeta.get('build')}/downloads/spigot",
    gfile,
    gsha,
)

# --- Floodgate-Spigot ---
fmeta = get_json(
    f"https://download.geysermc.org/v2/projects/floodgate/versions/{floodgate_ver}/builds/latest"
)
fdl = (fmeta.get("downloads") or {}).get("spigot") or {}
fsha = fdl.get("sha256")
print(f"Floodgate {fmeta.get('version')} build {fmeta.get('build')}")
download(
    f"https://download.geysermc.org/v2/projects/floodgate/versions/{fmeta.get('version')}/builds/{fmeta.get('build')}/downloads/spigot",
    os.path.join(plugins, "floodgate-spigot.jar"),
    fsha,
)

# --- ViaVersion (optional; older Java clients onto this Paper version) ---
if via not in ("0", "false", "no"):
    versions = get_json(
        "https://api.modrinth.com/v2/project/viaversion/version?loaders=%5B%22paper%22%5D"
    )
    rel = next(
        (
            v
            for v in versions
            if v.get("version_type") == "release"
            and paper_ver in (v.get("game_versions") or [])
        ),
        None,
    )
    if rel is None:
        rel = next((v for v in versions if v.get("version_type") == "release"), None)
    if rel is None:
        raise SystemExit("No ViaVersion release found on Modrinth")
    files = rel.get("files") or []
    prim = next((f for f in files if f.get("primary")), files[0])
    print(f"ViaVersion {rel.get('version_number')}")
    hashes = prim.get("hashes") if isinstance(prim.get("hashes"), dict) else {}
    download(prim["url"], os.path.join(plugins, "ViaVersion.jar"), hashes.get("sha256"))

print("Artifacts ready.")
PY
