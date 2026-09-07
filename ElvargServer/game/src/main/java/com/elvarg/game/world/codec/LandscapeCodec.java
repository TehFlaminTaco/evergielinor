package com.elvarg.game.world.codec;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Encoder and decoder for the JAG landscape format used by both the Elvarg client
 * (com.runescape.scene.MapRegion#readTile / #readObjectMap) and the Elvarg server
 * (com.elvarg.game.collision.RegionManager#loadMapFiles).
 *
 * Both sides parse the same bytes independently, so anything written here has to
 * satisfy both readers at once. The tile grammar, taken from the client's
 * readTile, is a repeating sequence per tile terminated by 0 or by a height pair:
 *
 * <pre>
 *   0            end of tile; the client derives the height procedurally
 *   1, h         end of tile; height is -h * 8 world units (h == 1 reads as 0)
 *   2..49, id    overlay: shape = (type - 2) / 4, rotation = (type - 2) &amp; 3
 *   50..81       tile flags, value = type - 49
 *   82..255      underlay, value = type - 81
 * </pre>
 *
 * Ordering within a tile is free because the ranges are disjoint, but the
 * terminator must come last. We emit underlay, overlay, flags, then height.
 *
 * @author EverGielinor world generator
 */
public final class LandscapeCodec {

    /** Lowest tile type that carries an overlay. */
    private static final int OVERLAY_BASE = 2;
    /** Offset applied to tile flags on the wire. */
    private static final int FLAG_OFFSET = 49;
    /** Offset applied to underlay ids on the wire. */
    private static final int UNDERLAY_OFFSET = 81;
    /** Highest underlay id the byte range can carry (255 - UNDERLAY_OFFSET). */
    public static final int MAX_UNDERLAY = 255 - UNDERLAY_OFFSET;
    /**
     * Object ids travel as an unsigned short in the spawn packet
     * (PacketSender#sendObject), so anything beyond this cannot be a real object
     * however it decodes. The junk-tailed map files in this cache decode to ids in
     * the hundreds of thousands.
     */
    public static final int MAX_OBJECT_ID = 0xffff;

    private LandscapeCodec() {
    }

    // ------------------------------------------------------------------
    // Terrain
    // ------------------------------------------------------------------

    /**
     * Encodes a region's terrain. Iteration order is plane, then x, then y, which
     * is the order both readers expect.
     */
    public static byte[] encodeTerrain(TerrainRegion region) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(48 * 1024);
        for (int plane = 0; plane < TerrainRegion.PLANES; plane++) {
            for (int x = 0; x < TerrainRegion.SIZE; x++) {
                for (int y = 0; y < TerrainRegion.SIZE; y++) {
                    encodeTile(out, region, plane, x, y);
                }
            }
        }
        return out.toByteArray();
    }

    private static void encodeTile(ByteArrayOutputStream out, TerrainRegion region, int plane, int x, int y) {
        int underlay = region.underlay[plane][x][y];
        if (underlay != 0) {
            // The wire byte is underlay + 81, so the id must fit in 1..174. The client
            // stores it in a signed byte but masks with 0xff before indexing
            // FloorDefinition.underlays[id - 1], so the full range survives.
            if (underlay < 1 || underlay > MAX_UNDERLAY) {
                throw new IllegalArgumentException("underlay " + underlay + " out of range 1.." + MAX_UNDERLAY);
            }
            out.write(underlay + UNDERLAY_OFFSET);
        }

        int overlay = region.overlay[plane][x][y];
        if (overlay != 0) {
            // Overlay ids are written as a raw byte and read back with readSignedByte,
            // then masked with 0xff, so ids above 127 round-trip as negative values.
            if (overlay < -128 || overlay > 255) {
                throw new IllegalArgumentException("overlay " + overlay + " does not fit in a byte");
            }
            int shape = region.overlayShape[plane][x][y];
            int rotation = region.overlayRotation[plane][x][y] & 3;
            if (shape < 0 || shape > 11) {
                throw new IllegalArgumentException("overlay shape " + shape + " out of range 0..11");
            }
            out.write(OVERLAY_BASE + shape * 4 + rotation);
            out.write(overlay & 0xff);
        }

        int flags = region.flags[plane][x][y];
        if (flags != 0) {
            if (flags < 1 || flags > 32) {
                throw new IllegalArgumentException("tile flags " + flags + " out of range 1..32");
            }
            out.write(flags + FLAG_OFFSET);
        }

        if (region.explicitHeight[plane][x][y]) {
            out.write(1);
            out.write(region.height[plane][x][y] & 0xff);
        } else {
            // Bare terminator: the client derives this tile's height itself.
            out.write(0);
        }
    }

    /**
     * Decodes a region's terrain. Used by the round-trip test and by tooling that
     * inspects shipped map files.
     *
     * @throws IllegalStateException if the stream is not consumed exactly, which is
     *                               the strongest single check that an encoder and
     *                               the readers agree.
     */
    public static TerrainRegion decodeTerrain(byte[] data) {
        TerrainRegion region = new TerrainRegion();
        for (int plane = 0; plane < TerrainRegion.PLANES; plane++) {
            region.planeUsed[plane] = false;
        }
        int pos = 0;
        for (int plane = 0; plane < TerrainRegion.PLANES; plane++) {
            for (int x = 0; x < TerrainRegion.SIZE; x++) {
                for (int y = 0; y < TerrainRegion.SIZE; y++) {
                    while (true) {
                        int type = data[pos++] & 0xff;
                        if (type == 0) {
                            break;
                        } else if (type == 1) {
                            int h = data[pos++] & 0xff;
                            region.setHeight(plane, x, y, (h == 1) ? 0 : h);
                            region.planeUsed[plane] = true;
                            break;
                        } else if (type <= 49) {
                            // The client masks with 0xff before use, so keep the
                            // unsigned value; a signed read would not round-trip.
                            int id = data[pos++] & 0xff;
                            region.overlay[plane][x][y] = id;
                            region.overlayShape[plane][x][y] = (type - OVERLAY_BASE) / 4;
                            region.overlayRotation[plane][x][y] = (type - OVERLAY_BASE) & 3;
                            region.planeUsed[plane] = true;
                        } else if (type <= 81) {
                            region.flags[plane][x][y] = type - FLAG_OFFSET;
                            region.planeUsed[plane] = true;
                        } else {
                            region.underlay[plane][x][y] = type - UNDERLAY_OFFSET;
                            region.planeUsed[plane] = true;
                        }
                    }
                }
            }
        }
        if (pos != data.length) {
            throw new IllegalStateException("terrain stream not consumed exactly: " + pos + " of " + data.length);
        }
        return region;
    }

    // ------------------------------------------------------------------
    // Objects
    // ------------------------------------------------------------------

    /**
     * Encodes a region's object file. Both ids and locations are delta-encoded and
     * therefore must be written in ascending order; this method sorts defensively
     * rather than trusting the caller, because an out-of-order entry produces a
     * file that decodes without error into the wrong world.
     */
    public static byte[] encodeObjects(List<PlacedObject> objects) {
        List<PlacedObject> sorted = new ArrayList<>(objects);
        sorted.sort(Comparator.comparingInt((PlacedObject o) -> o.id)
                .thenComparingInt(PlacedObject::packedLocation));

        ByteArrayOutputStream out = new ByteArrayOutputStream(8 * 1024);
        int previousId = -1;
        int index = 0;
        while (index < sorted.size()) {
            int id = sorted.get(index).id;
            writeSmart(out, id - previousId);
            previousId = id;

            int previousLocation = 0;
            while (index < sorted.size() && sorted.get(index).id == id) {
                PlacedObject object = sorted.get(index);
                int location = object.packedLocation();
                writeSmart(out, location - previousLocation + 1);
                previousLocation = location;
                out.write((object.type << 2) | (object.rotation & 3));
                index++;
            }
            // Terminate this id's location list.
            writeSmart(out, 0);
        }
        // Terminate the id list.
        writeSmart(out, 0);
        return out.toByteArray();
    }

    /**
     * Decodes an object file, mirroring RegionManager#loadMapFiles.
     *
     * Trailing bytes after the terminator are tolerated and reported through
     * {@link #trailingBytes(byte[])} rather than rejected. 384 of the 1657 object
     * files this cache ships carry junk after their terminator, and both the real
     * client and server parsers stop at the terminator and ignore it - so being
     * stricter here would reject data the game itself accepts.
     */
    public static List<PlacedObject> decodeObjects(byte[] data) {
        List<PlacedObject> objects = new ArrayList<>();
        int[] pos = {0};
        int id = -1;
        int idDelta;
        while ((idDelta = readSmart(data, pos)) != 0) {
            id += idDelta;
            int location = 0;
            int locationDelta;
            while ((locationDelta = readSmart(data, pos)) != 0) {
                location += locationDelta - 1;
                int hash = data[pos[0]++] & 0xff;
                int plane = location >> 12;
                // RegionManager#loadMapFiles consumes every entry but places only
                // those on a real plane; the junk-tailed files in this cache rely on
                // that. Mirroring it here keeps the decoder in step with the server.
                if (plane < 0 || plane > 3 || id < 0 || id > MAX_OBJECT_ID) {
                    continue;
                }
                objects.add(new PlacedObject(id,
                        (location >> 6) & 0x3f,
                        location & 0x3f,
                        plane,
                        hash >> 2,
                        hash & 3));
            }
        }
        return objects;
    }

    /**
     * How many bytes sit after an object file's terminator. Zero for a clean file.
     */
    public static int trailingBytes(byte[] data) {
        int[] pos = {0};
        int idDelta;
        while ((idDelta = readSmart(data, pos)) != 0) {
            while (readSmart(data, pos) != 0) {
                pos[0]++;
            }
        }
        return data.length - pos[0];
    }

    /**
     * The "smart" integer both readers use: one byte below 128, otherwise a
     * big-endian short with the high bit set.
     */
    private static void writeSmart(ByteArrayOutputStream out, int value) {
        if (value < 0 || value > 32767) {
            throw new IllegalArgumentException("smart value out of range: " + value);
        }
        if (value < 128) {
            out.write(value);
        } else {
            int biased = value + 32768;
            out.write((biased >> 8) & 0xff);
            out.write(biased & 0xff);
        }
    }

    private static int readSmart(byte[] data, int[] pos) {
        int first = data[pos[0]] & 0xff;
        if (first < 128) {
            pos[0]++;
            return first;
        }
        int value = ((data[pos[0]] & 0xff) << 8) | (data[pos[0] + 1] & 0xff);
        pos[0] += 2;
        return value - 32768;
    }
}
