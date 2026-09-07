package com.elvarg.game.world.tool;

import com.elvarg.game.world.codec.LandscapeCodec;
import com.elvarg.game.world.codec.MapIndex;
import com.elvarg.game.world.codec.PlacedObject;
import com.elvarg.game.world.codec.TerrainRegion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Proves {@link LandscapeCodec} against every real map file the game ships.
 *
 * A codec that only round-trips its own output proves nothing; the risk is that
 * it disagrees with the client and server parsers on real data. So this reads
 * each shipped file, decodes it, re-encodes it, and decodes it again, asserting
 * the two decodes are semantically identical.
 *
 * The re-encoded bytes are deliberately <em>not</em> compared to the original:
 * the format permits a tile whose height is procedural (a bare 0 terminator),
 * and the generator always writes explicit heights instead. Semantic equality
 * after a second decode is the property that actually matters.
 *
 * Usage: {@code CodecSelfTest [dataDirectory]}
 */
public final class CodecSelfTest {

    public static void main(String[] args) throws IOException {
        Path clipping = Paths.get(args.length > 0 ? args[0] : "../data/clipping");
        if (!Files.isDirectory(clipping)) {
            System.err.println("clipping directory not found: " + clipping.toAbsolutePath());
            System.exit(2);
        }
        MapIndex index = MapIndex.load(clipping.resolve("map_index"));
        Path maps = clipping.resolve("maps");
        System.out.println("map_index entries          " + index.size());

        int terrainOk = 0;
        int objectOk = 0;
        int objectTotal = 0;
        int objectTrailing = 0;
        int skipped = 0;

        for (int regionId = 0; regionId <= 0xffff; regionId++) {
            if (!index.contains(regionId)) {
                continue;
            }
            Path terrainFile = maps.resolve(index.terrainFile(regionId) + ".dat");
            if (Files.exists(terrainFile)) {
                byte[] raw = gunzip(Files.readAllBytes(terrainFile));
                TerrainRegion first = LandscapeCodec.decodeTerrain(raw);
                TerrainRegion second = LandscapeCodec.decodeTerrain(LandscapeCodec.encodeTerrain(first));
                assertTerrainEqual(first, second, regionId);
                terrainOk++;
            } else {
                skipped++;
            }

            Path objectFile = maps.resolve(index.objectFile(regionId) + ".dat");
            if (Files.exists(objectFile)) {
                byte[] raw = gunzip(Files.readAllBytes(objectFile));
                List<PlacedObject> first = LandscapeCodec.decodeObjects(raw);
                List<PlacedObject> second = LandscapeCodec.decodeObjects(LandscapeCodec.encodeObjects(first));
                assertObjectsEqual(first, second, regionId);
                objectOk++;
                objectTotal += first.size();
                if (LandscapeCodec.trailingBytes(raw) > 0) {
                    objectTrailing++;
                }
            }
        }

        System.out.println("terrain files round-tripped " + terrainOk + " (0 failures)");
        System.out.println("object files round-tripped  " + objectOk + " (0 failures)");
        System.out.println("objects round-tripped       " + objectTotal);
        System.out.println("files with trailing junk    " + objectTrailing + " (tolerated, as the real parsers do)");
        if (skipped > 0) {
            System.out.println("terrain files missing       " + skipped);
        }
        System.out.println("\nCODEC SELF-TEST PASSED");
    }

    private static void assertTerrainEqual(TerrainRegion a, TerrainRegion b, int regionId) {
        for (int p = 0; p < TerrainRegion.PLANES; p++) {
            for (int x = 0; x < TerrainRegion.SIZE; x++) {
                for (int y = 0; y < TerrainRegion.SIZE; y++) {
                    if (a.explicitHeight[p][x][y] != b.explicitHeight[p][x][y]) {
                        throw new AssertionError("region " + regionId + " plane " + p + " tile " + x + "," + y
                                + ": height explicitness differs");
                    }
                    if (a.explicitHeight[p][x][y]) {
                        check(a.height[p][x][y], b.height[p][x][y], regionId, p, x, y, "height");
                    }
                    check(a.underlay[p][x][y], b.underlay[p][x][y], regionId, p, x, y, "underlay");
                    check(a.overlay[p][x][y], b.overlay[p][x][y], regionId, p, x, y, "overlay");
                    check(a.flags[p][x][y], b.flags[p][x][y], regionId, p, x, y, "flags");
                    if (a.overlay[p][x][y] != 0) {
                        check(a.overlayShape[p][x][y], b.overlayShape[p][x][y], regionId, p, x, y, "overlayShape");
                        check(a.overlayRotation[p][x][y], b.overlayRotation[p][x][y], regionId, p, x, y, "overlayRot");
                    }
                }
            }
        }
    }

    private static void check(int a, int b, int regionId, int p, int x, int y, String what) {
        if (a != b) {
            throw new AssertionError("region " + regionId + " plane " + p + " tile " + x + "," + y
                    + ": " + what + " " + a + " != " + b);
        }
    }

    private static void assertObjectsEqual(List<PlacedObject> a, List<PlacedObject> b, int regionId) {
        if (a.size() != b.size()) {
            throw new AssertionError("region " + regionId + ": object count " + a.size() + " != " + b.size());
        }
        // encodeObjects sorts, so compare as sorted multisets.
        List<String> sa = a.stream().map(CodecSelfTest::key).sorted().toList();
        List<String> sb = b.stream().map(CodecSelfTest::key).sorted().toList();
        for (int i = 0; i < sa.size(); i++) {
            if (!sa.get(i).equals(sb.get(i))) {
                throw new AssertionError("region " + regionId + ": object " + sa.get(i) + " != " + sb.get(i));
            }
        }
    }

    private static String key(PlacedObject o) {
        return o.id + "@" + o.plane + ":" + o.localX + "," + o.localY + "/" + o.type + "r" + o.rotation;
    }

    private static byte[] gunzip(byte[] data) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(data))) {
            return readAll(in);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
