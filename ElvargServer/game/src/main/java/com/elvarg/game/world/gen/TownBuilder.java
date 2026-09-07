package com.elvarg.game.world.gen;

import com.elvarg.game.world.GeneratedWorld;

import java.util.Random;

/**
 * Builds a settlement: level ground, paving, the services the locality's identity
 * calls for, and the people who staff them.
 *
 * A town is placed as an intentional object, not as scattered furniture. It
 * flattens its own ground first, because a bank booth halfway up a cliff reads as
 * a bug even though the server is perfectly happy with it.
 *
 * @author EverGielinor world generator
 */
final class TownBuilder {

    /** Half-width of the paved plaza. */
    private static final int PLAZA_HALF = 8;

    private TownBuilder() {
    }

    static void build(Locality locality, int centreX, int centreY, IslandGeography geography,
                      WorldGenerator.Result result, boolean[][] occupied, Noise noise,
                      int[][] overlayOverride) {
        Random random = new Random(locality.id * 104729L + 17L);

        // Level the plaza to the centre's height so nothing is built on a slope.
        int level = geography.height[centreX][centreY];
        for (int dx = -PLAZA_HALF; dx <= PLAZA_HALF; dx++) {
            for (int dy = -PLAZA_HALF; dy <= PLAZA_HALF; dy++) {
                int x = centreX + dx;
                int y = centreY + dy;
                if (!IslandLayout.inBounds(x, y) || geography.biome[x][y].isWater()) {
                    continue;
                }
                geography.height[x][y] = level;
                // Square plaza core paved, softer dirt at the edges.
                boolean core = Math.abs(dx) <= PLAZA_HALF - 3 && Math.abs(dy) <= PLAZA_HALF - 3;
                overlayOverride[x][y] = core ? Biome.OVERLAY_PAVING : Biome.OVERLAY_DIRT_ROAD;
            }
        }

        // Services sit around the plaza edge on a fixed ring, so every town reads
        // the same way to a player while its contents vary by identity.
        int[][] slots = {
                {-5, -5}, {5, -5}, {-5, 5}, {5, 5},
                {0, -6}, {0, 6}, {-6, 0}, {6, 0}
        };
        int slot = 0;
        for (TownService service : locality.services) {
            if (!service.isObject()) {
                continue;
            }
            if (slot >= slots.length) {
                break;
            }
            int x = centreX + slots[slot][0];
            int y = centreY + slots[slot][1];
            slot++;
            if (!IslandLayout.inBounds(x, y)) {
                continue;
            }
            WorldGenerator.addObject(result.objects, IslandLayout.worldX(x), IslandLayout.worldY(y), 0,
                    service.objectId(), com.elvarg.game.world.codec.PlacedObject.TYPE_SCENERY,
                    random.nextInt(4));
            occupied[x][y] = true;
            result.world.townObjectCount++;
        }

        // Staff. A banker only appears where there is actually a bank.
        if (locality.services.contains(TownService.BANK)) {
            addNpc(result, locality, 394, centreX - 4, centreY - 4);
        }
        if (locality.services.contains(TownService.GENERAL_STORE)) {
            addNpc(result, locality, 506, centreX + 2, centreY - 2);
        }
        int residents = 2 + random.nextInt(3);
        for (int i = 0; i < residents; i++) {
            int id = switch (locality.type) {
                case FARMING_VILLAGE -> 3086;
                case FORTRESS, FRONTIER_OUTPOST -> 995;
                default -> (i % 2 == 0) ? 385 : 1119;
            };
            addNpc(result, locality, id,
                    centreX + random.nextInt(11) - 5,
                    centreY + random.nextInt(11) - 5);
        }
    }

    private static void addNpc(WorldGenerator.Result result, Locality locality, int id, int x, int y) {
        if (!IslandLayout.inBounds(x, y)) {
            return;
        }
        GeneratedWorld.NpcSpawn spawn = new GeneratedWorld.NpcSpawn(id,
                IslandLayout.worldX(x), IslandLayout.worldY(y), 0, 3);
        spawn.localityId = locality.id;
        result.world.npcSpawns.add(spawn);
    }
}
