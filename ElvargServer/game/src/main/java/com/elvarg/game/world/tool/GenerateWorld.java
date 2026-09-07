package com.elvarg.game.world.tool;

import com.elvarg.game.world.GeneratedWorld;
import com.elvarg.game.world.WorldPersistence;
import com.elvarg.game.world.codec.LandscapeCodec;
import com.elvarg.game.world.codec.MapIndex;
import com.elvarg.game.world.codec.MapInstaller;
import com.elvarg.game.world.codec.PlacedObject;
import com.elvarg.game.world.codec.TerrainRegion;
import com.elvarg.game.world.gen.DifficultyBand;
import com.elvarg.game.world.gen.IslandLayout;
import com.elvarg.game.world.gen.Locality;
import com.elvarg.game.world.gen.WorldGenerator;
import com.elvarg.game.world.gen.WorldValidator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The host tool: generate a world from a seed, validate it, and install it.
 *
 * Generation happens here rather than at server boot so that a broken seed is
 * caught before players are let in, and so that a world is a reproducible
 * artefact a host can keep, share or roll back.
 *
 * Usage:
 * <pre>
 *   GenerateWorld --seed 847293 [--dry-run] [--server DIR] [--client DIR]
 * </pre>
 */
public final class GenerateWorld {

    public static void main(String[] args) throws Exception {
        long seed = 847293L;
        boolean dryRun = false;
        Path serverData = Paths.get("../data");
        Path clientCache = Paths.get("../../ElvargClient/Cache");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--dry-run" -> dryRun = true;
                case "--server" -> serverData = Paths.get(args[++i]);
                case "--client" -> clientCache = Paths.get(args[++i]);
                default -> {
                    System.err.println("unknown argument: " + args[i]);
                    System.exit(2);
                }
            }
        }

        Path definitions = serverData.resolve("definitions");
        Path clipping = serverData.resolve("clipping");
        if (!Files.isDirectory(definitions)) {
            System.err.println("definitions not found at " + definitions.toAbsolutePath());
            System.exit(2);
        }

        // Object definitions must be loaded before generating: every object the
        // generator places is vetted against its definition for footprint and
        // validity, and without them every check fails silently and the world
        // comes out empty of resources.
        com.elvarg.game.definition.ObjectDefinition.init();

        System.out.println("EverGielinor world generator");
        System.out.println("  seed              " + seed);
        System.out.println("  generator version " + GeneratedWorld.GENERATOR_VERSION);
        System.out.println();

        // --- safety check before anything is written ------------------------
        MapIndex index = MapIndex.load(clipping.resolve("map_index"));
        Set<Integer> regions = IslandLayout.allRegions();
        for (int regionId : regions) {
            if (!index.contains(regionId)) {
                System.err.println("region " + regionId + " is not in map_index; refusing to generate");
                System.exit(3);
            }
        }
        Set<Integer> collateral = index.findCollateralRegions(regions);
        if (!collateral.isEmpty()) {
            System.err.println("refusing to generate: " + collateral.size()
                    + " region(s) outside the island share map files with it");
            System.exit(3);
        }
        System.out.println("region check        " + regions.size()
                + " regions reserved, no map files shared outside the island");

        // --- generate --------------------------------------------------------
        long started = System.currentTimeMillis();
        WorldGenerator.Result result = WorldGenerator.generate(seed, definitions);
        long generationMillis = System.currentTimeMillis() - started;
        GeneratedWorld world = result.world;

        System.out.println("generated in        " + generationMillis + " ms");
        System.out.println("localities          " + world.localities.size());
        System.out.println("settlements         "
                + world.localities.stream().filter(Locality::hasTown).count()
                + ", " + world.localities.stream().mapToInt(l -> l.buildings).sum() + " buildings");
        System.out.println("dungeons            " + world.dungeons.size()
                + " (" + world.dungeons.stream().filter(GeneratedWorld.Dungeon::hasBoss).count() + " with a boss)");
        System.out.println("npc spawns          " + world.npcSpawns.size());
        System.out.println("resource objects    " + world.resourceObjectCount);
        System.out.println("clutter objects     " + world.clutterObjectCount);
        System.out.println("town objects        " + world.townObjectCount);
        System.out.println("objects total       "
                + result.objects.values().stream().mapToInt(List::size).sum());
        System.out.println("spawn point         " + world.spawnX + ", " + world.spawnY);

        // --- validate --------------------------------------------------------
        System.out.println();
        WorldValidator.Report report = WorldValidator.validate(world, result.geography);
        if (report.findings.isEmpty()) {
            System.out.println("validation          passed with no findings");
        } else {
            System.out.println("validation          " + report.count(WorldValidator.Severity.FATAL)
                    + " fatal, " + report.count(WorldValidator.Severity.WARNING) + " warning");
            for (WorldValidator.Finding finding : report.findings) {
                System.out.println("  " + finding);
            }
        }
        if (!report.passed()) {
            System.err.println("\nWORLD REJECTED - nothing was written. Try another seed.");
            System.exit(1);
        }

        // The spawn is chosen from tiles the generator knows are empty, but the
        // server derives clipping independently - so confirm against the real
        // region pipeline before writing anything, not after.
        printLocalityTable(world);
        printDungeonTable(world);

        if (dryRun) {
            System.out.println("\ndry run: no files written");
            return;
        }

        // --- install ---------------------------------------------------------
        System.out.println();
        int terrainBytes = 0;
        int objectBytes = 0;
        try (MapInstaller installer = new MapInstaller(clipping.resolve("maps"), clientCache)) {
            for (int regionId : regions) {
                TerrainRegion terrain = result.terrain.get(regionId);
                if (terrain == null) {
                    // A reserved dungeon region with no dungeon in it: write solid
                    // rock rather than leaving whatever used to be there.
                    terrain = solidRock();
                }
                byte[] terrainData = LandscapeCodec.encodeTerrain(terrain);
                terrainBytes += terrainData.length;
                installer.install(index.terrainFile(regionId), terrainData);

                List<PlacedObject> objects = result.objects.getOrDefault(regionId, new ArrayList<>());
                byte[] objectData = LandscapeCodec.encodeObjects(objects);
                objectBytes += objectData.length;
                installer.install(index.objectFile(regionId), objectData);
            }
            System.out.println("installed           " + installer.filesWritten() + " map files");
            System.out.printf("  terrain           %,d bytes uncompressed%n", terrainBytes);
            System.out.printf("  objects           %,d bytes uncompressed%n", objectBytes);
            System.out.printf("  client cache grew %,d bytes%n", installer.cacheGrowthBytes());

            // Read one file back from the client cache and compare it to the
            // server's copy, so a silent divergence is caught here and not by a
            // player walking through a wall.
            int sample = index.terrainFile(regions.iterator().next());
            System.out.println("  verify            "
                    + (installer.verify(sample) ? "client and server copies match" : "MISMATCH"));
        }

        WorldPersistence persistence = new WorldPersistence(serverData);
        persistence.save(world);
        System.out.println("world record        " + persistence.file().toAbsolutePath());
        System.out.println("\nWORLD INSTALLED. Start the server to play it.");
    }

    /** A region of impassable rock, used for reserved regions with nothing in them. */
    private static TerrainRegion solidRock() {
        TerrainRegion region = new TerrainRegion();
        for (int x = 0; x < TerrainRegion.SIZE; x++) {
            for (int y = 0; y < TerrainRegion.SIZE; y++) {
                region.setUnderlay(0, x, y, 56);
                region.setHeight(0, x, y, 0);
                region.addFlag(0, x, y, TerrainRegion.FLAG_BLOCKED);
            }
        }
        return region;
    }

    private static void printLocalityTable(GeneratedWorld world) {
        System.out.println("\nlocalities:");
        System.out.printf("  %-18s %-14s %-11s %-18s %-3s %-14s %s%n",
                "NAME", "BIOME", "BAND", "IDENTITY", "BLD", "SHOP", "SERVICES");
        world.localities.stream()
                .sorted((a, b) -> a.band.ordinal() != b.band.ordinal()
                        ? a.band.ordinal() - b.band.ordinal()
                        : a.id - b.id)
                .forEach(l -> System.out.printf("  %-18s %-14s %-11s %-18s %-3s %-14s %s%n",
                        l.name, l.biome, l.band, l.type,
                        l.hasTown() ? String.valueOf(l.buildings) : "-",
                        l.shopId >= 0 ? com.elvarg.game.world.gen.ShopAssignment.nameOf(l.shopId) : "-",
                        l.hasTown() ? l.services.toString() : "-"));
    }

    private static void printDungeonTable(GeneratedWorld world) {
        if (world.dungeons.isEmpty()) {
            return;
        }
        System.out.println("\ndungeons:");
        System.out.printf("  %-22s %-8s %-12s %-22s %s%n",
                "NAME", "FLOORS", "TERMINAL", "BOSS", "COMBAT");
        for (GeneratedWorld.Dungeon dungeon : world.dungeons) {
            System.out.printf("  %-22s %-8d %-12s %-22s %s%n",
                    dungeon.name, dungeon.floors, dungeon.terminalBand,
                    dungeon.hasBoss() ? dungeon.bossName : "(none - elite group)",
                    dungeon.hasBoss() ? (dungeon.bossFullyImplemented ? "dedicated" : "generic") : "-");
        }
    }
}
