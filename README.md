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

Hardcore on playable seeds. Respawn and first join land at **seed 0**, the hub: **PvP off**, **full saturation**, **no build/edit except operators**. Each seed is **one vanilla world save** named by index (`0/`, `5/`, …). Overworld, Nether, and End live inside that folder as `dimensions/minecraft/{overworld,the_nether,the_end}` — the original scheme, not three sibling packs and not fake biomes. Seed 0 is a committed blank hub (`worlds/0/`, copied into `data/0` on first setup). Deaths drop a **gravestone** chest that vanishes when emptied. `/wild` (from spawn) rolls a random seed. `/sethome`, `/home`, `/tpa` work only while you are **not combat tagged**.

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
