package com.elvarg.game.world.gen;

import java.util.EnumSet;
import java.util.Set;

/**
 * The skillable resources the generator can place.
 *
 * The object ids come from the enums the skills already dispatch on -
 * {@code Mining.Rock} and {@code Woodcutting.Tree} - so anything placed here is
 * something the skill will actually respond to.
 *
 * Those enums are <em>not</em> a safe source of models, though. They list every
 * id that should trigger the skill across several game revisions, and in this
 * cache a good number of them are something else entirely: Willow includes 5553
 * "Cave", Runite includes a Gorilla Statue and a Danger sign, Gold includes
 * doors and gates, Clay includes tree stumps. Placing those put cave mouths in
 * the middle of forests.
 *
 * So {@link #renderableObjectIds()} filters each list against the real object
 * definitions at generation time and keeps only the ids whose name matches what
 * they are supposed to be. Run {@code ./gradlew :game:auditResources} to see the
 * full list and what was rejected.
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

    /** Ids that survived the definition check, resolved once per run. */
    private int[] renderable;

    /**
     * Picks one of the interchangeable variants that is actually this resource in
     * this cache, or -1 when none of the listed ids is.
     */
    public int objectId(int variant) {
        int[] usable = renderableObjectIds();
        if (usable.length == 0) {
            return -1;
        }
        return usable[Math.floorMod(variant, usable.length)];
    }

    /**
     * The listed ids, filtered down to those whose object definition exists and
     * whose name matches this resource's category.
     */
    public int[] renderableObjectIds() {
        if (renderable == null) {
            java.util.List<Integer> kept = new java.util.ArrayList<>();
            for (int id : objectIds) {
                if (ObjectVetting.isPlaceable(id) && namePlausible(ObjectVetting.nameOf(id))) {
                    kept.add(id);
                }
            }
            renderable = kept.stream().mapToInt(Integer::intValue).toArray();
        }
        return renderable;
    }

    /** Whether an object's name is consistent with being this kind of resource. */
    private boolean namePlausible(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (category == Category.TREE) {
            return lower.contains("tree") || lower.contains("evergreen") || lower.contains("oak")
                    || lower.contains("willow") || lower.contains("yew") || lower.contains("maple")
                    || lower.contains("magic") || lower.contains("teak") || lower.contains("mahogany")
                    || lower.contains("achey") || lower.contains("dramen") || lower.contains("pine");
        }
        // Mining nodes in this cache are almost all simply called "Rocks".
        return lower.contains("rock") || lower.contains("ore") || lower.contains("vein")
                || lower.contains("clay") || lower.contains("coal");
    }

    /** Whether anything in this resource's list is usable at all. */
    public boolean isUsable() {
        return renderableObjectIds().length > 0;
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
