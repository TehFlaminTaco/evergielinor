#!/usr/bin/env python3
"""
Phase 0 proof #1 - the Elvarg landscape format is fully understood.

Decodes every terrain file referenced by the SERVER's map_index using an
independent parser and asserts each is consumed byte-exactly. Then classifies
every 8x8 chunk by dominant underlay/overlay, which is the raw material for
biome-aware chunk stitching (opcode 241).

Run from the repository root:  python3 tools/verify_map_format.py
"""
import collections
import gzip
import os
import struct
import sys

CLIP = "ElvargServer/data/clipping/"

# Tile encoding, mirroring RegionManager.loadMapFiles:
#   0        -> end of tile
#   1        -> height byte follows, end of tile
#   2..49    -> overlay id byte follows (shape/rotation packed into the type)
#   50..81   -> tile settings, value = type - 49  (bit 1 blocked, bit 2 bridge)
#   82..     -> underlay, value = type - 81, no payload byte
BLOCKED = 1


def read_map_index(path):
    data = open(path, "rb").read()
    count = struct.unpack(">H", data[:2])[0]
    if len(data) != 2 + count * 6:
        raise ValueError(f"{path}: expected {2 + count * 6} bytes, got {len(data)}")
    return [struct.unpack(">HHH", data[2 + i * 6: 8 + i * 6]) for i in range(count)]


def decode_terrain(path):
    """Return (underlay, overlay, settings) for plane 0, or raise on a short read."""
    raw = gzip.decompress(open(path, "rb").read())
    und = [[0] * 64 for _ in range(64)]
    ovl = [[0] * 64 for _ in range(64)]
    setg = [[0] * 64 for _ in range(64)]
    i = 0
    for z in range(4):
        for x in range(64):
            for y in range(64):
                while True:
                    t = raw[i]; i += 1
                    if t == 0:
                        break
                    if t == 1:
                        i += 1          # height byte, which the server discards
                        break
                    if t <= 49:
                        o = raw[i]; i += 1
                        if z == 0:
                            ovl[x][y] = o
                    elif t <= 81:
                        if z == 0:
                            setg[x][y] = t - 49
                    else:
                        if z == 0:
                            und[x][y] = t - 81
    if i != len(raw):
        raise ValueError(f"{path}: consumed {i} of {len(raw)} bytes")
    return und, ovl, setg


def main():
    if not os.path.isdir(CLIP):
        sys.exit(f"run from the repository root; {CLIP} not found")

    entries = read_map_index(CLIP + "map_index")
    print(f"map_index entries          {len(entries)}")

    decoded = 0
    chunks = 0
    blocked_chunks = 0
    signatures = collections.Counter()

    for region_id, terrain_file, _object_file in entries:
        und, ovl, setg = decode_terrain(f"{CLIP}maps/{terrain_file}.dat")
        decoded += 1
        for cx in range(8):
            for cy in range(8):
                chunks += 1
                u = collections.Counter()
                o = collections.Counter()
                blocked = 0
                for x in range(cx * 8, cx * 8 + 8):
                    for y in range(cy * 8, cy * 8 + 8):
                        u[und[x][y]] += 1
                        if ovl[x][y]:
                            o[ovl[x][y]] += 1
                        if setg[x][y] & BLOCKED:
                            blocked += 1
                signatures[(u.most_common(1)[0][0],
                            o.most_common(1)[0][0] if o else 0)] += 1
                if blocked >= 48:
                    blocked_chunks += 1

    print(f"terrain files decoded      {decoded} (byte-exact, 0 failures)")
    print(f"8x8 chunks on plane 0      {chunks}")
    print(f"  >=75% blocked            {blocked_chunks}  (ocean / void)")
    print(f"  usable land chunks       {chunks - blocked_chunks}")
    print(f"distinct biome signatures  {len(signatures)}")
    print("\ntop chunk signatures (underlay, overlay) -> count")
    for (u, o), c in signatures.most_common(10):
        print(f"  und={u:<4} ovl={o:<4} {c:>7}")


if __name__ == "__main__":
    main()
