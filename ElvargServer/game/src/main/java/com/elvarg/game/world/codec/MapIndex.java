package com.elvarg.game.world.codec;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The region-to-file table shared by the client cache and the server's
 * {@code data/clipping} mirror. Each entry maps a region id to the ids of its
 * terrain and object files.
 *
 * EverGielinor deliberately does <em>not</em> add entries here. Generated regions
 * reuse the file ids of regions that already exist, so the table is identical
 * before and after generation and the client needs no cache-index rebuild. The
 * generator only overwrites the file payloads those ids point at.
 *
 * @author EverGielinor world generator
 */
public final class MapIndex {

    private final Map<Integer, int[]> entries = new HashMap<>();

    private MapIndex() {
    }

    public static MapIndex load(Path file) throws IOException {
        MapIndex index = new MapIndex();
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            int count = in.readUnsignedShort();
            for (int i = 0; i < count; i++) {
                int regionId = in.readUnsignedShort();
                int terrainFile = in.readUnsignedShort();
                int objectFile = in.readUnsignedShort();
                index.entries.put(regionId, new int[]{terrainFile, objectFile});
            }
        }
        return index;
    }

    public boolean contains(int regionId) {
        return entries.containsKey(regionId);
    }

    public int terrainFile(int regionId) {
        int[] e = entries.get(regionId);
        if (e == null) {
            throw new IllegalArgumentException("region " + regionId + " is not in map_index");
        }
        return e[0];
    }

    public int objectFile(int regionId) {
        int[] e = entries.get(regionId);
        if (e == null) {
            throw new IllegalArgumentException("region " + regionId + " is not in map_index");
        }
        return e[1];
    }

    public int size() {
        return entries.size();
    }

    /**
     * Verifies that no file id used by the given regions is also used by a region
     * outside that set. Overwriting a shared file would silently corrupt unrelated
     * parts of the map, so the generator refuses to run if this fails.
     *
     * @return the region ids outside {@code regionIds} that would be damaged
     */
    public java.util.Set<Integer> findCollateralRegions(java.util.Set<Integer> regionIds) {
        java.util.Set<Integer> claimed = new java.util.HashSet<>();
        for (int regionId : regionIds) {
            int[] e = entries.get(regionId);
            if (e == null) {
                throw new IllegalArgumentException("region " + regionId + " is not in map_index");
            }
            claimed.add(e[0]);
            claimed.add(e[1]);
        }
        java.util.Set<Integer> damaged = new java.util.HashSet<>();
        for (Map.Entry<Integer, int[]> entry : entries.entrySet()) {
            if (regionIds.contains(entry.getKey())) {
                continue;
            }
            if (claimed.contains(entry.getValue()[0]) || claimed.contains(entry.getValue()[1])) {
                damaged.add(entry.getKey());
            }
        }
        return damaged;
    }
}
