package com.elvarg.game.world.tool;

import com.elvarg.game.definition.ObjectDefinition;
import com.elvarg.game.world.codec.LandscapeCodec;
import com.elvarg.game.world.codec.MapIndex;
import com.elvarg.game.world.codec.PlacedObject;
import com.elvarg.game.world.codec.TerrainRegion;
import com.elvarg.game.world.gen.BuildingStyle;
import com.google.gson.GsonBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Harvests building <em>parts</em> from the original game map, grouped into
 * coherent architectural styles.
 *
 * This replaces the earlier approach of lifting whole buildings. A captured
 * building is only as good as the bounding box around it, and terraces, partial
 * upper storeys and shared walls meant a steady fraction came out as fragments -
 * houses with two walls and no back. Parts have no such problem: a wall piece is
 * a wall piece wherever it came from.
 *
 * A style is keyed on its wall material, because that is what makes buildings
 * look like they belong to the same town. Everything recorded alongside it - the
 * door, the window, the roof pieces, the flooring, the furniture - is what was
 * actually found in buildings using that wall.
 *
 * Usage: {@code ExtractBuildingParts [outputFile]}
 */
public final class ExtractBuildingParts {

    /** Minimum source buildings before a style is worth keeping. */
    private static final int MIN_FREQUENCY = 6;
    /** How many styles to keep. */
    private static final int MAX_STYLES = 14;

    private static final int TYPE_WALL_STRAIGHT = 0;
    private static final int TYPE_WALL_CORNER = 2;

    /** Working totals for one wall material. */
    private static final class Accumulator {
        int frequency;
        final Map<Integer, Integer> corners = new HashMap<>();
        final Map<Integer, Integer> doors = new HashMap<>();
        final Map<Integer, Integer> windows = new HashMap<>();
        final Map<Long, Integer> roofs = new HashMap<>();
        final Map<Integer, Integer> underlays = new HashMap<>();
        final Map<Integer, Integer> overlays = new HashMap<>();
        final Map<Integer, Integer> furniture = new HashMap<>();
        final Map<Long, Integer> wallDecor = new HashMap<>();
    }

    public static void main(String[] args) throws Exception {
        Path clipping = Paths.get("../data/clipping");
        Path output = Paths.get(args.length > 0 ? args[0] : "../data/definitions/building_styles.json.gz");

        ObjectDefinition.init();
        MapIndex index = MapIndex.load(clipping.resolve("map_index"));
        Map<Integer, Accumulator> byWall = new HashMap<>();
        int buildings = 0;
        int regions = 0;

        for (int regionId = 0; regionId <= 0xffff; regionId++) {
            if (!index.contains(regionId)) {
                continue;
            }
            regions++;
            List<PlacedObject> objects = readObjects(clipping, index.objectFile(regionId));
            if (objects.isEmpty()) {
                continue;
            }
            TerrainRegion terrain = readTerrain(clipping, index.terrainFile(regionId));
            buildings += harvest(objects, terrain, byWall);
        }

        List<BuildingStyle> styles = new ArrayList<>();
        for (Map.Entry<Integer, Accumulator> entry : byWall.entrySet()) {
            Accumulator acc = entry.getValue();
            if (acc.frequency < MIN_FREQUENCY) {
                continue;
            }
            BuildingStyle style = new BuildingStyle();
            style.wall = entry.getKey();
            style.frequency = acc.frequency;
            style.cornerWall = best(acc.corners, -1);
            style.door = best(acc.doors, -1);
            style.window = best(acc.windows, -1);
            style.floorUnderlay = best(acc.underlays, 0);
            style.floorOverlay = best(acc.overlays, 0);
            for (long packed : top(acc.roofs, 6)) {
                style.roofPieces.add(new int[]{(int) (packed >> 8), (int) (packed & 0xff)});
            }
            for (long packed : top(acc.wallDecor, 4)) {
                style.wallDecor.add(new int[]{(int) (packed >> 8), (int) (packed & 0xff)});
            }
            for (int id : topInts(acc.furniture, 14)) {
                style.furniture.add(id);
            }
            style.name = nameOf(style.wall);
            if (style.isUsable()) {
                styles.add(style);
            }
        }
        styles.sort(Comparator.comparingInt((BuildingStyle s) -> -s.frequency));
        if (styles.size() > MAX_STYLES) {
            styles = new ArrayList<>(styles.subList(0, MAX_STYLES));
        }

        Files.createDirectories(output.toAbsolutePath().getParent());
        try (Writer writer = new java.io.OutputStreamWriter(
                new GZIPOutputStream(Files.newOutputStream(output)), StandardCharsets.UTF_8)) {
            new GsonBuilder().create().toJson(styles, writer);
        }

        System.out.println("scanned " + regions + " regions, " + buildings + " source buildings");
        System.out.println("kept " + styles.size() + " architectural styles:");
        for (BuildingStyle style : styles) {
            System.out.println("  " + style);
        }
        System.out.println("\nwrote " + output.toAbsolutePath());
    }

    private static String nameOf(int wallId) {
        ObjectDefinition definition = ObjectDefinition.forId(wallId);
        String name = definition == null || definition.name == null ? "wall" : definition.name;
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_") + "_" + wallId;
    }

    // ------------------------------------------------------------------

    /** Finds buildings by their roofs and records the parts each one is made of. */
    private static int harvest(List<PlacedObject> objects, TerrainRegion terrain,
                               Map<Integer, Accumulator> byWall) {
        boolean[][] roofMask = new boolean[64][64];
        for (PlacedObject object : objects) {
            if (isRoof(object.type)) {
                roofMask[object.localX][object.localY] = true;
            }
        }
        boolean[][] seen = new boolean[64][64];
        int found = 0;
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                if (!roofMask[x][y] || seen[x][y]) {
                    continue;
                }
                int[] bounds = floodBounds(roofMask, seen, x, y);
                if (record(objects, terrain, bounds, byWall)) {
                    found++;
                }
            }
        }
        return found;
    }

    private static boolean record(List<PlacedObject> objects, TerrainRegion terrain,
                                  int[] bounds, Map<Integer, Accumulator> byWall) {
        int minX = Math.max(0, bounds[0] - 1);
        int minY = Math.max(0, bounds[1] - 1);
        int maxX = Math.min(63, bounds[2] + 1);
        int maxY = Math.min(63, bounds[3] + 1);
        if (maxX - minX < 3 || maxY - minY < 3) {
            return false;
        }

        // The wall material is whichever straight wall appears most in this
        // building; everything else is attributed to it.
        Map<Integer, Integer> wallCounts = new HashMap<>();
        List<PlacedObject> inside = new ArrayList<>();
        for (PlacedObject object : objects) {
            if (object.localX < minX || object.localX > maxX
                    || object.localY < minY || object.localY > maxY) {
                continue;
            }
            inside.add(object);
            if (object.type == TYPE_WALL_STRAIGHT && !isDoor(object.id)) {
                wallCounts.merge(object.id, 1, Integer::sum);
            }
        }
        int wall = best(wallCounts, -1);
        if (wall < 0) {
            return false;
        }

        Accumulator acc = byWall.computeIfAbsent(wall, k -> new Accumulator());
        acc.frequency++;
        for (PlacedObject object : inside) {
            if (isRoof(object.type)) {
                acc.roofs.merge(((long) object.id << 8) | object.type, 1, Integer::sum);
            } else if (object.type == TYPE_WALL_CORNER) {
                acc.corners.merge(object.id, 1, Integer::sum);
            } else if (isDoor(object.id)) {
                acc.doors.merge(object.id, 1, Integer::sum);
            } else if (object.type >= 4 && object.type <= 8) {
                if (isWindow(object.id)) {
                    acc.windows.merge(object.id, 1, Integer::sum);
                } else {
                    acc.wallDecor.merge(((long) object.id << 8) | object.type, 1, Integer::sum);
                }
            } else if ((object.type == 10 || object.type == 11) && isFurniture(object.id)) {
                acc.furniture.merge(object.id, 1, Integer::sum);
            }
        }
        for (int x = minX + 1; x < maxX; x++) {
            for (int y = minY + 1; y < maxY; y++) {
                if (terrain.underlay[0][x][y] != 0) {
                    acc.underlays.merge(terrain.underlay[0][x][y], 1, Integer::sum);
                }
                if (terrain.overlay[0][x][y] != 0) {
                    acc.overlays.merge(terrain.overlay[0][x][y], 1, Integer::sum);
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------

    private static boolean isRoof(int type) {
        return type >= 12 && type <= 21;
    }

    private static boolean isDoor(int id) {
        String name = nameFor(id);
        return name.contains("door") || name.contains("gate");
    }

    private static boolean isWindow(int id) {
        return nameFor(id).contains("window");
    }

    /** Objects worth putting inside a house, by name. */
    private static boolean isFurniture(int id) {
        String name = nameFor(id);
        if (name.isEmpty()) {
            return false;
        }
        for (String want : new String[]{"table", "chair", "bed", "bench", "stool", "crate",
                "barrel", "bookcase", "cupboard", "shelves", "rug", "sack", "chest",
                "fireplace", "cabinet", "drawers", "candle", "pot", "bowl"}) {
            if (name.contains(want)) {
                return true;
            }
        }
        return false;
    }

    private static String nameFor(int id) {
        ObjectDefinition definition = ObjectDefinition.forId(id);
        return definition == null || definition.name == null
                ? "" : definition.name.toLowerCase(Locale.ROOT);
    }

    private static <K> int best(Map<K, Integer> counts, int fallback) {
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> (Integer) e.getKey())
                .orElse(fallback);
    }

    private static List<Long> top(Map<Long, Integer> counts, int limit) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static List<Integer> topInts(Map<Integer, Integer> counts, int limit) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static int[] floodBounds(boolean[][] mask, boolean[][] seen, int startX, int startY) {
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
                    if (nx < 0 || ny < 0 || nx > 63 || ny > 63 || seen[nx][ny] || !mask[nx][ny]) {
                        continue;
                    }
                    seen[nx][ny] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        return new int[]{minX, minY, maxX, maxY};
    }

    private static List<PlacedObject> readObjects(Path clipping, int fileId) throws IOException {
        Path file = clipping.resolve("maps").resolve(fileId + ".dat");
        return Files.exists(file)
                ? LandscapeCodec.decodeObjects(gunzip(Files.readAllBytes(file)))
                : List.of();
    }

    private static TerrainRegion readTerrain(Path clipping, int fileId) throws IOException {
        Path file = clipping.resolve("maps").resolve(fileId + ".dat");
        return Files.exists(file)
                ? LandscapeCodec.decodeTerrain(gunzip(Files.readAllBytes(file)))
                : new TerrainRegion();
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
