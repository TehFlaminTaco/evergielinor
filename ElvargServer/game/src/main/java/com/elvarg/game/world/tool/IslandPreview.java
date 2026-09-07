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
                if (!biome.isWater()) {
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

        ImageIO.write(image, "png", out);

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
                boolean edge = Math.abs(dx) > radius || Math.abs(dy) > radius;
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
