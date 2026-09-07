package com.elvarg.game.world.tool;

import com.elvarg.game.collision.Region;
import com.elvarg.game.collision.RegionManager;
import com.elvarg.game.entity.impl.object.MapObjects;
import com.elvarg.game.model.Location;
import com.elvarg.game.world.GeneratedWorld;
import com.elvarg.game.world.WorldPersistence;
import com.elvarg.game.world.gen.IslandLayout;

import java.nio.file.Paths;
import java.util.Optional;

/**
 * Loads the installed island through the server's own region pipeline and checks
 * that what the generator wrote is what the engine reads.
 *
 * This is the test that matters. The codec self-test proves the bytes round-trip
 * through my own decoder; this proves {@code RegionManager.loadMapFiles} - the
 * code the running server actually uses - derives sane collision and objects
 * from the generated files, and that the spawn point is somewhere a player can
 * stand.
 */
public final class VerifyInstall {

    public static void main(String[] args) throws Exception {
        RegionManager.init();
        GeneratedWorld world = new WorldPersistence(Paths.get("../data")).load();

        System.out.println("world seed          " + world.seed);
        System.out.println("generator version   " + world.generatorVersion);
        System.out.println("spawn               " + world.spawnX + ", " + world.spawnY);

        int regionsLoaded = 0;
        long walkable = 0;
        long blocked = 0;
        long objectTiles = 0;

        for (int regionX = 0; regionX < IslandLayout.ISLAND_REGIONS; regionX++) {
            for (int regionY = 0; regionY < IslandLayout.ISLAND_REGIONS; regionY++) {
                int baseX = (IslandLayout.BLOCK_REGION_X + regionX) * 64;
                int baseY = (IslandLayout.BLOCK_REGION_Y + regionY) * 64;
                RegionManager.loadMapFiles(baseX + 32, baseY + 32);

                int regionId = IslandLayout.regionId(
                        IslandLayout.BLOCK_REGION_X + regionX, IslandLayout.BLOCK_REGION_Y + regionY);
                Optional<Region> region = RegionManager.getRegion(regionId);
                if (region.isEmpty()) {
                    System.out.println("  MISSING region " + regionId);
                    continue;
                }
                regionsLoaded++;
                for (int x = 0; x < 64; x++) {
                    for (int y = 0; y < 64; y++) {
                        int clip = region.get().getClip(baseX + x, baseY + y, 0);
                        if ((clip & RegionManager.BLOCKED_TILE) != 0) {
                            blocked++;
                        } else {
                            walkable++;
                        }
                    }
                }
            }
        }

        for (java.util.ArrayList<com.elvarg.game.entity.impl.object.GameObject> list
                : MapObjects.mapObjects.values()) {
            objectTiles += list.size();
        }

        System.out.println();
        System.out.println("regions loaded      " + regionsLoaded + " of "
                + (IslandLayout.ISLAND_REGIONS * IslandLayout.ISLAND_REGIONS));
        System.out.printf("walkable tiles      %,d%n", walkable);
        System.out.printf("blocked tiles       %,d  (%.1f%%)%n",
                blocked, 100.0 * blocked / (walkable + blocked));
        System.out.printf("map objects loaded  %,d%n", objectTiles);

        // The single most important property: a new player must be able to stand up.
        Location spawn = new Location(world.spawnX, world.spawnY, world.spawnZ);
        boolean spawnBlocked = RegionManager.blocked(spawn, null);
        System.out.println("spawn walkable      " + !spawnBlocked);
        if (spawnBlocked) {
            // Say what is in the way, rather than only that something is.
            System.out.println("  blocking the spawn tile:");
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Location at = new Location(spawn.getX() + dx, spawn.getY() + dy, 0);
                    java.util.ArrayList<com.elvarg.game.entity.impl.object.GameObject> here =
                            MapObjects.mapObjects.get(MapObjects.getHash(at.getX(), at.getY(), 0));
                    if (here == null) {
                        continue;
                    }
                    for (com.elvarg.game.entity.impl.object.GameObject object : here) {
                        System.out.println("    " + dx + "," + dy + "  id=" + object.getId()
                                + " type=" + object.getType()
                                + " name=" + com.elvarg.game.world.gen.ObjectVetting.nameOf(object.getId()));
                    }
                }
            }
        }

        int failures = 0;
        if (regionsLoaded != IslandLayout.ISLAND_REGIONS * IslandLayout.ISLAND_REGIONS) {
            System.out.println("FAIL: not every island region loaded");
            failures++;
        }
        if (spawnBlocked) {
            System.out.println("FAIL: spawn tile is blocked");
            failures++;
        }
        if (objectTiles == 0) {
            System.out.println("FAIL: no generated objects reached the server's object map");
            failures++;
        }
        if (walkable < 50_000) {
            System.out.println("FAIL: implausibly little walkable land (" + walkable + ")");
            failures++;
        }

        System.out.println(failures == 0 ? "\nINSTALL VERIFIED" : "\n" + failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
