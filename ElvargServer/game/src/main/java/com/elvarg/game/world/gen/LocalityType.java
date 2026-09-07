package com.elvarg.game.world.gen;

import java.util.EnumSet;
import java.util.Set;

/**
 * What a locality is <em>for</em>.
 *
 * The generator decides a locality's identity before it places anything, so that
 * a mining settlement gets ore, a smelter and an anvil because it is a mining
 * settlement - not because fourteen rocks happened to land nearby.
 *
 * @author EverGielinor world generator
 */
public enum LocalityType {

    STARTING_VILLAGE("Haven", true, EnumSet.of(
            TownService.BANK, TownService.GENERAL_STORE, TownService.RANGE,
            TownService.ANVIL, TownService.FURNACE, TownService.ALTAR)),
    FARMING_VILLAGE("Fields", true, EnumSet.of(
            TownService.GENERAL_STORE, TownService.RANGE, TownService.BANK)),
    LOGGING_CAMP("Timberfall", true, EnumSet.of(
            TownService.GENERAL_STORE, TownService.RANGE)),
    FISHING_VILLAGE("Cove", true, EnumSet.of(
            TownService.GENERAL_STORE, TownService.RANGE, TownService.BANK)),
    MINING_SETTLEMENT("Delve", true, EnumSet.of(
            TownService.FURNACE, TownService.ANVIL, TownService.BANK, TownService.GENERAL_STORE)),
    TRADING_POST("Crossing", true, EnumSet.of(
            TownService.BANK, TownService.GENERAL_STORE)),
    FORTRESS("Hold", true, EnumSet.of(
            TownService.BANK, TownService.ANVIL, TownService.ALTAR)),
    FRONTIER_OUTPOST("Watch", true, EnumSet.of(
            TownService.RANGE, TownService.ALTAR)),

    // Wild localities carry no settlement at all.
    WILDS("Wilds", false, EnumSet.noneOf(TownService.class)),
    HIGHLANDS("Heights", false, EnumSet.noneOf(TownService.class)),
    BADLANDS("Waste", false, EnumSet.noneOf(TownService.class));

    private final String nameSuffix;
    private final boolean settled;
    private final Set<TownService> services;

    LocalityType(String nameSuffix, boolean settled, Set<TownService> services) {
        this.nameSuffix = nameSuffix;
        this.settled = settled;
        this.services = services;
    }

    public String nameSuffix() {
        return nameSuffix;
    }

    public boolean isSettled() {
        return settled;
    }

    public Set<TownService> services() {
        return services;
    }

    /**
     * Chooses an identity from the locality's biome and how dangerous it is.
     */
    public static LocalityType forBiome(Biome biome, DifficultyBand band, boolean coastal, int variant) {
        if (band.ordinal() >= DifficultyBand.VERY_HIGH.ordinal()) {
            return switch (biome) {
                case VOLCANIC, WILDERNESS -> BADLANDS;
                case MOUNTAIN, SNOW -> HIGHLANDS;
                default -> WILDS;
            };
        }
        return switch (biome) {
            case MOUNTAIN, SNOW -> HIGHLANDS;
            case VOLCANIC, WILDERNESS -> BADLANDS;
            case ROCKY_HIGHLAND, DESERT -> MINING_SETTLEMENT;
            case DENSE_FOREST -> LOGGING_CAMP;
            case FOREST -> (variant % 2 == 0) ? LOGGING_CAMP : FARMING_VILLAGE;
            case SWAMP -> WILDS;
            case BEACH -> FISHING_VILLAGE;
            case PLAINS, GRASSLAND -> {
                if (coastal) {
                    yield FISHING_VILLAGE;
                }
                yield switch (variant % 3) {
                    case 0 -> FARMING_VILLAGE;
                    case 1 -> TRADING_POST;
                    default -> FARMING_VILLAGE;
                };
            }
            default -> WILDS;
        };
    }
}
