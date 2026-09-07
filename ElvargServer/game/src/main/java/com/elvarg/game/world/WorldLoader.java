package com.elvarg.game.world;

import com.elvarg.game.World;
import com.elvarg.game.entity.impl.npc.NPC;
import com.elvarg.game.model.Location;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Brings the generated world online at boot.
 *
 * Terrain and static objects are already in the map files by the time this runs
 * - RegionManager reads them like any other region - so the only work here is
 * the state the map files cannot carry: NPC spawns, the default respawn point,
 * and the dungeon and boss records that later systems consult.
 *
 * This must run <em>after</em> the background definition loaders have finished,
 * not as one of them: BackgroundLoader may use several threads, and spawning an
 * NPC before npc_defs.json has landed produces intermittent, timing-dependent
 * failures.
 *
 * @author EverGielinor
 */
public final class WorldLoader {

    private static GeneratedWorld world;
    private static WorldConfig config;
    private static boolean loaded;

    private WorldLoader() {
    }

    /** The active generated world, or null when the server is running vanilla maps. */
    public static GeneratedWorld world() {
        return world;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * Loads world configuration and, if a generated world is installed, its record.
     *
     * A missing world file is not an error: the server then runs on whatever maps
     * are installed, which is exactly what a host wants before generating for the
     * first time.
     */
    public static void init() {
        Path dataDirectory = Paths.get("../data");
        try {
            config = WorldConfig.load(dataDirectory.resolve("world_config.json"));
            WorldConfig.set(config);
            System.out.println("[world] config: " + config);
        } catch (IOException e) {
            System.err.println("[world] could not read world_config.json, using defaults: " + e.getMessage());
            config = new WorldConfig();
            WorldConfig.set(config);
        }

        WorldPersistence persistence = new WorldPersistence(dataDirectory);
        if (!persistence.exists()) {
            System.out.println("[world] no generated world installed; "
                    + "run ./gradlew :game:generateWorld -Pseed=<seed> to make one");
            return;
        }

        try {
            world = persistence.load();
        } catch (IOException e) {
            System.err.println("[world] FAILED to read world.json: " + e.getMessage());
            return;
        }

        if (world.seed != config.seed) {
            System.out.println("[world] NOTE: installed world is seed " + world.seed
                    + " but world_config.json says " + config.seed
                    + ". The installed world wins; re-generate to change it.");
        }

        GeneratedWorldInteractions.index(world);
        int spawned = spawnNpcs();
        loaded = true;

        System.out.println("[world] seed " + world.seed
                + " (generator v" + world.generatorVersion + ")"
                + ", " + world.localities.size() + " localities"
                + ", " + world.dungeons.size() + " dungeons"
                + ", " + spawned + " npcs spawned");
        System.out.println("[world] default respawn " + defaultSpawn());
    }

    private static int spawnNpcs() {
        int spawned = 0;
        for (GeneratedWorld.NpcSpawn spawn : world.npcSpawns) {
            try {
                NPC npc = NPC.create(spawn.id, new Location(spawn.x, spawn.y, spawn.z));
                npc.getMovementCoordinator().setRadius(spawn.radius);
                npc.setFace(parseFacing(spawn.facing));
                World.getAddNPCQueue().add(npc);
                spawned++;
            } catch (Exception e) {
                // One bad definition should not stop the world from coming up.
                System.err.println("[world] could not spawn npc " + spawn.id
                        + " at " + spawn.x + "," + spawn.y + ": " + e);
            }
        }
        return spawned;
    }

    private static com.elvarg.game.model.Direction parseFacing(String facing) {
        if (facing == null) {
            return com.elvarg.game.model.Direction.SOUTH;
        }
        try {
            return com.elvarg.game.model.Direction.valueOf(facing);
        } catch (IllegalArgumentException e) {
            return com.elvarg.game.model.Direction.SOUTH;
        }
    }

    /** The world's fallback respawn point, used when a player has no bed. */
    public static Location defaultSpawn() {
        if (world == null) {
            return com.elvarg.game.GameConstants.DEFAULT_LOCATION.clone();
        }
        return new Location(world.spawnX, world.spawnY, world.spawnZ);
    }
}
