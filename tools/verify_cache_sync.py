#!/usr/bin/env python3
"""
Phase 0 proof #2 - the client cache and the server share one map index.

The client resolves map files through a map_index stored inside its own cache
(idx0 archive 5, "versionlist"), then loads the .dat blobs from idx4. The server
reads a separate copy from data/clipping/map_index. If the two ever disagree,
object interaction desynchronises: the client draws one object and the server
validates against another.

This script reads the client's copy straight out of main_file_cache.dat and
diffs it against the server's.

Run from the repository root:  python3 tools/verify_cache_sync.py
"""
import bz2
import os
import struct
import sys

CACHE = "ElvargClient/Cache/"
SERVER_INDEX = "ElvargServer/data/clipping/map_index"

BLOCK = 520
HEADER = 8
VERSIONLIST_ARCHIVE = 5


def read_cache_file(index_no, file_id):
    """Reassemble one file from the 317-style .dat/.idx block chain."""
    with open(CACHE + f"main_file_cache.idx{index_no}", "rb") as idx:
        idx.seek(file_id * 6)
        header = idx.read(6)
    size = int.from_bytes(header[0:3], "big")
    block = int.from_bytes(header[3:6], "big")

    out = bytearray()
    chunk = 0
    remaining = size
    with open(CACHE + "main_file_cache.dat", "rb") as dat:
        while remaining > 0:
            dat.seek(block * BLOCK)
            buf = dat.read(BLOCK)
            got_file = int.from_bytes(buf[0:2], "big")
            got_chunk = int.from_bytes(buf[2:4], "big")
            nxt = int.from_bytes(buf[4:7], "big")
            got_index = buf[7]
            if (got_file, got_chunk, got_index) != (file_id, chunk, index_no + 1):
                raise ValueError(
                    f"block chain corrupt at block {block}: "
                    f"expected ({file_id},{chunk},{index_no + 1}), "
                    f"got ({got_file},{got_chunk},{got_index})"
                )
            take = min(BLOCK - HEADER, remaining)
            out += buf[HEADER:HEADER + take]
            remaining -= take
            chunk += 1
            block = nxt
    return bytes(out)


def name_hash(name):
    h = 0
    for ch in name.upper():
        h = (h * 61 + ord(ch) - 32) & 0xFFFFFFFF
    return h


def read_jag_archive(data):
    """Decode a .jag archive into {name_hash: bytes}."""
    uncompressed = int.from_bytes(data[0:3], "big")
    compressed = int.from_bytes(data[3:6], "big")
    pos = 6
    whole_archive_compressed = uncompressed != compressed
    if whole_archive_compressed:
        data = bz2.decompress(b"BZh1" + data[6:])
        pos = 0

    count = int.from_bytes(data[pos:pos + 2], "big")
    pos += 2
    meta = []
    for _ in range(count):
        meta.append((
            int.from_bytes(data[pos:pos + 4], "big"),       # name hash
            int.from_bytes(data[pos + 4:pos + 7], "big"),   # uncompressed
            int.from_bytes(data[pos + 7:pos + 10], "big"),  # compressed
        ))
        pos += 10

    files = {}
    offset = pos
    for nh, _unc, comp in meta:
        blob = data[offset:offset + comp]
        offset += comp
        files[nh] = blob if whole_archive_compressed else bz2.decompress(b"BZh1" + blob)
    return files


def parse_map_index(data):
    count = struct.unpack(">H", data[:2])[0]
    return {
        struct.unpack(">HHH", data[2 + i * 6: 8 + i * 6])[0]:
        struct.unpack(">HHH", data[2 + i * 6: 8 + i * 6])[1:]
        for i in range(count)
    }


def main():
    if not os.path.isdir(CACHE):
        sys.exit(f"run from the repository root; {CACHE} not found")

    versionlist = read_jag_archive(read_cache_file(0, VERSIONLIST_ARCHIVE))
    client = parse_map_index(versionlist[name_hash("map_index")])
    server = parse_map_index(open(SERVER_INDEX, "rb").read())

    print(f"client map_index entries   {len(client)}")
    print(f"server map_index entries   {len(server)}")

    only_client = set(client) - set(server)
    only_server = set(server) - set(client)
    mismatched = [r for r in set(client) & set(server) if client[r] != server[r]]

    print(f"regions only in client     {len(only_client)}")
    print(f"regions only in server     {len(only_server)}")
    print(f"file-id disagreements      {len(mismatched)}")

    in_sync = not (only_client or only_server or mismatched)
    print("\nclient/server map data:    " + ("IN SYNC" if in_sync else "OUT OF SYNC"))
    if not in_sync:
        for r in sorted(mismatched)[:20]:
            print(f"  region {r}: client={client[r]} server={server[r]}")
    return 0 if in_sync else 1


if __name__ == "__main__":
    sys.exit(main())
