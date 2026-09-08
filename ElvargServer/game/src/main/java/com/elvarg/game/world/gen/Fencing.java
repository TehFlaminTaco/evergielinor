package com.elvarg.game.world.gen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Lays fences along the verges of roads and lanes.
 *
 * A fence has to be a run to read as a fence. The first version scattered fence
 * models as free-standing scenery at random rotations, which is why the verges
 * came out as a litter of unconnected posts pointing in different directions:
 * ids like "Fence mid" only have a plain scenery model, so each one sat in the
 * middle of its own tile facing wherever the die landed.
 *
 * These ids are wall objects instead. Placed at landscape type 0 they sit on a
 * tile edge, so consecutive tiles along a verge join into a continuous line, and
 * every fence in a run faces the road it borders.
 *
 * @author EverGielinor world generator
 */
final class Fencing {

    /**
     * Fence materials with a straight wall model, checked at load rather than
     * assumed: an id without a type 0 model is written into the map and draws
     * nothing.
     */
    private static final int[] CANDIDATES = {
            5153,   // Wooden fence
            5631,   // Picket fence
            5632,   // Garden fence
            9029,   // Village fence
            2068    // Fence, squeeze-through
    };

    /** Shortest and longest run, in tiles. A two-post fence still reads as litter. */
    private static final int MIN_RUN = 4;
    private static final int MAX_RUN = 14;
    /** One verge in this many starts a run, so verges are lined but not walled in. */
    private static final int START_ODDS = 9;

    /** Offsets from a road tile to a candidate verge tile. */
    private static final int[][] SIDES = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
    /**
     * Which edge of the verge tile faces the road, as a type 0 rotation
     * (0 west, 1 north, 2 east, 3 south). A verge north of the road puts its
     * fence on its own south edge, and so on.
     */
    private static final int[] FACING = {3, 1, 0, 2};

    /** Receives one fence; returns whether it was actually placed. */
    interface Placer {
        boolean place(int id, int x, int y, int rotation);
    }

    private Fencing() {
    }

    /** The fence ids that will actually draw, worked out once. */
    static int[] usable() {
        List<Integer> ids = new ArrayList<>();
        for (int id : CANDIDATES) {
            if (ObjectVetting.rendersAt(id, 0)) {
                ids.add(id);
            }
        }
        int[] out = new int[ids.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = ids.get(i);
        }
        return out;
    }

    /**
     * Fences the verges of a set of path tiles.
     *
     * @param pathTiles the road or lane, as {x, y} pairs; iterated in the order
     *                  given, so pass a sorted list if the result has to be
     *                  reproducible
     * @return how many fence pieces were placed
     */
    static int layVerges(IslandGeography geography, boolean[][] isPath, List<int[]> pathTiles,
                         Random random, Placer placer) {
        int[] materials = usable();
        if (materials.length == 0) {
            return 0;
        }
        Set<Long> fenced = new HashSet<>();
        int placed = 0;
        for (int[] tile : pathTiles) {
            for (int side = 0; side < SIDES.length; side++) {
                int vx = tile[0] + SIDES[side][0];
                int vy = tile[1] + SIDES[side][1];
                if (!isVerge(geography, isPath, vx, vy) || fenced.contains(key(vx, vy))) {
                    continue;
                }
                if (random.nextInt(START_ODDS) != 0) {
                    continue;
                }
                // A run travels along the road, so perpendicular to the step that
                // reached the verge from it.
                int stepX = SIDES[side][0] == 0 ? 1 : 0;
                int stepY = SIDES[side][0] == 0 ? 0 : 1;
                int id = materials[random.nextInt(materials.length)];
                int rotation = FACING[side];
                int length = MIN_RUN + random.nextInt(MAX_RUN - MIN_RUN + 1);
                placed += layRun(geography, isPath, fenced, placer,
                        vx, vy, stepX, stepY, side, id, rotation, length);
            }
        }
        return placed;
    }

    /**
     * Walks one run along the verge, stopping where the verge stops - where the
     * road bends away, the ground is not walkable, or something is already there.
     */
    private static int layRun(IslandGeography geography, boolean[][] isPath, Set<Long> fenced,
                              Placer placer, int startX, int startY, int stepX, int stepY,
                              int side, int id, int rotation, int length) {
        int placed = 0;
        for (int i = 0; i < length; i++) {
            int x = startX + stepX * i;
            int y = startY + stepY * i;
            if (!isVerge(geography, isPath, x, y) || fenced.contains(key(x, y))) {
                break;
            }
            // The road has to still be on the same side, or the fence would peel
            // away from it and end up standing in a field.
            int roadX = x - SIDES[side][0];
            int roadY = y - SIDES[side][1];
            if (!IslandLayout.inBounds(roadX, roadY) || !isPath[roadX][roadY]) {
                break;
            }
            if (!placer.place(id, x, y, rotation)) {
                break;
            }
            fenced.add(key(x, y));
            placed++;
        }
        return placed;
    }

    private static boolean isVerge(IslandGeography geography, boolean[][] isPath, int x, int y) {
        return IslandLayout.inBounds(x, y) && !isPath[x][y] && geography.isWalkable(x, y);
    }

    private static long key(int x, int y) {
        return ((long) x << 20) | y;
    }
}
