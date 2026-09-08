package com.elvarg.game.world.tool;

import com.elvarg.game.world.gen.Biome;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Checks every biome's underlay against the client's own floor definitions.
 *
 * The client resolves an underlay through
 * {@code FloorDefinition.underlays[value - 1]}, so an id chosen by reading
 * flo.dat with the value as a direct index lands one entry short. That mistake
 * shipped: forest was written as 47, which resolves to entry 46, which is
 * #000000 - every forest on the island rendered as pure black ground with the
 * trees still standing on it.
 *
 * It is invisible from the server, invisible in the generator's own preview, and
 * only shows up by walking there. So it gets a test: this reads flo.dat straight
 * out of the client cache and fails if any biome resolves to black, or to a
 * colour that is not what the enum claims.
 *
 * The biome table is only half of it, though: building floors, dungeon floors and
 * mining pits all carry underlays and overlays of their own, harvested from the
 * original map or picked by hand, and any one of those can be black too. So the
 * second half of this generates worlds and checks every floor value that actually
 * reaches a map file, not just the ones the biome enum knows about.
 *
 * Usage: {@code VerifyPalette [clientCacheDir] [seed...]}
 */
public final class VerifyPalette {

    private static final int BLOCK_SIZE = 520;
    private static final int CONFIG_ARCHIVE = 2;

    public static void main(String[] args) throws Exception {
        Path cache = Paths.get(args.length > 0 ? args[0] : "../../ElvargClient/Cache");
        if (!Files.isDirectory(cache)) {
            System.err.println("client cache not found at " + cache.toAbsolutePath());
            System.exit(2);
        }

        long[] seeds = new long[Math.max(0, args.length - 1)];
        for (int i = 1; i < args.length && !args[i].equals("list"); i++) {
            seeds[i - 1] = Long.parseLong(args[i]);
        }
        if (seeds.length == 0) {
            seeds = new long[]{847293L, 1337L};
        }

        Floors floors = readFloors(cache);
        if (args.length > 1 && args[1].equals("list")) {
            // Listing mode: the whole palette, for picking ids by eye.
            for (int i = 0; i < floors.underlayColours.size(); i++) {
                System.out.printf("  underlay %-4d %s%n", i + 1, hex(floors.underlayColours.get(i)));
            }
            for (int i = 0; i < floors.overlayColours.size(); i++) {
                System.out.printf("  overlay  %-4d %s%s%n", i + 1, hex(floors.overlayColours.get(i)),
                        floors.overlayTextured.get(i) ? "  (textured)" : "");
            }
            return;
        }
        List<Integer> underlays = floors.underlayColours;
        System.out.println("flo.dat underlay entries: " + underlays.size());
        System.out.println();
        System.out.printf("  %-16s %-7s %-7s %-9s %-9s %s%n",
                "BIOME", "VALUE", "ENTRY", "CLAIMED", "ACTUAL", "");

        int failures = 0;
        for (Biome biome : Biome.values()) {
            int value = biome.underlay();
            int entry = value - 1;
            if (entry < 0 || entry >= underlays.size()) {
                System.out.printf("  %-16s %-7d %-7d %-9s %-9s  OUT OF RANGE%n",
                        biome, value, entry, hex(biome.renderedColour()), "-");
                failures++;
                continue;
            }
            int actual = underlays.get(entry);
            boolean black = actual == 0;
            boolean mismatch = actual != biome.renderedColour();
            String note = black ? "  BLACK - would render as void"
                    : mismatch ? "  MISMATCH" : "";
            System.out.printf("  %-16s %-7d %-7d %-9s %-9s%s%n",
                    biome, value, entry, hex(biome.renderedColour()), hex(actual), note);
            if (black || mismatch) {
                failures++;
            }
        }

        failures += scanGeneratedWorlds(floors, seeds);

        System.out.println();
        System.out.println(failures == 0
                ? "PALETTE VERIFIED - every floor value on the island resolves to a visible colour"
                : failures + " PALETTE FAULT(S)");
        System.exit(failures == 0 ? 0 : 1);
    }

    /**
     * Generates worlds and checks every floor value that lands in a map file.
     *
     * A biome table that is right is not enough: a building floor or a dungeon
     * floor with a black underlay renders as the same void, and it would only be
     * found by walking into that particular building.
     */
    private static int scanGeneratedWorlds(Floors floors, long[] seeds) throws IOException {
        int failures = 0;
        for (long seed : seeds) {
            java.util.Map<Integer, Integer> underlayUse = new java.util.TreeMap<>();
            java.util.Map<Integer, Integer> overlayUse = new java.util.TreeMap<>();
            com.elvarg.game.world.gen.WorldGenerator.Result result =
                    com.elvarg.game.world.gen.WorldGenerator.generate(seed, Paths.get("../data/definitions"));
            for (com.elvarg.game.world.codec.TerrainRegion region : result.terrain.values()) {
                for (int plane = 0; plane < com.elvarg.game.world.codec.TerrainRegion.PLANES; plane++) {
                    for (int x = 0; x < com.elvarg.game.world.codec.TerrainRegion.SIZE; x++) {
                        for (int y = 0; y < com.elvarg.game.world.codec.TerrainRegion.SIZE; y++) {
                            int underlay = region.underlay[plane][x][y];
                            if (underlay != 0) {
                                underlayUse.merge(underlay, 1, Integer::sum);
                            }
                            int overlay = region.overlay[plane][x][y];
                            if (overlay != 0) {
                                overlayUse.merge(overlay, 1, Integer::sum);
                            }
                        }
                    }
                }
            }

            System.out.println();
            System.out.println("seed " + seed + ": " + underlayUse.size() + " distinct underlays, "
                    + overlayUse.size() + " distinct overlays in the generated map");
            for (var entry : underlayUse.entrySet()) {
                int index = entry.getKey() - 1;
                if (index < 0 || index >= floors.underlayColours.size()) {
                    System.out.printf("  underlay %-4d %,9d tiles   OUT OF RANGE%n",
                            entry.getKey(), entry.getValue());
                    failures++;
                } else if (floors.underlayColours.get(index) == 0) {
                    System.out.printf("  underlay %-4d %,9d tiles   BLACK - renders as void%n",
                            entry.getKey(), entry.getValue());
                    failures++;
                }
            }
            for (var entry : overlayUse.entrySet()) {
                int index = entry.getKey() - 1;
                if (index < 0 || index >= floors.overlayColours.size()) {
                    System.out.printf("  overlay  %-4d %,9d tiles   OUT OF RANGE%n",
                            entry.getKey(), entry.getValue());
                    failures++;
                } else if (floors.overlayColours.get(index) == 0 && !floors.overlayTextured.get(index)) {
                    // A textured overlay draws its texture, so a black rgb there is
                    // not a fault; an untextured one really would be a black tile.
                    // Magenta (0xff00ff) is not a fault either: the client reads it
                    // as "shape this tile but draw no overlay colour", which is how
                    // the original map shapes a tile and lets its underlay show. The
                    // floor underneath is a real floor, so it is deliberate.
                    System.out.printf("  overlay  %-4d %,9d tiles   BLACK - renders as void%n",
                            entry.getKey(), entry.getValue());
                    failures++;
                }
            }
            System.out.println("  every value above the ones listed resolves to a visible colour");
        }
        return failures;
    }

    private static String hex(int rgb) {
        return String.format("#%06x", rgb);
    }

    // ------------------------------------------------------------------

    /** The two floor tables flo.dat carries, in the order the client reads them. */
    private static final class Floors {
        final List<Integer> underlayColours = new ArrayList<>();
        final List<Integer> overlayColours = new ArrayList<>();
        final List<Boolean> overlayTextured = new ArrayList<>();
    }

    /** Reads both floor tables out of the config archive's flo.dat. */
    private static Floors readFloors(Path cache) throws IOException {
        byte[] archive = readCacheFile(cache, 0, CONFIG_ARCHIVE);
        byte[] flo = readArchiveEntry(archive, nameHash("flo.dat"));
        if (flo == null) {
            throw new IOException("flo.dat not present in the config archive");
        }
        Floors floors = new Floors();
        int[] pos = {0};
        int underlayCount = ((flo[pos[0]] & 0xff) << 8) | (flo[pos[0] + 1] & 0xff);
        pos[0] += 2;
        for (int i = 0; i < underlayCount; i++) {
            floors.underlayColours.add(readUnderlay(flo, pos));
        }
        // The overlay table follows the underlay table in the same file, which is
        // what the client's FloorDefinition.init does.
        int overlayCount = ((flo[pos[0]] & 0xff) << 8) | (flo[pos[0] + 1] & 0xff);
        pos[0] += 2;
        for (int i = 0; i < overlayCount; i++) {
            readOverlay(flo, pos, floors);
        }
        return floors;
    }

    private static int readUnderlay(byte[] flo, int[] pos) {
        int rgb = 0;
        while (true) {
            int opcode = flo[pos[0]++] & 0xff;
            if (opcode == 0) {
                return rgb;
            }
            if (opcode == 1) {
                rgb = ((flo[pos[0]] & 0xff) << 16) | ((flo[pos[0] + 1] & 0xff) << 8) | (flo[pos[0] + 2] & 0xff);
                pos[0] += 3;
            }
        }
    }

    private static void readOverlay(byte[] flo, int[] pos, Floors floors) {
        int rgb = 0;
        boolean textured = false;
        while (true) {
            int opcode = flo[pos[0]++] & 0xff;
            if (opcode == 0) {
                floors.overlayColours.add(rgb);
                floors.overlayTextured.add(textured);
                return;
            }
            switch (opcode) {
                case 1 -> {
                    rgb = ((flo[pos[0]] & 0xff) << 16) | ((flo[pos[0] + 1] & 0xff) << 8)
                            | (flo[pos[0] + 2] & 0xff);
                    pos[0] += 3;
                }
                case 2 -> {
                    textured = true;
                    pos[0]++;
                }
                case 5 -> { /* does not occlude; no payload */ }
                case 7 -> pos[0] += 3;
                default -> throw new IllegalStateException("unknown overlay opcode " + opcode);
            }
        }
    }

    private static byte[] readCacheFile(Path cache, int indexNo, int fileId) throws IOException {
        try (RandomAccessFile index = new RandomAccessFile(
                cache.resolve("main_file_cache.idx" + indexNo).toFile(), "r");
             RandomAccessFile data = new RandomAccessFile(
                     cache.resolve("main_file_cache.dat").toFile(), "r")) {
            index.seek((long) fileId * 6);
            byte[] entry = new byte[6];
            index.readFully(entry);
            int size = triple(entry, 0);
            int block = triple(entry, 3);

            ByteArrayOutputStream out = new ByteArrayOutputStream(size);
            byte[] buffer = new byte[BLOCK_SIZE];
            int written = 0;
            while (written < size) {
                data.seek((long) block * BLOCK_SIZE);
                data.readFully(buffer);
                int take = Math.min(BLOCK_SIZE - 8, size - written);
                out.write(buffer, 8, take);
                written += take;
                block = triple(buffer, 4);
            }
            return out.toByteArray();
        }
    }

    /**
     * Pulls one file out of a .jag archive. Handles both whole-archive
     * compression and the per-file form.
     */
    private static byte[] readArchiveEntry(byte[] archive, int wantedHash) throws IOException {
        int uncompressed = triple(archive, 0);
        int compressed = triple(archive, 3);
        int pos = 6;
        boolean wholeArchive = uncompressed != compressed;
        if (wholeArchive) {
            archive = bunzip(archive, 6);
            pos = 0;
        }
        int count = ((archive[pos] & 0xff) << 8) | (archive[pos + 1] & 0xff);
        pos += 2;
        int[] hashes = new int[count];
        int[] sizes = new int[count];
        for (int i = 0; i < count; i++) {
            hashes[i] = ((archive[pos] & 0xff) << 24) | ((archive[pos + 1] & 0xff) << 16)
                    | ((archive[pos + 2] & 0xff) << 8) | (archive[pos + 3] & 0xff);
            sizes[i] = triple(archive, pos + 7);
            pos += 10;
        }
        for (int i = 0; i < count; i++) {
            if (hashes[i] == wantedHash) {
                byte[] blob = new byte[sizes[i]];
                System.arraycopy(archive, pos, blob, 0, sizes[i]);
                return wholeArchive ? blob : bunzip(blob, 0);
            }
            pos += sizes[i];
        }
        return null;
    }

    /** The cache's bzip2 streams have their four-byte header stripped. */
    private static byte[] bunzip(byte[] data, int offset) throws IOException {
        byte[] withHeader = new byte[data.length - offset + 4];
        withHeader[0] = 'B';
        withHeader[1] = 'Z';
        withHeader[2] = 'h';
        withHeader[3] = '1';
        System.arraycopy(data, offset, withHeader, 4, data.length - offset);
        try (InputStream in = new org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(
                new java.io.ByteArrayInputStream(withHeader))) {
            return in.readAllBytes();
        }
    }

    private static int triple(byte[] b, int off) {
        return ((b[off] & 0xff) << 16) | ((b[off + 1] & 0xff) << 8) | (b[off + 2] & 0xff);
    }

    private static int nameHash(String name) {
        int hash = 0;
        for (char c : name.toUpperCase().toCharArray()) {
            hash = hash * 61 + c - 32;
        }
        return hash;
    }
}
