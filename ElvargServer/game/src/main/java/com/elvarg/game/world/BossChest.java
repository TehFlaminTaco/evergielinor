package com.elvarg.game.world;

import com.elvarg.game.World;
import com.elvarg.game.definition.NpcDropDefinition;
import com.elvarg.game.entity.impl.grounditem.ItemOnGroundManager;
import com.elvarg.game.entity.impl.npc.NPC;
import com.elvarg.game.entity.impl.npc.NPCDropGenerator;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.Item;
import com.elvarg.game.model.Location;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The reward chest at the end of a dungeon.
 *
 * It rolls the dungeon boss's own drop table through Elvarg's existing
 * {@link NPCDropGenerator} rather than introducing new items, so a chest can
 * never hand out something its boss does not already drop and the loot economy
 * stays where it was.
 *
 * The chest opens once per clear: a claim is recorded against the dungeon's
 * identity, and cleared again when that dungeon's boss respawns. Claims are held
 * in memory only - a restart forgets them, which errs toward the player and
 * avoids inventing a persistence format for something this small.
 *
 * @author EverGielinor
 */
public final class BossChest {

    /** Username to the set of dungeon ids they have looted since the boss last died. */
    private static final Map<String, Set<Integer>> claims = new HashMap<>();

    private BossChest() {
    }

    static boolean open(Player player, GeneratedWorld.Dungeon dungeon, Location location) {
        if (dungeon == null) {
            return false;
        }
        NPC boss = findBoss(dungeon);
        if (boss != null && boss.getHitpoints() > 0) {
            player.getPacketSender().sendMessage("The chest will not open while its guardian lives.");
            return true;
        }

        Set<Integer> claimed = claims.computeIfAbsent(player.getUsername(), k -> new HashSet<>());
        if (!claimed.add(dungeon.id)) {
            player.getPacketSender().sendMessage("You have already emptied this chest.");
            return true;
        }

        int given = 0;
        if (dungeon.hasBoss()) {
            Optional<NpcDropDefinition> definition = NpcDropDefinition.get(dungeon.bossNpcId);
            if (definition.isPresent()) {
                List<Item> loot = new NPCDropGenerator(player, definition.get()).getDropList();
                for (Item item : loot) {
                    if (item == null || item.getId() <= 0) {
                        continue;
                    }
                    ItemOnGroundManager.register(player, item, location);
                    given++;
                }
            }
        }

        if (given == 0) {
            player.getPacketSender().sendMessage("The chest holds nothing of worth.");
        } else {
            player.getPacketSender().sendMessage("You empty the chest.");
        }
        return true;
    }

    /** Clears every player's claim on a dungeon, called when its boss respawns. */
    public static void onBossRespawn(int dungeonId) {
        for (Set<Integer> claimed : claims.values()) {
            claimed.remove(dungeonId);
        }
    }

    private static NPC findBoss(GeneratedWorld.Dungeon dungeon) {
        if (!dungeon.hasBoss()) {
            return null;
        }
        for (NPC npc : World.getNpcs()) {
            if (npc == null || npc.getId() != dungeon.bossNpcId) {
                continue;
            }
            int region = ((npc.getLocation().getX() / 64) << 8) | (npc.getLocation().getY() / 64);
            if (region == dungeon.regionId) {
                return npc;
            }
        }
        return null;
    }
}
