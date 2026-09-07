package com.elvarg.game.world.gen;

/**
 * Coarse difficulty tiers used to keep a locality's monsters, resources and
 * rewards in the same conversation with one another.
 *
 * The bands carry an explicit combat-level and skill-level window rather than a
 * single number, because the brief calls for content that mostly sits inside its
 * band with a deliberate tail outside it.
 *
 * @author EverGielinor world generator
 */
public enum DifficultyBand {

    BEGINNER(1, 10, 1, 15),
    LOW(5, 30, 1, 30),
    MEDIUM(25, 70, 20, 50),
    HIGH(60, 130, 40, 70),
    VERY_HIGH(110, 220, 60, 85),
    ENDGAME(180, 400, 70, 99);

    private final int minCombat;
    private final int maxCombat;
    private final int minSkill;
    private final int maxSkill;

    DifficultyBand(int minCombat, int maxCombat, int minSkill, int maxSkill) {
        this.minCombat = minCombat;
        this.maxCombat = maxCombat;
        this.minSkill = minSkill;
        this.maxSkill = maxSkill;
    }

    public int minCombat() {
        return minCombat;
    }

    public int maxCombat() {
        return maxCombat;
    }

    public int minSkill() {
        return minSkill;
    }

    public int maxSkill() {
        return maxSkill;
    }

    /**
     * The skill-level ceiling a locality will place, allowing the deliberate tail
     * above the band's nominal maximum that the design calls for.
     */
    public int exceptionalSkillCeiling() {
        return Math.min(99, maxSkill + 10);
    }

    public DifficultyBand harder() {
        return ordinal() + 1 < values().length ? values()[ordinal() + 1] : ENDGAME;
    }

    public static DifficultyBand forOrdinal(int ordinal) {
        int clamped = Math.max(0, Math.min(values().length - 1, ordinal));
        return values()[clamped];
    }
}
