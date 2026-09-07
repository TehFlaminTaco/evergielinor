package com.elvarg.game.world.codec;

/**
 * A mutable, in-memory representation of one 64x64 map region across all four
 * planes, in the terms the JAG landscape format actually stores.
 *
 * The client reconstructs a tile's appearance from four independent channels
 * (underlay, overlay, tile flags, height); this class holds them separately so
 * the generator can reason about each without worrying about the byte encoding.
 * {@link LandscapeCodec} owns the translation in both directions.
 *
 * @author EverGielinor world generator
 */
public final class TerrainRegion {

    public static final int PLANES = 4;
    public static final int SIZE = 64;

    /**
     * Tile flag bits, matching the client's MapRegion constants. The flag byte is
     * stored on the wire as {@code tileType - 49}, so only values 1..32 survive a
     * round trip.
     */
    public static final int FLAG_BLOCKED = 0x1;
    public static final int FLAG_BRIDGE = 0x2;
    public static final int FLAG_ROOF = 0x4;
    public static final int FLAG_FORCE_LOWEST_PLANE = 0x8;

    /**
     * Height is stored as the raw byte the format carries. The client renders it as
     * {@code -value * 8} world units and a tile is 128 units wide, so 16 steps of
     * this value equal one tile-width of elevation.
     *
     * The value 1 is reserved: the client maps a height byte of 1 to 0, so the
     * generator must never emit it. {@link #setHeight} enforces that.
     */
    public final int[][][] height = new int[PLANES][SIZE][SIZE];
    public final int[][][] underlay = new int[PLANES][SIZE][SIZE];
    public final int[][][] overlay = new int[PLANES][SIZE][SIZE];
    public final int[][][] overlayShape = new int[PLANES][SIZE][SIZE];
    public final int[][][] overlayRotation = new int[PLANES][SIZE][SIZE];
    public final int[][][] flags = new int[PLANES][SIZE][SIZE];

    /**
     * Whether a tile states its height outright. The format allows a tile to end
     * with a bare terminator instead, in which case the client derives the height
     * from its own noise function on plane 0, or from the plane below elsewhere.
     * That distinction is not cosmetic - a tile with an explicit height of 0 sits
     * at sea level, while a tile with no height at all follows the terrain around
     * it - so it has to be tracked per tile rather than per plane.
     */
    public final boolean[][][] explicitHeight = new boolean[PLANES][SIZE][SIZE];

    /**
     * Convenience marker for the generator: whether a plane carries authored
     * content. The encoder does not consult it - it encodes each tile faithfully -
     * but the generator uses it to decide which planes to populate.
     */
    public final boolean[] planeUsed = new boolean[PLANES];

    public TerrainRegion() {
        planeUsed[0] = true;
    }

    public void setHeight(int plane, int x, int y, int value) {
        if (value < 0) {
            value = 0;
        } else if (value > 255) {
            value = 255;
        }
        // The client reads 1 as 0; emitting it would silently flatten the tile.
        if (value == 1) {
            value = 2;
        }
        height[plane][x][y] = value;
        explicitHeight[plane][x][y] = true;
    }

    /**
     * Clears an explicit height, letting the client derive it instead.
     */
    public void clearHeight(int plane, int x, int y) {
        height[plane][x][y] = 0;
        explicitHeight[plane][x][y] = false;
    }

    public void setUnderlay(int plane, int x, int y, int id) {
        underlay[plane][x][y] = id;
        if (id != 0) {
            planeUsed[plane] = true;
        }
    }

    public void setOverlay(int plane, int x, int y, int id, int shape, int rotation) {
        overlay[plane][x][y] = id;
        overlayShape[plane][x][y] = shape;
        overlayRotation[plane][x][y] = rotation & 3;
        if (id != 0) {
            planeUsed[plane] = true;
        }
    }

    public void addFlag(int plane, int x, int y, int flag) {
        flags[plane][x][y] |= flag;
        if (flag != 0) {
            planeUsed[plane] = true;
        }
    }

    public boolean isBlocked(int plane, int x, int y) {
        return (flags[plane][x][y] & FLAG_BLOCKED) != 0;
    }
}
