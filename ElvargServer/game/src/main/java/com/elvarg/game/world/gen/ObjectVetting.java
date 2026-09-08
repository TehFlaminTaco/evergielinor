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
 * It also answers the question that turns out to matter most for buildings:
 * whether an object has a model for the landscape type it is being placed at.
 * The client resolves one through {@code ObjectDefinition.model}, which searches
 * {@code modelTypes} for the requested type and returns null if it is not there -
 * and a null model draws nothing at all, silently. That is how a wall ends up
 * with holes at its corners, and how a roof piece placed at the wrong type
 * vanishes: the object is in the map file, the server clips it, and the player
 * sees empty air.
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

    /**
     * Whether an object will actually draw at a given landscape type.
     *
     * This mirrors {@code ObjectDefinition.model} in the client exactly: an
     * object with a {@code modelTypes} table only draws at the types listed in
     * it, and an object without one is a plain scenery model that draws only at
     * type 10. Anything else returns a null model, which renders as nothing.
     */
    public static boolean rendersAt(int objectId, int landscapeType) {
        ObjectDefinition definition = definitionOf(objectId);
        if (definition == null) {
            return false;
        }
        if (definition.modelTypes == null) {
            return landscapeType == 10;
        }
        for (int type : definition.modelTypes) {
            if (type == landscapeType) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether an object can form a closed wall: it needs a straight run (type 0)
     * and a corner (type 2). A rectangle of walls has four tiles that carry two
     * edges each, and only type 2 draws both - a tile can hold one wall object
     * per plane, so two straight walls on a corner tile means the second replaces
     * the first and the corner is simply missing.
     */
    public static boolean canFormWalls(int objectId) {
        return rendersAt(objectId, 0) && rendersAt(objectId, 2);
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
