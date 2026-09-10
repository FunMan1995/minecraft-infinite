#!/usr/bin/env python3
"""Write a blank vanilla 26.x world save for seed 0 (one folder, three dimensions)."""
from __future__ import annotations

import gzip
import struct
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HUB = ROOT / "worlds" / "0"
DATA_VERSION = 4903  # Minecraft 26.2


class Nbt:
    END = 0
    BYTE = 1
    SHORT = 2
    INT = 3
    LONG = 4
    FLOAT = 5
    DOUBLE = 6
    STRING = 8
    LIST = 9
    COMPOUND = 10

    @staticmethod
    def _str(s: str) -> bytes:
        b = s.encode("utf-8")
        return struct.pack(">H", len(b)) + b

    @classmethod
    def named(cls, tag: int, name: str, payload: bytes) -> bytes:
        return struct.pack(">B", tag) + cls._str(name) + payload

    @classmethod
    def byte(cls, name: str, v: int) -> bytes:
        return cls.named(cls.BYTE, name, struct.pack(">b", v))

    @classmethod
    def int(cls, name: str, v: int) -> bytes:
        return cls.named(cls.INT, name, struct.pack(">i", v))

    @classmethod
    def long(cls, name: str, v: int) -> bytes:
        return cls.named(cls.LONG, name, struct.pack(">q", v))

    @classmethod
    def double(cls, name: str, v: float) -> bytes:
        return cls.named(cls.DOUBLE, name, struct.pack(">d", v))

    @classmethod
    def string(cls, name: str, v: str) -> bytes:
        return cls.named(cls.STRING, name, cls._str(v))

    @classmethod
    def compound(cls, name: str, *children: bytes) -> bytes:
        return cls.named(cls.COMPOUND, name, b"".join(children) + bytes([cls.END]))

    @classmethod
    def empty_list(cls, name: str, child_type: int) -> bytes:
        return cls.named(cls.LIST, name, struct.pack(">Bi", child_type, 0))


def level_dat() -> bytes:
    now = int(time.time() * 1000)
    data = Nbt.compound(
        "Data",
        Nbt.int("DataVersion", DATA_VERSION),
        Nbt.int("version", 19133),
        Nbt.string("LevelName", "0"),
        Nbt.int("GameType", 0),
        Nbt.byte("hardcore", 0),
        Nbt.byte("Difficulty", 0),
        Nbt.byte("initialized", 1),
        Nbt.byte("allowCommands", 1),
        Nbt.int("SpawnX", 8),
        Nbt.int("SpawnY", 65),
        Nbt.int("SpawnZ", 8),
        Nbt.long("Time", 0),
        Nbt.long("DayTime", 0),
        Nbt.long("LastPlayed", now),
        Nbt.long("RandomSeed", 0),
        Nbt.compound(
            "Version",
            Nbt.int("Id", DATA_VERSION),
            Nbt.string("Name", "26.2"),
            Nbt.byte("Snapshot", 0),
        ),
        Nbt.compound("GameRules"),
    )
    root = struct.pack(">B", Nbt.COMPOUND) + Nbt._str("") + data + bytes([Nbt.END])
    return gzip.compress(root)


def main() -> None:
    for rel in (
        "dimensions/minecraft/overworld/region",
        "dimensions/minecraft/the_nether/region",
        "dimensions/minecraft/the_end/region",
        "datapacks/infinite-hub",
        "players/data",
    ):
        (HUB / rel).mkdir(parents=True, exist_ok=True)
        keep = HUB / rel / ".gitkeep"
        if rel.endswith("region") or rel.endswith("data"):
            keep.write_text("", encoding="utf-8")

    (HUB / "datapacks/infinite-hub/pack.mcmeta").write_text(
        """{
  "pack": {
    "description": "Minecraft Infinite hub — blank seed 0 (one vanilla save)",
    "min_format": [107, 1],
    "max_format": [107, 1]
  }
}
""",
        encoding="utf-8",
    )
    (HUB / "level.dat").write_bytes(level_dat())
    print(f"Wrote hub world {HUB} ({(HUB / 'level.dat').stat().st_size} byte level.dat)")


if __name__ == "__main__":
    main()
