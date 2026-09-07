package com.elvarg.game.world.gen;

/**
 * Object ids the generator uses for dungeon furniture.
 *
 * These are interacted with through EverGielinor's own handler rather than
 * Elvarg's object switch, so the ids only need to exist in the cache and look
 * right; behaviour is bound to the world's dungeon records, not to the id.
 *
 * @author EverGielinor world generator
 */
public final class GeneratedDungeonObjects {

    /**
     * Placed on the overworld; descends to floor one.
     *
     * Object 2123 is a 3x2 "Cave Entrance" whose first action is Enter. A 1x1
     * ladder was used at first and looked wrong standing in open country; a cave
     * mouth reads as a way into a hillside. Being 3x2 it has to be placed with
     * its footprint reserved - see ObjectVetting.
     */
    public static final int ENTRANCE_OBJECT = 2123;
    /** Object 11, "Ladder", Climb-up. Ascends a floor, or leaves from floor one. */
    public static final int LADDER_UP = 11;
    /** Object 10, "Ladder", Climb-down. */
    public static final int LADDER_DOWN = 10;
    /** Object 75, "Chest", Open. The boss room reward. */
    public static final int BOSS_CHEST = 75;

    private GeneratedDungeonObjects() {
    }
}
