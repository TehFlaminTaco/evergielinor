package com.elvarg.game.world.tool;

import com.elvarg.game.world.codec.LandscapeCodec;
import com.elvarg.game.world.codec.PlacedObject;
import com.elvarg.game.world.gen.WorldGenerator;
import com.google.gson.GsonBuilder;

import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Proves that a seed reproduces a world.
 *
 * This is the property the whole design rests on: worlds are shareable and
 * re-rollable only if generation is a pure function of the seed. It is easy to
 * break by accident - an unseeded Random, iteration over a HashMap, anything
 * that depends on the order things happened to be visited - so it is checked
 * rather than assumed.
 *
 * Two independent runs of the same seed are compared on both outputs: the world
 * record, and every byte of every map file. A third run on a different seed
 * confirms the check would actually notice a difference.
 */
public final class DeterminismTest {

    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 847293L;
        var definitions = Paths.get("../data/definitions");

        System.out.println("generating seed " + seed + " twice...");
        WorldGenerator.Result first = WorldGenerator.generate(seed, definitions);
        WorldGenerator.Result second = WorldGenerator.generate(seed, definitions);

        String recordA = recordDigest(first);
        String recordB = recordDigest(second);
        String mapA = mapDigest(first);
        String mapB = mapDigest(second);

        System.out.println("  world record  " + recordA + (recordA.equals(recordB) ? "  MATCH" : "  DIFFER"));
        System.out.println("  map files     " + mapA + (mapA.equals(mapB) ? "  MATCH" : "  DIFFER"));

        System.out.println("generating a different seed as a control...");
        WorldGenerator.Result other = WorldGenerator.generate(seed + 1, definitions);
        String recordC = recordDigest(other);
        String mapC = mapDigest(other);
        System.out.println("  world record  " + recordC + (recordC.equals(recordA) ? "  UNCHANGED" : "  differs"));
        System.out.println("  map files     " + mapC + (mapC.equals(mapA) ? "  UNCHANGED" : "  differs"));

        boolean reproducible = recordA.equals(recordB) && mapA.equals(mapB);
        boolean seedMatters = !recordA.equals(recordC) && !mapA.equals(mapC);

        if (!reproducible) {
            System.out.println("\nFAIL: the same seed produced two different worlds");
        }
        if (!seedMatters) {
            System.out.println("\nFAIL: changing the seed did not change the world");
        }
        System.out.println(reproducible && seedMatters ? "\nDETERMINISM VERIFIED" : "\nDETERMINISM BROKEN");
        System.exit(reproducible && seedMatters ? 0 : 1);
    }

    private static String recordDigest(WorldGenerator.Result result) throws Exception {
        String json = new GsonBuilder().create().toJson(result.world);
        // The timestamp is the one field that legitimately varies between runs.
        json = json.replaceAll("\"generatedAtEpochMillis\":[0-9]+", "");
        return sha256(json.getBytes("UTF-8"));
    }

    /** Digest over every region's encoded terrain and objects, in region order. */
    private static String mapDigest(WorldGenerator.Result result) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int regionId : new TreeSet<>(result.terrain.keySet())) {
            digest.update(LandscapeCodec.encodeTerrain(result.terrain.get(regionId)));
        }
        for (Map.Entry<Integer, List<PlacedObject>> entry
                : new java.util.TreeMap<>(result.objects).entrySet()) {
            digest.update(LandscapeCodec.encodeObjects(entry.getValue()));
        }
        return hex(digest.digest());
    }

    private static String sha256(byte[] data) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            out.append(String.format("%02x", bytes[i]));
        }
        return out.toString();
    }
}
