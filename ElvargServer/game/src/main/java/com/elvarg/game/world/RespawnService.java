package com.elvarg.game.world;

import com.elvarg.game.GameConstants;
import com.elvarg.game.collision.RegionManager;
import com.elvarg.game.entity.impl.object.MapObjects;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.Location;

/**
 * Decides where a player wakes up.
 *
 * A claimed bed is honoured only if it is still there and still usable. Beds are
 * world deltas and the world outlives any one of them - a bed can be destroyed,
 * or sit in a region that no longer exists after a re-roll - so every claim is
 * re-checked at the moment of death rather than trusted because it was valid
 * when it was made.
 *
 * @author EverGielinor
 */
public final class RespawnService {

    /** Object ids that count as a bed a player may claim. */
    private static final int[] BED_OBJECTS = {
            // ObjectIdentifiers BED..BED_17, the standard bed models.
            417, 418, 419, 420, 421, 422, 423, 424, 425, 426, 427, 428, 429, 432, 433, 434, 435
    };

    private RespawnService() {
    }

    public static boolean isBed(int objectId) {
        for (int id : BED_OBJECTS) {
            if (id == objectId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves a player's respawn point, falling back as far as necessary.
     *
     * Order: the player's claimed bed, then the generated world's starting
     * village, then Elvarg's built-in default. The last of these always works,
     * so a player can never be left with nowhere to appear.
     */
    public static Location respawnFor(Player player) {
        Location claimed = player.getRespawnLocation();
        if (claimed != null && isBedStillValid(player, claimed)) {
            return claimed.clone();
        }
        if (claimed != null) {
            // Tell them why they woke up somewhere else, rather than silently moving them.
            player.getPacketSender().sendMessage("Your bed is gone. You wake up back at the village.");
            player.setRespawnLocation(null);
        }
        Location worldSpawn = WorldLoader.defaultSpawn();
        return worldSpawn != null ? worldSpawn : GameConstants.DEFAULT_LOCATION.clone();
    }

    /**
     * Whether a claimed bed still exists and is somewhere a player can stand.
     */
    private static boolean isBedStillValid(Player player, Location location) {
        RegionManager.loadMapFiles(location.getX(), location.getY());
        if (RegionManager.getRegion(location.getX(), location.getY()).isEmpty()) {
            return false;
        }
        for (int id : BED_OBJECTS) {
            if (MapObjects.get(id, location, null) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Claims a bed as the player's respawn point.
     *
     * @return true if the claim was accepted
     */
    public static boolean claim(Player player, Location bedLocation) {
        if (!isBedStillValid(player, bedLocation)) {
            player.getPacketSender().sendMessage("You cannot make this your home.");
            return false;
        }
        player.setRespawnLocation(bedLocation.clone());
        player.getPacketSender().sendMessage("You will wake up here from now on.");
        return true;
    }
}
