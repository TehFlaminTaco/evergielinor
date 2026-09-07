package com.elvarg.game.world.tool;

import com.elvarg.game.world.GeneratedWorld;
import com.elvarg.game.world.gen.Biome;
import com.elvarg.game.world.gen.IslandGeography;
import com.elvarg.game.world.gen.IslandLayout;
import com.elvarg.game.world.gen.Locality;
import com.elvarg.game.world.gen.WorldGenerator;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.EnumMap;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Renders a generated island's biomes and relief to a PNG, so geography can be
 * judged by eye before anything is written into the game's map files.
 *
 * Usage: {@code IslandPreview <seed> <outputPng>}
 */
public final class IslandPreview {

    /**
     * Preview colours, taken from each biome's real flo.dat underlay colour so the
     * preview and the rendered game agree about what the island looks like.
     */
    private static final Map<Biome, Integer> COLOURS = new EnumMap<>(Biome.class);

    static {
        COLOURS.put(Biome.OCEAN, 0x2c4a6b);
        COLOURS.put(Biome.SHALLOWS, 0x557799);
        COLOURS.put(Biome.BEACH, 0xcbba76);
        COLOURS.put(Biome.PLAINS, 0x6cac10);
        COLOURS.put(Biome.GRASSLAND, 0x58680b);
        COLOURS.put(Biome.FOREST, 0x35720a);
        COLOURS.put(Biome.DENSE_FOREST, 0x244d07);
        COLOURS.put(Biome.SWAMP, 0x125841);
        COLOURS.put(Biome.DESERT, 0x827944);
        COLOURS.put(Biome.ROCKY_HIGHLAND, 0x767676);
        COLOURS.put(Biome.MOUNTAIN, 0x4d4d4d);
        COLOURS.put(Biome.SNOW, 0xd1d6e7);
        COLOURS.put(Biome.VOLCANIC, 0x663300);
        COLOURS.put(Biome.WILDERNESS, 0x644e1e);
    }

    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 847293L;
        File out = new File(args.length > 1 ? args[1] : "island.png");
        // Optional close-up: --zoom <scale> centres on the start village so town
        // layout can be judged, not just island shape.
        int zoom = 1;
        int cropHalf = 0;
        for (int i = 2; i < args.length - 1; i++) {
            if (args[i].equals("--zoom")) {
                zoom = Integer.parseInt(args[i + 1]);
            } else if (args[i].equals("--crop")) {
                cropHalf = Integer.parseInt(args[i + 1]);
            }
        }

        // Run the whole generator, not just geography, so the preview shows the
        // world that would actually be installed - settlements, roads and dungeon
        // mouths included - rather than the landscape they were placed on.
        long started = System.currentTimeMillis();
        WorldGenerator.Result result = WorldGenerator.generate(seed,
                java.nio.file.Paths.get("../data/definitions"));
        IslandGeography geography = result.geography;
        GeneratedWorld world = result.world;
        long elapsed = System.currentTimeMillis() - started;

        int size = IslandLayout.SIZE;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Map<Biome, Integer> counts = new EnumMap<>(Biome.class);

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                Biome biome = geography.biome[x][y];
                counts.merge(biome, 1, Integer::sum);
                int rgb = COLOURS.getOrDefault(biome, 0xff00ff);
                if (geography.cliff[x][y]) {
                    // Cliff faces draw as bare rock so impassable ground is visible.
                    rgb = 0x5a5148;
                } else if (!biome.isWater()) {
                    // Hillshade from the local gradient so relief is visible.
                    int hx = geography.height[Math.min(size - 1, x + 1)][y] - geography.height[Math.max(0, x - 1)][y];
                    int hy = geography.height[x][Math.min(size - 1, y + 1)] - geography.height[x][Math.max(0, y - 1)];
                    double shade = 1.0 + (hx * 0.9 + hy * 0.6) * 0.016;
                    rgb = shade(rgb, Math.max(0.55, Math.min(1.5, shade)));
                }
                // Image y grows downward; world y grows north, so flip for a map view.
                image.setRGB(x, size - 1 - y, rgb);
            }
        }
        // Every generated object, so buildings and resource clusters are visible as
        // structure rather than having to be taken on trust.
        for (var entry : result.objects.entrySet()) {
            int regionX = (entry.getKey() >> 8) - IslandLayout.BLOCK_REGION_X;
            int regionY = (entry.getKey() & 0xff) - IslandLayout.BLOCK_REGION_Y;
            if (regionX < 0 || regionY < 0
                    || regionX >= IslandLayout.ISLAND_REGIONS || regionY >= IslandLayout.ISLAND_REGIONS) {
                continue;
            }
            for (var object : entry.getValue()) {
                int x = regionX * 64 + object.localX;
                int y = regionY * 64 + object.localY;
                if (x < 0 || y < 0 || x >= size || y >= size) {
                    continue;
                }
                // Walls and roofs draw dark so a building reads as an outline;
                // everything else (trees, rocks) draws as a lighter speck.
                boolean structural = object.type <= 3 || object.type == 9
                        || (object.type >= 12 && object.type <= 21);
                image.setRGB(x, size - 1 - y, structural ? 0x1a1512 : 0x3d3128);
            }
        }

        // Markers, drawn over the terrain.
        for (Locality locality : world.localities) {
            if (locality.hasTown()) {
                marker(image, locality.townX, locality.townY, 0xfff2d8, 4);
            }
        }
        for (GeneratedWorld.Dungeon dungeon : world.dungeons) {
            marker(image, dungeon.entranceX - IslandLayout.ORIGIN_X,
                    dungeon.entranceY - IslandLayout.ORIGIN_Y,
                    dungeon.hasBoss() ? 0xd83a3a : 0x9a5ad8, 3);
        }
        marker(image, world.spawnX - IslandLayout.ORIGIN_X,
                world.spawnY - IslandLayout.ORIGIN_Y, 0x33ddff, 6);

        BufferedImage rendered = image;
        if (cropHalf > 0 || zoom > 1) {
            int cx = world.spawnX - IslandLayout.ORIGIN_X;
            int cy = world.spawnY - IslandLayout.ORIGIN_Y;
            int half = cropHalf > 0 ? cropHalf : size / 2;
            int x0 = Math.max(0, Math.min(size - half * 2, cx - half));
            int y0 = Math.max(0, Math.min(size - half * 2, cy - half));
            int side = Math.min(half * 2, size);
            BufferedImage crop = new BufferedImage(side * zoom, side * zoom, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < side * zoom; x++) {
                for (int y = 0; y < side * zoom; y++) {
                    int srcX = x0 + x / zoom;
                    int srcY = (size - 1 - (y0 + side - 1)) + y / zoom;
                    crop.setRGB(x, y, image.getRGB(
                            Math.min(size - 1, srcX), Math.max(0, Math.min(size - 1, srcY))));
                }
            }
            rendered = crop;
        }
        ImageIO.write(rendered, "png", out);

        int cliffs = 0;
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                if (geography.cliff[x][y]) {
                    cliffs++;
                }
            }
        }
        System.out.printf("cliff tiles          %d (%.1f%% of land)%n",
                cliffs, 100.0 * cliffs / Math.max(1, geography.landTiles));
        System.out.printf("reachable land       %.1f%%%n",
                100.0 * geography.reachableLandTiles / Math.max(1, geography.landTiles));

        int maxHeight = 0;
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                maxHeight = Math.max(maxHeight, geography.height[x][y]);
            }
        }

        System.out.println("seed                 " + seed);
        System.out.println("island               " + size + " x " + size + " tiles ("
                + IslandLayout.ISLAND_REGIONS + " x " + IslandLayout.ISLAND_REGIONS + " regions)");
        System.out.println("generated in         " + elapsed + " ms");
        System.out.println("land tiles           " + geography.landTiles + " of " + (size * size)
                + String.format(" (%.1f%%)", 100.0 * geography.landTiles / (size * size)));
        System.out.println("peak height byte     " + maxHeight + "  (" + (maxHeight * 8) + " world units, "
                + String.format("%.1f", maxHeight * 8 / 128.0) + " tile-widths)");
        int highland = counts.getOrDefault(Biome.ROCKY_HIGHLAND, 0)
                + counts.getOrDefault(Biome.MOUNTAIN, 0) + counts.getOrDefault(Biome.SNOW, 0);
        System.out.printf("highland share       %.1f%% of land%n", 100.0 * highland / geography.landTiles);
        System.out.println("\nbiome coverage:");
        counts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> System.out.printf("  %-16s %7d  %5.1f%%%n",
                        e.getKey(), e.getValue(), 100.0 * e.getValue() / (size * size)));
        System.out.println("\nmarkers: cyan = start village, cream = settlement,");
        System.out.println("         red = dungeon with a boss, purple = boss-less dungeon");
        System.out.println("\nwrote " + out.getAbsolutePath());
    }

    /** Draws a filled square with a dark outline, so markers read on any biome. */
    private static void marker(BufferedImage image, int x, int y, int rgb, int radius) {
        int size = image.getWidth();
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dy = -radius - 1; dy <= radius + 1; dy++) {
                int px = x + dx;
                int py = size - 1 - (y + dy);
                if (px < 0 || py < 0 || px >= size || py >= size) {
                    continue;
                }
                // Hollow ring: a filled marker over a town hides the town.
                boolean edge = Math.abs(dx) > radius || Math.abs(dy) > radius;
                boolean interior = Math.abs(dx) < radius && Math.abs(dy) < radius;
                if (interior) {
                    continue;
                }
                image.setRGB(px, py, edge ? 0x101010 : rgb);
            }
        }
    }

    private static int shade(int rgb, double factor) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xff) * factor));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xff) * factor));
        int b = Math.min(255, (int) ((rgb & 0xff) * factor));
        return (r << 16) | (g << 8) | b;
    }
}
