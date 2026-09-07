package com.elvarg.game.world.tool;

import com.elvarg.game.definition.ObjectDefinition;
import com.elvarg.game.world.codec.LandscapeCodec;
import com.elvarg.game.world.codec.MapIndex;
import com.elvarg.game.world.codec.PlacedObject;
import com.elvarg.game.world.codec.TerrainRegion;
import com.elvarg.game.world.gen.BuildingPrefab;
import com.google.gson.GsonBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Extracts building prefabs from the original game map.
 *
 * Buildings are found by their roofs. Roof objects (landscape types 12-21) sit
 * on the plane above the walls and cover a building's footprint exactly, so a
 * connected cluster of roof tiles is a building outline - far more reliable than
 * trying to infer one from wall segments, which run into each other between
 * terraced houses.
 *
 * Everything inside the outline is captured across every plane: walls, windows,
 * doors, stairs, furniture and floor materials. The result is written to
 * data/definitions/building_prefabs.json for the generator to stamp.
 *
 * Usage: {@code ExtractPrefabs [outputFile]}
 */
public final class ExtractPrefabs {

    private static final int MIN_SIDE = 4;
    private static final int MAX_SIDE = 13;
    private static final int MIN_OBJECTS = 8;

    public static void main(String[] args) throws Exception {
        Path clipping = Paths.get("../data/clipping");
        Path output = Paths.get(args.length > 0 ? args[0] : "../data/definitions/building_prefabs.json.gz");

        ObjectDefinition.init();
        MapIndex index = MapIndex.load(clipping.resolve("map_index"));

        List<BuildingPrefab> prefabs = new ArrayList<>();
        Set<String> signatures = new HashSet<>();

        // Harvest the whole map rather than a hand-picked shortlist. Twelve regions
        // yielded 24 buildings; the original game has hundreds, across every
        // architectural style it ships, and there is no reason to use a dozen of
        // them when the rest cost nothing but a scan.
        int regionsScanned = 0;
        for (int regionId = 0; regionId <= 0xffff; regionId++) {
            if (!index.contains(regionId)) {
                continue;
            }
            regionsScanned++;
            List<PlacedObject> objects = readObjects(clipping, index.objectFile(regionId));
            TerrainRegion terrain = readTerrain(clipping, index.terrainFile(regionId));
            if (objects.isEmpty()) {
                continue;
            }
            for (BuildingPrefab prefab : extract(regionId, objects, terrain)) {
                // Towns across the original map reuse the same house repeatedly;
                // keeping one of each keeps the library varied rather than large.
                String signature = signature(prefab);
                if (signatures.add(signature)) {
                    prefabs.add(prefab);
                }
            }
        }

        System.out.println("scanned " + regionsScanned + " regions");
        prefabs.sort((a, b) -> a.area() - b.area());
        for (int i = 0; i < prefabs.size(); i++) {
            prefabs.get(i).name = String.format(Locale.ROOT, "building_%02d_%dx%d",
                    i, prefabs.get(i).width, prefabs.get(i).height);
        }

        // Gzipped: the uncompressed library is ~11 MB of coordinates, which is not
        // a sensible thing to keep in a repository when it compresses by an order
        // of magnitude and is only ever read by one loader.
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (Writer writer = new java.io.OutputStreamWriter(
                new GZIPOutputStream(Files.newOutputStream(output)), java.nio.charset.StandardCharsets.UTF_8)) {
            new GsonBuilder().create().toJson(prefabs, writer);
        }

        System.out.println("extracted " + prefabs.size() + " distinct building prefabs");
        int withDoor = 0;
        int withFacility = 0;
        for (BuildingPrefab prefab : prefabs) {
            if (prefab.hasDoor()) {
                withDoor++;
            }
            if (!prefab.facilities.isEmpty()) {
                withFacility++;
            }
        }
        System.out.println("  with a door       " + withDoor);
        System.out.println("  with a facility   " + withFacility);
        System.out.println("  size range        "
                + prefabs.get(0).width + "x" + prefabs.get(0).height + " to "
                + prefabs.get(prefabs.size() - 1).width + "x" + prefabs.get(prefabs.size() - 1).height);
        System.out.println("\nwrote " + output.toAbsolutePath());
    }

    /** A cheap shape-and-contents fingerprint, used to drop duplicate houses. */
    private static String signature(BuildingPrefab prefab) {
        StringBuilder out = new StringBuilder(prefab.width + "x" + prefab.height + ":");
        prefab.objects.stream()
                .map(o -> o.id + "@" + o.x + "," + o.y + "," + o.plane)
                .sorted()
                .forEach(s -> out.append(s).append(';'));
        return out.toString();
    }

    // ------------------------------------------------------------------

    private static List<BuildingPrefab> extract(int regionId, List<PlacedObject> objects,
                                                TerrainRegion terrain) {
        // Roof coverage, projected down to a single 64x64 mask.
        boolean[][] roof = new boolean[64][64];
        for (PlacedObject object : objects) {
            if (object.type >= 12 && object.type <= 21) {
                roof[object.localX][object.localY] = true;
            }
        }

        List<BuildingPrefab> out = new ArrayList<>();
        boolean[][] seen = new boolean[64][64];
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                if (!roof[x][y] || seen[x][y]) {
                    continue;
                }
                int[] bounds = floodBounds(roof, seen, x, y);
                BuildingPrefab prefab = capture(regionId, objects, terrain, bounds);
                if (prefab != null) {
                    out.add(prefab);
                }
            }
        }
        return out;
    }

    /** Flood-fills a roof cluster and returns its bounding box. */
    private static int[] floodBounds(boolean[][] roof, boolean[][] seen, int startX, int startY) {
        int minX = startX;
        int maxX = startX;
        int minY = startY;
        int maxY = startY;
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startY});
        seen[startX][startY] = true;
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            minX = Math.min(minX, cur[0]);
            maxX = Math.max(maxX, cur[0]);
            minY = Math.min(minY, cur[1]);
            maxY = Math.max(maxY, cur[1]);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int nx = cur[0] + dx;
                    int ny = cur[1] + dy;
                    if (nx < 0 || ny < 0 || nx > 63 || ny > 63 || seen[nx][ny] || !roof[nx][ny]) {
                        continue;
                    }
                    seen[nx][ny] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        return new int[]{minX, minY, maxX, maxY};
    }

    /**
     * Captures everything inside a footprint. The box is grown by one tile so the
     * walls on the building's outer edge come with it - walls sit on the boundary
     * of the tile they enclose, not inside it.
     */
    private static BuildingPrefab capture(int regionId, List<PlacedObject> objects,
                                          TerrainRegion terrain, int[] bounds) {
        int minX = Math.max(0, bounds[0] - 1);
        int minY = Math.max(0, bounds[1] - 1);
        int maxX = Math.min(63, bounds[2] + 1);
        int maxY = Math.min(63, bounds[3] + 1);

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        if (width < MIN_SIDE || height < MIN_SIDE || width > MAX_SIDE || height > MAX_SIDE) {
            return null;
        }

        BuildingPrefab prefab = new BuildingPrefab();
        prefab.sourceRegion = regionId;
        prefab.width = width;
        prefab.height = height;

        for (PlacedObject object : objects) {
            if (object.localX < minX || object.localX > maxX
                    || object.localY < minY || object.localY > maxY) {
                continue;
            }
            int localX = object.localX - minX;
            int localY = object.localY - minY;
            prefab.objects.add(new BuildingPrefab.PrefabObject(
                    object.id, localX, localY, object.plane, object.type, object.rotation));
            prefab.planes = Math.max(prefab.planes, object.plane + 1);

            ObjectDefinition definition = ObjectDefinition.forId(object.id);
            String name = definition == null || definition.name == null ? "" : definition.name;
            if (prefab.doorX < 0 && name.contains("Door")) {
                prefab.doorX = localX;
                prefab.doorY = localY;
            }
            String facility = facilityFor(name);
            if (facility != null && !prefab.facilities.contains(facility)) {
                prefab.facilities.add(facility);
            }
        }

        if (prefab.objects.size() < MIN_OBJECTS) {
            return null;
        }
        // A building nobody can walk into is scenery, not architecture.
        if (!prefab.hasDoor()) {
            return null;
        }

        for (int plane = 0; plane < prefab.planes; plane++) {
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    int underlay = terrain.underlay[plane][x][y];
                    int overlay = terrain.overlay[plane][x][y];
                    if (underlay == 0 && overlay == 0) {
                        continue;
                    }
                    BuildingPrefab.PrefabTile tile = new BuildingPrefab.PrefabTile();
                    tile.x = x - minX;
                    tile.y = y - minY;
                    tile.plane = plane;
                    tile.underlay = underlay;
                    tile.overlay = overlay;
                    tile.overlayShape = terrain.overlayShape[plane][x][y];
                    tile.overlayRotation = terrain.overlayRotation[plane][x][y];
                    prefab.tiles.add(tile);
                }
            }
        }
        return prefab;
    }

    /** Maps an object's name onto the service it provides, if any. */
    private static String facilityFor(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("anvil")) {
            return "ANVIL";
        }
        if (lower.contains("furnace")) {
            return "FURNACE";
        }
        if (lower.contains("range") || lower.contains("stove") || lower.contains("cooking")) {
            return "RANGE";
        }
        if (lower.contains("bank")) {
            return "BANK";
        }
        if (lower.contains("altar")) {
            return "ALTAR";
        }
        if (lower.equals("bed") || lower.startsWith("bed ")) {
            return "BED";
        }
        return null;
    }

    // ------------------------------------------------------------------

    private static List<PlacedObject> readObjects(Path clipping, int fileId) throws IOException {
        Path file = clipping.resolve("maps").resolve(fileId + ".dat");
        if (!Files.exists(file)) {
            return List.of();
        }
        return LandscapeCodec.decodeObjects(gunzip(Files.readAllBytes(file)));
    }

    private static TerrainRegion readTerrain(Path clipping, int fileId) throws IOException {
        Path file = clipping.resolve("maps").resolve(fileId + ".dat");
        if (!Files.exists(file)) {
            return new TerrainRegion();
        }
        return LandscapeCodec.decodeTerrain(gunzip(Files.readAllBytes(file)));
    }

    private static byte[] gunzip(byte[] data) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}
