package com.elvarg.game.world.gen;

import com.elvarg.game.world.GeneratedWorld;
import com.elvarg.game.world.codec.PlacedObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds a settlement out of real buildings.
 *
 * A town is laid out as a paved square with buildings around it and lanes
 * running between them. The buildings are prefabs lifted whole from the original
 * game map, so a generated village has actual houses - walls, windows, doors,
 * upper storeys, furniture - rather than service objects standing in a field.
 *
 * Ground is levelled first. A prefab's geometry assumes flat ground, and a house
 * stamped across a slope has its walls sunk into the hillside.
 *
 * @author EverGielinor world generator
 */
final class TownBuilder {

    /** Half-width of the levelled town footprint. */
    private static final int TOWN_HALF = 22;
    /** Half-width of the paved central square. */
    private static final int PLAZA_HALF = 5;
    /** Gap left between buildings so lanes stay walkable. */
    private static final int BUILDING_GAP = 2;
    /** Half-width of the always-clear centre of the square. */
    private static final int CORE_HALF = 3;

    /** Townsfolk ids, from NpcIdentifiers. */
    private static final int NPC_BANKER = 394;
    private static final int NPC_SHOP_KEEPER = 506;
    private static final int NPC_MAN = 385;
    private static final int NPC_WOMAN = 1119;
    private static final int NPC_GUARD = 995;
    private static final int NPC_FARMER = 3086;

    private TownBuilder() {
    }

    static void build(Locality locality, int centreX, int centreY, IslandGeography geography,
                      WorldGenerator.Result result, boolean[][] occupied, boolean[][] hasObject,
                      PrefabLibrary prefabs, List<TerrainPatch> patches, int[][] overlayOverride) {
        Random random = new Random(locality.id * 104729L + 17L);
        int level = geography.height[centreX][centreY];

        levelGround(locality, centreX, centreY, geography, patches, overlayOverride, level);
        reserveCore(centreX, centreY, occupied);
        List<int[]> buildingSlots = placeBuildings(locality, centreX, centreY, geography, result,
                occupied, hasObject, prefabs, patches, overlayOverride, random, level);
        locality.buildings = buildingSlots.size();
        placeServices(locality, centreX, centreY, result, occupied, hasObject, random);
        placeTownsfolk(locality, centreX, centreY, result, random, buildingSlots);
        reserveFootprint(centreX, centreY, occupied);
    }

    /**
     * Keeps the middle of the square clear before anything is built.
     *
     * Walls clip the tile they stand on and the one beside it, so a house built
     * flush against the square can block the centre even though nothing stands
     * on it. The starting village's centre is the world's spawn point, so this is
     * reserved first and stays empty.
     */
    static void reserveCore(int centreX, int centreY, boolean[][] occupied) {
        for (int dx = -CORE_HALF; dx <= CORE_HALF; dx++) {
            for (int dy = -CORE_HALF; dy <= CORE_HALF; dy++) {
                int x = centreX + dx;
                int y = centreY + dy;
                if (IslandLayout.inBounds(x, y)) {
                    occupied[x][y] = true;
                }
            }
        }
    }

    /**
     * Claims the whole settlement so nothing is scattered into it afterwards.
     *
     * Resources are placed after towns and only avoid tiles already taken, so
     * without this an oak grows in the market square - and, on one seed, on the
     * starting village's spawn tile, which left new players standing inside a
     * tree. Reserving the footprint is done last so it does not also block the
     * buildings from being placed.
     */
    private static void reserveFootprint(int centreX, int centreY, boolean[][] occupied) {
        // Only the built-up part is claimed. Reserving the whole town radius kept
        // resources out but also stripped the grass from the outskirts, which left
        // every village sitting in a bare ring.
        int reserved = TOWN_HALF - 7;
        for (int dx = -reserved; dx <= reserved; dx++) {
            for (int dy = -reserved; dy <= reserved; dy++) {
                if (dx * dx + dy * dy > reserved * reserved) {
                    continue;
                }
                int x = centreX + dx;
                int y = centreY + dy;
                if (IslandLayout.inBounds(x, y)) {
                    occupied[x][y] = true;
                }
            }
        }
    }

    // ------------------------------------------------------------------

    /**
     * Flattens and paves the town footprint. The centre is paved, the rest is
     * packed earth, and the height is forced to the centre's so buildings sit
     * squarely on it.
     */
    private static void levelGround(Locality locality, int centreX, int centreY,
                                    IslandGeography geography, List<TerrainPatch> patches,
                                    int[][] overlayOverride, int level) {
        for (int dx = -TOWN_HALF; dx <= TOWN_HALF; dx++) {
            for (int dy = -TOWN_HALF; dy <= TOWN_HALF; dy++) {
                int x = centreX + dx;
                int y = centreY + dy;
                if (!IslandLayout.inBounds(x, y) || geography.biome[x][y].isWater()) {
                    continue;
                }
                double distance = Math.sqrt(dx * dx + dy * dy);
                if (distance > TOWN_HALF) {
                    continue;
                }
                // Ease the levelling out at the rim so the town does not sit on a
                // plateau with a cliff around it.
                // Flat right out to the rim, blending only over the last few tiles,
                // so the whole buildable area is level rather than just its middle.
                double blend = Math.min(1.0, Math.max(0.0, (TOWN_HALF - distance) / 4.0));
                int blended = (int) Math.round(geography.height[x][y] * (1 - blend) + level * blend);
                geography.height[x][y] = (blended == 1) ? 2 : blended;

                if (Math.abs(dx) <= PLAZA_HALF && Math.abs(dy) <= PLAZA_HALF) {
                    overlayOverride[x][y] = Biome.OVERLAY_PAVING;
                } else if (distance < TOWN_HALF - 6 && overlayOverride[x][y] == 0) {
                    overlayOverride[x][y] = Biome.OVERLAY_DIRT_ROAD;
                }
            }
        }
    }

    /**
     * Lays out streets from the plaza and lines them with buildings.
     *
     * Scattering houses on a ring produced a clearing with sheds in it. Real
     * settlements are organised around the way through them, so four streets run
     * out of the square and buildings stand in rows on both sides of each, set
     * back by a tile. That reads as a village from the ground and from the map.
     */
    private static List<int[]> placeBuildings(Locality locality, int centreX, int centreY,
                                              IslandGeography geography, WorldGenerator.Result result,
                                              boolean[][] occupied, boolean[][] hasObject,
                                              PrefabLibrary prefabs, List<TerrainPatch> patches,
                                              int[][] overlayOverride, Random random, int level) {
        List<int[]> placed = new ArrayList<>();
        if (prefabs.isEmpty()) {
            return placed;
        }
        int wanted = buildingCountFor(locality, random);
        List<BuildingPrefab> candidates = prefabs.fitting(TOWN_HALF - 6, TOWN_HALF - 6);
        if (candidates.isEmpty()) {
            return placed;
        }

        // Streets: north, south, east, west out of the square.
        int[][] directions = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        for (int[] direction : directions) {
            carveStreet(centreX, centreY, direction, geography, overlayOverride);
        }

        // Walk each street, alternating sides, fitting whatever prefab still fits.
        for (int pass = 0; pass < 3 && placed.size() < wanted; pass++) {
            for (int[] direction : directions) {
                for (int side = -1; side <= 1; side += 2) {
                    if (placed.size() >= wanted) {
                        break;
                    }
                    int[] door = placeAlongStreet(centreX, centreY, direction, side, candidates,
                            geography, result, occupied, hasObject, patches, random, level);
                    if (door != null) {
                        placed.add(door);
                    }
                }
            }
        }
        return placed;
    }

    /** Paves a lane out of the square in one direction. */
    private static void carveStreet(int centreX, int centreY, int[] direction,
                                    IslandGeography geography, int[][] overlayOverride) {
        for (int step = 0; step <= TOWN_HALF; step++) {
            for (int across = -1; across <= 1; across++) {
                int x = centreX + direction[0] * step + direction[1] * across;
                int y = centreY + direction[1] * step + direction[0] * across;
                if (!IslandLayout.inBounds(x, y) || geography.biome[x][y].isWater()) {
                    continue;
                }
                overlayOverride[x][y] = Biome.OVERLAY_PAVING;
            }
        }
    }

    /**
     * Fits one building against a street, as far in toward the square as it will
     * go, so rows fill from the centre outward rather than leaving gaps.
     */
    private static int[] placeAlongStreet(int centreX, int centreY, int[] direction, int side,
                                          List<BuildingPrefab> candidates, IslandGeography geography,
                                          WorldGenerator.Result result, boolean[][] occupied,
                                          boolean[][] hasObject, List<TerrainPatch> patches,
                                          Random random, int level) {
        for (int step = PLAZA_HALF + 1; step <= TOWN_HALF - 4; step++) {
            for (int attempt = 0; attempt < 6; attempt++) {
                BuildingPrefab prefab = candidates.get(random.nextInt(candidates.size()));
                // Offset perpendicular to the street by the setback plus the
                // building's own depth, so it sits beside the lane, not on it.
                int depth = (direction[0] != 0) ? prefab.height : prefab.width;
                int perpendicular = 2 + (side < 0 ? depth : 0);
                int anchorX = centreX + direction[0] * step + direction[1] * side * perpendicular;
                int anchorY = centreY + direction[1] * step + direction[0] * side * perpendicular;
                int originX = anchorX - (direction[0] != 0 ? 0 : prefab.width / 2);
                int originY = anchorY - (direction[1] != 0 ? 0 : prefab.height / 2);

                if (!canPlace(originX, originY, prefab, geography, occupied, level)) {
                    continue;
                }
                stamp(prefab, originX, originY, result, occupied, hasObject, patches);
                return new int[]{originX + prefab.doorX, originY + prefab.doorY};
            }
        }
        return null;
    }

    private static int buildingCountFor(Locality locality, Random random) {
        return switch (locality.type) {
            case STARTING_VILLAGE -> 7 + random.nextInt(3);
            case TRADING_POST, FORTRESS -> 6 + random.nextInt(3);
            case FARMING_VILLAGE, FISHING_VILLAGE, MINING_SETTLEMENT -> 5 + random.nextInt(3);
            case LOGGING_CAMP -> 4 + random.nextInt(3);
            case FRONTIER_OUTPOST -> 3 + random.nextInt(2);
            default -> 3;
        };
    }

    /** Whether a prefab fits: on land, level, unoccupied, and clear of its neighbours. */
    private static boolean canPlace(int originX, int originY, BuildingPrefab prefab,
                                    IslandGeography geography, boolean[][] occupied, int level) {
        for (int x = originX - BUILDING_GAP; x < originX + prefab.width + BUILDING_GAP; x++) {
            for (int y = originY - BUILDING_GAP; y < originY + prefab.height + BUILDING_GAP; y++) {
                if (!IslandLayout.inBounds(x, y)) {
                    return false;
                }
                if (geography.biome[x][y].isWater() || !geography.reachable[x][y]) {
                    return false;
                }
                if (occupied[x][y]) {
                    return false;
                }
                // Refuse a site the levelling pass did not reach; a building there
                // would be cut into a slope.
                if (Math.abs(geography.height[x][y] - level) > 4) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Writes a prefab's objects and floors into the world at an origin. */
    private static void stamp(BuildingPrefab prefab, int originX, int originY,
                              WorldGenerator.Result result, boolean[][] occupied,
                              boolean[][] hasObject, List<TerrainPatch> patches) {
        for (BuildingPrefab.PrefabObject object : prefab.objects) {
            int x = originX + object.x;
            int y = originY + object.y;
            if (!IslandLayout.inBounds(x, y)) {
                continue;
            }
            WorldGenerator.addObject(result.objects, IslandLayout.worldX(x), IslandLayout.worldY(y),
                    object.plane, object.id, object.type, object.rotation);
            result.world.townObjectCount++;
        }
        for (BuildingPrefab.PrefabTile tile : prefab.tiles) {
            int x = originX + tile.x;
            int y = originY + tile.y;
            if (!IslandLayout.inBounds(x, y)) {
                continue;
            }
            patches.add(new TerrainPatch(x, y, tile.plane, tile.underlay, tile.overlay,
                    tile.overlayShape, tile.overlayRotation, -1));
        }
        // Reserve the footprint so resources and monsters are not scattered inside.
        for (int x = originX; x < originX + prefab.width; x++) {
            for (int y = originY; y < originY + prefab.height; y++) {
                if (IslandLayout.inBounds(x, y)) {
                    occupied[x][y] = true;
                    hasObject[x][y] = true;
                }
            }
        }
    }

    // ------------------------------------------------------------------

    /**
     * Places the settlement's services around the edge of the plaza.
     *
     * They go outdoors deliberately. An anvil or furnace stamped inside a prefab
     * would land wherever that building's floor plan allows, which is often
     * nowhere reachable - and the original game puts anvils and furnaces on the
     * street in Varrock and Falador anyway.
     */
    private static void placeServices(Locality locality, int centreX, int centreY,
                                      WorldGenerator.Result result, boolean[][] occupied,
                                      boolean[][] hasObject, Random random) {
        int edge = PLAZA_HALF - 1;
        int[][] slots = {
                {-edge, -edge}, {edge, -edge}, {-edge, edge}, {edge, edge},
                {0, -edge}, {0, edge}, {-edge, 0}, {edge, 0},
        };
        int slot = 0;
        for (TownService service : locality.services) {
            if (!service.isObject()) {
                continue;
            }
            int id = service.objectId();
            int rotation = random.nextInt(4);
            int width = ObjectVetting.footprintX(id, rotation);
            int height = ObjectVetting.footprintY(id, rotation);

            // Walk the slots until one has room. Placing blind put a furnace
            // inside a building wall, because a slot can already be taken by a
            // house that reached the plaza edge.
            boolean placed = false;
            for (int attempt = 0; attempt < slots.length && !placed; attempt++) {
                int[] offset = slots[(slot + attempt) % slots.length];
                int x = centreX + offset[0];
                int y = centreY + offset[1];
                if (!fits(x, y, width, height, hasObject)) {
                    continue;
                }
                WorldGenerator.addObject(result.objects, IslandLayout.worldX(x), IslandLayout.worldY(y), 0,
                        id, PlacedObject.TYPE_SCENERY, rotation);
                for (int fx = x; fx < x + width; fx++) {
                    for (int fy = y; fy < y + height; fy++) {
                        if (IslandLayout.inBounds(fx, fy)) {
                            occupied[fx][fy] = true;
                            hasObject[fx][fy] = true;
                        }
                    }
                }
                result.world.townObjectCount++;
                slot = (slot + attempt + 1) % slots.length;
                placed = true;
            }
        }
    }

    /** Whether a footprint is inside the island and free of objects. */
    private static boolean fits(int x, int y, int width, int height, boolean[][] hasObject) {
        for (int fx = x; fx < x + width; fx++) {
            for (int fy = y; fy < y + height; fy++) {
                if (!IslandLayout.inBounds(fx, fy) || hasObject[fx][fy]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void placeTownsfolk(Locality locality, int centreX, int centreY,
                                       WorldGenerator.Result result, Random random,
                                       List<int[]> doorways) {
        if (locality.services.contains(TownService.BANK)) {
            addNpc(result, locality, NPC_BANKER, centreX - PLAZA_HALF + 1, centreY - PLAZA_HALF + 2, null);
        }
        if (locality.services.contains(TownService.GENERAL_STORE)) {
            // The shopkeeper carries the shop this settlement actually runs.
            locality.shopId = ShopAssignment.forLocality(locality);
            addNpc(result, locality, NPC_SHOP_KEEPER, centreX + 2, centreY - 2, locality.shopId);
        }

        int residents = 3 + random.nextInt(4);
        for (int i = 0; i < residents; i++) {
            int id = switch (locality.type) {
                case FARMING_VILLAGE -> NPC_FARMER;
                case FORTRESS, FRONTIER_OUTPOST -> NPC_GUARD;
                default -> (i % 2 == 0) ? NPC_MAN : NPC_WOMAN;
            };
            // Residents stand near their doors where there are doors to stand near.
            int x;
            int y;
            if (!doorways.isEmpty() && i < doorways.size()) {
                x = doorways.get(i)[0] + random.nextInt(3) - 1;
                y = doorways.get(i)[1] + random.nextInt(3) - 1;
            } else {
                x = centreX + random.nextInt(2 * PLAZA_HALF + 1) - PLAZA_HALF;
                y = centreY + random.nextInt(2 * PLAZA_HALF + 1) - PLAZA_HALF;
            }
            addNpc(result, locality, id, x, y, null);
        }
    }

    private static void addNpc(WorldGenerator.Result result, Locality locality, int id,
                               int x, int y, Integer shopId) {
        if (!IslandLayout.inBounds(x, y)) {
            return;
        }
        GeneratedWorld.NpcSpawn spawn = new GeneratedWorld.NpcSpawn(id,
                IslandLayout.worldX(x), IslandLayout.worldY(y), 0, 3);
        spawn.localityId = locality.id;
        if (shopId != null) {
            spawn.shopId = shopId;
        }
        result.world.npcSpawns.add(spawn);
    }
}
