package com.elvarg.game.world.tool;

import com.elvarg.game.world.GeneratedWorld;
import com.elvarg.game.world.WorldConfig;
import com.elvarg.game.world.WorldPersistence;
import com.elvarg.game.world.gen.DifficultyBand;
import com.elvarg.game.world.gen.Locality;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;

/**
 * Prints the installed world, so a host can see what a seed produced without
 * starting the server.
 */
public final class InspectWorld {

    public static void main(String[] args) throws Exception {
        Path data = Paths.get(args.length > 0 ? args[0] : "../data");
        WorldPersistence persistence = new WorldPersistence(data);
        if (!persistence.exists()) {
            System.out.println("No world installed at " + persistence.file().toAbsolutePath());
            System.out.println("Run: ./gradlew :game:generateWorld -Pseed=<seed>");
            return;
        }
        GeneratedWorld world = persistence.load();
        WorldConfig config = WorldConfig.load(data.resolve("world_config.json"));

        System.out.println("WORLD");
        System.out.println("  seed                " + world.seed);
        System.out.println("  generator version   " + world.generatorVersion
                + (world.generatorVersion == GeneratedWorld.GENERATOR_VERSION
                ? " (current)" : " (this build is v" + GeneratedWorld.GENERATOR_VERSION + ")"));
        System.out.println("  generated           " + DateTimeFormatter.ISO_LOCAL_DATE_TIME
                .format(Instant.ofEpochMilli(world.generatedAtEpochMillis).atZone(ZoneId.systemDefault())));
        System.out.println("  default spawn       " + world.spawnX + ", " + world.spawnY + ", " + world.spawnZ);

        System.out.println("\nCONFIGURATION");
        System.out.println("  xp multiplier       combat " + config.combatSkillsXpMultiplier
                + ", other " + config.regularSkillsXpMultiplier);
        System.out.println("  skill requirements  " + (config.enforceSkillRequirements ? "enforced" : "ignored"));
        System.out.println("  pvp                 " + (config.pvpEnabled ? "enabled" : "disabled"));

        Map<DifficultyBand, Integer> byBand = new EnumMap<>(DifficultyBand.class);
        for (Locality locality : world.localities) {
            byBand.merge(locality.band, 1, Integer::sum);
        }
        System.out.println("\nLOCALITIES          " + world.localities.size() + " total, "
                + world.localities.stream().filter(Locality::hasTown).count() + " settled");
        for (DifficultyBand band : DifficultyBand.values()) {
            int count = byBand.getOrDefault(band, 0);
            if (count > 0) {
                System.out.printf("  %-12s %3d  %s%n", band, count, "#".repeat(Math.min(50, count)));
            }
        }

        System.out.println("\nDUNGEONS            " + world.dungeons.size() + " total, "
                + world.dungeons.stream().filter(GeneratedWorld.Dungeon::hasBoss).count() + " with a boss");
        System.out.printf("  %-24s %-7s %-11s %-22s %s%n", "NAME", "FLOORS", "TERMINAL", "BOSS", "ENTRANCE");
        for (GeneratedWorld.Dungeon dungeon : world.dungeons) {
            System.out.printf("  %-24s %-7d %-11s %-22s %d,%d%n",
                    dungeon.name, dungeon.floors, dungeon.terminalBand,
                    dungeon.hasBoss() ? dungeon.bossName : "(none)",
                    dungeon.entranceX, dungeon.entranceY);
        }

        System.out.println("\nPOPULATION");
        System.out.println("  npc spawns          " + world.npcSpawns.size());
        System.out.println("    overworld         "
                + world.npcSpawns.stream().filter(s -> s.dungeonId == -1).count());
        System.out.println("    dungeons          "
                + world.npcSpawns.stream().filter(s -> s.dungeonId != -1).count());
        System.out.println("  resource objects    " + world.resourceObjectCount);
        System.out.println("  town objects        " + world.townObjectCount);
    }
}
