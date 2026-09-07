package com.elvarg.game.world.gen;

import java.util.HashSet;
import java.util.Set;

/**
 * Where the island lives in the world's coordinate space.
 *
 * EverGielinor does not add regions to map_index; it overwrites the map files of
 * regions that are already in it. This class fixes which ones. The block was
 * chosen because all 144 of its regions exist in map_index, none of their map
 * file ids is shared with any region outside the block, and the block is far from
 * the original game's populated areas - so a generated world can be installed and
 * rolled back without touching anything else.
 *
 * @author EverGielinor world generator
 */
public final class IslandLayout {

    /** South-west corner of the reserved block, in region coordinates. */
    public static final int BLOCK_REGION_X = 18;
    public static final int BLOCK_REGION_Y = 39;
    /** The reserved block is square, in regions. */
    public static final int BLOCK_REGIONS = 12;

    /** The overworld island occupies the south-west corner of the block. */
    public static final int ISLAND_REGIONS = 10;
    /** Island side length in tiles. */
    public static final int SIZE = ISLAND_REGIONS * 64;

    /** World tile coordinate of the island's south-west corner. */
    public static final int ORIGIN_X = BLOCK_REGION_X * 64;
    public static final int ORIGIN_Y = BLOCK_REGION_Y * 64;

    private IslandLayout() {
    }

    /** Region ids covered by the overworld island. */
    public static Set<Integer> overworldRegions() {
        Set<Integer> ids = new HashSet<>();
        for (int rx = 0; rx < ISLAND_REGIONS; rx++) {
            for (int ry = 0; ry < ISLAND_REGIONS; ry++) {
                ids.add(regionId(BLOCK_REGION_X + rx, BLOCK_REGION_Y + ry));
            }
        }
        return ids;
    }

    /**
     * Region ids reserved for underground content. These are the parts of the
     * block the island does not cover: a two-region eastern column and a
     * two-region northern strip.
     *
     * Dungeons are built as ordinary terrain in their own regions rather than on
     * upper planes above the island, because the client draws every plane at or
     * below the player's, and a dungeon on plane 1 would show the overworld
     * through its floor.
     */
    public static java.util.List<Integer> dungeonRegions() {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        for (int rx = 0; rx < BLOCK_REGIONS; rx++) {
            for (int ry = 0; ry < BLOCK_REGIONS; ry++) {
                if (rx < ISLAND_REGIONS && ry < ISLAND_REGIONS) {
                    continue;
                }
                ids.add(regionId(BLOCK_REGION_X + rx, BLOCK_REGION_Y + ry));
            }
        }
        return ids;
    }

    /** Every region the generator writes to. */
    public static Set<Integer> allRegions() {
        Set<Integer> ids = overworldRegions();
        ids.addAll(dungeonRegions());
        return ids;
    }

    public static int regionId(int regionX, int regionY) {
        return (regionX << 8) | regionY;
    }

    /** World x for an island-local tile x. */
    public static int worldX(int localX) {
        return ORIGIN_X + localX;
    }

    /** World y for an island-local tile y. */
    public static int worldY(int localY) {
        return ORIGIN_Y + localY;
    }

    public static boolean inBounds(int localX, int localY) {
        return localX >= 0 && localX < SIZE && localY >= 0 && localY < SIZE;
    }
}
