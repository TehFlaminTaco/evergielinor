package com.elvarg.game.world.tool;

import com.elvarg.game.definition.ObjectDefinition;
import com.elvarg.game.world.codec.LandscapeCodec;
import com.elvarg.game.world.codec.MapIndex;
import com.elvarg.game.world.codec.PlacedObject;
import com.elvarg.game.world.codec.TerrainRegion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Prints what is actually in the map files over a patch of the world.
 *
 * The installed map holds both the original RuneScape regions and the generated
 * island, so the same tool answers "what does a real building look like" and
 * "what did we write". That matters because most of the mistakes in this project
 * have been ones where the generated data was structurally valid and simply not
 * what the original does - roofs tiled from one piece, overlays written as full
 * squares - and the only way to tell is to look at both.
 *
 * Usage: {@code InspectArea <worldX> <worldY> [radius] [plane]}
 */
public final class InspectArea {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: InspectArea <worldX> <worldY> [radius] [plane]");
            System.exit(2);
        }
        int centreX = Integer.parseInt(args[0]);
        int centreY = Integer.parseInt(args[1]);
        int radius = args.length > 2 ? Integer.parseInt(args[2]) : 12;
        int plane = args.length > 3 ? Integer.parseInt(args[3]) : 0;

        ObjectDefinition.init();
        Path clipping = Paths.get("../data/clipping");
        MapIndex index = MapIndex.load(clipping.resolve("map_index"));

        System.out.println("area around " + centreX + "," + centreY + " plane " + plane
                + ", radius " + radius);
        System.out.println();

        printTerrain(clipping, index, centreX, centreY, radius, plane);
        System.out.println();
        printWalls(clipping, index, centreX, centreY, radius, plane);
        System.out.println();
        printObjects(clipping, index, centreX, centreY, radius, plane);
    }

    /**
     * The terrain grid, one cell per tile: overlay id and shape, or the underlay
     * where there is no overlay. North is up, matching how the map reads in game.
     */
    private static void printTerrain(Path clipping, MapIndex index, int centreX, int centreY,
                                     int radius, int plane) throws IOException {
        System.out.println("terrain  (o<id>/<shape>r<rot> = overlay, u<id> = underlay only)");
        for (int y = centreY + radius; y >= centreY - radius; y--) {
            StringBuilder line = new StringBuilder(String.format("%5d ", y));
            for (int x = centreX - radius; x <= centreX + radius; x++) {
                TerrainRegion region = terrainAt(clipping, index, x, y);
                int lx = x & 63;
                int ly = y & 63;
                int overlay = region.overlay[plane][lx][ly];
                if (overlay != 0) {
                    line.append(String.format(" o%3d/%d%d", overlay,
                            region.overlayShape[plane][lx][ly],
                            region.overlayRotation[plane][lx][ly]));
                } else {
                    line.append(String.format(" u%3d   ", region.underlay[plane][lx][ly]));
                }
            }
            System.out.println(line);
        }
    }

    /**
     * Walls and roofs as a grid, which is the only way to see the shape of a
     * building. Each cell is the landscape type and rotation of the wall on that
     * tile - there can only be one, since the client keeps a single wall object
     * per tile per plane - and 'R' marks a tile carrying a roof piece.
     */
    private static void printWalls(Path clipping, MapIndex index, int centreX, int centreY,
                                   int radius, int plane) throws IOException {
        System.out.println("walls  (<type><rot>, R = roof above, . = none)");
        for (int y = centreY + radius; y >= centreY - radius; y--) {
            StringBuilder line = new StringBuilder(String.format("%5d ", y));
            for (int x = centreX - radius; x <= centreX + radius; x++) {
                String cell = "  .";
                boolean roof = false;
                for (PlacedObject object : objectsAt(clipping, index, x, y)) {
                    if (object.localX != (x & 63) || object.localY != (y & 63)) {
                        continue;
                    }
                    if (object.plane == plane
                            && (object.type <= 3 || object.type == 9)) {
                        cell = String.format(" %d%d", object.type, object.rotation);
                    }
                    if (object.type >= 12 && object.type <= 21) {
                        if (object.plane == plane && cell.equals("  .")) {
                            // On the roof's own plane, show which piece it is:
                            // a roof is made of differently rotated slopes and
                            // trim, and tiling one piece is what makes it a slab.
                            cell = String.format(" %d%d", object.type, object.rotation);
                        }
                        roof = true;
                    }
                }
                line.append(roof && cell.equals("  .") ? "  R" : cell);
            }
            System.out.println(line);
        }
    }

    /** Every object in the patch, with the landscape type and rotation it carries. */
    private static void printObjects(Path clipping, MapIndex index, int centreX, int centreY,
                                     int radius, int plane) throws IOException {
        System.out.println("objects  (type: 0-3,9 walls, 4-8 wall decor, 10-11 scenery, 12-21 roofs, 22 floor decor)");
        List<String> rows = new ArrayList<>();
        for (int y = centreY - radius; y <= centreY + radius; y++) {
            for (int x = centreX - radius; x <= centreX + radius; x++) {
                for (PlacedObject object : objectsAt(clipping, index, x, y)) {
                    if (object.plane != plane || object.localX != (x & 63) || object.localY != (y & 63)) {
                        continue;
                    }
                    ObjectDefinition definition = ObjectDefinition.forId(object.id);
                    String name = definition == null || definition.name == null ? "?" : definition.name;
                    String draws = definition == null || definition.modelTypes == null
                            ? "10" : java.util.Arrays.toString(definition.modelTypes);
                    rows.add(String.format("  %5d,%-5d id %-6d type %-3d rot %d  %-22s draws at %s%s",
                            x, y, object.id, object.type, object.rotation, name, draws,
                            renders(definition, object.type) ? "" : "   <-- NO MODEL FOR THIS TYPE"));
                }
            }
        }
        rows.forEach(System.out::println);
        System.out.println("  " + rows.size() + " objects");
    }

    private static boolean renders(ObjectDefinition definition, int type) {
        if (definition == null) {
            return false;
        }
        if (definition.modelTypes == null) {
            return type == 10;
        }
        for (int candidate : definition.modelTypes) {
            if (candidate == type) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------

    private static final java.util.Map<Integer, TerrainRegion> TERRAIN = new java.util.HashMap<>();
    private static final java.util.Map<Integer, List<PlacedObject>> OBJECTS = new java.util.HashMap<>();

    private static TerrainRegion terrainAt(Path clipping, MapIndex index, int x, int y)
            throws IOException {
        int regionId = ((x >> 6) << 8) | (y >> 6);
        TerrainRegion cached = TERRAIN.get(regionId);
        if (cached == null) {
            byte[] data = read(clipping, index.terrainFile(regionId));
            cached = data == null ? new TerrainRegion() : LandscapeCodec.decodeTerrain(data);
            TERRAIN.put(regionId, cached);
        }
        return cached;
    }

    private static List<PlacedObject> objectsAt(Path clipping, MapIndex index, int x, int y)
            throws IOException {
        int regionId = ((x >> 6) << 8) | (y >> 6);
        List<PlacedObject> cached = OBJECTS.get(regionId);
        if (cached == null) {
            byte[] data = read(clipping, index.objectFile(regionId));
            cached = data == null ? List.of() : LandscapeCodec.decodeObjects(data);
            OBJECTS.put(regionId, cached);
        }
        return cached;
    }

    private static byte[] read(Path clipping, int fileId) throws IOException {
        if (fileId <= 0) {
            return null;
        }
        Path file = clipping.resolve("maps").resolve(fileId + ".dat");
        if (!Files.exists(file)) {
            return null;
        }
        try (GZIPInputStream in = new GZIPInputStream(
                new ByteArrayInputStream(Files.readAllBytes(file)))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }
}
