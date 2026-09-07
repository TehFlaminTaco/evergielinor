package com.elvarg.game.world.gen;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The building prefabs available to the generator, loaded from the library that
 * {@code ExtractPrefabs} harvests out of the original game map.
 *
 * @author EverGielinor world generator
 */
public final class PrefabLibrary {

    private static final PrefabLibrary EMPTY = new PrefabLibrary(List.of());

    private final List<BuildingPrefab> prefabs;

    private PrefabLibrary(List<BuildingPrefab> prefabs) {
        this.prefabs = prefabs;
    }

    public static PrefabLibrary load(Path definitionsDirectory) {
        Path file = definitionsDirectory.resolve("building_prefabs.json.gz");
        if (!Files.exists(file)) {
            System.out.println("[world] no building_prefabs.json.gz; settlements will have no buildings."
                    + " Run: ./gradlew :game:extractPrefabs");
            return EMPTY;
        }
        try (Reader reader = new java.io.InputStreamReader(
                new java.util.zip.GZIPInputStream(Files.newInputStream(file)),
                java.nio.charset.StandardCharsets.UTF_8)) {
            List<BuildingPrefab> loaded = new Gson().fromJson(reader,
                    new TypeToken<List<BuildingPrefab>>() { }.getType());
            return new PrefabLibrary(loaded == null ? List.of() : loaded);
        } catch (IOException e) {
            System.err.println("[world] could not read building prefabs: " + e.getMessage());
            return EMPTY;
        }
    }

    public boolean isEmpty() {
        return prefabs.isEmpty();
    }

    public int size() {
        return prefabs.size();
    }

    public List<BuildingPrefab> all() {
        return Collections.unmodifiableList(prefabs);
    }

    /** Prefabs that fit within the given footprint, largest first. */
    public List<BuildingPrefab> fitting(int maxWidth, int maxHeight) {
        List<BuildingPrefab> out = new ArrayList<>();
        for (BuildingPrefab prefab : prefabs) {
            if (prefab.width <= maxWidth && prefab.height <= maxHeight) {
                out.add(prefab);
            }
        }
        out.sort((a, b) -> b.area() - a.area());
        return out;
    }

    /** The smallest prefab that provides a named facility, or null. */
    public BuildingPrefab providing(String facility, int maxWidth, int maxHeight) {
        BuildingPrefab best = null;
        for (BuildingPrefab prefab : prefabs) {
            if (!prefab.facilities.contains(facility)) {
                continue;
            }
            if (prefab.width > maxWidth || prefab.height > maxHeight) {
                continue;
            }
            if (best == null || prefab.area() < best.area()) {
                best = prefab;
            }
        }
        return best;
    }
}
