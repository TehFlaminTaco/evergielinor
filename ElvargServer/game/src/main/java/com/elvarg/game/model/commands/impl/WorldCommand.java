package com.elvarg.game.model.commands.impl;

import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.Location;
import com.elvarg.game.model.commands.Command;
import com.elvarg.game.model.rights.PlayerRights;
import com.elvarg.game.world.GeneratedWorld;
import com.elvarg.game.world.WorldConfig;
import com.elvarg.game.world.WorldLoader;
import com.elvarg.game.world.gen.Locality;

/**
 * In-game host tools for the generated world.
 *
 * Generation itself is not exposed here on purpose. Installing a world rewrites
 * the map files both the client and the server read, which cannot be done under
 * a running server without desynchronising everyone in it - so re-rolling stays
 * an offline gradle task and this command covers inspection and the settings
 * that are safe to change live.
 *
 * <pre>
 *   ::world                 summary of the installed world
 *   ::world seed            seed and generator version
 *   ::world here            the locality the player is standing in
 *   ::world dungeons        dungeon list with boss assignments
 *   ::world goto &lt;name&gt;     teleport to a locality or dungeon entrance
 *   ::world xp &lt;n&gt;          set the non-combat xp multiplier
 *   ::world reqs on|off     toggle skill level requirements
 * </pre>
 */
public class WorldCommand implements Command {

    @Override
    public void execute(Player player, String command, String[] parts) {
        GeneratedWorld world = WorldLoader.world();
        if (world == null) {
            player.getPacketSender().sendMessage(
                    "No generated world is installed. Run: gradlew :game:generateWorld -Pseed=<seed>");
            return;
        }
        String action = parts.length > 1 ? parts[1].toLowerCase() : "summary";

        switch (action) {
            case "summary" -> summary(player, world);
            case "seed" -> player.getPacketSender().sendMessage("Seed " + world.seed
                    + ", generator version " + world.generatorVersion);
            case "here" -> here(player, world);
            case "dungeons" -> dungeons(player, world);
            case "goto" -> teleport(player, world, parts);
            case "xp" -> setXp(player, parts);
            case "reqs" -> setRequirements(player, parts);
            default -> player.getPacketSender().sendMessage(
                    "::world [seed|here|dungeons|goto <name>|xp <n>|reqs on/off]");
        }
    }

    private void summary(Player player, GeneratedWorld world) {
        long settled = world.localities.stream().filter(Locality::hasTown).count();
        long bossed = world.dungeons.stream().filter(GeneratedWorld.Dungeon::hasBoss).count();
        player.getPacketSender().sendMessage("Seed " + world.seed
                + " (generator v" + world.generatorVersion + ")");
        player.getPacketSender().sendMessage(world.localities.size() + " localities, "
                + settled + " settled, " + world.dungeons.size() + " dungeons ("
                + bossed + " with a boss)");
        player.getPacketSender().sendMessage(WorldConfig.get().toString());
    }

    private void here(Player player, GeneratedWorld world) {
        Locality nearest = null;
        int best = Integer.MAX_VALUE;
        for (Locality locality : world.localities) {
            int worldX = com.elvarg.game.world.gen.IslandLayout.worldX(locality.centreX);
            int worldY = com.elvarg.game.world.gen.IslandLayout.worldY(locality.centreY);
            int distance = Math.abs(worldX - player.getLocation().getX())
                    + Math.abs(worldY - player.getLocation().getY());
            if (distance < best) {
                best = distance;
                nearest = locality;
            }
        }
        if (nearest == null) {
            player.getPacketSender().sendMessage("You are not on the generated island.");
            return;
        }
        player.getPacketSender().sendMessage(nearest.name + " - " + nearest.biome
                + ", " + nearest.band + ", " + nearest.type);
        player.getPacketSender().sendMessage(nearest.hasTown()
                ? "Services: " + nearest.services
                : "No settlement here.");
    }

    private void dungeons(Player player, GeneratedWorld world) {
        for (GeneratedWorld.Dungeon dungeon : world.dungeons) {
            player.getPacketSender().sendMessage(dungeon.name + " - " + dungeon.floors
                    + " floors, " + dungeon.terminalBand + ", "
                    + (dungeon.hasBoss() ? dungeon.bossName : "no boss")
                    + " @ " + dungeon.entranceX + "," + dungeon.entranceY);
        }
    }

    private void teleport(Player player, GeneratedWorld world, String[] parts) {
        if (parts.length < 3) {
            player.getPacketSender().sendMessage("::world goto <locality or dungeon name>");
            return;
        }
        String query = String.join(" ", java.util.Arrays.copyOfRange(parts, 2, parts.length)).toLowerCase();
        for (Locality locality : world.localities) {
            if (!locality.name.toLowerCase().startsWith(query)) {
                continue;
            }
            int x = locality.hasTown()
                    ? com.elvarg.game.world.gen.IslandLayout.worldX(locality.townX)
                    : com.elvarg.game.world.gen.IslandLayout.worldX(locality.centreX);
            int y = locality.hasTown()
                    ? com.elvarg.game.world.gen.IslandLayout.worldY(locality.townY)
                    : com.elvarg.game.world.gen.IslandLayout.worldY(locality.centreY);
            player.moveTo(new Location(x, y, 0));
            player.getPacketSender().sendMessage("Moved to " + locality.name + ".");
            return;
        }
        for (GeneratedWorld.Dungeon dungeon : world.dungeons) {
            if (dungeon.name.toLowerCase().startsWith(query)) {
                player.moveTo(new Location(dungeon.entranceX, dungeon.entranceY, 0));
                player.getPacketSender().sendMessage("Moved to " + dungeon.name + " entrance.");
                return;
            }
        }
        player.getPacketSender().sendMessage("No locality or dungeon called '" + query + "'.");
    }

    private void setXp(Player player, String[] parts) {
        if (parts.length < 3) {
            player.getPacketSender().sendMessage("Current xp multiplier: "
                    + WorldConfig.get().regularSkillsXpMultiplier);
            return;
        }
        try {
            double value = Double.parseDouble(parts[2]);
            if (value <= 0 || value > 1000) {
                player.getPacketSender().sendMessage("Multiplier must be between 0 and 1000.");
                return;
            }
            WorldConfig.get().regularSkillsXpMultiplier = value;
            player.getPacketSender().sendMessage("Non-combat xp multiplier is now " + value
                    + ". This lasts until restart unless world_config.json is updated too.");
        } catch (NumberFormatException e) {
            player.getPacketSender().sendMessage("'" + parts[2] + "' is not a number.");
        }
    }

    private void setRequirements(Player player, String[] parts) {
        if (parts.length < 3) {
            player.getPacketSender().sendMessage("Skill requirements are "
                    + (WorldConfig.get().enforceSkillRequirements ? "enforced" : "ignored") + ".");
            return;
        }
        boolean enforce = parts[2].equalsIgnoreCase("on") || parts[2].equalsIgnoreCase("true");
        WorldConfig.get().enforceSkillRequirements = enforce;
        player.getPacketSender().sendMessage("Skill requirements are now "
                + (enforce ? "enforced" : "ignored - resources are the only limit") + ".");
    }

    @Override
    public boolean canUse(Player player) {
        PlayerRights rights = player.getRights();
        return rights == PlayerRights.OWNER || rights == PlayerRights.DEVELOPER;
    }
}
