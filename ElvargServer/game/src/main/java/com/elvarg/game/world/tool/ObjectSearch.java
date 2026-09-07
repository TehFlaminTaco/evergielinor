package com.elvarg.game.world.tool;

import com.elvarg.game.definition.ObjectDefinition;

/**
 * Prints object ids whose name matches a query, with the size and clipping the
 * generator needs in order to place them sensibly.
 *
 * Usage: {@code ObjectSearch <substring> [maxResults]}
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
            System.out.printf("  %-6d %-28s size=%dx%d solid=%s actions=%s%n",
                    id, def.name, def.objectSizeX, def.objectSizeY,
                    def.obstructsGround || def.impenetrable,
                    def.interactions == null ? "-" : String.join(",", sanitise(def.interactions)));
            shown++;
        }
    }

    private static String[] sanitise(String[] actions) {
        String[] out = new String[actions.length];
        for (int i = 0; i < actions.length; i++) {
            out[i] = actions[i] == null ? "-" : actions[i];
        }
        return out;
    }
}
