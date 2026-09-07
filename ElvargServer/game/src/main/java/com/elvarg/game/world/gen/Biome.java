package com.elvarg.game.world.gen;

/**
 * The island's biome palette.
 *
 * Every material id here was read out of this cache's own flo.dat and checked
 * against a region that actually uses it, rather than assumed from RuneScape
 * knowledge. The comments record what each id looks like so the palette can be
 * re-tuned without going back to the cache.
 *
 * @author EverGielinor world generator
 */
public enum Biome {

    // ---- water -------------------------------------------------------------
    /** Open sea. Underlay #557799 with the animated water overlay (texture 25). */
    OCEAN(72, 6, true, DifficultyBand.BEGINNER),
    /** Coastal shallows, same materials as ocean but generated at the margin. */
    SHALLOWS(72, 6, true, DifficultyBand.BEGINNER),

    // ---- lowland -----------------------------------------------------------
    /** Sand, #cbba76. */
    BEACH(67, 0, false, DifficultyBand.BEGINNER),
    /** Bright green #6cac10 - the Lumbridge-meadow look. */
    PLAINS(50, 0, false, DifficultyBand.BEGINNER),
    /** Olive #58680b. */
    GRASSLAND(48, 0, false, DifficultyBand.LOW),
    /** Deeper green #35720a. */
    FOREST(47, 0, false, DifficultyBand.LOW),
    DENSE_FOREST(47, 0, false, DifficultyBand.MEDIUM),
    /** Dark teal-green #125841. */
    SWAMP(53, 0, false, DifficultyBand.MEDIUM),

    // ---- arid --------------------------------------------------------------
    /** Sand underlay with the desert overlay #827944 that Al Kharid uses. */
    DESERT(67, 25, false, DifficultyBand.MEDIUM),

    // ---- highland ----------------------------------------------------------
    /** Grey #767676. */
    ROCKY_HIGHLAND(54, 0, false, DifficultyBand.MEDIUM),
    /** Darker grey #4d4d4d. */
    MOUNTAIN(55, 0, false, DifficultyBand.HIGH),
    /** Pale blue-white #d1d6e7. */
    SNOW(58, 0, false, DifficultyBand.HIGH),

    // ---- hostile -----------------------------------------------------------
    /** Scorched brown #663300; lava pools are painted separately with overlay 19. */
    VOLCANIC(65, 0, false, DifficultyBand.VERY_HIGH),
    /** Brown #644e1e, the wasteland look. */
    WILDERNESS(63, 0, false, DifficultyBand.VERY_HIGH);

    /** Overlay id for lava, verified against the Fight Caves region (texture 15). */
    public static final int OVERLAY_LAVA = 19;
    /** Overlay id for water, verified against every coastal region (texture 25). */
    public static final int OVERLAY_WATER = 6;
    /** Overlay id for a dirt road, #6d5b2b. */
    public static final int OVERLAY_DIRT_ROAD = 22;
    /** Overlay id for town paving, #666666. */
    public static final int OVERLAY_PAVING = 2;

    private final int underlay;
    private final int overlay;
    private final boolean water;
    private final DifficultyBand baseDifficulty;

    Biome(int underlay, int overlay, boolean water, DifficultyBand baseDifficulty) {
        this.underlay = underlay;
        this.overlay = overlay;
        this.water = water;
        this.baseDifficulty = baseDifficulty;
    }

    public int underlay() {
        return underlay;
    }

    public int overlay() {
        return overlay;
    }

    /** Whether the biome is impassable water, which also sets the blocked tile flag. */
    public boolean isWater() {
        return water;
    }

    public DifficultyBand baseDifficulty() {
        return baseDifficulty;
    }

    /** Whether towns and roads may be generated here. */
    public boolean isHabitable() {
        return !water && this != MOUNTAIN && this != SNOW && this != VOLCANIC;
    }

    /** Whether trees may grow here. */
    public boolean supportsTrees() {
        return this == PLAINS || this == GRASSLAND || this == FOREST
                || this == DENSE_FOREST || this == SWAMP || this == WILDERNESS;
    }

    /** Whether ore may be generated here. Rock needs rock, or scorched ground. */
    public boolean supportsOre() {
        return this == ROCKY_HIGHLAND || this == MOUNTAIN || this == SNOW
                || this == VOLCANIC || this == WILDERNESS || this == DESERT;
    }
}
