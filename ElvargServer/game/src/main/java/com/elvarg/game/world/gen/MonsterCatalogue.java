package com.elvarg.game.world.gen;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The pool of NPCs the generator may place, read from the server's own
 * definition files rather than from a hand-written list.
 *
 * Two filters matter. The first is junk: this cache's npc_defs.json contains
 * placeholder combat levels - a dozen guards at level 1337, and an entry at
 * 12500 - which would silently poison every difficulty band if placed. The
 * second is loot: an NPC with no drop table is not worth fighting, so monsters
 * are drawn only from ids that npc_drops.json covers.
 *
 * @author EverGielinor world generator
 */
public final class MonsterCatalogue {

    /**
     * Highest combat level treated as real. The genuine top of this roster is the
     * Corporeal Beast at 785; everything above is placeholder data.
     */
    private static final int MAX_PLAUSIBLE_LEVEL = 900;

    /** An NPC the generator may place. */
    public static final class Monster {
        public final int id;
        public final String name;
        public final int combatLevel;
        public final int size;

        Monster(int id, String name, int combatLevel, int size) {
            this.id = id;
            this.name = name;
            this.combatLevel = combatLevel;
            this.size = size;
        }

        @Override
        public String toString() {
            return name + " (" + id + ", lvl " + combatLevel + ")";
        }
    }

    private final List<Monster> monsters = new ArrayList<>();

    public static MonsterCatalogue load(Path definitionsDirectory) throws IOException {
        MonsterCatalogue catalogue = new MonsterCatalogue();
        Set<Integer> withDrops = readDropTableIds(definitionsDirectory.resolve("npc_drops.json"));

        try (Reader reader = Files.newBufferedReader(definitionsDirectory.resolve("npc_defs.json"))) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            for (JsonElement element : array) {
                JsonObject object = element.getAsJsonObject();
                if (!object.has("id") || !object.has("combatLevel")) {
                    continue;
                }
                int id = object.get("id").getAsInt();
                int level = object.get("combatLevel").getAsInt();
                if (level <= 0 || level > MAX_PLAUSIBLE_LEVEL) {
                    continue;
                }
                if (!withDrops.contains(id)) {
                    continue;
                }
                if (object.has("attackable") && !object.get("attackable").getAsBoolean()) {
                    continue;
                }
                String name = object.has("name") ? object.get("name").getAsString() : null;
                if (name == null || name.isEmpty() || "null".equalsIgnoreCase(name)) {
                    continue;
                }
                int size = object.has("size") ? object.get("size").getAsInt() : 1;
                catalogue.monsters.add(new Monster(id, name, level, Math.max(1, size)));
            }
        }
        catalogue.monsters.sort((a, b) -> a.combatLevel != b.combatLevel
                ? Integer.compare(a.combatLevel, b.combatLevel)
                : Integer.compare(a.id, b.id));
        return catalogue;
    }

    private static Set<Integer> readDropTableIds(Path dropsFile) throws IOException {
        Set<Integer> ids = new HashSet<>();
        if (!Files.exists(dropsFile)) {
            return ids;
        }
        try (Reader reader = Files.newBufferedReader(dropsFile)) {
            JsonArray array = new Gson().fromJson(reader, JsonArray.class);
            for (JsonElement element : array) {
                JsonObject object = element.getAsJsonObject();
                if (!object.has("npcIds")) {
                    continue;
                }
                for (JsonElement idElement : object.getAsJsonArray("npcIds")) {
                    ids.add(idElement.getAsInt());
                }
            }
        }
        return ids;
    }

    public int size() {
        return monsters.size();
    }

    /** Every monster whose combat level falls inside the band. */
    public List<Monster> inBand(DifficultyBand band) {
        List<Monster> out = new ArrayList<>();
        for (Monster monster : monsters) {
            if (monster.combatLevel >= band.minCombat() && monster.combatLevel <= band.maxCombat()) {
                out.add(monster);
            }
        }
        return out;
    }

    /** Every monster within a level window, used for dungeon floor progression. */
    public List<Monster> inLevelRange(int min, int max) {
        List<Monster> out = new ArrayList<>();
        for (Monster monster : monsters) {
            if (monster.combatLevel >= min && monster.combatLevel <= max) {
                out.add(monster);
            }
        }
        return out;
    }

    public List<Monster> all() {
        return java.util.Collections.unmodifiableList(monsters);
    }
}
