package dev.alan.combat;

/** Pure numbers of the anvil ore upgrades: caps and prices. No Minecraft types, so it is unit-testable. */
public final class UpgradeRules {
    /** Levels one ore can give a single piece of armor. */
    public static final int MAX_PER_ORE = 4;
    /** Levels all ores together can give a single piece of armor. */
    public static final int MAX_TOTAL = 10;
    /** From this many levels on, every further level costs {@link #EXPENSIVE_COST} ores. */
    public static final int EXPENSIVE_FROM = 6;
    public static final int EXPENSIVE_COST = 2;

    private UpgradeRules() {}

    /** Ores needed for the next level, given how many levels the piece already has in total. */
    public static int oreCost(int totalBefore) { return totalBefore >= EXPENSIVE_FROM ? EXPENSIVE_COST : 1; }

    /** How many levels {@code available} ores buy, and how many of them get used up. */
    public record Plan(int gained, int oreUsed) {}

    public static Plan plan(int currentForOre, int currentTotal, int available) {
        int gained = 0, used = 0, total = currentTotal;
        while (currentForOre + gained < MAX_PER_ORE && total < MAX_TOTAL) {
            int cost = oreCost(total);
            if (used + cost > available) break;
            used += cost;
            total++;
            gained++;
        }
        return new Plan(gained, used);
    }

    /** Like {@link #plan} for upgrades that always cost one item per level, with their own caps. */
    public static Plan flatPlan(int currentForMod, int currentTotal, int maxForMod, int maxTotal, int available) {
        int gained = Math.max(0, Math.min(Math.min(maxForMod - currentForMod, maxTotal - currentTotal), available));
        return new Plan(gained, gained);
    }
}
