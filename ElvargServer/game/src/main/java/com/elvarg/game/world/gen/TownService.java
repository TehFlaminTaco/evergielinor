package com.elvarg.game.world.gen;

/**
 * A service a settlement can provide, and the object id that provides it.
 *
 * Every id here is one that {@code ObjectActionPacketListener} already handles,
 * which is what makes generated infrastructure work without any new interaction
 * code: place a 6084 and it is a functioning bank the moment a player clicks it.
 *
 * @author EverGielinor world generator
 */
public enum TownService {

    /** ObjectIdentifiers.BANK_BOOTH - handled in the second-click switch. */
    BANK(6084, "Bank booth"),
    /** ObjectIdentifiers.FURNACE_18 - opens the smelting interface. */
    FURNACE(24009, "Furnace"),
    /** ObjectIdentifiers.ANVIL - opens the smithing interface. */
    ANVIL(2031, "Anvil"),
    /** ObjectIdentifiers.COOKING_RANGE. */
    RANGE(114, "Cooking range"),
    /** ObjectIdentifiers.ALTAR - prayer restore. */
    ALTAR(409, "Altar"),
    /** Shop is an NPC rather than an object; the id is a placeholder marker. */
    GENERAL_STORE(-1, "General store");

    private final int objectId;
    private final String label;

    TownService(int objectId, String label) {
        this.objectId = objectId;
        this.label = label;
    }

    /** The object id, or -1 when the service is staffed by an NPC instead. */
    public int objectId() {
        return objectId;
    }

    public boolean isObject() {
        return objectId != -1;
    }

    public String label() {
        return label;
    }
}
