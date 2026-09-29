package dev.alan.shapeshift;

/** Pure math for switching forms, kept free of Minecraft types so it can be unit tested. */
public final class FormStats {
    private FormStats() {}
    /** Keeps the same health percentage when the maximum changes; never kills and never exceeds the new maximum. */
    public static float scaledHealth(float health, float oldMax, float newMax) {
        if (oldMax <= 0 || newMax <= 0) throw new IllegalArgumentException("Max health must be positive");
        float ratio = Math.clamp(health / oldMax, 0f, 1f);
        return Math.clamp(ratio * newMax, Math.min(1f, newMax), newMax);
    }
    /** Remaining cooldown in ticks, or 0 when the player may switch again. */
    public static long cooldownRemaining(long now, long lastSwitch, int cooldownTicks) {
        if (cooldownTicks <= 0 || lastSwitch < 0) return 0;
        return Math.max(0, lastSwitch + cooldownTicks - now);
    }
}
