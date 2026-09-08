package com.elvarg.game.world.gen;

/**
 * Works out which overlay shape and rotation paints a given set of tile corners.
 *
 * A landscape overlay carries a shape (0..11) and a rotation (0..3) as well as an
 * id. Writing shape 0 everywhere - a full square of overlay on every tile - is
 * what makes a generated path read as a staircase of blocks rather than a track,
 * and a shoreline read as a flight of steps. The client has shapes that fill only
 * part of a tile, which is how the original map draws a diagonal.
 *
 * Rather than hardcode which shape is which - the numbering is not documented
 * anywhere and reading it off screenshots is guesswork - this replays the
 * client's own tile geometry. The two tables below are copied verbatim from
 * {@code com.runescape.scene.object.tile.ShapedTile}: the first lists which of
 * the client's numbered tile points each shape uses, and the second lists the
 * triangles as (isOverlay, indexA, indexB, indexC) over that list. Rotating and
 * evaluating them tells us exactly which corners of a tile each shape covers, so
 * the lookup below is derived from the renderer instead of guessed at.
 *
 * Corners are indexed 0=south-west, 1=south-east, 2=north-east, 3=north-west,
 * matching the client's points 1, 3, 5 and 7.
 *
 * @author EverGielinor world generator
 */
public final class OverlayShape {

    /** Full-tile overlay: every corner covered. */
    public static final int FULL = 0;

    /** Points used by each shape, indexed by the client's shape number. */
    private static final int[][] SHAPE_POINTS = {
            {1, 3, 5, 7}, {1, 3, 5, 7}, {1, 3, 5, 7},
            {1, 3, 5, 7, 6}, {1, 3, 5, 7, 6}, {1, 3, 5, 7, 6}, {1, 3, 5, 7, 6},
            {1, 3, 5, 7, 2, 6}, {1, 3, 5, 7, 2, 8}, {1, 3, 5, 7, 2, 8},
            {1, 3, 5, 7, 11, 12}, {1, 3, 5, 7, 11, 12}, {1, 3, 5, 7, 13, 14}
    };

    /** Triangles per shape, as (isOverlay, a, b, c) indices into SHAPE_POINTS. */
    private static final int[][] SHAPE_TRIANGLES = {
            {0, 1, 2, 3, 0, 0, 1, 3},
            {1, 1, 2, 3, 1, 0, 1, 3},
            {0, 1, 2, 3, 1, 0, 1, 3},
            {0, 0, 1, 2, 0, 0, 2, 4, 1, 0, 4, 3},
            {0, 0, 1, 4, 0, 0, 4, 3, 1, 1, 2, 4},
            {0, 0, 4, 3, 1, 0, 1, 2, 1, 0, 2, 4},
            {0, 1, 2, 4, 1, 0, 1, 4, 1, 0, 4, 3},
            {0, 4, 1, 2, 0, 4, 2, 5, 1, 0, 4, 5, 1, 0, 5, 3},
            {0, 4, 1, 2, 0, 4, 2, 3, 0, 4, 3, 5, 1, 0, 4, 5},
            {0, 0, 4, 5, 1, 4, 1, 2, 1, 4, 2, 3, 1, 4, 3, 5},
            {0, 0, 1, 5, 0, 1, 4, 5, 0, 1, 2, 4, 1, 0, 5, 3, 1, 5, 4, 3, 1, 4, 2, 3},
            {1, 0, 1, 5, 1, 1, 4, 5, 1, 1, 2, 4, 0, 0, 5, 3, 0, 5, 4, 3, 0, 4, 2, 3},
            {1, 0, 5, 4, 1, 0, 1, 5, 0, 0, 4, 3, 0, 4, 5, 3, 0, 5, 2, 3, 0, 1, 2, 5}
    };

    /**
     * Which shape and rotation to write for each pattern of covered corners,
     * packed as {@code shape << 2 | rotation}, indexed by a bitmask of corners
     * (bit 0 = south-west, 1 = south-east, 2 = north-east, 3 = north-west).
     * -1 where no shape covers exactly that pattern.
     */
    private static final int[] BY_CORNERS = new int[16];

    static {
        java.util.Arrays.fill(BY_CORNERS, -1);
        // Landscape shape n maps to the client's shape n + 1; shape 0 is the plain
        // untextured tile and never appears in a landscape file.
        for (int shape = 0; shape <= 11; shape++) {
            for (int rotation = 0; rotation < 4; rotation++) {
                int corners = coveredCorners(shape + 1, rotation);
                // Prefer the lowest shape number that produces a pattern, so the
                // simple diagonals win over the elaborate multi-triangle shapes.
                if (BY_CORNERS[corners] == -1) {
                    BY_CORNERS[corners] = (shape << 2) | rotation;
                }
            }
        }
    }

    private OverlayShape() {
    }

    /**
     * The shape and rotation that covers exactly these corners, packed as
     * {@code shape << 2 | rotation}, or -1 if no shape does.
     *
     * @param corners bitmask, bit 0 = south-west, 1 = south-east, 2 = north-east,
     *                3 = north-west
     */
    public static int forCorners(int corners) {
        return BY_CORNERS[corners & 0xf];
    }

    public static int shapeOf(int packed) {
        return packed >> 2;
    }

    public static int rotationOf(int packed) {
        return packed & 3;
    }

    // ------------------------------------------------------------------

    /**
     * Replays one shape at one rotation and reports which tile corners end up
     * inside an overlay triangle.
     *
     * This is the client's own arithmetic. {@code ShapedTile} rotates a shape by
     * rotating the triangles' vertex indices, not the points: an index below 4
     * addresses one of the four corners and becomes {@code (index - rotation) & 3},
     * while higher indices address points inside the tile and are left alone. A
     * corner counts as covered when it is a vertex of a triangle flagged overlay.
     */
    private static int coveredCorners(int shape, int rotation) {
        int covered = 0;
        int[] triangles = SHAPE_TRIANGLES[shape];
        for (int i = 0; i < triangles.length; i += 4) {
            if (triangles[i] == 0) {
                continue;
            }
            for (int vertex = 1; vertex <= 3; vertex++) {
                int index = triangles[i + vertex];
                if (index < 4) {
                    // SHAPE_POINTS begins {1, 3, 5, 7} for every shape, which are
                    // the south-west, south-east, north-east and north-west
                    // corners in that order.
                    covered |= 1 << ((index - rotation) & 3);
                }
            }
        }
        return covered;
    }

    /** Prints the derived table, so the mapping can be checked rather than trusted. */
    public static void dump() {
        String[] names = {"SW", "SE", "NE", "NW"};
        for (int corners = 0; corners < 16; corners++) {
            StringBuilder set = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                if ((corners & (1 << i)) != 0) {
                    set.append(set.length() == 0 ? "" : "+").append(names[i]);
                }
            }
            int packed = BY_CORNERS[corners];
            System.out.printf("  corners %-12s -> %s%n",
                    set.length() == 0 ? "(none)" : set.toString(),
                    packed < 0 ? "no shape covers this" : "shape " + shapeOf(packed)
                            + " rotation " + rotationOf(packed));
        }
    }
}
