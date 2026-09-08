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
    /** Open sea. Slate blue #557799, under the animated water overlay. */
    OCEAN(73, 0x557799, 6, true, DifficultyBand.BEGINNER),
    /** Coastal shallows: same materials, generated at the margin. */
    SHALLOWS(73, 0x557799, 6, true, DifficultyBand.BEGINNER),

    // ---- lowland -----------------------------------------------------------
    /** Sand #cbba76. */
    BEACH(68, 0xcbba76, 0, false, DifficultyBand.BEGINNER),
    /** Bright meadow green #6cac10. */
    PLAINS(51, 0x6cac10, 0, false, DifficultyBand.BEGINNER),
    /** Olive pasture #58680b. */
    GRASSLAND(49, 0x58680b, 0, false, DifficultyBand.LOW),
    /** Deep green #35720a. */
    FOREST(48, 0x35720a, 0, false, DifficultyBand.LOW),
    /** Darker canopy green #396215. */
    DENSE_FOREST(100, 0x396215, 0, false, DifficultyBand.MEDIUM),
    /** Dark teal-green #125841. */
    SWAMP(54, 0x125841, 0, false, DifficultyBand.MEDIUM),

    // ---- arid --------------------------------------------------------------
    /** Pale sand #d0c074, under the desert overlay Al Kharid uses. */
    DESERT(62, 0xd0c074, 25, false, DifficultyBand.MEDIUM),

    // ---- highland ----------------------------------------------------------
    /** Grey #767676. */
    ROCKY_HIGHLAND(55, 0x767676, 0, false, DifficultyBand.MEDIUM),
    /** Darker grey #4d4d4d. */
    MOUNTAIN(56, 0x4d4d4d, 0, false, DifficultyBand.HIGH),
    /** Pale blue-white #d1d6e7. */
    SNOW(59, 0xd1d6e7, 0, false, DifficultyBand.HIGH),

    // ---- hostile -----------------------------------------------------------
    /** Scorched brown #663300; lava pools are painted separately. */
    VOLCANIC(66, 0x663300, 0, false, DifficultyBand.VERY_HIGH),
    /** Wasteland brown #644e1e. */
    WILDERNESS(64, 0x644e1e, 0, false, DifficultyBand.VERY_HIGH);

    /** Overlay id for lava, verified against the Fight Caves region (texture 15). */
    public static final int OVERLAY_LAVA = 19;
    /** Overlay id for water, verified against every coastal region (texture 25). */
    public static final int OVERLAY_WATER = 6;
    /** Overlay id for a dirt road, #6d5b2b. */
    public static final int OVERLAY_DIRT_ROAD = 22;
    /**
     * Overlay id for town paving. Value 10 is what Lumbridge and Varrock write
     * for their roads, so it is known to render as paving rather than chosen from
     * a colour table.
     */
    public static final int OVERLAY_PAVING = 10;

    private final int underlay;
    private final int renderedColour;
    private final int overlay;
    private final boolean water;
    private final DifficultyBand baseDifficulty;

    Biome(int underlay, int renderedColour, int overlay, boolean water, DifficultyBand baseDifficulty) {
        this.underlay = underlay;
        this.renderedColour = renderedColour;
        this.overlay = overlay;
        this.water = water;
        this.baseDifficulty = baseDifficulty;
    }

    /**
     * The colour the client actually paints for this biome's underlay.
     *
     * Recorded here because getting it wrong is invisible until someone walks
     * there: the client resolves an underlay through
     * {@code FloorDefinition.underlays[value - 1]}, and an id chosen by reading
     * flo.dat with the value as a direct index lands one entry short. That is
     * exactly what happened - forest was written as 47, which resolves to entry
     * 46, which is #000000, so every forest on the island rendered pure black.
     *
     * {@code ./gradlew :game:verifyPalette} checks each of these against the
     * cache's own flo.dat so the mistake cannot come back unnoticed.
     */
    public int renderedColour() {
        return renderedColour;
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
