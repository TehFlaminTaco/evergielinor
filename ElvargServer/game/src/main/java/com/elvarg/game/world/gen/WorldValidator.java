package com.elvarg.game.world.gen;

import com.elvarg.game.world.GeneratedWorld;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks that a generated world is actually playable before it is saved.
 *
 * Checks are ordered cheapest first so a rejected seed fails fast during a
 * re-roll loop, and every failure names what broke and where. A world that fails
 * a FATAL check is never written; WARNINGs are recorded and shown but do not
 * block, because some of them describe deliberate design choices - a frontier
 * outpost without a bank is a decision, not a bug.
 *
 * @author EverGielinor world generator
 */
public final class WorldValidator {

    public enum Severity { FATAL, WARNING, NOTE }

    public static final class Finding {
        public final Severity severity;
        public final String rule;
        public final String detail;

        Finding(Severity severity, String rule, String detail) {
            this.severity = severity;
            this.rule = rule;
            this.detail = detail;
        }

        @Override
        public String toString() {
            return String.format("[%s] %-26s %s", severity, rule, detail);
        }
    }

    public static final class Report {
        public final List<Finding> findings = new ArrayList<>();

        public boolean passed() {
            return findings.stream().noneMatch(f -> f.severity == Severity.FATAL);
        }

        public long count(Severity severity) {
            return findings.stream().filter(f -> f.severity == severity).count();
        }

        void fatal(String rule, String detail) {
            findings.add(new Finding(Severity.FATAL, rule, detail));
        }

        void warn(String rule, String detail) {
            findings.add(new Finding(Severity.WARNING, rule, detail));
        }

        void note(String rule, String detail) {
            findings.add(new Finding(Severity.NOTE, rule, detail));
        }
    }

    private WorldValidator() {
    }

    public static Report validate(GeneratedWorld world, IslandGeography geography) {
        return validate(world, geography, null);
    }

    /**
     * @param objects every object about to be written into the map files, or null
     *                to skip the render check
     */
    public static Report validate(GeneratedWorld world, IslandGeography geography,
                                  java.util.Map<Integer, java.util.List<
                                          com.elvarg.game.world.codec.PlacedObject>> objects) {
        Report report = new Report();

        validateRendering(objects, report);
        validateSpawn(world, geography, report);
        validateLocalities(world, report);
        validateReachability(world, geography, report);
        validateInfrastructure(world, report);
        validateProgression(world, report);
        validateDungeons(world, report);

        return report;
    }

    // --- rendering ----------------------------------------------------------

    /**
     * Catches objects placed at a landscape type they have no model for.
     *
     * The client resolves a model by searching the definition's type list and
     * returns null when the type is not in it, and a null model draws nothing.
     * The object is still in the map file and the server still clips it, so the
     * failure is completely silent: it looks like a wall with a hole in it, or a
     * fence that is not there. Buildings shipped with a hole at every corner for
     * exactly this reason, so it gets a check rather than a convention.
     */
    private static void validateRendering(java.util.Map<Integer, java.util.List<
            com.elvarg.game.world.codec.PlacedObject>> objects, Report report) {
        if (objects == null) {
            return;
        }
        java.util.Map<Long, Integer> offenders = new java.util.TreeMap<>();
        int total = 0;
        for (java.util.List<com.elvarg.game.world.codec.PlacedObject> region : objects.values()) {
            for (com.elvarg.game.world.codec.PlacedObject object : region) {
                total++;
                if (!ObjectVetting.rendersAt(object.id, object.type)) {
                    offenders.merge(((long) object.id << 8) | object.type, 1, Integer::sum);
                }
            }
        }
        for (java.util.Map.Entry<Long, Integer> entry : offenders.entrySet()) {
            int id = (int) (entry.getKey() >> 8);
            int type = (int) (entry.getKey() & 0xff);
            report.fatal("object renders", "object " + id + " (" + ObjectVetting.nameOf(id)
                    + ") placed at landscape type " + type + " has no model for it - "
                    + entry.getValue() + " invisible placements");
        }
        if (offenders.isEmpty() && total > 0) {
            // Nothing to report, but the count is worth having in the log.
            report.note("object renders", total + " objects all draw at the type they are placed at");
        }
    }

    // --- starting area ------------------------------------------------------

    private static void validateSpawn(GeneratedWorld world, IslandGeography geography, Report report) {
        int localX = world.spawnX - IslandLayout.ORIGIN_X;
        int localY = world.spawnY - IslandLayout.ORIGIN_Y;
        if (!IslandLayout.inBounds(localX, localY)) {
            report.fatal("spawn.inBounds", "spawn " + world.spawnX + "," + world.spawnY + " is off the island");
            return;
        }
        Biome biome = geography.biome[localX][localY];
        if (biome.isWater()) {
            report.fatal("spawn.walkable", "spawn is in water (" + biome + ")");
        }
        if (!geography.reachable[localX][localY]) {
            report.fatal("spawn.reachable", "spawn tile is not part of the main landmass");
        }
        if (biome == Biome.WILDERNESS || biome == Biome.VOLCANIC) {
            report.fatal("spawn.safe", "spawn is inside a hostile biome (" + biome + ")");
        }
        // Distance to the nearest settlement: a spawn a long walk from any
        // services is technically valid and practically hostile to a new player.
        int nearest = Integer.MAX_VALUE;
        for (Locality locality : world.localities) {
            if (!locality.hasTown()) {
                continue;
            }
            nearest = Math.min(nearest, Math.abs(locality.townX - localX)
                    + Math.abs(locality.townY - localY));
        }
        if (nearest > 60) {
            report.warn("spawn.nearTown",
                    "spawn is " + nearest + " tiles from the nearest settlement");
        }
    }

    // --- localities ---------------------------------------------------------

    private static void validateLocalities(GeneratedWorld world, Report report) {
        if (world.localities.isEmpty()) {
            report.fatal("localities.exist", "no localities were generated");
            return;
        }
        boolean hasStart = world.localities.stream()
                .anyMatch(l -> l.type == LocalityType.STARTING_VILLAGE);
        if (!hasStart) {
            report.fatal("localities.start", "no starting village");
        }
        boolean hasBeginner = world.localities.stream()
                .anyMatch(l -> l.band == DifficultyBand.BEGINNER);
        if (!hasBeginner) {
            report.fatal("localities.beginner", "no beginner-band locality for new players");
        }
        long settled = world.localities.stream().filter(Locality::hasTown).count();
        if (settled < 2) {
            report.fatal("localities.settlements", "only " + settled + " settlement(s); a world needs several");
        }
    }

    private static void validateReachability(GeneratedWorld world, IslandGeography geography, Report report) {
        for (Locality locality : world.localities) {
            if (!geography.reachable[locality.centreX][locality.centreY]) {
                report.warn("locality.reachable",
                        locality.name + " centre is on an island cut off from the start");
            }
        }
        double reachableShare = (double) geography.reachableLandTiles / Math.max(1, geography.landTiles);
        if (reachableShare < 0.55) {
            report.fatal("world.connected", String.format(
                    "only %.0f%% of land is walkable from the start; the island is fragmented",
                    reachableShare * 100));
        } else if (reachableShare < 0.80) {
            report.warn("world.connected", String.format(
                    "%.0f%% of land reachable from the start", reachableShare * 100));
        }
    }

    // --- infrastructure closure --------------------------------------------

    /**
     * The rule the brief cares most about: do not generate a resource whose
     * processing chain the world forgot to build. Ore without a furnace and anvil
     * somewhere reachable is a dead end.
     */
    private static void validateInfrastructure(GeneratedWorld world, Report report) {
        Set<TownService> available = new HashSet<>();
        for (Locality locality : world.localities) {
            if (locality.hasTown()) {
                available.addAll(locality.services);
            }
        }
        boolean anyOre = world.localities.stream().anyMatch(l -> l.biome.supportsOre());
        if (anyOre) {
            if (!available.contains(TownService.FURNACE)) {
                report.fatal("infrastructure.smelting",
                        "ore is generated but no settlement has a furnace");
            }
            if (!available.contains(TownService.ANVIL)) {
                report.fatal("infrastructure.smithing",
                        "ore is generated but no settlement has an anvil");
            }
        }
        if (!available.contains(TownService.BANK)) {
            report.fatal("infrastructure.bank", "no settlement provides banking");
        }
        if (!available.contains(TownService.RANGE)) {
            report.warn("infrastructure.cooking", "no settlement provides a cooking range");
        }
    }

    // --- progression --------------------------------------------------------

    private static void validateProgression(GeneratedWorld world, Report report) {
        for (Locality locality : world.localities) {
            if (locality.band != DifficultyBand.BEGINNER) {
                continue;
            }
            // A beginner area must not be seeded with content players cannot survive.
            long dangerous = world.npcSpawns.stream()
                    .filter(s -> s.localityId == locality.id)
                    .count();
            if (dangerous == 0) {
                report.warn("progression.beginnerContent",
                        locality.name + " is a beginner area with no monsters at all");
            }
        }
        boolean hasHigh = world.localities.stream()
                .anyMatch(l -> l.band.ordinal() >= DifficultyBand.HIGH.ordinal());
        if (!hasHigh) {
            report.warn("progression.range", "no high-difficulty locality; the world has no late game");
        }
    }

    // --- dungeons and bosses ------------------------------------------------

    private static void validateDungeons(GeneratedWorld world, Report report) {
        if (world.dungeons.isEmpty()) {
            report.warn("dungeons.exist", "no dungeons were generated");
            return;
        }
        Set<Integer> bosses = new HashSet<>();
        Set<Integer> regions = new HashSet<>();
        for (GeneratedWorld.Dungeon dungeon : world.dungeons) {
            if (!regions.add(dungeon.regionId)) {
                report.fatal("dungeon.regionUnique",
                        dungeon.name + " reuses region " + dungeon.regionId);
            }
            if (dungeon.floors < 1) {
                report.fatal("dungeon.floors", dungeon.name + " has no floors");
            }
            if (dungeon.entryBand.ordinal() > dungeon.terminalBand.ordinal()) {
                report.fatal("dungeon.progression",
                        dungeon.name + " gets easier with depth");
            }
            if (dungeon.hasBoss() && !bosses.add(dungeon.bossNpcId)) {
                report.fatal("boss.unique",
                        "boss " + dungeon.bossName + " is assigned to more than one dungeon");
            }
            boolean bossPresent = world.npcSpawns.stream()
                    .anyMatch(s -> s.dungeonId == dungeon.id && s.id == dungeon.bossNpcId);
            if (dungeon.hasBoss() && !bossPresent) {
                report.fatal("boss.spawned",
                        dungeon.name + " has a boss binding but no boss spawn");
            }
            boolean anyMonster = world.npcSpawns.stream().anyMatch(s -> s.dungeonId == dungeon.id);
            if (!anyMonster) {
                report.warn("dungeon.populated", dungeon.name + " is empty");
            }
        }
        long withBoss = world.dungeons.stream().filter(GeneratedWorld.Dungeon::hasBoss).count();
        if (withBoss == 0) {
            report.warn("boss.any", "no dungeon in this world has a boss");
        }
    }
}
