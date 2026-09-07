package com.elvarg.game.world.gen;

import java.util.EnumSet;
import java.util.Set;

/**
 * The skillable resources the generator can place.
 *
 * Every object id here was taken from the enum the corresponding skill already
 * dispatches on - {@code Mining.Rock} and {@code Woodcutting.Tree} - rather than
 * from RuneScape knowledge. That matters: an object placed with an id the skill
 * does not recognise is scenery a player cannot use, and the difference is
 * invisible until someone clicks it.
 *
 * @author EverGielinor world generator
 */
public enum ResourceKind {

    // --- Woodcutting (ids and levels from Woodcutting.Tree) -----------------
    TREE(Category.TREE, 1, new int[]{1276, 1277, 1278, 1279, 1280, 1282, 2091, 2890},
            EnumSet.of(Biome.PLAINS, Biome.GRASSLAND, Biome.FOREST, Biome.DENSE_FOREST, Biome.SWAMP)),
    OAK(Category.TREE, 15, new int[]{1281, 3037, 9734, 1751},
            EnumSet.of(Biome.GRASSLAND, Biome.FOREST, Biome.DENSE_FOREST)),
    WILLOW(Category.TREE, 30, new int[]{1308, 5551, 5552, 5553, 1750, 1758},
            EnumSet.of(Biome.SWAMP, Biome.FOREST)),
    TEAK(Category.TREE, 35, new int[]{9036},
            EnumSet.of(Biome.DENSE_FOREST, Biome.SWAMP)),
    MAPLE(Category.TREE, 45, new int[]{1759, 4674},
            EnumSet.of(Biome.FOREST, Biome.DENSE_FOREST)),
    MAHOGANY(Category.TREE, 50, new int[]{9034},
            EnumSet.of(Biome.DENSE_FOREST)),
    YEW(Category.TREE, 60, new int[]{1309, 1753},
            EnumSet.of(Biome.DENSE_FOREST, Biome.WILDERNESS)),
    MAGIC_TREE(Category.TREE, 75, new int[]{1761},
            EnumSet.of(Biome.WILDERNESS, Biome.DENSE_FOREST)),

    // --- Mining (ids and levels from Mining.Rock) ---------------------------
    CLAY(Category.ROCK, 1, new int[]{9711, 9712, 9713, 15503, 15504, 15505},
            EnumSet.of(Biome.DESERT, Biome.ROCKY_HIGHLAND, Biome.SWAMP)),
    COPPER(Category.ROCK, 1, new int[]{7453},
            EnumSet.of(Biome.ROCKY_HIGHLAND, Biome.DESERT, Biome.MOUNTAIN)),
    TIN(Category.ROCK, 1, new int[]{7486},
            EnumSet.of(Biome.ROCKY_HIGHLAND, Biome.DESERT, Biome.MOUNTAIN)),
    IRON(Category.ROCK, 15, new int[]{7455, 7488},
            EnumSet.of(Biome.ROCKY_HIGHLAND, Biome.MOUNTAIN, Biome.DESERT)),
    SILVER(Category.ROCK, 20, new int[]{7457},
            EnumSet.of(Biome.ROCKY_HIGHLAND, Biome.MOUNTAIN)),
    COAL(Category.ROCK, 30, new int[]{7456},
            EnumSet.of(Biome.MOUNTAIN, Biome.ROCKY_HIGHLAND, Biome.VOLCANIC)),
    GOLD(Category.ROCK, 40, new int[]{9720, 9721, 9722, 11951, 11183, 11184, 11185, 2099},
            EnumSet.of(Biome.MOUNTAIN, Biome.VOLCANIC, Biome.SNOW)),
    MITHRIL(Category.ROCK, 50, new int[]{7492, 7459},
            EnumSet.of(Biome.MOUNTAIN, Biome.SNOW, Biome.VOLCANIC, Biome.WILDERNESS)),
    ADAMANTITE(Category.ROCK, 70, new int[]{7460},
            EnumSet.of(Biome.SNOW, Biome.VOLCANIC, Biome.WILDERNESS)),
    RUNITE(Category.ROCK, 85, new int[]{14859, 4860, 2106, 2107, 7461},
            EnumSet.of(Biome.VOLCANIC, Biome.WILDERNESS, Biome.SNOW));

    public enum Category { TREE, ROCK }

    private final Category category;
    private final int level;
    private final int[] objectIds;
    private final Set<Biome> biomes;

    ResourceKind(Category category, int level, int[] objectIds, Set<Biome> biomes) {
        this.category = category;
        this.level = level;
        this.objectIds = objectIds;
        this.biomes = biomes;
    }

    public Category category() {
        return category;
    }

    /** The skill level the resource requires, used to match it to a difficulty band. */
    public int level() {
        return level;
    }

    public boolean allowedIn(Biome biome) {
        return biomes.contains(biome);
    }

    /** Picks one of the interchangeable object variants for visual variety. */
    public int objectId(int variant) {
        return objectIds[Math.floorMod(variant, objectIds.length)];
    }

    public int[] objectIds() {
        return objectIds.clone();
    }

    /**
     * Whether this resource belongs in a locality of the given band.
     *
     * The window is deliberately loose at the top: the design calls for a locality
     * to hold mostly band-appropriate resources plus a rarer tail above it, so a
     * player exploring a mid-level area can still find something worth the trip.
     */
    public boolean suitsBand(DifficultyBand band, boolean allowExceptional) {
        int ceiling = allowExceptional ? band.exceptionalSkillCeiling() : band.maxSkill();
        return level >= Math.max(1, band.minSkill() - 10) && level <= ceiling;
    }
}
