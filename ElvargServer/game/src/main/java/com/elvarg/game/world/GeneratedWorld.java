package com.elvarg.game.world;

import com.elvarg.game.world.gen.Biome;
import com.elvarg.game.world.gen.DifficultyBand;
import com.elvarg.game.world.gen.Locality;

import java.util.ArrayList;
import java.util.List;

/**
 * A generated world, in the form that is written to disk and reloaded at boot.
 *
 * Terrain is deliberately absent: it lives in the map files the generator
 * installs, which both the client and the server already know how to read. What
 * is stored here is everything the server needs that the map files cannot carry
 * - spawns, identities, difficulty and the boss bindings that must survive a
 * restart even when a dungeon's layout does not.
 *
 * @author EverGielinor world generator
 */
public final class GeneratedWorld {

    /**
     * Bumped whenever a change to the generator would alter what a seed produces.
     * A saved world records the version it was made with, so an old world keeps
     * loading as itself instead of silently becoming a different place.
     */
    public static final int GENERATOR_VERSION = 1;

    public long seed;
    public int generatorVersion = GENERATOR_VERSION;
    public long generatedAtEpochMillis;

    /** Default respawn point, in world tile coordinates. */
    public int spawnX;
    public int spawnY;
    public int spawnZ;

    public final List<Locality> localities = new ArrayList<>();
    public final List<NpcSpawn> npcSpawns = new ArrayList<>();
    public final List<Dungeon> dungeons = new ArrayList<>();

    /** Counts recorded at generation time, for host inspection. */
    public int resourceObjectCount;
    public int sceneryObjectCount;
    public int townObjectCount;
    public int clutterObjectCount;

    /** An NPC the world places. Mirrors NpcSpawnDefinition so the loader is trivial. */
    public static final class NpcSpawn {
        public int id;
        public int x;
        public int y;
        public int z;
        public int radius;
        /** Direction enum name; kept as text so world.json stays readable by hand. */
        public String facing = "SOUTH";
        /** Locality this spawn belongs to, or -1 for dungeon spawns. */
        public int localityId = -1;
        /** Dungeon this spawn belongs to, or -1 for overworld spawns. */
        public int dungeonId = -1;
        /**
         * Shop this NPC runs, or -1. Shopkeepers share one NPC id across the whole
         * map, so a generated shop has to be bound to the individual spawn rather
         * than to the id.
         */
        public int shopId = -1;

        public NpcSpawn() {
        }

        public NpcSpawn(int id, int x, int y, int z, int radius) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
        }
    }

    /**
     * A dungeon's persistent identity.
     *
     * The layout seed is stored so a floor can be rebuilt identically, but the
     * layout itself is not persisted - it is a pure function of
     * {@code (worldSeed, dungeonId, floor)}. The boss binding, by contrast, is
     * persisted and never recomputed, which is what guarantees that a dungeon
     * keeps its boss across restarts even if its rooms are rebuilt.
     */
    public static final class Dungeon {
        public int id;
        public String name;
        /** Entrance on the overworld, in world tile coordinates. */
        public int entranceX;
        public int entranceY;
        /** Region reserved for this dungeon's floors. */
        public int regionId;
        public int floors;
        public Biome biome;
        public DifficultyBand entryBand;
        public DifficultyBand terminalBand;
        /** NPC id of the boss, or -1 for a boss-less dungeon. */
        public int bossNpcId = -1;
        public String bossName;
        /** Whether the boss has a dedicated combat method. */
        public boolean bossFullyImplemented;
        /** Where the player arrives on floor 1, in world tile coordinates. */
        public int arrivalX;
        public int arrivalY;
        /** Boss room centre, in world tile coordinates. */
        public int bossRoomX;
        public int bossRoomY;

        public boolean hasBoss() {
            return bossNpcId != -1;
        }
    }

    public Locality localityAt(int localityId) {
        for (Locality locality : localities) {
            if (locality.id == localityId) {
                return locality;
            }
        }
        return null;
    }

    public Dungeon dungeonAt(int dungeonId) {
        for (Dungeon dungeon : dungeons) {
            if (dungeon.id == dungeonId) {
                return dungeon;
            }
        }
        return null;
    }
}
