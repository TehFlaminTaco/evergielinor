package com.elvarg.game.world.gen;

/**
 * Which shop a settlement's shopkeeper runs.
 *
 * A settlement's trade follows its identity: a mining camp sells tools, a
 * fishing village sells food. Ids are Elvarg's own {@code ShopIdentifiers}, so
 * generated shops reuse the existing stock, currency and buy/sell rules rather
 * than introducing a parallel economy.
 *
 * @author EverGielinor world generator
 */
public final class ShopAssignment {

    private static final int GENERAL_STORE = 0;
    private static final int FOOD_SHOP = 1;
    private static final int ARMOR_SHOP = 3;
    private static final int RANGE_SHOP = 4;
    private static final int TOOL_SHOP = 9;

    private ShopAssignment() {
    }

    public static int forLocality(Locality locality) {
        return switch (locality.type) {
            case MINING_SETTLEMENT, LOGGING_CAMP -> TOOL_SHOP;
            case FARMING_VILLAGE, FISHING_VILLAGE -> FOOD_SHOP;
            case FORTRESS -> ARMOR_SHOP;
            case FRONTIER_OUTPOST -> RANGE_SHOP;
            default -> GENERAL_STORE;
        };
    }

    /** Human-readable name for host tooling. */
    public static String nameOf(int shopId) {
        return switch (shopId) {
            case GENERAL_STORE -> "General Store";
            case FOOD_SHOP -> "Food";
            case ARMOR_SHOP -> "Armour";
            case RANGE_SHOP -> "Ranged gear";
            case TOOL_SHOP -> "Tools";
            default -> "Shop " + shopId;
        };
    }
}
