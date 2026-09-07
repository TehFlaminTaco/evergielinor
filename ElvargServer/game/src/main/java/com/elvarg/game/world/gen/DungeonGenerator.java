package com.elvarg.game.world.gen;

import com.elvarg.game.world.GeneratedWorld;
import com.elvarg.game.world.codec.PlacedObject;
import com.elvarg.game.world.codec.TerrainRegion;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Builds the island's dungeons.
 *
 * These are shared world content, not instances: a dungeon is generated once
 * when the world is made, written into its own map region, and every player
 * walks into the same rooms. That follows from the decision to run one shared
 * world - with no instancing there is nothing to instance, and a dungeon is
 * simply part of the map that happens to be underground.
 *
 * Floors are stacked on planes within a single region, which is why a dungeon
 * is capped at four. The region is reserved territory outside the island itself,
 * so nothing of the overworld is visible from inside.
 *
 * @author EverGielinor world generator
 */
final class DungeonGenerator {

    /** Cave floor, grey #767676. */
    private static final int FLOOR_UNDERLAY = 54;
    /** Solid rock, near-black #2e2e2e. */
    private static final int ROCK_UNDERLAY = 56;

    private static final int MIN_FLOORS = 2;
    private static final int MAX_FLOORS = 4;
    private static final int ROOMS_PER_FLOOR_MIN = 5;
    private static final int ROOMS_PER_FLOOR_MAX = 9;

    /** How much harder the boss is than the deepest floor, in combat levels. */
    private static final double BOSS_STEP = 1.25;

    /**
     * Upper bound on dungeons in a world. Reserved regions would allow far more,
     * but a world with a dungeon under every hill devalues all of them - and the
     * boss roster only stretches so far before most of them are boss-less.
     */
    private static final int MAX_DUNGEONS = 14;
    /** Roughly one dungeon per this many eligible localities. */
    private static final int LOCALITIES_PER_DUNGEON = 3;

    private DungeonGenerator() {
    }

    static void generate(long seed, WorldGenerator.Result result, IslandGeography geography,
                         MonsterCatalogue monsters, Noise noise) {
        List<Integer> availableRegions = IslandLayout.dungeonRegions();
        Set<BossRoster> usedBosses = EnumSet.noneOf(BossRoster.class);

        // Dungeons go in localities that are dangerous enough to justify one, most
        // dangerous first, so the best bosses land in the best places while the
        // roster still has them.
        List<Locality> candidates = new ArrayList<>();
        for (Locality locality : result.world.localities) {
            if (locality.band.ordinal() >= DifficultyBand.LOW.ordinal()) {
                candidates.add(locality);
            }
        }
        int wanted = Math.min(MAX_DUNGEONS,
                Math.max(3, candidates.size() / LOCALITIES_PER_DUNGEON));
        candidates = spreadAcrossBands(candidates, wanted);

        int nextId = 0;
        for (Locality locality : candidates) {
            if (availableRegions.isEmpty() || nextId >= wanted) {
                break;
            }
            Random random = new Random(seed * 61L + locality.id * 15485863L);

            int[] entrance = findEntranceSite(locality, geography, random);
            if (entrance == null) {
                continue;
            }

            GeneratedWorld.Dungeon dungeon = new GeneratedWorld.Dungeon();
            dungeon.id = nextId++;
            dungeon.regionId = availableRegions.remove(0);
            dungeon.biome = locality.biome;
            dungeon.entryBand = locality.band;
            dungeon.terminalBand = locality.band.harder();
            dungeon.floors = MIN_FLOORS + random.nextInt(MAX_FLOORS - MIN_FLOORS + 1);
            dungeon.entranceX = IslandLayout.worldX(entrance[0]);
            dungeon.entranceY = IslandLayout.worldY(entrance[1]);
            dungeon.name = locality.name + " Depths";

            assignBoss(dungeon, usedBosses, random);
            carve(dungeon, result, monsters, noise, random);

            // The entrance itself: a staircase down, placed on the overworld.
            WorldGenerator.addObject(result.objects, dungeon.entranceX, dungeon.entranceY, 0,
                    GeneratedDungeonObjects.ENTRANCE_OBJECT, PlacedObject.TYPE_SCENERY, 0);

            result.world.dungeons.add(dungeon);
        }
    }

    /**
     * Picks dungeon sites spread across the difficulty range instead of taking the
     * most dangerous localities outright.
     *
     * Sorting by danger and taking the top N puts every dungeon in the endgame,
     * which leaves a new player with nothing to descend into. This takes them in
     * rounds - one band at a time, easiest first - so the island has dungeons a
     * player can grow through.
     */
    private static List<Locality> spreadAcrossBands(List<Locality> candidates, int wanted) {
        java.util.Map<DifficultyBand, List<Locality>> byBand = new java.util.EnumMap<>(DifficultyBand.class);
        for (Locality locality : candidates) {
            byBand.computeIfAbsent(locality.band, k -> new ArrayList<>()).add(locality);
        }
        for (List<Locality> group : byBand.values()) {
            group.sort((a, b) -> a.id - b.id);
        }
        List<Locality> chosen = new ArrayList<>();
        int round = 0;
        while (chosen.size() < wanted) {
            boolean tookAny = false;
            for (DifficultyBand band : DifficultyBand.values()) {
                List<Locality> group = byBand.get(band);
                if (group == null || round >= group.size() || chosen.size() >= wanted) {
                    continue;
                }
                chosen.add(group.get(round));
                tookAny = true;
            }
            if (!tookAny) {
                break;
            }
            round++;
        }
        return chosen;
    }

    /**
     * Chooses a boss whose biome and difficulty suit the dungeon, never reusing
     * one. A dungeon with no match is left boss-less on purpose - the design calls
     * for that rather than forcing an ill-fitting boss into every hole in the
     * ground.
     */
    private static void assignBoss(GeneratedWorld.Dungeon dungeon, Set<BossRoster> used, Random random) {
        List<BossRoster> fits = new ArrayList<>();
        for (BossRoster boss : BossRoster.values()) {
            if (used.contains(boss)) {
                continue;
            }
            if (boss.suits(dungeon.biome, dungeon.terminalBand)) {
                fits.add(boss);
            }
        }
        if (fits.isEmpty()) {
            dungeon.bossNpcId = -1;
            return;
        }
        // Prefer a boss with a real combat method when one fits. MINIGAME_ONLY
        // bosses are already excluded by suits().
        fits.sort((a, b) -> {
            int tier = a.tier().ordinal() - b.tier().ordinal();
            return tier != 0 ? tier : a.ordinal() - b.ordinal();
        });
        BossRoster chosen = fits.get(0);
        used.add(chosen);
        dungeon.bossNpcId = chosen.npcId();
        dungeon.bossName = chosen.displayName();
        dungeon.bossFullyImplemented = chosen.tier() == BossRoster.Tier.IMPLEMENTED;
    }

    private static int[] findEntranceSite(Locality locality, IslandGeography geography, Random random) {
        for (int attempt = 0; attempt < 200; attempt++) {
            int x = locality.centreX + random.nextInt(locality.radius * 2 + 1) - locality.radius;
            int y = locality.centreY + random.nextInt(locality.radius * 2 + 1) - locality.radius;
            if (!IslandLayout.inBounds(x, y)) {
                continue;
            }
            if (geography.biome[x][y].isWater() || !geography.reachable[x][y]) {
                continue;
            }
            // Keep entrances out of settlements so a town square is not a dungeon mouth.
            if (locality.hasTown()
                    && Math.abs(x - locality.townX) < 16 && Math.abs(y - locality.townY) < 16) {
                continue;
            }
            return new int[]{x, y};
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    /**
     * Carves every floor of a dungeon into its region.
     *
     * The whole region starts as solid rock and rooms are cut out of it, so a
     * player can never walk off the edge of the authored area.
     */
    private static void carve(GeneratedWorld.Dungeon dungeon, WorldGenerator.Result result,
                              MonsterCatalogue monsters, Noise noise, Random random) {
        TerrainRegion region = new TerrainRegion();
        int baseX = (dungeon.regionId >> 8) * 64;
        int baseY = (dungeon.regionId & 0xff) * 64;

        for (int plane = 0; plane < dungeon.floors; plane++) {
            fillSolid(region, plane);
            List<int[]> rooms = layoutRooms(region, plane, random);
            if (rooms.isEmpty()) {
                continue;
            }
            connectRooms(region, plane, rooms);

            int[] first = rooms.get(0);
            int[] last = rooms.get(rooms.size() - 1);

            if (plane == 0) {
                dungeon.arrivalX = baseX + first[0];
                dungeon.arrivalY = baseY + first[1];
                // A way back out, at the arrival point.
                WorldGenerator.addObject(result.objects, dungeon.arrivalX, dungeon.arrivalY, plane,
                        GeneratedDungeonObjects.LADDER_UP, PlacedObject.TYPE_SCENERY, 0);
            } else {
                WorldGenerator.addObject(result.objects, baseX + first[0], baseY + first[1], plane,
                        GeneratedDungeonObjects.LADDER_UP, PlacedObject.TYPE_SCENERY, 0);
            }

            boolean deepest = plane == dungeon.floors - 1;
            if (!deepest) {
                WorldGenerator.addObject(result.objects, baseX + last[0], baseY + last[1], plane,
                        GeneratedDungeonObjects.LADDER_DOWN, PlacedObject.TYPE_SCENERY, 0);
            }

            populate(dungeon, result, monsters, rooms, plane, baseX, baseY, deepest, random);
        }
    }

    private static void fillSolid(TerrainRegion region, int plane) {
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                region.setUnderlay(plane, x, y, ROCK_UNDERLAY);
                region.setHeight(plane, x, y, 0);
                region.addFlag(plane, x, y, TerrainRegion.FLAG_BLOCKED);
            }
        }
    }

    /** Cuts non-overlapping rectangular rooms and returns their centres. */
    private static List<int[]> layoutRooms(TerrainRegion region, int plane, Random random) {
        List<int[]> centres = new ArrayList<>();
        List<int[]> boxes = new ArrayList<>();
        int target = ROOMS_PER_FLOOR_MIN + random.nextInt(ROOMS_PER_FLOOR_MAX - ROOMS_PER_FLOOR_MIN + 1);

        for (int attempt = 0; attempt < 200 && boxes.size() < target; attempt++) {
            int w = 5 + random.nextInt(9);
            int h = 5 + random.nextInt(9);
            int x = 3 + random.nextInt(64 - w - 6);
            int y = 3 + random.nextInt(64 - h - 6);
            boolean clash = false;
            for (int[] box : boxes) {
                if (x - 2 < box[0] + box[2] && x + w + 2 > box[0]
                        && y - 2 < box[1] + box[3] && y + h + 2 > box[1]) {
                    clash = true;
                    break;
                }
            }
            if (clash) {
                continue;
            }
            boxes.add(new int[]{x, y, w, h});
            for (int dx = 0; dx < w; dx++) {
                for (int dy = 0; dy < h; dy++) {
                    open(region, plane, x + dx, y + dy);
                }
            }
            centres.add(new int[]{x + w / 2, y + h / 2});
        }
        return centres;
    }

    private static void connectRooms(TerrainRegion region, int plane, List<int[]> rooms) {
        for (int i = 1; i < rooms.size(); i++) {
            int[] a = rooms.get(i - 1);
            int[] b = rooms.get(i);
            int x = a[0];
            int y = a[1];
            while (x != b[0]) {
                x += Integer.signum(b[0] - x);
                open(region, plane, x, y);
                open(region, plane, x, y + 1);
            }
            while (y != b[1]) {
                y += Integer.signum(b[1] - y);
                open(region, plane, x, y);
                open(region, plane, x + 1, y);
            }
        }
    }

    private static void open(TerrainRegion region, int plane, int x, int y) {
        if (x < 1 || y < 1 || x > 62 || y > 62) {
            return;
        }
        region.setUnderlay(plane, x, y, FLOOR_UNDERLAY);
        region.flags[plane][x][y] &= ~TerrainRegion.FLAG_BLOCKED;
    }

    // ------------------------------------------------------------------
    // Inhabitants
    // ------------------------------------------------------------------

    /**
     * Fills a floor with enemies whose level rises with depth, and puts the boss
     * and its chest in the last room of the deepest floor.
     */
    private static void populate(GeneratedWorld.Dungeon dungeon, WorldGenerator.Result result,
                                 MonsterCatalogue monsters, List<int[]> rooms, int plane,
                                 int baseX, int baseY, boolean deepest, Random random) {
        double progress = dungeon.floors == 1 ? 1.0 : (double) plane / (dungeon.floors - 1);
        int entryMid = (dungeon.entryBand.minCombat() + dungeon.entryBand.maxCombat()) / 2;
        int terminalMid = (dungeon.terminalBand.minCombat() + dungeon.terminalBand.maxCombat()) / 2;
        int centre = (int) Math.round(entryMid + (terminalMid - entryMid) * progress);
        int spread = Math.max(6, centre / 3);

        List<MonsterCatalogue.Monster> pool =
                monsters.inLevelRange(Math.max(1, centre - spread), centre + spread);
        if (pool.isEmpty()) {
            pool = monsters.inBand(dungeon.entryBand);
        }

        for (int roomIndex = 0; roomIndex < rooms.size(); roomIndex++) {
            int[] room = rooms.get(roomIndex);
            boolean bossRoom = deepest && roomIndex == rooms.size() - 1;
            if (bossRoom) {
                continue;
            }
            int count = 2 + random.nextInt(3);
            for (int i = 0; i < count && !pool.isEmpty(); i++) {
                MonsterCatalogue.Monster monster = pool.get(random.nextInt(pool.size()));
                GeneratedWorld.NpcSpawn spawn = new GeneratedWorld.NpcSpawn(monster.id,
                        baseX + room[0] + random.nextInt(3) - 1,
                        baseY + room[1] + random.nextInt(3) - 1,
                        plane, 3);
                spawn.dungeonId = dungeon.id;
                result.world.npcSpawns.add(spawn);
            }
        }

        if (!deepest) {
            return;
        }
        int[] bossRoom = rooms.get(rooms.size() - 1);
        dungeon.bossRoomX = baseX + bossRoom[0];
        dungeon.bossRoomY = baseY + bossRoom[1];

        if (dungeon.hasBoss()) {
            GeneratedWorld.NpcSpawn boss = new GeneratedWorld.NpcSpawn(dungeon.bossNpcId,
                    dungeon.bossRoomX, dungeon.bossRoomY, plane, 4);
            boss.dungeonId = dungeon.id;
            result.world.npcSpawns.add(boss);
        } else {
            // A boss-less dungeon still escalates: its last room holds an elite
            // group a step above the deepest floor rather than nothing at all.
            int eliteLevel = (int) Math.round(centre * BOSS_STEP);
            List<MonsterCatalogue.Monster> elites =
                    monsters.inLevelRange((int) (eliteLevel * 0.85), (int) (eliteLevel * 1.15));
            if (elites.isEmpty()) {
                elites = pool;
            }
            for (int i = 0; i < 4 && !elites.isEmpty(); i++) {
                MonsterCatalogue.Monster monster = elites.get(random.nextInt(elites.size()));
                GeneratedWorld.NpcSpawn spawn = new GeneratedWorld.NpcSpawn(monster.id,
                        dungeon.bossRoomX + (i % 2) * 2 - 1,
                        dungeon.bossRoomY + (i / 2) * 2 - 1, plane, 3);
                spawn.dungeonId = dungeon.id;
                result.world.npcSpawns.add(spawn);
            }
        }

        // The reward chest sits in the boss room either way.
        WorldGenerator.addObject(result.objects, dungeon.bossRoomX + 2, dungeon.bossRoomY, plane,
                GeneratedDungeonObjects.BOSS_CHEST, PlacedObject.TYPE_SCENERY, 0);
    }
}
