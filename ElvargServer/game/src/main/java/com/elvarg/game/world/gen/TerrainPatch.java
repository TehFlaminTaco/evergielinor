package com.elvarg.game.world.gen;

/**
 * A terrain override applied after the biome pass: a floor tile belonging to a
 * building, a paved square, or a road.
 *
 * Content is generated before terrain is painted, so anything that needs to
 * change the ground beneath it records a patch instead of writing directly.
 *
 * @author EverGielinor world generator
 */
public final class TerrainPatch {

    public final int localX;
    public final int localY;
    public final int plane;
    public final int underlay;
    public final int overlay;
    public final int overlayShape;
    public final int overlayRotation;
    /** Height byte, or -1 to leave the terrain's own height alone. */
    public final int height;

    public TerrainPatch(int localX, int localY, int plane, int underlay, int overlay,
                        int overlayShape, int overlayRotation, int height) {
        this.localX = localX;
        this.localY = localY;
        this.plane = plane;
        this.underlay = underlay;
        this.overlay = overlay;
        this.overlayShape = overlayShape;
        this.overlayRotation = overlayRotation;
        this.height = height;
    }
}
