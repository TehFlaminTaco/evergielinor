package com.elvarg.game.world.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Designs a building rather than copying one.
 *
 * Lifting whole buildings out of the original map kept producing fragments, so
 * the generator now lays out its own: a footprint, interior walls dividing it
 * into rooms, doors on the outside and doorways between rooms, windows, a roof,
 * sometimes an upper storey, furniture to make it look lived in, and whatever
 * utility the settlement needs it to hold.
 *
 * The parts it builds from are real - harvested from the original map by
 * {@code ExtractBuildingParts} and grouped into styles that genuinely occur
 * together - so a generated house is made of Falador's stone or Lumbridge's
 * timber rather than of invented ids.
 *
 * Two things about the format decide most of how this reads in game. A tile
 * holds one wall object per plane, so the four corners of a rectangle - which
 * carry two edges each - have to be the two-edged corner type, not two straight
 * walls fighting over the same tile. And an object only draws at the landscape
 * types listed in its definition, so every id here is checked against
 * {@link ObjectVetting#rendersAt} before it is emitted: an object placed at a
 * type it has no model for is written to the map, clipped by the server, and
 * invisible to the player.
 *
 * Output is a {@link BuildingPrefab} so the rest of the town builder does not
 * care whether a building was designed or captured.
 *
 * @author EverGielinor world generator
 */
public final class BuildingDesigner {

    /**
     * Landscape type for a straight wall. Rotation picks the edge it sits on:
     * 0 west, 1 north, 2 east, 3 south - the same convention
     * {@code RegionManager.addClippingForVariableObject} decodes.
     */
    private static final int TYPE_WALL = 0;
    /**
     * Landscape type for a corner wall, which draws two edges at once. Rotation
     * picks which pair, read off the client's occlusion flags in
     * {@code MapRegion}: 0 west+north, 1 north+east, 2 east+south, 3 south+west.
     */
    private static final int TYPE_WALL_CORNER = 2;
    /** Landscape type for a wall-mounted decoration such as a window or torch. */
    private static final int TYPE_WALL_DECOR = 4;
    private static final int TYPE_SCENERY = 10;

    private static final int ROT_WEST = 0;
    private static final int ROT_NORTH = 1;
    private static final int ROT_EAST = 2;
    private static final int ROT_SOUTH = 3;

    /** Object 2118 "Staircase", Climb-down; 2119 "Staircase", Climb-up. */
    private static final int STAIRCASE_UP = 2119;
    private static final int STAIRCASE_DOWN = 2118;

    private BuildingDesigner() {
    }

    /**
     * @param utility object id this building should contain, or -1 for a dwelling
     */
    public static BuildingPrefab design(BuildingStyle style, Random random, int utility) {
        int width = 6 + random.nextInt(6);
        int height = 6 + random.nextInt(6);
        boolean twoStorey = random.nextInt(4) == 0 && width >= 8 && height >= 8;

        BuildingPrefab prefab = new BuildingPrefab();
        prefab.name = style.name + "_" + width + "x" + height;
        prefab.width = width;
        prefab.height = height;
        prefab.planes = twoStorey ? 3 : 2;

        floor(prefab, style, width, height, 0);
        outerWalls(prefab, style, width, height, 0, random);
        boolean[][] taken = new boolean[width][height];
        List<int[]> rooms = partition(prefab, style, width, height, 0, random, taken);
        furnish(prefab, style, rooms, random, utility, 0, taken);

        if (twoStorey) {
            floor(prefab, style, width, height, 1);
            outerWalls(prefab, style, width, height, 1, random);
            boolean[][] upperTaken = new boolean[width][height];
            List<int[]> upper = partition(prefab, style, width, height, 1, random, upperTaken);
            addStairs(prefab, rooms, upper, taken, upperTaken);
            furnish(prefab, style, upper, random, -1, 1, upperTaken);
            roof(prefab, style, width, height, 2);
        } else {
            roof(prefab, style, width, height, 1);
        }
        return prefab;
    }

    // ------------------------------------------------------------------

    private static void floor(BuildingPrefab prefab, BuildingStyle style,
                              int width, int height, int plane) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                BuildingPrefab.PrefabTile tile = new BuildingPrefab.PrefabTile();
                tile.x = x;
                tile.y = y;
                tile.plane = plane;
                tile.underlay = style.floorUnderlay;
                tile.overlay = style.floorOverlay;
                prefab.tiles.add(tile);
            }
        }
    }

    /**
     * Walls around the outside, with one or two doors on different sides and
     * windows spaced along the remaining runs.
     *
     * The four corner tiles are deliberately left out of the straight runs and
     * given corner pieces instead - see the note on the class.
     */
    private static void outerWalls(BuildingPrefab prefab, BuildingStyle style,
                                   int width, int height, int plane, Random random) {
        // Doors only on the ground floor, and on two different walls when the
        // building gets two, so a house is not entered twice from the same side.
        int doorSideA = plane == 0 ? random.nextInt(4) : -1;
        int doorSideB = (plane == 0 && random.nextInt(3) == 0)
                ? (doorSideA + 1 + random.nextInt(3)) % 4 : -1;
        int doorAlongA = 1 + random.nextInt(Math.max(1, (doorSideA % 2 == 0 ? height : width) - 2));
        int doorAlongB = 1 + random.nextInt(Math.max(1, (doorSideB % 2 == 0 ? height : width) - 2));

        for (int y = 1; y < height - 1; y++) {
            addWall(prefab, style, 0, y, plane, ROT_WEST,
                    doorSideA == 0 && y == doorAlongA || doorSideB == 0 && y == doorAlongB, random);
            addWall(prefab, style, width - 1, y, plane, ROT_EAST,
                    doorSideA == 2 && y == doorAlongA || doorSideB == 2 && y == doorAlongB, random);
        }
        for (int x = 1; x < width - 1; x++) {
            addWall(prefab, style, x, 0, plane, ROT_SOUTH,
                    doorSideA == 3 && x == doorAlongA || doorSideB == 3 && x == doorAlongB, random);
            addWall(prefab, style, x, height - 1, plane, ROT_NORTH,
                    doorSideA == 1 && x == doorAlongA || doorSideB == 1 && x == doorAlongB, random);
        }

        int corner = ObjectVetting.canFormWalls(style.cornerWall) ? style.cornerWall : style.wall;
        addCorner(prefab, corner, 0, height - 1, plane, 0);              // west + north
        addCorner(prefab, corner, width - 1, height - 1, plane, 1);      // north + east
        addCorner(prefab, corner, width - 1, 0, plane, 2);               // east + south
        addCorner(prefab, corner, 0, 0, plane, 3);                       // south + west

        if (plane == 0) {
            recordDoor(prefab, doorSideA, doorAlongA, width, height);
        }
    }

    /**
     * Records where a player walks in, and the tile they stand on to do it.
     *
     * A wall sits on one edge of its tile, so the approach is the tile on the
     * far side of that edge - outside the building. Routing a lane from the door
     * tile itself starts the walk inside the house, and the route then leaves
     * through whichever wall is cheapest, which is why paths used to surface
     * somewhere along the side of a building rather than at its door.
     */
    private static void recordDoor(BuildingPrefab prefab, int side, int along, int width, int height) {
        switch (side) {
            case 0 -> {
                prefab.doorX = 0;
                prefab.doorY = along;
                prefab.doorApproachX = -1;
                prefab.doorApproachY = along;
            }
            case 1 -> {
                prefab.doorX = along;
                prefab.doorY = height - 1;
                prefab.doorApproachX = along;
                prefab.doorApproachY = height;
            }
            case 2 -> {
                prefab.doorX = width - 1;
                prefab.doorY = along;
                prefab.doorApproachX = width;
                prefab.doorApproachY = along;
            }
            default -> {
                prefab.doorX = along;
                prefab.doorY = 0;
                prefab.doorApproachX = along;
                prefab.doorApproachY = -1;
            }
        }
    }

    private static void addCorner(BuildingPrefab prefab, int id, int x, int y, int plane, int rotation) {
        if (!ObjectVetting.rendersAt(id, TYPE_WALL_CORNER)) {
            return;
        }
        prefab.objects.add(new BuildingPrefab.PrefabObject(id, x, y, plane, TYPE_WALL_CORNER, rotation));
    }

    private static void addWall(BuildingPrefab prefab, BuildingStyle style,
                                int x, int y, int plane, int rotation, boolean door, Random random) {
        int id = door && ObjectVetting.rendersAt(style.door, TYPE_WALL) ? style.door : style.wall;
        if (!ObjectVetting.rendersAt(id, TYPE_WALL)) {
            return;
        }
        prefab.objects.add(new BuildingPrefab.PrefabObject(id, x, y, plane, TYPE_WALL, rotation));
        if (door) {
            return;
        }
        // Windows and wall hangings, spaced out so a wall is not solid decoration.
        if (style.window > 0 && random.nextInt(5) == 0
                && ObjectVetting.rendersAt(style.window, TYPE_WALL_DECOR)) {
            prefab.objects.add(new BuildingPrefab.PrefabObject(
                    style.window, x, y, plane, TYPE_WALL_DECOR, rotation));
        } else if (!style.wallDecor.isEmpty() && random.nextInt(9) == 0) {
            int[] decor = style.wallDecor.get(random.nextInt(style.wallDecor.size()));
            if (ObjectVetting.rendersAt(decor[0], decor[1])) {
                prefab.objects.add(new BuildingPrefab.PrefabObject(
                        decor[0], x, y, plane, decor[1], rotation));
            }
        }
    }

    /**
     * Splits the interior into rooms with a doorway between each pair.
     *
     * Returns each room as {x0, y0, x1, y1} so the furnisher knows where the
     * open floor is, and marks the partition walls in {@code taken} so nothing
     * is later stood on top of them.
     */
    private static List<int[]> partition(BuildingPrefab prefab, BuildingStyle style,
                                         int width, int height, int plane, Random random,
                                         boolean[][] taken) {
        List<int[]> rooms = new ArrayList<>();
        rooms.add(new int[]{1, 1, width - 2, height - 2});
        int splits = (width >= 9 || height >= 9) ? 1 + random.nextInt(2) : random.nextInt(2);
        boolean canWall = ObjectVetting.rendersAt(style.wall, TYPE_WALL);

        for (int i = 0; i < splits && canWall; i++) {
            // Split the largest room so partitions stay usefully sized.
            int[] room = rooms.stream()
                    .max((a, b) -> area(a) - area(b))
                    .orElse(null);
            if (room == null || area(room) < 20) {
                break;
            }
            rooms.remove(room);
            boolean vertical = (room[2] - room[0]) >= (room[3] - room[1]);
            if (vertical && room[2] - room[0] >= 4) {
                int at = room[0] + 2 + random.nextInt(room[2] - room[0] - 3);
                int doorway = room[1] + random.nextInt(room[3] - room[1] + 1);
                for (int y = room[1]; y <= room[3]; y++) {
                    if (y != doorway) {
                        prefab.objects.add(new BuildingPrefab.PrefabObject(
                                style.wall, at, y, plane, TYPE_WALL, ROT_WEST));
                        taken[at][y] = true;
                    }
                }
                rooms.add(new int[]{room[0], room[1], at - 1, room[3]});
                rooms.add(new int[]{at, room[1], room[2], room[3]});
            } else if (room[3] - room[1] >= 4) {
                int at = room[1] + 2 + random.nextInt(room[3] - room[1] - 3);
                int doorway = room[0] + random.nextInt(room[2] - room[0] + 1);
                for (int x = room[0]; x <= room[2]; x++) {
                    if (x != doorway) {
                        prefab.objects.add(new BuildingPrefab.PrefabObject(
                                style.wall, x, at, plane, TYPE_WALL, ROT_SOUTH));
                        taken[x][at] = true;
                    }
                }
                rooms.add(new int[]{room[0], room[1], room[2], at - 1});
                rooms.add(new int[]{room[0], at, room[2], room[3]});
            } else {
                rooms.add(room);
                break;
            }
        }
        return rooms;
    }

    private static int area(int[] room) {
        return (room[2] - room[0] + 1) * (room[3] - room[1] + 1);
    }

    /**
     * Puts a staircase in a room on each floor, at a spot with room for it.
     *
     * The staircase models are 2x2 and 2x4, so dropping one on a single tile in
     * a corner used to bury it in two walls.
     */
    private static void addStairs(BuildingPrefab prefab, List<int[]> ground, List<int[]> upper,
                                  boolean[][] taken, boolean[][] upperTaken) {
        int[] up = firstFit(ground, STAIRCASE_UP, taken);
        if (up == null) {
            return;
        }
        prefab.objects.add(new BuildingPrefab.PrefabObject(
                STAIRCASE_UP, up[0], up[1], 0, TYPE_SCENERY, 0));
        // The way down lands where the way up arrives, so the two line up.
        int[] down = fits(upper, up[0], up[1], STAIRCASE_DOWN, upperTaken)
                ? new int[]{up[0], up[1]} : firstFit(upper, STAIRCASE_DOWN, upperTaken);
        if (down != null) {
            prefab.objects.add(new BuildingPrefab.PrefabObject(
                    STAIRCASE_DOWN, down[0], down[1], 1, TYPE_SCENERY, 0));
        }
    }

    /**
     * Fills rooms with furniture, and puts the building's utility in the largest
     * one where it has space around it.
     */
    private static void furnish(BuildingPrefab prefab, BuildingStyle style, List<int[]> rooms,
                                Random random, int utility, int plane, boolean[][] taken) {
        if (rooms.isEmpty()) {
            return;
        }
        if (utility > 0 && ObjectVetting.rendersAt(utility, TYPE_SCENERY)) {
            List<int[]> largestFirst = new ArrayList<>(rooms);
            largestFirst.sort((a, b) -> area(b) - area(a));
            int[] at = firstFit(largestFirst, utility, taken);
            if (at != null) {
                prefab.objects.add(new BuildingPrefab.PrefabObject(
                        utility, at[0], at[1], plane, TYPE_SCENERY, 0));
                prefab.facilities.add(String.valueOf(utility));
            }
        }
        if (style.furniture.isEmpty()) {
            return;
        }
        for (int[] room : rooms) {
            int pieces = Math.max(1, area(room) / 8);
            for (int i = 0; i < pieces; i++) {
                int id = style.furniture.get(random.nextInt(style.furniture.size()));
                if (!ObjectVetting.rendersAt(id, TYPE_SCENERY)) {
                    continue;
                }
                int x = room[0] + random.nextInt(room[2] - room[0] + 1);
                int y = room[1] + random.nextInt(room[3] - room[1] + 1);
                // Leave the doorway clear; furniture in a doorway blocks the house.
                if (plane == 0 && x == prefab.doorX && y == prefab.doorY) {
                    continue;
                }
                if (!fits(room, x, y, id, taken)) {
                    continue;
                }
                claim(taken, x, y, id);
                prefab.objects.add(new BuildingPrefab.PrefabObject(
                        id, x, y, plane, TYPE_SCENERY, 0));
            }
        }
    }

    /**
     * First tile in these rooms with enough clear floor for an object's whole
     * footprint, claiming it. Rotation is always 0 here so the footprint is the
     * definition's own way round.
     */
    private static int[] firstFit(List<int[]> rooms, int id, boolean[][] taken) {
        for (int[] room : rooms) {
            for (int x = room[0]; x <= room[2]; x++) {
                for (int y = room[1]; y <= room[3]; y++) {
                    if (fits(room, x, y, id, taken)) {
                        claim(taken, x, y, id);
                        return new int[]{x, y};
                    }
                }
            }
        }
        return null;
    }

    private static boolean fits(List<int[]> rooms, int x, int y, int id, boolean[][] taken) {
        for (int[] room : rooms) {
            if (fits(room, x, y, id, taken)) {
                return true;
            }
        }
        return false;
    }

    private static boolean fits(int[] room, int x, int y, int id, boolean[][] taken) {
        int sizeX = ObjectVetting.sizeX(id);
        int sizeY = ObjectVetting.sizeY(id);
        if (x < room[0] || y < room[1] || x + sizeX - 1 > room[2] || y + sizeY - 1 > room[3]) {
            return false;
        }
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dy = 0; dy < sizeY; dy++) {
                if (taken[x + dx][y + dy]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void claim(boolean[][] taken, int x, int y, int id) {
        for (int dx = 0; dx < ObjectVetting.sizeX(id); dx++) {
            for (int dy = 0; dy < ObjectVetting.sizeY(id); dy++) {
                if (x + dx < taken.length && y + dy < taken[0].length) {
                    taken[x + dx][y + dy] = true;
                }
            }
        }
    }

    /**
     * Lays roof pieces over the footprint, one plane above the top storey.
     *
     * Types 12 to 17 are the roof body; 18 to 21 are edge trim that reads as a
     * fringe when tiled over a whole building, so a body piece is preferred.
     */
    private static void roof(BuildingPrefab prefab, BuildingStyle style,
                             int width, int height, int plane) {
        int[] piece = null;
        for (int[] candidate : style.roofPieces) {
            if (!ObjectVetting.rendersAt(candidate[0], candidate[1])) {
                continue;
            }
            boolean body = candidate[1] >= 12 && candidate[1] <= 17;
            if (piece == null || (body && !(piece[1] >= 12 && piece[1] <= 17))) {
                piece = candidate;
            }
        }
        if (piece == null) {
            return;
        }
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                prefab.objects.add(new BuildingPrefab.PrefabObject(
                        piece[0], x, y, plane, piece[1], 0));
            }
        }
        prefab.planes = Math.max(prefab.planes, plane + 1);
    }
}
