package com.elvarg.game.world.gen;

import java.util.ArrayList;
import java.util.List;

/**
 * A coherent set of building parts harvested from one architectural style in the
 * original game map: its wall material, the doors and windows that go with it,
 * its roof pieces, its flooring, and the furniture found inside it.
 *
 * Stealing whole buildings gave houses with missing walls, because a captured
 * footprint is only ever as good as the bounding box around it. Harvesting parts
 * instead means the generator lays out its own rooms and then dresses them in a
 * style that genuinely occurs together in the source material.
 *
 * @author EverGielinor world generator
 */
public final class BuildingStyle {

    public String name;
    /** How many source buildings this style was seen in, for ranking. */
    public int frequency;

    /** Straight wall piece, landscape type 0. */
    public int wall;
    /** Wall corner, landscape type 2, when the style has one. */
    public int cornerWall = -1;
    /** Door, landscape type 0. */
    public int door = -1;
    /** Window, a wall decoration (types 4-8). */
    public int window = -1;
    /** Roof pieces, landscape types 12-21, with the type each was seen as. */
    public final List<int[]> roofPieces = new ArrayList<>();

    /** Floor materials inside buildings of this style. */
    public int floorUnderlay;
    public int floorOverlay;

    /** Free-standing furniture ids seen inside this style. */
    public final List<Integer> furniture = new ArrayList<>();
    /** Wall-mounted decoration ids (torches, hangings) with the type seen. */
    public final List<int[]> wallDecor = new ArrayList<>();

    public boolean isUsable() {
        return wall > 0 && door > 0;
    }

    /**
     * A roof id able to build a whole hipped roof: it needs a sloped panel
     * (type 12), a hip corner (13) and a flat apex (17). Most of the original
     * map's roof objects carry all of 12 to 17 in one definition, so one id
     * usually covers every role; a style whose pieces do not has no roof rather
     * than a roof with invisible corners.
     */
    public int roofBody() {
        for (int[] piece : roofPieces) {
            if (ObjectVetting.rendersAt(piece[0], 12)
                    && ObjectVetting.rendersAt(piece[0], 13)
                    && ObjectVetting.rendersAt(piece[0], 17)) {
                return piece[0];
            }
        }
        return -1;
    }

    public boolean hasRoof() {
        return !roofPieces.isEmpty();
    }

    @Override
    public String toString() {
        return String.format("%-22s seen %-4d wall=%-6d door=%-6d window=%-6s roof=%-2d furniture=%d",
                name, frequency, wall, door,
                window > 0 ? String.valueOf(window) : "-",
                roofPieces.size(), furniture.size());
    }
}
