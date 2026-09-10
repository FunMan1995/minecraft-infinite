# Minecraft Infinite

Native **Paper 26.2** server (Java + Bedrock via Geyser/Floodgate) with stacked, non-Euclidean realms. No Docker. No custom terrain — vanilla generation plus edge/height portals.

Each seed is its own world, capped at a **float32-safe** size so Bedrock/Geyser and Java cameras do not drift. Walking off an edge does not wrap the same map; it enters the **next seed** of the same dimension.

## Topology

Minecraft **X/Z** = the ground plane (your “X and Y”). Minecraft **Y** = up (your “Z through the dimensions”).

```
        ↑  top of The End  →  Nether floor of seed+1   (see-through)
┌─────────────────────────┐
│  The End           ±B   │  same size as Overworld
├────── bedrock ──────────┤  walk through (see-through)
│  Overworld         ±B   │
├──── BLACK / PORTAL ─────┤  not walkable — vanilla nether portals only
│  Nether          ±B/8   │  8:1 so nether travel stays inside this seed
└─────────────────────────┘
        ↓  nether floor  →  End ceiling of seed−1      (see-through)
```

Horizontal helix (same dimension):

| Leave seed N | Enter |
| --- | --- |
| +X or +Z rim | −X or −Z of **seed N+1** |
| −X or −Z rim | +X or +Z of **seed N−1** |

See-through: the playable rim streams the next seed’s chunks past the border (vanilla + Bedrock packets). The Overworld↔Nether face is **black** and only crosses with a nether portal.

**Sideways** seed travel (the ground plane — Minecraft X and Z) is locked until **that seed’s Ender Dragon is dead**, or you are holding a **dragon egg**. **Vertical** travel through the stack (Minecraft Y) stays open so a griefed End cannot trap you.

Hardcore is a **locked master rule**. Death is spectator; items go to a gravestone. After a **wall-clock** wait (default **24h** on master, ticks while offline) you get a spectator **Reincarnate** item (`/reincarnate`). Subs may **shorten** that wait only for deaths on **their** claimed seeds. Deaths on master blocks, or visitors who transferred in from master, keep the 24h timer — same kind of lock as sideways seed travel without a dragon/egg.

The host has **three world trees**. A seed lives in exactly one of them; that location decides whether server mods load and whether client mods are handed across the border.

| Folder | What | Mods |
| --- | --- | --- |
| `vanilla/<seed>/` | Master rules, hub, unclaimed seeds | none extra |
| `subs/<server-name>/` | Named subserver | `server-mods/` + `client-mods/` + `worlds/<seed>/` |
| `kits/<player-uuid>/` | Client-only kit | `client-mods/` only + `worlds/<seed>/` |

`/sub import <name> vanilla\|modded` needs a server name. `/kit claim` binds the current seed to **your UUID** (no server name, no server-mods). Crossing offers a download of that tree’s client-mods instead of a corrupt-pack warning.

## Float cap

float32 ULP at magnitude `2^e` is `2^(e−23)`. We require ULP ≤ `1/16` block at the rim, so `|coord| < 2^19 = 524288`. Playable half-extent is that minus the see-through overhang, aligned to 8 (nether scale). Override with `border-half` in `plugin/src/main/resources/config.yml`.

## Run

```bash
cd ~/code/projects/minecraft-server
cp .env.example .env          # EULA=true
./scripts/bootstrap.sh        # JDK 25 if needed, Paper + Geyser + Floodgate
./scripts/build.sh            # MinecraftInfinite.jar
./scripts/start.sh
```

In-game: `/infinite` prints seed index, dimension, and border (more commands later).

| Client | Address |
| --- | --- |
| Java | `IP:25565` TCP |
| Bedrock | `IP:19132` UDP |

## Layout

```
plugin/     Paper plugin (stack, portals, see-through, InfiniteApi)
config/     Paper / Geyser / Floodgate templates
scripts/    bootstrap, fetch, build, start, stop
data/       runtime (gitignored)
```

`InfiniteApi` is registered as a Bukkit service for the command pack.
