package com.elvarg.game.world.gen;

import java.util.ArrayList;
import java.util.List;

/**
 * A building lifted whole out of the original RuneScape map.
 *
 * Hand-authoring convincing architecture out of raw wall ids is a losing game -
 * the result reads as a box with a door in it. These prefabs are the real thing:
 * every wall, window, roof, floor tile and piece of furniture from an actual
 * building in Lumbridge, Falador, Varrock and elsewhere, captured with its
 * geometry intact and stamped into generated settlements.
 *
 * Coordinates are relative to the prefab's south-west corner.
 *
 * @author EverGielinor world generator
 */
public final class BuildingPrefab {

    /** One object within the prefab, in prefab-local coordinates. */
    public static final class PrefabObject {
        public int id;
        public int x;
        public int y;
        public int plane;
        public int type;
        public int rotation;

        public PrefabObject() {
        }

        public PrefabObject(int id, int x, int y, int plane, int type, int rotation) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.plane = plane;
            this.type = type;
            this.rotation = rotation;
        }
    }

    /** One floor tile's materials, so a stamped building keeps its own flooring. */
    public static final class PrefabTile {
        public int x;
        public int y;
        public int plane;
        public int underlay;
        public int overlay;
        public int overlayShape;
        public int overlayRotation;

        public PrefabTile() {
        }
    }

    public String name;
    /** Region the prefab was captured from, kept so a bad prefab can be traced back. */
    public int sourceRegion;
    public int width;
    public int height;
    /** Highest plane the building occupies, so upper storeys survive the copy. */
    public int planes = 1;
    public final List<PrefabObject> objects = new ArrayList<>();
    public final List<PrefabTile> tiles = new ArrayList<>();
    /** Entrance tile in prefab-local coordinates, or -1 when no door was found. */
    public int doorX = -1;
    public int doorY = -1;
    /** Names of interactive facilities found inside, for matching to a town's needs. */
    public final List<String> facilities = new ArrayList<>();

    public int area() {
        return width * height;
    }

    public boolean hasDoor() {
        return doorX >= 0;
    }

    @Override
    public String toString() {
        return String.format("%s %dx%d, %d objects, %d planes%s",
                name, width, height, objects.size(), planes,
                facilities.isEmpty() ? "" : " " + facilities);
    }
}
