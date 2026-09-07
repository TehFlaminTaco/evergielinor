package com.elvarg.game.world.gen;

import java.util.ArrayList;
import java.util.List;

/**
 * A named, bounded part of the island with a single identity and difficulty.
 *
 * Localities are the unit the generator reasons in. Resources, monsters, rewards
 * and settlement are all decided per locality, which is what keeps a region
 * internally coherent instead of being an average of the whole island.
 *
 * @author EverGielinor world generator
 */
public final class Locality {

    public int id;
    public String name;
    /** Centre in island-local tile coordinates. */
    public int centreX;
    public int centreY;
    public int radius;
    public Biome biome;
    public DifficultyBand band;
    public LocalityType type;
    public boolean coastal;
    /** Straight-line distance from the starting village, in tiles. */
    public int distanceFromStart;
    /** Services the settlement here provides, empty for unsettled localities. */
    public final List<TownService> services = new ArrayList<>();
    /** Town centre in island-local tiles, or -1 when the locality has no town. */
    public int townX = -1;
    public int townY = -1;
    /** How many buildings the settlement actually got, for validation and inspection. */
    public int buildings;
    /** Shop this settlement runs, or -1 when it has no shopkeeper. */
    public int shopId = -1;

    public boolean hasTown() {
        return townX >= 0;
    }

    @Override
    public String toString() {
        return String.format("#%d %s [%s, %s, %s]%s", id, name, biome, band, type,
                hasTown() ? " town@" + townX + "," + townY : "");
    }
}
