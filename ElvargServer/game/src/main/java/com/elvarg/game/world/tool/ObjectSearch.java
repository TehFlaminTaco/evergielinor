package com.elvarg.game.world.tool;

import com.elvarg.game.definition.ObjectDefinition;

/**
 * Prints object ids whose name matches a query, with the size and clipping the
 * generator needs in order to place them sensibly.
 *
 * Usage: {@code ObjectSearch <substring> [maxResults]}, or
 * {@code ObjectSearch id:1902,1631 } to look up specific ids.
 */
public final class ObjectSearch {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: ObjectSearch <substring> [maxResults]");
            System.exit(2);
        }
        String query = args[0].toLowerCase();
        int limit = args.length > 1 ? Integer.parseInt(args[1]) : 40;

        ObjectDefinition.init();
        if (query.startsWith("id:")) {
            for (String part : query.substring(3).split(",")) {
                describe(Integer.parseInt(part.trim()));
            }
            return;
        }
        // ObjectDefinition.init assigns its count to a local and leaves the static
        // totalObjects at zero, so the index table is the reliable count here.
        int total = ObjectDefinition.streamIndices.length;
        System.out.println("object definitions: " + total);
        int shown = 0;
        for (int id = 0; id < total && shown < limit; id++) {
            ObjectDefinition def = ObjectDefinition.forId(id);
            if (def == null || def.name == null) {
                continue;
            }
            String name = def.name.toLowerCase();
            if (!name.contains(query)) {
                continue;
            }
            describe(id);
            shown++;
        }
    }

    /** Everything the generator needs to decide whether an object is placeable. */
    private static void describe(int id) {
        ObjectDefinition def = ObjectDefinition.forId(id);
        if (def == null) {
            System.out.printf("  %-6d (no definition)%n", id);
            return;
        }
        // modelTypes is the landscape types this object actually draws at. An
        // object placed at a type not listed here renders as nothing at all.
        String types = def.modelTypes == null ? "10 (plain scenery)"
                : java.util.Arrays.toString(def.modelTypes);
        System.out.printf("  %-6d %-24s size=%dx%d solid=%-5s draws at %-22s actions=%s%n",
                id, def.name == null ? "null" : def.name, def.objectSizeX, def.objectSizeY,
                def.obstructsGround || def.impenetrable, types,
                def.interactions == null ? "-" : String.join(",", sanitise(def.interactions)));
    }

    private static String[] sanitise(String[] actions) {
        String[] out = new String[actions.length];
        for (int i = 0; i < actions.length; i++) {
            out[i] = actions[i] == null ? "-" : actions[i];
        }
        return out;
    }
}
