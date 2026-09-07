package com.elvarg.game.world;

import com.elvarg.game.entity.impl.object.GameObject;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.Location;
import com.elvarg.game.world.gen.GeneratedDungeonObjects;

import java.util.HashMap;
import java.util.Map;

/**
 * Interactions belonging to generated content: dungeon entrances, the ladders
 * between floors, beds, and the boss chest.
 *
 * These are dispatched before Elvarg's own object switch and only claim an
 * object when the world record says that exact tile is part of generated
 * content. That matters because the ids reused here - a plain ladder, a plain
 * chest - also occur in the original map, and a generated behaviour must not
 * leak onto them.
 *
 * @author EverGielinor
 */
public final class GeneratedWorldInteractions {

    /** Dungeon by the tile its overworld entrance sits on. */
    private static final Map<Long, GeneratedWorld.Dungeon> entrances = new HashMap<>();
    /** Dungeon by the region its floors occupy. */
    private static final Map<Integer, GeneratedWorld.Dungeon> byRegion = new HashMap<>();
    /**
     * Shop id by the tile its keeper stands on. Shopkeepers all share one NPC id,
     * so the binding has to be positional - otherwise opening one generated shop
     * would open the same shop everywhere on the island.
     */
    private static final Map<Long, Integer> shopkeepers = new HashMap<>();

    private GeneratedWorldInteractions() {
    }

    /** Indexes the world's dungeons for lookup. Called once, after the world loads. */
    public static void index(GeneratedWorld world) {
        entrances.clear();
        byRegion.clear();
        shopkeepers.clear();
        for (GeneratedWorld.Dungeon dungeon : world.dungeons) {
            entrances.put(key(dungeon.entranceX, dungeon.entranceY), dungeon);
            byRegion.put(dungeon.regionId, dungeon);
        }
        for (GeneratedWorld.NpcSpawn spawn : world.npcSpawns) {
            if (spawn.shopId >= 0) {
                shopkeepers.put(key(spawn.x, spawn.y), spawn.shopId);
            }
        }
    }

    /**
     * Opens the shop belonging to a generated shopkeeper.
     *
     * Matched on the tile the NPC spawned at rather than the NPC itself, since
     * shopkeepers wander a little and the spawn point is the stable identity.
     *
     * @return true when this NPC runs a generated shop
     */
    public static boolean openShop(com.elvarg.game.entity.impl.player.Player player,
                                   com.elvarg.game.entity.impl.npc.NPC npc) {
        if (npc == null) {
            return false;
        }
        Location spawn = npc.getSpawnPosition() != null ? npc.getSpawnPosition() : npc.getLocation();
        Integer shopId = shopkeepers.get(key(spawn.getX(), spawn.getY()));
        if (shopId == null) {
            return false;
        }
        com.elvarg.game.model.container.shop.ShopManager.open(player, shopId);
        return true;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    /**
     * Handles a first-click on an object.
     *
     * @return true when this handled the click and Elvarg's own switch should not
     */
    public static boolean firstClick(Player player, GameObject object) {
        if (object == null) {
            return false;
        }
        Location location = object.getLocation();
        int id = object.getId();

        if (RespawnService.isBed(id)) {
            return RespawnService.claim(player, location);
        }
        if (id == GeneratedDungeonObjects.BOSS_CHEST && isInsideDungeon(location)) {
            return BossChest.open(player, byRegion.get(regionOf(location)), location);
        }
        if (id == GeneratedDungeonObjects.ENTRANCE_OBJECT) {
            // The same id is both the overworld entrance and the descend-a-floor
            // ladder, so which one this is depends on where it is standing.
            GeneratedWorld.Dungeon entrance = entrances.get(key(location.getX(), location.getY()));
            if (entrance != null && location.getZ() == 0 && !isInsideDungeon(location)) {
                return enter(player, entrance);
            }
            if (isInsideDungeon(location)) {
                return descend(player, location);
            }
            return false;
        }
        if (id == GeneratedDungeonObjects.LADDER_UP && isInsideDungeon(location)) {
            return ascend(player, location);
        }
        return false;
    }

    private static int regionOf(Location location) {
        return ((location.getX() / 64) << 8) | (location.getY() / 64);
    }

    private static boolean isInsideDungeon(Location location) {
        return byRegion.containsKey(regionOf(location));
    }

    private static boolean enter(Player player, GeneratedWorld.Dungeon dungeon) {
        player.moveTo(new Location(dungeon.arrivalX, dungeon.arrivalY, 0));
        player.getPacketSender().sendMessage("You climb down into " + dungeon.name + ".");
        if (dungeon.hasBoss()) {
            player.getPacketSender().sendMessage(
                    "Something " + describe(dungeon) + " stirs in the depths below.");
        }
        return true;
    }

    private static String describe(GeneratedWorld.Dungeon dungeon) {
        return switch (dungeon.terminalBand) {
            case BEGINNER, LOW -> "small";
            case MEDIUM -> "restless";
            case HIGH -> "large";
            case VERY_HIGH -> "enormous";
            case ENDGAME -> "terrible";
        };
    }

    private static boolean descend(Player player, Location location) {
        GeneratedWorld.Dungeon dungeon = byRegion.get(regionOf(location));
        if (dungeon == null) {
            return false;
        }
        int next = location.getZ() + 1;
        if (next >= dungeon.floors) {
            player.getPacketSender().sendMessage("The way down is blocked.");
            return true;
        }
        // Step off the ladder rather than into it.
        player.moveTo(new Location(location.getX() - 1, location.getY(), next));
        player.getPacketSender().sendMessage("You descend to floor " + (next + 1)
                + " of " + dungeon.floors + ".");
        return true;
    }

    private static boolean ascend(Player player, Location location) {
        GeneratedWorld.Dungeon dungeon = byRegion.get(regionOf(location));
        if (dungeon == null) {
            return false;
        }
        if (location.getZ() == 0) {
            player.moveTo(new Location(dungeon.entranceX, dungeon.entranceY, 0));
            player.getPacketSender().sendMessage("You climb back out into the daylight.");
            return true;
        }
        player.moveTo(new Location(location.getX() - 1, location.getY(), location.getZ() - 1));
        player.getPacketSender().sendMessage("You climb up to floor " + location.getZ() + ".");
        return true;
    }
}
