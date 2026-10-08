package dev.alan.combat;

/** Pure numbers for how gear changes a shapeshifted player's R-key ability. */
public final class SkillMath {
    /** Each level of emerald armor upgrade adds this much ability power, summed over the worn pieces. */
    public static final float POWER_PER_EMERALD_LEVEL = 0.05f;
    /** Cooldown factor with a skill charm worn. */
    public static final float FOCUS_COOLDOWN = 0.7f;

    /** Ability power factor with the master's charm worn. */
    public static final float MASTER_POWER = 1.5f;

    private SkillMath() {}

    public static float power(int emeraldLevels) { return power(emeraldLevels, false); }
    public static float power(int emeraldLevels, boolean master) {
        return (1f + POWER_PER_EMERALD_LEVEL * Math.max(0, emeraldLevels)) * (master ? MASTER_POWER : 1f);
    }
    public static float cooldown(boolean focus) { return cooldown(focus, false); }
    /** The master's charm removes the cooldown altogether. */
    public static float cooldown(boolean focus, boolean master) { return master ? 0f : focus ? FOCUS_COOLDOWN : 1f; }
}
