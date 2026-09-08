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
    /** Landscape type for a wall-mounted decoration such as a window or torch. */
    private static final int TYPE_WALL_DECOR = 4;
    private static final int TYPE_SCENERY = 10;

    private static final int ROT_WEST = 0;
    private static final int ROT_NORTH = 1;
    private static final int ROT_EAST = 2;
    private static final int ROT_SOUTH = 3;

    private BuildingDesigner() {
    }

    /**
     * @param utility object id this building should contain, or -1 for a dwelling
     */
    public static BuildingPrefab design(BuildingStyle style, Random random, int utility) {
        int width = 6 + random.nextInt(6);
        int height = 6 + random.nextInt(6);
        boolean twoStorey = random.nextInt(4) == 0 && width >= 7 && height >= 7;

        BuildingPrefab prefab = new BuildingPrefab();
        prefab.name = style.name + "_" + width + "x" + height;
        prefab.width = width;
        prefab.height = height;
        prefab.planes = twoStorey ? 3 : 2;

        floor(prefab, style, width, height, 0);
        outerWalls(prefab, style, width, height, 0, random);
        List<int[]> rooms = partition(prefab, style, width, height, 0, random);
        furnish(prefab, style, rooms, random, utility);

        if (twoStorey) {
            floor(prefab, style, width, height, 1);
            outerWalls(prefab, style, width, height, 1, random);
            // Stairs up, against an inside corner of the ground floor.
            prefab.objects.add(new BuildingPrefab.PrefabObject(
                    style.wall > 0 ? STAIRCASE_UP : STAIRCASE_UP, 1, 1, 0, TYPE_SCENERY, 0));
            prefab.objects.add(new BuildingPrefab.PrefabObject(
                    STAIRCASE_DOWN, 1, 1, 1, TYPE_SCENERY, 0));
            List<int[]> upper = partition(prefab, style, width, height, 1, random);
            furnish(prefab, style, upper, random, -1);
            roof(prefab, style, width, height, 2);
        } else {
            roof(prefab, style, width, height, 1);
        }
        return prefab;
    }

    /** Object 2118 "Staircase", Climb-down; 2119 "Staircase", Climb-up. */
    private static final int STAIRCASE_UP = 2119;
    private static final int STAIRCASE_DOWN = 2118;

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

        for (int y = 0; y < height; y++) {
            addWall(prefab, style, 0, y, plane, ROT_WEST,
                    doorSideA == 0 && y == doorAlongA || doorSideB == 0 && y == doorAlongB, random);
            addWall(prefab, style, width - 1, y, plane, ROT_EAST,
                    doorSideA == 2 && y == doorAlongA || doorSideB == 2 && y == doorAlongB, random);
        }
        for (int x = 0; x < width; x++) {
            addWall(prefab, style, x, 0, plane, ROT_SOUTH,
                    doorSideA == 3 && x == doorAlongA || doorSideB == 3 && x == doorAlongB, random);
            addWall(prefab, style, x, height - 1, plane, ROT_NORTH,
                    doorSideA == 1 && x == doorAlongA || doorSideB == 1 && x == doorAlongB, random);
        }

        if (plane == 0) {
            // Record where a player walks in, for path routing.
            switch (doorSideA) {
                case 0 -> { prefab.doorX = 0; prefab.doorY = doorAlongA; }
                case 1 -> { prefab.doorX = doorAlongA; prefab.doorY = height - 1; }
                case 2 -> { prefab.doorX = width - 1; prefab.doorY = doorAlongA; }
                default -> { prefab.doorX = doorAlongA; prefab.doorY = 0; }
            }
        }
    }

    private static void addWall(BuildingPrefab prefab, BuildingStyle style,
                                int x, int y, int plane, int rotation, boolean door, Random random) {
        int id = door ? style.door : style.wall;
        prefab.objects.add(new BuildingPrefab.PrefabObject(id, x, y, plane, TYPE_WALL, rotation));
        if (door) {
            return;
        }
        // Windows and wall hangings, spaced out so a wall is not solid decoration.
        if (style.window > 0 && random.nextInt(5) == 0) {
            prefab.objects.add(new BuildingPrefab.PrefabObject(
                    style.window, x, y, plane, TYPE_WALL_DECOR, rotation));
        } else if (!style.wallDecor.isEmpty() && random.nextInt(9) == 0) {
            int[] decor = style.wallDecor.get(random.nextInt(style.wallDecor.size()));
            prefab.objects.add(new BuildingPrefab.PrefabObject(
                    decor[0], x, y, plane, decor[1], rotation));
        }
    }

    /**
     * Splits the interior into rooms with a doorway between each pair.
     *
     * Returns each room as {x0, y0, x1, y1} so the furnisher knows where the
     * open floor is.
     */
    private static List<int[]> partition(BuildingPrefab prefab, BuildingStyle style,
                                         int width, int height, int plane, Random random) {
        List<int[]> rooms = new ArrayList<>();
        rooms.add(new int[]{1, 1, width - 2, height - 2});
        int splits = (width >= 9 || height >= 9) ? 1 + random.nextInt(2) : random.nextInt(2);

        for (int i = 0; i < splits; i++) {
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
     * Fills rooms with furniture, and puts the building's utility in the largest
     * one where it has space around it.
     */
    private static void furnish(BuildingPrefab prefab, BuildingStyle style,
                                List<int[]> rooms, Random random, int utility) {
        if (rooms.isEmpty()) {
            return;
        }
        if (utility > 0) {
            int[] main = rooms.stream().max((a, b) -> area(a) - area(b)).orElse(rooms.get(0));
            prefab.objects.add(new BuildingPrefab.PrefabObject(utility,
                    (main[0] + main[2]) / 2, (main[1] + main[3]) / 2, 0, TYPE_SCENERY,
                    random.nextInt(4)));
            prefab.facilities.add(String.valueOf(utility));
        }
        if (style.furniture.isEmpty()) {
            return;
        }
        for (int[] room : rooms) {
            int pieces = Math.max(1, area(room) / 8);
            for (int i = 0; i < pieces; i++) {
                int x = room[0] + random.nextInt(room[2] - room[0] + 1);
                int y = room[1] + random.nextInt(room[3] - room[1] + 1);
                // Leave the doorway tiles clear; furniture in a doorway blocks it.
                if (x == prefab.doorX && y == prefab.doorY) {
                    continue;
                }
                int id = style.furniture.get(random.nextInt(style.furniture.size()));
                prefab.objects.add(new BuildingPrefab.PrefabObject(
                        id, x, y, 0, TYPE_SCENERY, random.nextInt(4)));
            }
        }
    }

    /** Lays roof pieces over the footprint, one plane above the top storey. */
    private static void roof(BuildingPrefab prefab, BuildingStyle style,
                             int width, int height, int plane) {
        if (!style.hasRoof()) {
            return;
        }
        int[] piece = style.roofPieces.get(0);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                prefab.objects.add(new BuildingPrefab.PrefabObject(
                        piece[0], x, y, plane, piece[1], 0));
            }
        }
        prefab.planes = Math.max(prefab.planes, plane + 1);
    }
}
