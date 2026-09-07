package com.elvarg.game.world.gen;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Ground clutter: the grass, ferns and bushes that make terrain read as a place
 * rather than a coloured plane.
 *
 * Ids and sizes were taken from the cache's own object definitions. Everything
 * here is non-blocking where the definition allows, so clutter never walls a
 * player in.
 *
 * @author EverGielinor world generator
 */
public final class Decoration {

    /** Clutter that suits each biome, in rough order of how common it should be. */
    private static final Map<Biome, int[]> BY_BIOME = new EnumMap<>(Biome.class);

    static {
        // 3794/3795 Grass, 4815 Long grass, 1298 Fern, 1173 Small fern (all 1x1).
        int[] temperate = {3794, 3795, 4815, 1298, 1173};
        int[] lush = {4815, 3794, 1298, 4812, 1204};
        int[] scrub = {1298, 1173, 3794};

        BY_BIOME.put(Biome.PLAINS, temperate);
        BY_BIOME.put(Biome.GRASSLAND, temperate);
        BY_BIOME.put(Biome.FOREST, lush);
        BY_BIOME.put(Biome.DENSE_FOREST, lush);
        BY_BIOME.put(Biome.SWAMP, new int[]{4815, 1204, 1184});
        BY_BIOME.put(Biome.BEACH, scrub);
        BY_BIOME.put(Biome.WILDERNESS, scrub);
        BY_BIOME.put(Biome.ROCKY_HIGHLAND, scrub);
        BY_BIOME.put(Biome.MOUNTAIN, new int[]{1298});
        // Desert, snow and volcanic ground stays bare on purpose.
    }

    /** Bushes, which are 2x2 and read as hedgerow when placed along a treeline. */
    public static final int[] BUSHES = {1118, 1120, 1122, 1123};

    private Decoration() {
    }

    /** Clutter ids for a biome, or an empty array where bare ground is correct. */
    public static int[] forBiome(Biome biome) {
        return BY_BIOME.getOrDefault(biome, new int[0]);
    }

    /**
     * Peak chance per tile of clutter inside a patch.
     *
     * These are the density at the middle of a clump, not an island-wide average -
     * the noise gate means only part of the map is cluttered at all, so the
     * numbers look high and the result does not.
     */
    public static double densityFor(Biome biome) {
        return switch (biome) {
            case DENSE_FOREST -> 0.42;
            case FOREST -> 0.32;
            case SWAMP -> 0.30;
            case GRASSLAND -> 0.24;
            case PLAINS -> 0.18;
            case BEACH -> 0.10;
            case WILDERNESS, ROCKY_HIGHLAND -> 0.09;
            case MOUNTAIN -> 0.03;
            default -> 0.0;
        };
    }
}
