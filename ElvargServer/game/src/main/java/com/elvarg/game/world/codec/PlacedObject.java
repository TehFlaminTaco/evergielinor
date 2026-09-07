package com.elvarg.game.world.codec;

/**
 * One entry in a region's object (landscape) file: an object id at a local tile,
 * with the format's type and rotation.
 *
 * Objects written into the map file are loaded by the client from its cache and
 * by the server through {@code RegionManager.loadMapFiles}, which means they get
 * collision and interaction for free and never occupy a slot in
 * {@code World.getObjects()}. That is deliberate - see the Phase 0 report on why
 * the dynamic object list does not scale to a generated world.
 *
 * @author EverGielinor world generator
 */
public final class PlacedObject {

    /** Standard type for a full free-standing scenery object (trees, rocks, ...). */
    public static final int TYPE_SCENERY = 10;
    /** Type for a ground-level decoration such as a floor marking. */
    public static final int TYPE_GROUND_DECOR = 22;
    /** Type for a wall running along the west/north edge of its tile. */
    public static final int TYPE_WALL = 0;

    public final int id;
    public final int localX;
    public final int localY;
    public final int plane;
    public final int type;
    public final int rotation;

    public PlacedObject(int id, int localX, int localY, int plane, int type, int rotation) {
        this.id = id;
        this.localX = localX;
        this.localY = localY;
        this.plane = plane;
        this.type = type;
        this.rotation = rotation & 3;
    }

    public PlacedObject(int id, int localX, int localY, int plane) {
        this(id, localX, localY, plane, TYPE_SCENERY, 0);
    }

    /**
     * The packed location word the landscape format uses, which the object stream
     * delta-encodes. Layout is {@code plane << 12 | x << 6 | y}.
     */
    public int packedLocation() {
        return (plane << 12) | (localX << 6) | localY;
    }
}
