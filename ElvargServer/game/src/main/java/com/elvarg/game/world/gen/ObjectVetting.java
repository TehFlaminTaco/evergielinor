package com.elvarg.game.world.gen;

import com.elvarg.game.definition.ObjectDefinition;

/**
 * Checks an object is fit to place, and reports how much room it needs.
 *
 * Objects in this cache are not all one tile. A bush is 2x2, a cave entrance is
 * 4x2, and several tree models are 2x2 or 3x3. Placing them a tile apart without
 * consulting their definition makes them intersect, which renders as models
 * sunk into one another - the half-buried cave mouths that showed up in play.
 *
 * Everything the generator places goes through here first.
 *
 * @author EverGielinor world generator
 */
public final class ObjectVetting {

    private ObjectVetting() {
    }

    /**
     * Whether an object id is safe to place as free-standing scenery: it has a
     * definition, it has a name, and its footprint is a sane size.
     */
    public static boolean isPlaceable(int objectId) {
        ObjectDefinition definition = definitionOf(objectId);
        if (definition == null || definition.name == null || definition.name.isEmpty()
                || "null".equalsIgnoreCase(definition.name)) {
            return false;
        }
        int sizeX = Math.max(1, definition.objectSizeX);
        int sizeY = Math.max(1, definition.objectSizeY);
        // Anything larger than this is a landmark that belongs in a hand-built
        // scene, not something to scatter across a hillside.
        return sizeX <= 8 && sizeY <= 8;
    }

    public static int sizeX(int objectId) {
        ObjectDefinition definition = definitionOf(objectId);
        return definition == null ? 1 : Math.max(1, definition.objectSizeX);
    }

    public static int sizeY(int objectId) {
        ObjectDefinition definition = definitionOf(objectId);
        return definition == null ? 1 : Math.max(1, definition.objectSizeY);
    }

    /**
     * Footprint of an object once rotated. Odd rotations swap the axes, which is
     * how the format works and is easy to forget when reserving space.
     */
    public static int footprintX(int objectId, int rotation) {
        return (rotation & 1) == 0 ? sizeX(objectId) : sizeY(objectId);
    }

    public static int footprintY(int objectId, int rotation) {
        return (rotation & 1) == 0 ? sizeY(objectId) : sizeX(objectId);
    }

    public static String nameOf(int objectId) {
        ObjectDefinition definition = definitionOf(objectId);
        return definition == null || definition.name == null ? "?" : definition.name;
    }

    private static ObjectDefinition definitionOf(int objectId) {
        if (objectId < 0) {
            return null;
        }
        try {
            return ObjectDefinition.forId(objectId);
        } catch (RuntimeException e) {
            // A malformed entry in loc.dat should skip the object, not stop the world.
            return null;
        }
    }
}
