package com.elvarg.game.world.gen;

import java.util.EnumSet;
import java.util.Set;

/**
 * The bosses a generated world may assign to dungeons.
 *
 * Split into two tiers by how much of the boss actually exists in this codebase.
 * IMPLEMENTED bosses have a dedicated class under {@code entity/impl/npc/impl}
 * with a bespoke combat method; PROMOTED bosses have definitions and drop tables
 * but fight with generic NPC combat. Both are placeable, and the distinction is
 * recorded so a host can see which dungeons have a fully realised fight.
 *
 * Every id was read from {@code NpcIdentifiers} and cross-checked against the
 * {@code @Ids} annotation on the boss's implementation class.
 *
 * @author EverGielinor world generator
 */
public enum BossRoster {

    // --- bosses with a dedicated combat method ------------------------------
    KING_BLACK_DRAGON(239, "King Black Dragon", Tier.IMPLEMENTED, DifficultyBand.VERY_HIGH,
            EnumSet.of(Biome.VOLCANIC, Biome.MOUNTAIN)),
    /**
     * Present for completeness but never assigned. TztokJad's only constructor is
     * {@code (Player, FightCavesArea, int, Location)}: it is built to be spawned
     * into the Fight Caves minigame with an owner, so NPC.create cannot
     * instantiate it as world content and silently falls back to a plain NPC -
     * a level 702 monster with no combat method at all. Placing it would look
     * like a boss and behave like a training dummy.
     */
    TZTOK_JAD(3127, "TzTok-Jad", Tier.MINIGAME_ONLY, DifficultyBand.ENDGAME,
            EnumSet.of(Biome.VOLCANIC)),
    CALLISTO(6609, "Callisto", Tier.IMPLEMENTED, DifficultyBand.VERY_HIGH,
            EnumSet.of(Biome.WILDERNESS, Biome.DENSE_FOREST)),
    VENENATIS(6504, "Venenatis", Tier.IMPLEMENTED, DifficultyBand.VERY_HIGH,
            EnumSet.of(Biome.WILDERNESS, Biome.SWAMP)),
    VETION(6611, "Vet'ion", Tier.IMPLEMENTED, DifficultyBand.VERY_HIGH,
            EnumSet.of(Biome.WILDERNESS)),
    CHAOS_ELEMENTAL(2054, "Chaos Elemental", Tier.IMPLEMENTED, DifficultyBand.HIGH,
            EnumSet.of(Biome.WILDERNESS, Biome.ROCKY_HIGHLAND)),
    CHAOS_FANATIC(6619, "Chaos Fanatic", Tier.IMPLEMENTED, DifficultyBand.HIGH,
            EnumSet.of(Biome.WILDERNESS)),
    CRAZY_ARCHAEOLOGIST(6618, "Crazy Archaeologist", Tier.IMPLEMENTED, DifficultyBand.HIGH,
            EnumSet.of(Biome.DESERT, Biome.WILDERNESS)),

    // --- bosses with definitions and loot but generic combat ----------------
    CORPOREAL_BEAST(319, "Corporeal Beast", Tier.PROMOTED, DifficultyBand.ENDGAME,
            EnumSet.of(Biome.SWAMP, Biome.DENSE_FOREST)),
    GENERAL_GRAARDOR(2215, "General Graardor", Tier.PROMOTED, DifficultyBand.VERY_HIGH,
            EnumSet.of(Biome.MOUNTAIN, Biome.ROCKY_HIGHLAND)),
    COMMANDER_ZILYANA(2205, "Commander Zilyana", Tier.PROMOTED, DifficultyBand.VERY_HIGH,
            EnumSet.of(Biome.SNOW, Biome.MOUNTAIN)),
    KRIL_TSUTSAROTH(3129, "K'ril Tsutsaroth", Tier.PROMOTED, DifficultyBand.VERY_HIGH,
            EnumSet.of(Biome.VOLCANIC)),
    KREEARRA(3162, "Kree'arra", Tier.PROMOTED, DifficultyBand.VERY_HIGH,
            EnumSet.of(Biome.MOUNTAIN, Biome.SNOW)),
    GIANT_MOLE(5779, "Giant Mole", Tier.PROMOTED, DifficultyBand.MEDIUM,
            EnumSet.of(Biome.FOREST, Biome.GRASSLAND, Biome.PLAINS)),
    DAGANNOTH_SUPREME(2265, "Dagannoth Supreme", Tier.PROMOTED, DifficultyBand.HIGH,
            EnumSet.of(Biome.BEACH, Biome.SNOW)),
    DAGANNOTH_PRIME(2266, "Dagannoth Prime", Tier.PROMOTED, DifficultyBand.HIGH,
            EnumSet.of(Biome.BEACH, Biome.SWAMP)),
    DAGANNOTH_REX(2267, "Dagannoth Rex", Tier.PROMOTED, DifficultyBand.HIGH,
            EnumSet.of(Biome.BEACH, Biome.ROCKY_HIGHLAND));

    public enum Tier {
        /** Has a dedicated combat method in content/combat/method/impl/npcs. */
        IMPLEMENTED,
        /** Has definitions and a drop table, but fights with generic NPC combat. */
        PROMOTED,
        /**
         * Cannot be spawned as free-standing world content: its implementation
         * class requires minigame context that a generated dungeon cannot supply.
         */
        MINIGAME_ONLY
    }

    private final int npcId;
    private final String displayName;
    private final Tier tier;
    private final DifficultyBand band;
    private final Set<Biome> biomes;

    BossRoster(int npcId, String displayName, Tier tier, DifficultyBand band, Set<Biome> biomes) {
        this.npcId = npcId;
        this.displayName = displayName;
        this.tier = tier;
        this.band = band;
        this.biomes = biomes;
    }

    public int npcId() {
        return npcId;
    }

    public String displayName() {
        return displayName;
    }

    public Tier tier() {
        return tier;
    }

    public DifficultyBand band() {
        return band;
    }

    /**
     * Whether this boss belongs in a dungeon of the given biome and terminal
     * difficulty. Biome must match outright; difficulty may be one band either
     * side, so a world short of deep dungeons still places most of the roster.
     */
    /** Whether this boss can be spawned into a generated dungeon at all. */
    public boolean isPlaceable() {
        return tier != Tier.MINIGAME_ONLY;
    }

    public boolean suits(Biome dungeonBiome, DifficultyBand terminalBand) {
        if (!isPlaceable() || !biomes.contains(dungeonBiome)) {
            return false;
        }
        return Math.abs(band.ordinal() - terminalBand.ordinal()) <= 1;
    }
}
