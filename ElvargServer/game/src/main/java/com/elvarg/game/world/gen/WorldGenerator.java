package com.elvarg.game.world.gen;

import com.elvarg.game.world.GeneratedWorld;
import com.elvarg.game.world.codec.PlacedObject;
import com.elvarg.game.world.codec.TerrainRegion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Turns a seed into a finite island: geography, then biomes, then localities,
 * then everything a locality implies.
 *
 * The ordering is the point. Nothing is scattered independently - a resource is
 * placed because a locality of that identity and difficulty should have it, and
 * a settlement exists because a locality needs somewhere to process what grows
 * nearby. Randomness picks which world; these rules decide whether it makes
 * sense.
 *
 * @author EverGielinor world generator
 */
public final class WorldGenerator {

    /** Side of a locality cell, in tiles. */
    private static final int LOCALITY_CELL = 80;
    /** A cell needs at least this much land to become a locality. */
    private static final int MIN_LOCALITY_LAND = 900;
    /** Noise value above which ground clutter may appear at all. */
    private static final double GATE_FLOOR = 0.48;

    /** Townsfolk ids, from NpcIdentifiers. */
    private static final int NPC_BANKER = 394;
    private static final int NPC_SHOP_KEEPER = 506;
    private static final int NPC_MAN = 385;
    private static final int NPC_WOMAN = 1119;
    private static final int NPC_GUARD = 995;
    private static final int NPC_FARMER = 3086;

    /** Result of a generation run: the world record plus the map data to install. */
    public static final class Result {
        public final GeneratedWorld world = new GeneratedWorld();
        public final Map<Integer, TerrainRegion> terrain = new HashMap<>();
        public final Map<Integer, List<PlacedObject>> objects = new HashMap<>();
        public IslandGeography geography;
    }

    private final long seed;
    private final MonsterCatalogue monsters;
    private final Noise noise;

    private IslandGeography geography;
    private Result result;
    private PrefabLibrary prefabs = PrefabLibrary.load(java.nio.file.Paths.get("../data/definitions"));
    /** Terrain overrides from buildings and paving, applied when terrain is painted. */
    private final java.util.List<TerrainPatch> patches = new ArrayList<>();
    /**
     * Tiles claimed for placement. This is broader than "has an object on it" -
     * a town reserves its whole built-up area to keep resources out of it.
     */
    private boolean[][] occupied;
    /**
     * Tiles an object genuinely covers. Kept separate from {@link #occupied}
     * because the spawn search needs to know what is really in the way, not what
     * has merely been set aside.
     */
    private boolean[][] hasObject;
    /**
     * Overlay chosen by a settlement or road rather than by the biome. Terrain is
     * painted after content is placed, so these are applied last and win.
     */
    private int[][] overlayOverride;

    public WorldGenerator(long seed, MonsterCatalogue monsters) {
        this.seed = seed;
        this.monsters = monsters;
        this.noise = new Noise(seed);
    }

    public static Result generate(long seed, Path definitionsDirectory) throws IOException {
        WorldGenerator generator = new WorldGenerator(seed, MonsterCatalogue.load(definitionsDirectory));
        generator.prefabs = PrefabLibrary.load(definitionsDirectory);
        return generator.run();
    }

    public Result run() {
        result = new Result();
        result.world.seed = seed;
        result.world.generatorVersion = GeneratedWorld.GENERATOR_VERSION;
        result.world.generatedAtEpochMillis = System.currentTimeMillis();

        geography = new IslandGeography(seed);
        geography.generate();
        result.geography = geography;
        occupied = new boolean[geography.size][geography.size];
        overlayOverride = new int[geography.size][geography.size];
        hasObject = new boolean[geography.size][geography.size];

        int[] start = chooseStartingSite();
        geography.computeReachability(start[0], start[1]);

        buildLocalities(start);
        buildTowns(start);
        buildRoads();
        placeResources();
        fenceRoads();
        placeMonsters();
        DungeonGenerator.generate(seed, this, result, geography, monsters, noise);
        placeClutter();

        finaliseSpawn(start);
        paintTerrain();
        return result;
    }

    // ------------------------------------------------------------------
    // Starting site
    // ------------------------------------------------------------------

    /**
     * Picks the starting village: coastal, gentle, temperate, and surrounded by
     * enough walkable land to hold a settlement.
     *
     * A new world must never start somewhere inaccessible or hostile, so this
     * scores candidates rather than taking the first that fits, and the scan is a
     * fixed sweep so the answer depends only on the seed.
     */
    private int[] chooseStartingSite() {
        int size = geography.size;
        int bestX = -1;
        int bestY = -1;
        double bestScore = -1;
        for (int x = 24; x < size - 24; x += 2) {
            for (int y = 24; y < size - 24; y += 2) {
                Biome biome = geography.biome[x][y];
                if (biome != Biome.PLAINS && biome != Biome.GRASSLAND) {
                    continue;
                }
                int fromSea = geography.distanceFromSea[x][y];
                // Near the coast, but not on the beach itself.
                if (fromSea < 6 || fromSea > 40) {
                    continue;
                }
                int land = 0;
                int flat = 0;
                int centre = geography.height[x][y];
                for (int dx = -10; dx <= 10; dx++) {
                    for (int dy = -10; dy <= 10; dy++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (!IslandLayout.inBounds(nx, ny) || geography.biome[nx][ny].isWater()) {
                            continue;
                        }
                        land++;
                        if (Math.abs(geography.height[nx][ny] - centre) <= 3) {
                            flat++;
                        }
                    }
                }
                if (land < 330) {
                    continue;
                }
                double score = flat / 441.0 * 0.7 + land / 441.0 * 0.3;
                if (score > bestScore) {
                    bestScore = score;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        if (bestX < 0) {
            // No temperate coastal shelf: fall back to the flattest reachable land.
            for (int x = 20; x < size - 20; x += 2) {
                for (int y = 20; y < size - 20; y += 2) {
                    if (geography.biome[x][y].isWater() || !geography.biome[x][y].isHabitable()) {
                        continue;
                    }
                    double score = -geography.distanceFromSea[x][y];
                    if (score > bestScore) {
                        bestScore = score;
                        bestX = x;
                        bestY = y;
                    }
                }
            }
        }
        result.world.spawnX = IslandLayout.worldX(bestX);
        result.world.spawnY = IslandLayout.worldY(bestY);
        result.world.spawnZ = 0;
        return new int[]{bestX, bestY};
    }

    /**
     * Fixes the world's spawn point once everything has been placed.
     *
     * Choosing it up front kept going wrong: the tile is chosen before towns,
     * resources and clutter exist, and any of them can end up on it or beside it
     * - and a wall clips the tile next to it, not just its own. So the spawn is
     * decided last, from tiles this generator knows are empty, preferring the
     * starting village's square and searching outward from it otherwise.
     */
    private void finaliseSpawn(int[] preferred) {
        int originX = preferred[0];
        int originY = preferred[1];
        for (Locality locality : result.world.localities) {
            if (locality.type == LocalityType.STARTING_VILLAGE && locality.hasTown()) {
                originX = locality.townX;
                originY = locality.townY;
                break;
            }
        }
        int[] chosen = findClearTile(originX, originY);
        if (chosen == null) {
            // Nothing clear anywhere near: fall back to the original site rather
            // than leaving the world without a spawn. Validation will flag it.
            chosen = new int[]{originX, originY};
        }
        result.world.spawnX = IslandLayout.worldX(chosen[0]);
        result.world.spawnY = IslandLayout.worldY(chosen[1]);
        result.world.spawnZ = 0;
    }

    /**
     * Nearest tile with nothing on it and nothing on its neighbours, searched in
     * expanding rings so the answer is the closest one and does not depend on
     * iteration order.
     */
    private int[] findClearTile(int originX, int originY) {
        for (int radius = 0; radius < 48; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) {
                        continue;
                    }
                    int x = originX + dx;
                    int y = originY + dy;
                    if (!IslandLayout.inBounds(x, y)) {
                        continue;
                    }
                    if (geography.biome[x][y].isWater() || !geography.reachable[x][y]) {
                        continue;
                    }
                    if (hasAnythingWithin(x, y, 1)) {
                        continue;
                    }
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }

    /** Whether any object sits on a tile or within {@code margin} of it. */
    private boolean hasAnythingWithin(int centreX, int centreY, int margin) {
        for (int x = centreX - margin; x <= centreX + margin; x++) {
            for (int y = centreY - margin; y <= centreY + margin; y++) {
                if (!IslandLayout.inBounds(x, y) || hasObject[x][y]) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Localities
    // ------------------------------------------------------------------

    private void buildLocalities(int[] start) {
        int size = geography.size;
        int cells = size / LOCALITY_CELL;
        int nextId = 0;

        for (int cx = 0; cx < cells; cx++) {
            for (int cy = 0; cy < cells; cy++) {
                int x0 = cx * LOCALITY_CELL;
                int y0 = cy * LOCALITY_CELL;

                Map<Biome, Integer> counts = new HashMap<>();
                int land = 0;
                int reachable = 0;
                long sumX = 0;
                long sumY = 0;
                for (int x = x0; x < x0 + LOCALITY_CELL; x++) {
                    for (int y = y0; y < y0 + LOCALITY_CELL; y++) {
                        Biome biome = geography.biome[x][y];
                        if (biome.isWater()) {
                            continue;
                        }
                        land++;
                        sumX += x;
                        sumY += y;
                        counts.merge(biome, 1, Integer::sum);
                        if (geography.reachable[x][y]) {
                            reachable++;
                        }
                    }
                }
                if (land < MIN_LOCALITY_LAND || reachable * 2 < land) {
                    continue;
                }

                Biome dominant = counts.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(Biome.PLAINS);

                Locality locality = new Locality();
                locality.id = nextId++;
                locality.centreX = (int) (sumX / land);
                locality.centreY = (int) (sumY / land);
                locality.radius = LOCALITY_CELL / 2;
                locality.biome = dominant;
                locality.coastal = geography.distanceFromSea[locality.centreX][locality.centreY] < 24;
                // The centroid of a cell's land can land on an offshore islet even
                // when most of the cell is connected, which would site a town
                // somewhere no player can walk to. Pull it to the nearest reachable
                // tile instead.
                if (!geography.reachable[locality.centreX][locality.centreY]) {
                    int[] anchor = nearestReachable(locality.centreX, locality.centreY, x0, y0);
                    if (anchor == null) {
                        continue;
                    }
                    locality.centreX = anchor[0];
                    locality.centreY = anchor[1];
                }

                locality.distanceFromStart = (int) Math.round(
                        Math.hypot(locality.centreX - start[0], locality.centreY - start[1]));
                result.world.localities.add(locality);
            }
        }

        assignDifficulty(start);
        assignIdentities();
    }

    /**
     * Assigns difficulty bands once every locality is known.
     *
     * Remoteness is measured against the furthest locality that actually exists,
     * not against a theoretical island radius. Normalising against the theory
     * saturates the scale whenever the start village sits near an edge, which
     * pushes almost the whole island into the top bands and leaves no mid-game.
     */
    private void assignDifficulty(int[] start) {
        int furthest = 1;
        int deepest = 1;
        for (Locality locality : result.world.localities) {
            furthest = Math.max(furthest, locality.distanceFromStart);
            deepest = Math.max(deepest, geography.distanceFromSea[locality.centreX][locality.centreY]);
        }
        for (Locality locality : result.world.localities) {
            double remoteness = (double) locality.distanceFromStart / furthest;
            double inland = (double) geography.distanceFromSea[locality.centreX][locality.centreY] / deepest;
            // Danger grows with distance from home and with depth inland, which is
            // the pattern the design wants players to be able to learn.
            double danger = remoteness * 0.68 + inland * 0.32;

            int top = DifficultyBand.values().length - 1;
            DifficultyBand geographic = DifficultyBand.forOrdinal((int) Math.round(danger * top));
            DifficultyBand fromBiome = locality.biome.baseDifficulty();
            locality.band = geographic.ordinal() >= fromBiome.ordinal() ? geographic : fromBiome;

            // The coast is the approach to the island, so it stays gentler than the
            // interior even a long way round from the start.
            if (locality.coastal && locality.biome.isHabitable()
                    && locality.band.ordinal() > DifficultyBand.HIGH.ordinal()) {
                locality.band = DifficultyBand.HIGH;
            }
            if (locality.distanceFromStart < LOCALITY_CELL) {
                locality.band = DifficultyBand.BEGINNER;
            }
        }
    }

    /** Names and identities, assigned after difficulty so both can inform them. */
    private void assignIdentities() {
        java.util.Set<String> usedNames = new java.util.HashSet<>();
        Locality startLocality = null;
        for (Locality locality : result.world.localities) {
            if (startLocality == null
                    || locality.distanceFromStart < startLocality.distanceFromStart) {
                startLocality = locality;
            }
        }
        for (Locality locality : result.world.localities) {
            if (locality == startLocality) {
                locality.band = DifficultyBand.BEGINNER;
                locality.type = LocalityType.STARTING_VILLAGE;
            } else {
                locality.type = LocalityType.forBiome(locality.biome, locality.band, locality.coastal,
                        noise.intAt(locality.centreX, locality.centreY, 97));
            }
            locality.services.addAll(locality.type.services());
            locality.name = uniqueName(locality, usedNames);
        }
    }

    /** Nearest walkable tile to a point, searched within its own locality cell. */
    private int[] nearestReachable(int cx, int cy, int cellX, int cellY) {
        int best = Integer.MAX_VALUE;
        int[] found = null;
        for (int x = cellX; x < cellX + LOCALITY_CELL; x++) {
            for (int y = cellY; y < cellY + LOCALITY_CELL; y++) {
                if (!geography.reachable[x][y]) {
                    continue;
                }
                int distance = Math.abs(x - cx) + Math.abs(y - cy);
                if (distance < best) {
                    best = distance;
                    found = new int[]{x, y};
                }
            }
        }
        return found;
    }

    private static final String[] NAME_STEMS = {
            "Ash", "Bram", "Cinder", "Dun", "Elder", "Fen", "Grim", "Hollow", "Iron", "Kelp",
            "Loam", "Mire", "North", "Old", "Pine", "Quarry", "Raven", "Stone", "Thorn", "Umber",
            "Vale", "West", "Yew", "Amber", "Bleak", "Coral", "Drift", "Ember"
    };

    /**
     * Names a locality, walking the stem list until an unused name is found.
     * Duplicate place names are worse than slightly arbitrary ones - a player
     * cannot tell two "Yew Timberfall"s apart, and neither can a host reading a
     * validation report.
     */
    private String uniqueName(Locality locality, java.util.Set<String> used) {
        int start = noise.intAt(locality.centreX * 7 + 13, locality.centreY * 3 + 5, NAME_STEMS.length);
        for (int i = 0; i < NAME_STEMS.length; i++) {
            String candidate = NAME_STEMS[(start + i) % NAME_STEMS.length] + locality.type.nameSuffix();
            if (used.add(candidate)) {
                return candidate;
            }
        }
        String fallback = NAME_STEMS[start] + locality.type.nameSuffix() + " " + locality.id;
        used.add(fallback);
        return fallback;
    }

    // ------------------------------------------------------------------
    // Towns
    // ------------------------------------------------------------------

    private void buildTowns(int[] start) {
        for (Locality locality : result.world.localities) {
            if (!locality.type.isSettled()) {
                continue;
            }
            int[] site = (locality.type == LocalityType.STARTING_VILLAGE)
                    ? start
                    : findFlatSite(locality.centreX, locality.centreY, locality.radius - 12, 11);
            if (site == null) {
                // No buildable ground: the locality keeps its identity but loses its
                // settlement, and validation will notice if that leaves a gap.
                locality.services.clear();
                continue;
            }
            locality.townX = site[0];
            locality.townY = site[1];
            TownBuilder.build(locality, site[0], site[1], geography, result, occupied, hasObject,
                    prefabs, patches, overlayOverride);
        }
    }

    /**
     * Finds level ground of at least {@code half*2+1} square near a point, so a
     * town is not built across a cliff. Returns null when nothing qualifies.
     */
    private int[] findFlatSite(int cx, int cy, int searchRadius, int half) {
        int bestX = -1;
        int bestY = -1;
        int bestSpread = Integer.MAX_VALUE;
        for (int x = cx - searchRadius; x <= cx + searchRadius; x += 2) {
            for (int y = cy - searchRadius; y <= cy + searchRadius; y += 2) {
                if (!IslandLayout.inBounds(x - half, y - half) || !IslandLayout.inBounds(x + half, y + half)) {
                    continue;
                }
                int min = Integer.MAX_VALUE;
                int max = Integer.MIN_VALUE;
                boolean ok = true;
                for (int dx = -half; dx <= half && ok; dx++) {
                    for (int dy = -half; dy <= half; dy++) {
                        Biome biome = geography.biome[x + dx][y + dy];
                        if (biome.isWater() || !geography.reachable[x + dx][y + dy]) {
                            ok = false;
                            break;
                        }
                        int h = geography.height[x + dx][y + dy];
                        min = Math.min(min, h);
                        max = Math.max(max, h);
                    }
                }
                if (!ok) {
                    continue;
                }
                int spread = max - min;
                if (spread < bestSpread) {
                    bestSpread = spread;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        // TownBuilder levels its own plaza, so the site only has to be walkable
        // land that is not a cliff face. Rejecting anything but near-flat ground
        // left most localities unsettled and the world without infrastructure.
        return (bestX < 0 || bestSpread > 34) ? null : new int[]{bestX, bestY};
    }

    // ------------------------------------------------------------------
    // Roads
    // ------------------------------------------------------------------

    /**
     * Links each town to its nearest neighbour with a walked path.
     *
     * The route is a greedy walk that prefers level ground rather than a straight
     * line, so roads bend around hills the way a real track would, and a road is
     * never laid across water - if the walk cannot get there, no road is drawn and
     * validation reports the pair as unconnected.
     */
    /** Tiles a road runs over, packed as x<<16|y, used to fence the verges. */
    private final java.util.Set<Integer> roadTiles = new java.util.HashSet<>();

    private void buildRoads() {
        List<Locality> towns = new ArrayList<>();
        for (Locality locality : result.world.localities) {
            if (locality.hasTown()) {
                towns.add(locality);
            }
        }
        // Each town links to its two nearest neighbours rather than one, which
        // turns a chain of settlements into a road network a player can actually
        // navigate by.
        for (Locality from : towns) {
            List<Locality> others = new ArrayList<>(towns);
            others.remove(from);
            others.sort((a, b) -> Integer.compare(
                    Math.abs(a.townX - from.townX) + Math.abs(a.townY - from.townY),
                    Math.abs(b.townX - from.townX) + Math.abs(b.townY - from.townY)));
            for (int i = 0; i < Math.min(2, others.size()); i++) {
                traceRoad(from.townX, from.townY, others.get(i).townX, others.get(i).townY);
            }
        }
    }

    private void traceRoad(int fromX, int fromY, int toX, int toY) {
        int x = fromX;
        int y = fromY;
        int guard = 0;
        int limit = (Math.abs(toX - fromX) + Math.abs(toY - fromY)) * 4 + 64;
        while ((x != toX || y != toY) && guard++ < limit) {
            int bestX = x;
            int bestY = y;
            double bestCost = Double.MAX_VALUE;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    int nx = x + dx;
                    int ny = y + dy;
                    if (!IslandLayout.inBounds(nx, ny) || !geography.isWalkable(nx, ny)) {
                        continue;
                    }
                    double remaining = Math.hypot(toX - nx, toY - ny);
                    double climb = Math.abs(geography.height[nx][ny] - geography.height[x][y]);
                    double cost = remaining + climb * 2.5;
                    if (cost < bestCost) {
                        bestCost = cost;
                        bestX = nx;
                        bestY = ny;
                    }
                }
            }
            if (bestX == x && bestY == y) {
                return;
            }
            x = bestX;
            y = bestY;
            // Two tiles wide, so a road reads as a road from the ground.
            for (int across = 0; across <= 1; across++) {
                int rx = x + across;
                int ry = y;
                if (IslandLayout.inBounds(rx, ry) && !geography.biome[rx][ry].isWater()
                        && overlayOverride[rx][ry] == 0) {
                    overlayOverride[rx][ry] = Biome.OVERLAY_DIRT_ROAD;
                    roadTiles.add((rx << 16) | ry);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Resources
    // ------------------------------------------------------------------

    /**
     * Places resources as places rather than as sprinkles.
     *
     * Scattering a weighted draw over a locality gives an even fog of one tree
     * every few tiles, which reads as noise. Real woodland is a stand of mostly
     * one species with a few others in it, and an ore field is a worked patch of
     * bare ground with rocks in it. So resources are placed as clusters: groves
     * for trees, mines for ore, each with a dominant kind and a minority.
     */
    private void placeResources() {
        for (Locality locality : result.world.localities) {
            Random random = localityRandom(locality, 0x8E5D);
            List<ResourceKind> pool = poolFor(locality);
            if (pool.isEmpty()) {
                continue;
            }
            List<ResourceKind> trees = pool.stream()
                    .filter(k -> k.category() == ResourceKind.Category.TREE).toList();
            List<ResourceKind> ores = pool.stream()
                    .filter(k -> k.category() == ResourceKind.Category.ROCK).toList();

            if (!trees.isEmpty()) {
                int groves = switch (locality.biome) {
                    case DENSE_FOREST -> 9;
                    case FOREST -> 7;
                    case SWAMP, GRASSLAND -> 4;
                    default -> 3;
                };
                for (int i = 0; i < groves; i++) {
                    placeGrove(locality, trees, random);
                }
            }
            if (!ores.isEmpty()) {
                int mines = locality.biome.supportsOre() ? 4 : 1;
                for (int i = 0; i < mines; i++) {
                    placeMine(locality, ores, random);
                }
            }
        }
    }

    /**
     * A stand of trees: one dominant species with a scattering of others, denser
     * at the middle and thinning at the edges so it has an edge rather than a
     * boundary.
     */
    private void placeGrove(Locality locality, List<ResourceKind> trees, Random random) {
        int[] centre = openTileIn(locality, random);
        if (centre == null) {
            return;
        }
        ResourceKind dominant = weightedPick(trees, locality, random);
        if (dominant == null) {
            return;
        }
        int radius = 7 + random.nextInt(8);
        // Enough attempts to fill the disc even though most are rejected for
        // footprint overlap; a grove should read as woodland, not as an orchard.
        int attempts = radius * radius * 3;
        for (int i = 0; i < attempts; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            // Square-root bias fills the disc evenly instead of clumping at the pin.
            double distance = Math.sqrt(random.nextDouble()) * radius;
            int x = centre[0] + (int) Math.round(Math.cos(angle) * distance);
            int y = centre[1] + (int) Math.round(Math.sin(angle) * distance);
            if (!IslandLayout.inBounds(x, y) || !dominant.allowedIn(geography.biome[x][y])) {
                continue;
            }
            // One tree in five is something else, so a grove is not a monoculture.
            ResourceKind kind = random.nextInt(5) == 0
                    ? weightedPick(trees, locality, random) : dominant;
            if (kind == null || !kind.allowedIn(geography.biome[x][y])) {
                continue;
            }
            if (placeObject(kind.objectId(random.nextInt(64)), x, y, 0,
                    PlacedObject.TYPE_SCENERY, random.nextInt(4))) {
                result.world.resourceObjectCount++;
            }
        }
    }

    /**
     * A worked ore field: bare dug ground with rocks in it, the way the mines in
     * the original game read from above.
     */
    private void placeMine(Locality locality, List<ResourceKind> ores, Random random) {
        int[] centre = openTileIn(locality, random);
        if (centre == null) {
            return;
        }
        ResourceKind dominant = weightedPick(ores, locality, random);
        if (dominant == null) {
            return;
        }
        int radius = 4 + random.nextInt(4);

        // Strip the ground inside the pit back to bare earth.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int x = centre[0] + dx;
                int y = centre[1] + dy;
                if (!IslandLayout.inBounds(x, y) || dx * dx + dy * dy > radius * radius) {
                    continue;
                }
                if (geography.biome[x][y].isWater() || overlayOverride[x][y] != 0) {
                    continue;
                }
                overlayOverride[x][y] = Biome.OVERLAY_DIRT_ROAD;
            }
        }

        int rocks = 6 + random.nextInt(8);
        for (int i = 0; i < rocks * 4; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = Math.sqrt(random.nextDouble()) * radius;
            int x = centre[0] + (int) Math.round(Math.cos(angle) * distance);
            int y = centre[1] + (int) Math.round(Math.sin(angle) * distance);
            if (!IslandLayout.inBounds(x, y)) {
                continue;
            }
            // A quarter of the rocks are a second ore, as most real mines mix two.
            ResourceKind kind = random.nextInt(4) == 0
                    ? weightedPick(ores, locality, random) : dominant;
            if (kind == null || !kind.allowedIn(geography.biome[x][y])) {
                continue;
            }
            if (placeObject(kind.objectId(random.nextInt(64)), x, y, 0,
                    PlacedObject.TYPE_SCENERY, random.nextInt(4))) {
                result.world.resourceObjectCount++;
            }
        }
    }

    /** Resources allowed in this locality's biome and band. */
    private List<ResourceKind> poolFor(Locality locality) {
        List<ResourceKind> pool = new ArrayList<>();
        for (ResourceKind kind : ResourceKind.values()) {
            // A resource whose every listed id turns out to be something else in
            // this cache is dropped rather than placed as whatever it really is.
            if (kind.isUsable() && kind.allowedIn(locality.biome)
                    && kind.suitsBand(locality.band, true)) {
                pool.add(kind);
            }
        }
        return pool;
    }

    /**
     * Picks a resource, favouring ones squarely inside the band and letting the
     * above-band tail through rarely. This is what produces "mostly level 40-60
     * with the occasional 70" instead of a flat spread.
     */
    private ResourceKind weightedPick(List<ResourceKind> pool, Locality locality, Random random) {
        if (pool.isEmpty()) {
            return null;
        }
        double total = 0;
        double[] weights = new double[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            weights[i] = pool.get(i).suitsBand(locality.band, false) ? 1.0 : 0.12;
            total += weights[i];
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll <= 0) {
                return pool.get(i);
            }
        }
        return pool.get(pool.size() - 1);
    }

    /**
     * Scatters grass, ferns and bushes over the island.
     *
     * Purely cosmetic, and the last thing placed so it fills the gaps between
     * everything that matters rather than crowding it out.
     */
    private void placeClutter() {
        Noise scatter = noise.channel(31);
        for (int x = 0; x < geography.size; x++) {
            for (int y = 0; y < geography.size; y++) {
                if (occupied[x][y]) {
                    continue;
                }
                Biome biome = geography.biome[x][y];
                if (biome.isWater() || !geography.reachable[x][y]) {
                    continue;
                }
                int[] palette = Decoration.forBiome(biome);
                if (palette.length == 0) {
                    continue;
                }
                // Clumped rather than uniform: noise gates where clutter may appear
                // at all, so undergrowth gathers instead of speckling evenly.
                double gate = scatter.fbm(x, y, 22, 3);
                if (gate < GATE_FLOOR) {
                    continue;
                }
                // Ramp to full density over a narrow band above the gate, so the
                // middle of a clump is thick and its edge thins out. Scaling by the
                // raw distance above the floor never reached full density at all,
                // which left the island looking mown.
                double ramp = Math.min(1.0, (gate - GATE_FLOOR) / 0.12);
                double density = Decoration.densityFor(biome) * ramp;
                if (scatter.at(x * 31 + 7, y * 17 + 3) > density) {
                    continue;
                }
                int id = palette[scatter.intAt(x, y, palette.length)];
                if (placeObject(id, x, y, 0, PlacedObject.TYPE_SCENERY, scatter.intAt(x + 5, y + 9, 4))) {
                    result.world.clutterObjectCount++;
                }
            }
        }
    }

    /**
     * Puts fencing along the verges of country roads.
     *
     * Not continuous - a fenced lane every so often, broken where it meets other
     * things. The point is to make a road look like a route somebody maintains
     * rather than a stripe of different-coloured ground.
     */
    private void fenceRoads() {
        Noise pick = noise.channel(41);
        for (int packed : roadTiles) {
            int x = packed >> 16;
            int y = packed & 0xffff;
            // Only fence a verge: a tile beside the road that is not itself road.
            for (int[] d : new int[][]{{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
                int vx = x + d[0];
                int vy = y + d[1];
                if (!IslandLayout.inBounds(vx, vy) || roadTiles.contains((vx << 16) | vy)) {
                    continue;
                }
                if (!geography.isWalkable(vx, vy) || occupied[vx][vy]) {
                    continue;
                }
                if (pick.at(vx * 13 + 1, vy * 7 + 5) > 0.16) {
                    continue;
                }
                int id = FENCE_OBJECTS[pick.intAt(vx, vy, FENCE_OBJECTS.length)];
                if (placeObject(id, vx, vy, 0, PlacedObject.TYPE_SCENERY, pick.intAt(vx + 3, vy, 4))) {
                    result.world.clutterObjectCount++;
                }
            }
        }
    }

    /**
     * Fence pieces, from the cache's own definitions. All 1x1 and solid, so a
     * fenced verge is a real barrier rather than decoration a player walks over.
     */
    private static final int[] FENCE_OBJECTS = {17783, 17787, 17782, 1116, 1117};

    // ------------------------------------------------------------------
    // Monsters
    // ------------------------------------------------------------------

    private void placeMonsters() {
        for (Locality locality : result.world.localities) {
            List<MonsterCatalogue.Monster> pool = monsters.inBand(locality.band);
            if (pool.isEmpty()) {
                continue;
            }
            Random random = localityRandom(locality, 0x51F3);
            // A locality draws from a handful of species rather than the whole
            // roster, so its inhabitants read as belonging together.
            int speciesCount = 3 + random.nextInt(3);
            List<MonsterCatalogue.Monster> species = new ArrayList<>();
            for (int i = 0; i < speciesCount; i++) {
                species.add(pool.get(random.nextInt(pool.size())));
            }

            int budget = locality.type.isSettled() ? 18 : 46;
            for (int i = 0; i < budget; i++) {
                MonsterCatalogue.Monster monster = species.get(random.nextInt(species.size()));
                int[] tile = openTileIn(locality, random);
                if (tile == null) {
                    continue;
                }
                // Keep hostiles out of the settlement itself.
                if (locality.hasTown()
                        && Math.abs(tile[0] - locality.townX) < 14
                        && Math.abs(tile[1] - locality.townY) < 14) {
                    continue;
                }
                GeneratedWorld.NpcSpawn spawn = new GeneratedWorld.NpcSpawn(monster.id,
                        IslandLayout.worldX(tile[0]), IslandLayout.worldY(tile[1]), 0, 5);
                spawn.localityId = locality.id;
                result.world.npcSpawns.add(spawn);
            }
        }
    }

    private int[] openTileIn(Locality locality, Random random) {
        for (int attempt = 0; attempt < 20; attempt++) {
            int x = locality.centreX + random.nextInt(locality.radius * 2 + 1) - locality.radius;
            int y = locality.centreY + random.nextInt(locality.radius * 2 + 1) - locality.radius;
            if (!IslandLayout.inBounds(x, y) || occupied[x][y]) {
                continue;
            }
            if (geography.biome[x][y].isWater() || !geography.reachable[x][y]) {
                continue;
            }
            return new int[]{x, y};
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /**
     * A generator scoped to one locality, so a locality's contents never depend
     * on how many localities were processed before it.
     */
    private Random localityRandom(Locality locality, int salt) {
        return new Random(seed * 31L + locality.id * 7919L + salt);
    }

    /**
     * Places an object and reserves the tiles it actually covers.
     *
     * Reserving only the origin tile lets a 2x2 bush and a 4x2 cave mouth grow
     * into each other, which renders as models sunk halfway into one another.
     */
    boolean placeObject(int objectId, int localX, int localY, int plane, int type, int rotation) {
        if (!ObjectVetting.isPlaceable(objectId)) {
            return false;
        }
        int width = ObjectVetting.footprintX(objectId, rotation);
        int height = ObjectVetting.footprintY(objectId, rotation);
        if (!isClear(localX, localY, width, height)) {
            return false;
        }
        addObject(IslandLayout.worldX(localX), IslandLayout.worldY(localY), plane, objectId, type, rotation);
        markObject(localX, localY, width, height);
        return true;
    }

    /**
     * Places a dungeon mouth, clearing whatever clutter is in the way.
     *
     * The entrance is not optional - a dungeon nobody can enter is worse than no
     * dungeon - so this frees the tiles rather than giving up on the site, and
     * records where it actually landed.
     */
    void placeDungeonEntrance(com.elvarg.game.world.GeneratedWorld.Dungeon dungeon,
                              int localX, int localY) {
        int id = GeneratedDungeonObjects.ENTRANCE_OBJECT;
        int width = ObjectVetting.footprintX(id, 0);
        int height = ObjectVetting.footprintY(id, 0);
        markObject(localX, localY, width, height);
        dungeon.entranceX = IslandLayout.worldX(localX);
        dungeon.entranceY = IslandLayout.worldY(localY);
        addObject(dungeon.entranceX, dungeon.entranceY, 0, id, PlacedObject.TYPE_SCENERY, 0);
    }

    /** Records that an object covers a footprint, for both grids. */
    void markObject(int localX, int localY, int width, int height) {
        for (int x = localX; x < localX + width; x++) {
            for (int y = localY; y < localY + height; y++) {
                if (IslandLayout.inBounds(x, y)) {
                    occupied[x][y] = true;
                    hasObject[x][y] = true;
                }
            }
        }
    }

    /** Whether a footprint is free land the player can reach. */
    private boolean isClear(int localX, int localY, int width, int height) {
        for (int x = localX; x < localX + width; x++) {
            for (int y = localY; y < localY + height; y++) {
                if (!IslandLayout.inBounds(x, y) || occupied[x][y]) {
                    return false;
                }
                if (!geography.isWalkable(x, y) || !geography.reachable[x][y]) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Adds an object at world coordinates, bucketed into its region's file. */
    static void addObject(Map<Integer, List<PlacedObject>> objects,
                          int worldX, int worldY, int plane, int objectId, int type, int rotation) {
        int regionId = ((worldX / 64) << 8) | (worldY / 64);
        objects.computeIfAbsent(regionId, k -> new ArrayList<>())
                .add(new PlacedObject(objectId, worldX & 63, worldY & 63, plane, type, rotation));
    }

    private void addObject(int worldX, int worldY, int plane, int objectId, int type, int rotation) {
        addObject(result.objects, worldX, worldY, plane, objectId, type, rotation);
        result.world.sceneryObjectCount++;
    }

    // ------------------------------------------------------------------
    // Terrain painting
    // ------------------------------------------------------------------

    /**
     * Renders the geography into one {@link TerrainRegion} per map region.
     *
     * The whole island is painted, including its ocean margin, so a generated
     * region never shows leftovers of whatever used to occupy that map file.
     */
    private void paintTerrain() {
        for (int regionX = 0; regionX < IslandLayout.ISLAND_REGIONS; regionX++) {
            for (int regionY = 0; regionY < IslandLayout.ISLAND_REGIONS; regionY++) {
                int regionId = IslandLayout.regionId(
                        IslandLayout.BLOCK_REGION_X + regionX, IslandLayout.BLOCK_REGION_Y + regionY);
                TerrainRegion region = new TerrainRegion();
                for (int x = 0; x < 64; x++) {
                    for (int y = 0; y < 64; y++) {
                        int localX = regionX * 64 + x;
                        int localY = regionY * 64 + y;
                        Biome biome = geography.biome[localX][localY];
                        region.setUnderlay(0, x, y, biome.underlay());
                        int override = overlayOverride[localX][localY];
                        if (override != 0) {
                            region.setOverlay(0, x, y, override, 0, 0);
                        } else if (biome.overlay() != 0) {
                            region.setOverlay(0, x, y, biome.overlay(), 0, 0);
                        }
                        region.setHeight(0, x, y, geography.height[localX][localY]);
                        if (biome.isWater() || geography.cliff[localX][localY]) {
                            region.addFlag(0, x, y, TerrainRegion.FLAG_BLOCKED);
                        }
                    }
                }
                paintLava(region, regionX, regionY);
                result.terrain.put(regionId, region);
            }
        }
        applyPatches();
    }

    /**
     * Applies building floors and paving over the biome pass.
     *
     * Buildings bring their own flooring - boards, flagstones, rugs - and their
     * upper storeys, so a patch can target any plane. Height is left alone: the
     * town levelling already set it, and a prefab's floor should sit on the
     * ground it was placed on.
     */
    private void applyPatches() {
        for (TerrainPatch patch : patches) {
            int regionX = patch.localX / 64;
            int regionY = patch.localY / 64;
            int regionId = IslandLayout.regionId(
                    IslandLayout.BLOCK_REGION_X + regionX, IslandLayout.BLOCK_REGION_Y + regionY);
            TerrainRegion region = result.terrain.get(regionId);
            if (region == null) {
                continue;
            }
            int x = patch.localX & 63;
            int y = patch.localY & 63;
            if (patch.underlay != 0) {
                region.setUnderlay(patch.plane, x, y, patch.underlay);
            }
            if (patch.overlay != 0) {
                region.setOverlay(patch.plane, x, y, patch.overlay,
                        patch.overlayShape, patch.overlayRotation);
            }
            if (patch.height >= 0) {
                region.setHeight(patch.plane, x, y, patch.height);
            }
        }
    }

    /**
     * Paints lava pools into the volcanic biome. Lava is an overlay rather than a
     * biome of its own because only part of a volcanic region should be molten -
     * the rest is the scorched ground a player can actually walk on.
     */
    private void paintLava(TerrainRegion region, int regionX, int regionY) {
        Noise lava = noise.channel(21);
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                int localX = regionX * 64 + x;
                int localY = regionY * 64 + y;
                if (geography.biome[localX][localY] != Biome.VOLCANIC) {
                    continue;
                }
                if (lava.fbm(localX, localY, 18, 3) > 0.62) {
                    region.setOverlay(0, x, y, Biome.OVERLAY_LAVA, 0, 0);
                    region.addFlag(0, x, y, TerrainRegion.FLAG_BLOCKED);
                }
            }
        }
    }

    public IslandGeography geography() {
        return geography;
    }
}
