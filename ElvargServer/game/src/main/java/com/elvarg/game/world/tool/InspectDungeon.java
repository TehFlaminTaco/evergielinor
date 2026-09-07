package com.elvarg.game.world.tool;

import com.elvarg.game.collision.Region;
import com.elvarg.game.collision.RegionManager;
import com.elvarg.game.world.GeneratedWorld;
import com.elvarg.game.world.WorldPersistence;

import java.nio.file.Paths;
import java.util.Optional;

/**
 * Reports what a generated dungeon actually looks like to the server: how much
 * of each floor is walkable, and whether the tile the player arrives on has
 * anywhere to go.
 *
 * A dungeon that renders as a grey box the player cannot walk out of is
 * indistinguishable, from a screenshot, from one whose rooms were never carved.
 * This tells them apart.
 */
public final class InspectDungeon {

    public static void main(String[] args) throws Exception {
        RegionManager.init();
        GeneratedWorld world = new WorldPersistence(Paths.get("../data")).load();
        int wanted = args.length > 0 ? Integer.parseInt(args[0]) : -1;

        for (GeneratedWorld.Dungeon dungeon : world.dungeons) {
            if (wanted >= 0 && dungeon.id != wanted) {
                continue;
            }
            int baseX = (dungeon.regionId >> 8) * 64;
            int baseY = (dungeon.regionId & 0xff) * 64;
            RegionManager.loadMapFiles(baseX + 32, baseY + 32);
            Optional<Region> region = RegionManager.getRegion(dungeon.regionId);

            System.out.println(dungeon.name + "  region " + dungeon.regionId
                    + ", " + dungeon.floors + " floors"
                    + (dungeon.hasBoss() ? ", boss " + dungeon.bossName : ", no boss"));
            if (region.isEmpty()) {
                System.out.println("  region did not load");
                continue;
            }
            for (int plane = 0; plane < dungeon.floors; plane++) {
                int walkable = 0;
                for (int x = 0; x < 64; x++) {
                    for (int y = 0; y < 64; y++) {
                        int clip = region.get().getClip(baseX + x, baseY + y, plane);
                        if ((clip & 0x1280120) == 0) {
                            walkable++;
                        }
                    }
                }
                System.out.printf("    floor %d  %,5d walkable of 4096 (%.1f%%)%n",
                        plane + 1, walkable, 100.0 * walkable / 4096);
            }
            int arrivalClip = RegionManager.getClipping(dungeon.arrivalX, dungeon.arrivalY, 0, null);
            System.out.println("    arrival " + dungeon.arrivalX + "," + dungeon.arrivalY
                    + "  walkable=" + ((arrivalClip & 0x1280120) == 0));
            int open = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    int clip = RegionManager.getClipping(
                            dungeon.arrivalX + dx, dungeon.arrivalY + dy, 0, null);
                    if ((clip & 0x1280120) == 0) {
                        open++;
                    }
                }
            }
            System.out.println("    neighbours open around arrival: " + open + " of 8");
        }
    }
}
