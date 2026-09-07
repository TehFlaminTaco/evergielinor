package com.elvarg.game.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Host-facing world settings.
 *
 * These are the rules a host should be able to change without reading code, so
 * they live in a JSON file rather than in constants. The XP multiplier and the
 * skill-requirement toggle in particular exist so progression can be tested both
 * ways - level-gated and resource-gated - without touching the skill system.
 *
 * @author EverGielinor
 */
public final class WorldConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static WorldConfig active = new WorldConfig();

    /** The seed the overworld was generated from. */
    public long seed = 847293L;

    /** Multiplies experience from non-combat skills. */
    public double regularSkillsXpMultiplier = 18.0;
    /** Multiplies experience from combat skills. */
    public double combatSkillsXpMultiplier = 6.0;

    /**
     * When false, skill level requirements are not enforced and resources become
     * the bottleneck instead - a player who can reach the ore may smelt it.
     * Deliberately left as a switch rather than a decision; both modes need play
     * testing before either is chosen.
     */
    public boolean enforceSkillRequirements = true;

    /** Whether player-versus-player combat is permitted anywhere in the world. */
    public boolean pvpEnabled = false;

    /** Regenerate dungeon layouts on boot instead of reusing the installed ones. */
    public boolean regenerateDungeonsOnBoot = false;

    public static WorldConfig get() {
        return active;
    }

    public static void set(WorldConfig config) {
        active = config;
    }

    public static WorldConfig load(Path file) throws IOException {
        if (!Files.exists(file)) {
            WorldConfig config = new WorldConfig();
            config.save(file);
            return config;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            WorldConfig config = GSON.fromJson(reader, WorldConfig.class);
            return config == null ? new WorldConfig() : config;
        }
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(this, writer);
        }
    }

    @Override
    public String toString() {
        return "seed=" + seed
                + " xp(regular)=" + regularSkillsXpMultiplier
                + " xp(combat)=" + combatSkillsXpMultiplier
                + " enforceSkillRequirements=" + enforceSkillRequirements
                + " pvp=" + pvpEnabled;
    }
}
