package com.elvarg.game.world.gen;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * The architectural styles available to the building designer, harvested from
 * the original map by {@code ExtractBuildingParts}.
 *
 * @author EverGielinor world generator
 */
public final class StyleLibrary {

    private static final StyleLibrary EMPTY = new StyleLibrary(List.of());

    private final List<BuildingStyle> styles;

    private StyleLibrary(List<BuildingStyle> styles) {
        this.styles = styles;
    }

    public static StyleLibrary load(Path definitionsDirectory) {
        Path file = definitionsDirectory.resolve("building_styles.json.gz");
        if (!Files.exists(file)) {
            System.out.println("[world] no building_styles.json.gz; settlements will have no buildings."
                    + " Run: ./gradlew :game:extractBuildingParts");
            return EMPTY;
        }
        try (Reader reader = new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8)) {
            List<BuildingStyle> loaded = new Gson().fromJson(reader,
                    new TypeToken<List<BuildingStyle>>() { }.getType());
            List<BuildingStyle> usable = new ArrayList<>();
            for (BuildingStyle style : loaded == null ? List.<BuildingStyle>of() : loaded) {
                if (style.isUsable()) {
                    usable.add(style);
                }
            }
            return new StyleLibrary(usable);
        } catch (IOException e) {
            System.err.println("[world] could not read building styles: " + e.getMessage());
            return EMPTY;
        }
    }

    public boolean isEmpty() {
        return styles.isEmpty();
    }

    public int size() {
        return styles.size();
    }

    public List<BuildingStyle> all() {
        return Collections.unmodifiableList(styles);
    }

    /**
     * The style a settlement builds in.
     *
     * One style per settlement, chosen from the locality's own id, so a village
     * is architecturally consistent and neighbouring villages differ.
     */
    public BuildingStyle forLocality(Locality locality) {
        if (styles.isEmpty()) {
            return null;
        }
        return styles.get(Math.floorMod(locality.id * 7 + locality.biome.ordinal(), styles.size()));
    }
}
