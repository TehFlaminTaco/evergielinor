package com.elvarg.game.world.tool;

import com.elvarg.game.definition.ObjectDefinition;
import com.elvarg.game.world.gen.ResourceKind;

/**
 * Prints the real definition behind every resource object id the generator can
 * place, so a wrong id shows up here rather than as a cave mouth in a forest.
 */
public final class AuditResources {

    public static void main(String[] args) {
        ObjectDefinition.init();
        for (ResourceKind kind : ResourceKind.values()) {
            System.out.println(kind + "  (" + kind.category() + ", level " + kind.level() + ")");
            for (int id : kind.objectIds()) {
                ObjectDefinition definition = ObjectDefinition.forId(id);
                String name = definition == null || definition.name == null ? "<no definition>" : definition.name;
                String size = definition == null ? "?" : definition.objectSizeX + "x" + definition.objectSizeY;
                boolean suspicious = !matches(kind, name);
                System.out.printf("    %-7d %-26s %-6s%s%n", id, name, size, suspicious ? "   <== SUSPICIOUS" : "");
            }
        }
    }

    private static boolean matches(ResourceKind kind, String name) {
        String lower = name.toLowerCase();
        if (kind.category() == ResourceKind.Category.TREE) {
            return lower.contains("tree") || lower.contains("dead tree") || lower.contains("evergreen")
                    || lower.contains("oak") || lower.contains("willow") || lower.contains("yew")
                    || lower.contains("maple") || lower.contains("magic") || lower.contains("teak")
                    || lower.contains("mahogany") || lower.contains("achey") || lower.contains("dramen");
        }
        return lower.contains("rock") || lower.contains("ore") || lower.contains("vein")
                || lower.contains("clay") || lower.contains("coal");
    }
}
