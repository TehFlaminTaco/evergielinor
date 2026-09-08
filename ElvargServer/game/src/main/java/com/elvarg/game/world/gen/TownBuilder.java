package com.elvarg.game.world.gen;

import com.elvarg.game.world.GeneratedWorld;
import com.elvarg.game.world.codec.PlacedObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds a settlement.
 *
 * Buildings are scattered on whatever level ground the site offers, then linked
 * by paths worn between their doors. Nothing is laid out on a grid and no ground
 * is painted wholesale: earlier versions paved a disc with a cross through it,
 * which read from above as exactly that.
 *
 * The buildings themselves are designed by {@link BuildingDesigner} from parts
 * harvested out of the original map, so a settlement is architecturally
 * consistent - one style per village - without being a copy of anywhere.
 *
 * @author EverGielinor world generator
 */
final class TownBuilder {

    /** Half-width of the area a settlement may spread over. */
    private static final int TOWN_HALF = 24;
    /** Gap left between buildings so lanes stay walkable. */
    private static final int BUILDING_GAP = 3;
    /** Half-width of the always-clear centre, which is the world spawn in the start village. */
    private static final int CORE_HALF = 3;
    /**
     * Greatest height spread a building site may already have, in height bytes.
     * Small enough that levelling the footprint afterwards is imperceptible.
     */
    private static final int MAX_BUILDING_SLOPE = 16;
    /**
     * Cost per height byte of climb on a village lane. Higher than the roads
     * between settlements: within a village a track really will go round a bank
     * rather than up it.
     */
    private static final double PATH_CLIMB_WEIGHT = 0.5;
    /** A lane never leaves the settlement, so its search does not need to roam. */
    private static final int PATH_SEARCH_BUDGET = 20_000;
    /** Rings of ground graded from a building's pad back out into the hillside. */
    private static final int PAD_SKIRT = 2;

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
                      StyleLibrary styles, List<TerrainPatch> patches, int[][] overlayOverride) {
        Random random = new Random(locality.id * 104729L + 17L);
        BuildingStyle style = styles.forLocality(locality);
        if (style == null) {
            return;
        }

        reserveCore(centreX, centreY, occupied);
        List<int[]> doorways = scatterBuildings(locality, centreX, centreY, geography, result,
                occupied, hasObject, style, patches, random);
        locality.buildings = doorways.size();

        wearPaths(centreX, centreY, doorways, geography, overlayOverride);
        fenceLanes(centreX, centreY, geography, result, occupied, hasObject, overlayOverride, random);
        placeServices(locality, centreX, centreY, geography, result, occupied, hasObject, random);
        placeTownsfolk(locality, centreX, centreY, result, random, doorways);
        dressSettlement(centreX, centreY, geography, result, occupied, hasObject, overlayOverride, random);
        reserveFootprint(centreX, centreY, occupied);
    }

    /**
     * Keeps the middle of the settlement clear.
     *
     * Walls clip the tile they stand on and the one beside it, so a house built
     * flush against the centre can block it even though nothing stands there. The
     * starting village's centre is the world's spawn point.
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
     * Claims the settlement so resources are not scattered through it afterwards.
     * Only the built-up part, so grass still grows at the outskirts.
     */
    private static void reserveFootprint(int centreX, int centreY, boolean[][] occupied) {
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
     * Scatters buildings across the site.
     *
     * Candidates are drawn anywhere in the settlement's radius and kept if the
     * ground is clear and near enough to level. There is no grid and no ring, so
     * two villages on different terrain come out genuinely different shapes.
     *
     * @return the tile outside each building's door
     */
    private static List<int[]> scatterBuildings(Locality locality, int centreX, int centreY,
                                                IslandGeography geography, WorldGenerator.Result result,
                                                boolean[][] occupied, boolean[][] hasObject,
                                                BuildingStyle style, List<TerrainPatch> patches,
                                                Random random) {
        List<int[]> doorways = new ArrayList<>();
        int wanted = buildingCountFor(locality, random);
        List<Integer> utilities = utilitiesFor(locality);

        for (int attempt = 0; attempt < 900 && doorways.size() < wanted; attempt++) {
            int utility = doorways.size() < utilities.size() ? utilities.get(doorways.size()) : -1;
            BuildingPrefab building = BuildingDesigner.design(style, random, utility);

            // Uniform over the disc rather than over the square, so buildings do
            // not bunch into the corners of the site.
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = Math.sqrt(random.nextDouble()) * (TOWN_HALF - 6) + CORE_HALF + 2;
            int originX = centreX + (int) Math.round(Math.cos(angle) * distance) - building.width / 2;
            int originY = centreY + (int) Math.round(Math.sin(angle) * distance) - building.height / 2;

            // The slope a site may have is relaxed as attempts run out. A village
            // on a hillside would otherwise reject every candidate and come out
            // with one house in it, which is what happened when the limit was a
            // constant: half the settlements on this island had none at all.
            int slopeLimit = MAX_BUILDING_SLOPE + (attempt / 150) * 6;
            if (!canPlace(originX, originY, building, geography, occupied, slopeLimit)) {
                continue;
            }
            stamp(building, originX, originY, geography, result, occupied, hasObject, patches);
            // The approach tile, not the door tile: a lane starting inside the
            // house routes out through whatever wall is cheapest and surfaces
            // somewhere along its side.
            doorways.add(new int[]{originX + building.doorApproachX, originY + building.doorApproachY,
                    originX + building.doorX, originY + building.doorY});
        }
        return doorways;
    }

    /** Utility objects this settlement's buildings should contain, in priority order. */
    private static List<Integer> utilitiesFor(Locality locality) {
        List<Integer> utilities = new ArrayList<>();
        for (TownService service : locality.services) {
            if (service.isObject()) {
                utilities.add(service.objectId());
            }
        }
        return utilities;
    }

    private static int buildingCountFor(Locality locality, Random random) {
        return switch (locality.type) {
            case STARTING_VILLAGE -> 8 + random.nextInt(4);
            case TRADING_POST, FORTRESS -> 7 + random.nextInt(4);
            case FARMING_VILLAGE, FISHING_VILLAGE, MINING_SETTLEMENT -> 6 + random.nextInt(4);
            case LOGGING_CAMP -> 5 + random.nextInt(3);
            case FRONTIER_OUTPOST -> 3 + random.nextInt(3);
            default -> 3;
        };
    }

    /**
     * Whether a building fits: clear, walkable ground that is already close to
     * level.
     *
     * A building's geometry assumes a flat floor, so rather than flattening the
     * landscape to suit it, a site is only accepted where the terrain is nearly
     * flat already.
     */
    private static boolean canPlace(int originX, int originY, BuildingPrefab building,
                                    IslandGeography geography, boolean[][] occupied, int slopeLimit) {
        // The gap ring only has to be clear and walkable - a lane runs over it, and
        // a lane can be on a slope. Measuring the ring's height spread as well is
        // what made siting impossible: a 3-tile margin on every side turns an 8x10
        // building into a 14x16 flatness test, and on relief this steep almost
        // nothing passes that.
        for (int x = originX - BUILDING_GAP; x < originX + building.width + BUILDING_GAP; x++) {
            for (int y = originY - BUILDING_GAP; y < originY + building.height + BUILDING_GAP; y++) {
                if (!IslandLayout.inBounds(x, y) || occupied[x][y]) {
                    return false;
                }
                if (!geography.isWalkable(x, y) || !geography.reachable[x][y]) {
                    return false;
                }
            }
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int x = originX; x < originX + building.width; x++) {
            for (int y = originY; y < originY + building.height; y++) {
                min = Math.min(min, geography.height[x][y]);
                max = Math.max(max, geography.height[x][y]);
            }
        }
        return max - min <= slopeLimit;
    }

    /** Writes a building's objects and floors into the world at an origin. */
    private static void stamp(BuildingPrefab building, int originX, int originY,
                              IslandGeography geography, WorldGenerator.Result result,
                              boolean[][] occupied, boolean[][] hasObject, List<TerrainPatch> patches) {
        // Level just this footprint, to the height it mostly already is.
        long sum = 0;
        int count = 0;
        for (int x = originX; x < originX + building.width; x++) {
            for (int y = originY; y < originY + building.height; y++) {
                if (IslandLayout.inBounds(x, y)) {
                    sum += geography.height[x][y];
                    count++;
                }
            }
        }
        int level = count == 0 ? 0 : (int) (sum / count);
        if (level == 1) {
            // Height byte 1 reads back as 0 in the landscape format, so a floor at
            // 1 would silently sit a step below where it was put.
            level = 2;
        }
        for (int x = originX; x < originX + building.width; x++) {
            for (int y = originY; y < originY + building.height; y++) {
                if (IslandLayout.inBounds(x, y)) {
                    geography.height[x][y] = level;
                }
            }
        }
        // Grade a skirt out from the pad so a house on a slope stands on a terrace
        // that eases back into the hill, rather than on a plateau with a step all
        // the way round it.
        for (int ring = 1; ring <= PAD_SKIRT; ring++) {
            double blend = 1.0 - (double) ring / (PAD_SKIRT + 1);
            for (int x = originX - ring; x < originX + building.width + ring; x++) {
                for (int y = originY - ring; y < originY + building.height + ring; y++) {
                    boolean onRing = x == originX - ring || y == originY - ring
                            || x == originX + building.width + ring - 1
                            || y == originY + building.height + ring - 1;
                    if (!onRing || !IslandLayout.inBounds(x, y)) {
                        continue;
                    }
                    int blended = (int) Math.round(geography.height[x][y] * (1 - blend) + level * blend);
                    geography.height[x][y] = blended == 1 ? 2 : blended;
                }
            }
        }

        for (BuildingPrefab.PrefabObject object : building.objects) {
            int x = originX + object.x;
            int y = originY + object.y;
            if (!IslandLayout.inBounds(x, y)) {
                continue;
            }
            WorldGenerator.addObject(result.objects, IslandLayout.worldX(x), IslandLayout.worldY(y),
                    object.plane, object.id, object.type, object.rotation);
            result.world.townObjectCount++;
        }
        for (BuildingPrefab.PrefabTile tile : building.tiles) {
            int x = originX + tile.x;
            int y = originY + tile.y;
            if (!IslandLayout.inBounds(x, y)) {
                continue;
            }
            patches.add(new TerrainPatch(x, y, tile.plane, tile.underlay, tile.overlay,
                    tile.overlayShape, tile.overlayRotation, -1));
        }
        for (int x = originX; x < originX + building.width; x++) {
            for (int y = originY; y < originY + building.height; y++) {
                if (IslandLayout.inBounds(x, y)) {
                    occupied[x][y] = true;
                    hasObject[x][y] = true;
                }
            }
        }
    }

    // ------------------------------------------------------------------

    /**
     * Wears paths between the doors.
     *
     * Every door is joined to the settlement's centre by a least-cost walk that
     * charges for climbing and gives a discount for ground already worn, so tracks
     * bend around what is in the way and merge into shared lanes on their way in.
     * Only the tiles a track crosses are painted - the settlement as a whole keeps
     * its own ground, because painting a disc and a cross through it is exactly
     * what a village should not look like from above.
     *
     * Doors are walked in order of distance so the near ones lay the trunk and the
     * far ones join it, rather than every door cutting its own line to the middle.
     */
    private static void wearPaths(int centreX, int centreY, List<int[]> doorways,
                                  IslandGeography geography, int[][] overlayOverride) {
        List<int[]> ordered = new ArrayList<>(doorways);
        ordered.sort((a, b) -> Integer.compare(
                (a[0] - centreX) * (a[0] - centreX) + (a[1] - centreY) * (a[1] - centreY),
                (b[0] - centreX) * (b[0] - centreX) + (b[1] - centreY) * (b[1] - centreY)));
        for (int[] door : ordered) {
            if (!IslandLayout.inBounds(door[0], door[1]) || !geography.isWalkable(door[0], door[1])) {
                continue;
            }
            List<int[]> route = Pathing.route(geography, door[0], door[1], centreX, centreY,
                    overlayOverride, PATH_CLIMB_WEIGHT, PATH_SEARCH_BUDGET);
            if (route == null) {
                // Still lay the doorstep, so the entrance reads as an entrance
                // even where the lane could not be walked to the middle.
                overlayOverride[door[0]][door[1]] = Biome.OVERLAY_DIRT_ROAD;
                continue;
            }
            for (int[] tile : route) {
                if (overlayOverride[tile[0]][tile[1]] == 0) {
                    overlayOverride[tile[0]][tile[1]] = Biome.OVERLAY_DIRT_ROAD;
                }
            }
        }
        // The middle of a settlement is where every lane meets, so it is trodden
        // bare rather than being one tile wide like the lanes feeding it.
        for (int dx = -CORE_HALF; dx <= CORE_HALF; dx++) {
            for (int dy = -CORE_HALF; dy <= CORE_HALF; dy++) {
                int x = centreX + dx;
                int y = centreY + dy;
                if (IslandLayout.inBounds(x, y) && geography.isWalkable(x, y)
                        && overlayOverride[x][y] == 0 && dx * dx + dy * dy <= CORE_HALF * CORE_HALF) {
                    overlayOverride[x][y] = Biome.OVERLAY_DIRT_ROAD;
                }
            }
        }
    }

    /**
     * Fences the verges of the settlement's lanes.
     *
     * Same machinery as the roads between towns, scoped to the village: a run of
     * wall-type fence along one side of a lane, facing it.
     */
    private static void fenceLanes(int centreX, int centreY, IslandGeography geography,
                                   WorldGenerator.Result result, boolean[][] occupied,
                                   boolean[][] hasObject, int[][] overlayOverride, Random random) {
        boolean[][] isLane = new boolean[IslandLayout.SIZE][IslandLayout.SIZE];
        List<int[]> tiles = new ArrayList<>();
        for (int dx = -TOWN_HALF; dx <= TOWN_HALF; dx++) {
            for (int dy = -TOWN_HALF; dy <= TOWN_HALF; dy++) {
                int x = centreX + dx;
                int y = centreY + dy;
                if (IslandLayout.inBounds(x, y) && overlayOverride[x][y] == Biome.OVERLAY_DIRT_ROAD) {
                    isLane[x][y] = true;
                    tiles.add(new int[]{x, y});
                }
            }
        }
        Fencing.layVerges(geography, isLane, tiles, random, (id, x, y, rotation) -> {
            if (!IslandLayout.inBounds(x, y) || occupied[x][y] || hasObject[x][y]) {
                return false;
            }
            WorldGenerator.addObject(result.objects, IslandLayout.worldX(x), IslandLayout.worldY(y), 0,
                    id, PlacedObject.TYPE_WALL, rotation);
            hasObject[x][y] = true;
            result.world.clutterObjectCount++;
            return true;
        });
    }

    /**
     * Places services that did not fit inside a building, on open ground near the
     * middle of the settlement.
     */
    private static void placeServices(Locality locality, int centreX, int centreY,
                                      IslandGeography geography, WorldGenerator.Result result,
                                      boolean[][] occupied, boolean[][] hasObject, Random random) {
        List<Integer> utilities = utilitiesFor(locality);
        // The first few are inside buildings already; anything left over stands out.
        int fromIndex = Math.min(utilities.size(), locality.buildings);
        for (int i = fromIndex; i < utilities.size(); i++) {
            int id = utilities.get(i);
            int rotation = random.nextInt(4);
            int width = ObjectVetting.footprintX(id, rotation);
            int height = ObjectVetting.footprintY(id, rotation);
            for (int attempt = 0; attempt < 80; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = CORE_HALF + 1 + random.nextDouble() * 6;
                int x = centreX + (int) Math.round(Math.cos(angle) * distance);
                int y = centreY + (int) Math.round(Math.sin(angle) * distance);
                if (!fits(x, y, width, height, geography, hasObject)) {
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
                break;
            }
        }
    }

    private static boolean fits(int x, int y, int width, int height,
                                IslandGeography geography, boolean[][] hasObject) {
        for (int fx = x; fx < x + width; fx++) {
            for (int fy = y; fy < y + height; fy++) {
                if (!IslandLayout.inBounds(fx, fy) || hasObject[fx][fy]) {
                    return false;
                }
                if (!geography.isWalkable(fx, fy)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Dresses the settlement: fences beside the tracks, and stones and grass
     * gathered around the buildings rather than spread evenly.
     */
    private static void dressSettlement(int centreX, int centreY, IslandGeography geography,
                                        WorldGenerator.Result result, boolean[][] occupied,
                                        boolean[][] hasObject, int[][] overlayOverride, Random random) {
        for (int dx = -TOWN_HALF; dx <= TOWN_HALF; dx++) {
            for (int dy = -TOWN_HALF; dy <= TOWN_HALF; dy++) {
                int x = centreX + dx;
                int y = centreY + dy;
                if (!IslandLayout.inBounds(x, y) || occupied[x][y] || hasObject[x][y]) {
                    continue;
                }
                if (!geography.isWalkable(x, y) || overlayOverride[x][y] != 0) {
                    continue;
                }
                boolean besidePath = false;
                boolean nearBuilding = false;
                for (int nx = -1; nx <= 1 && !besidePath; nx++) {
                    for (int ny = -1; ny <= 1; ny++) {
                        int px = x + nx;
                        int py = y + ny;
                        if (!IslandLayout.inBounds(px, py)) {
                            continue;
                        }
                        if (overlayOverride[px][py] == Biome.OVERLAY_DIRT_ROAD) {
                            besidePath = true;
                            break;
                        }
                    }
                }
                for (int nx = -3; nx <= 3 && !nearBuilding; nx++) {
                    for (int ny = -3; ny <= 3; ny++) {
                        int px = x + nx;
                        int py = y + ny;
                        if (IslandLayout.inBounds(px, py) && hasObject[px][py]) {
                            nearBuilding = true;
                            break;
                        }
                    }
                }
                if (besidePath) {
                    // Fences beside a lane are laid as runs by fenceLanes; here
                    // the verge only gets undergrowth, so the two do not fight
                    // over the same tiles.
                    continue;
                }
                if (nearBuilding && random.nextInt(5) == 0) {
                    int id = SETTLEMENT_CLUTTER[random.nextInt(SETTLEMENT_CLUTTER.length)];
                    place(result, occupied, hasObject, geography, id, x, y, random.nextInt(4));
                }
            }
        }
    }

    /** Fence pieces used along village tracks. */
    /** Ground clutter that reads as a lived-in yard. */
    private static final int[] SETTLEMENT_CLUTTER = {3794, 3795, 4815, 1298, 1173};

    private static void place(WorldGenerator.Result result, boolean[][] occupied,
                              boolean[][] hasObject, IslandGeography geography,
                              int id, int x, int y, int rotation) {
        if (!ObjectVetting.isPlaceable(id)) {
            return;
        }
        int width = ObjectVetting.footprintX(id, rotation);
        int height = ObjectVetting.footprintY(id, rotation);
        if (!fits(x, y, width, height, geography, hasObject)) {
            return;
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
        result.world.clutterObjectCount++;
    }

    private static void placeTownsfolk(Locality locality, int centreX, int centreY,
                                       WorldGenerator.Result result, Random random,
                                       List<int[]> doorways) {
        if (locality.services.contains(TownService.BANK)) {
            addNpc(result, locality, NPC_BANKER, centreX - 2, centreY + 2, null);
        }
        if (locality.services.contains(TownService.GENERAL_STORE)) {
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
            int x;
            int y;
            if (!doorways.isEmpty() && i < doorways.size()) {
                x = doorways.get(i)[0] + random.nextInt(3) - 1;
                y = doorways.get(i)[1] + random.nextInt(3) - 1;
            } else {
                x = centreX + random.nextInt(11) - 5;
                y = centreY + random.nextInt(11) - 5;
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
