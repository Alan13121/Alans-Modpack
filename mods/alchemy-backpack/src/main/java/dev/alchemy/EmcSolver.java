package dev.alchemy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Derives the EMC of crafted items from the EMC of their ingredients. Pure logic with no Minecraft types, so it is unit tested.
 *
 * <p>Rules: an explicit value always wins (0 means "cannot be converted"); an item that nothing can make is a base item and is
 * worth {@code base}; any other item costs the cheapest recipe, which is the sum of its cheapest usable ingredients divided by the
 * recipe's output count. Values are found by relaxation (repeatedly lowering each item to its cheapest known recipe until nothing
 * changes), so the result does not depend on item order and a recipe loop (ingots and blocks, or tools smelted into nuggets) can
 * never leave a wrong value behind. A loop with no way in is cut by giving one of its items the base value; those are reported
 * so they can be given an explicit value.
 */
public final class EmcSolver {
    /** Derived values never exceed this; {@link EnergyValues#MAX_VALUE} is the same limit. */
    static final long MAX_VALUE = 1_000_000_000L;
    private static final int MAX_PASSES = 10_000;

    /** One way to make {@code output}: each input is a list of interchangeable items (a tag), and {@code count} items come out. */
    public record Recipe(List<List<String>> inputs, String output, int count) {
        public Recipe {
            if (count < 1) throw new IllegalArgumentException("count must be at least 1");
            inputs = List.copyOf(inputs);
        }
    }

    /**
     * Every item's value; the items nothing can craft that got the base value; and the items cut out of a recipe loop that got
     * it (they have recipes, but only ones that need themselves, so they need an explicit value).
     */
    public record Solution(Map<String, Long> values, Set<String> baseItems, Set<String> loopItems) {}

    private EmcSolver() {}

    public static Solution solve(Collection<String> items, Map<String, Long> explicit, Collection<Recipe> recipes, long base) {
        List<Recipe> usable = new ArrayList<>();
        Map<String, Integer> bestCount = new HashMap<>();
        for (Recipe recipe : recipes) {
            if (explicit.containsKey(recipe.output())) continue;
            usable.add(recipe);
            bestCount.merge(recipe.output(), recipe.count(), Math::max);
        }
        usable.sort(Comparator.comparing(Recipe::output).thenComparing(r -> r.inputs().toString()));

        Map<String, Long> values = new HashMap<>();
        Set<String> baseItems = new TreeSet<>();
        Set<String> loopItems = new TreeSet<>();
        TreeSet<String> unresolved = new TreeSet<>();
        for (String item : items) {
            Long fixed = explicit.get(item);
            if (fixed != null) values.put(item, fixed);
            else if (!bestCount.containsKey(item)) {
                values.put(item, base);
                baseItems.add(item);
            } else unresolved.add(item);
        }

        relax(usable, values);
        unresolved.removeIf(values::containsKey);
        while (!unresolved.isEmpty()) {
            // Something loops on itself with no way in: seed the unit item (the one a recipe makes several of), then relax again.
            String seed = unresolved.stream().max(Comparator.<String>comparingInt(bestCount::get).thenComparing(Comparator.<String>naturalOrder().reversed())).get();
            values.put(seed, base);
            loopItems.add(seed);
            relax(usable, values);
            unresolved.removeIf(values::containsKey);
        }

        Map<String, Long> out = new TreeMap<>();
        for (String item : items) out.put(item, values.get(item));
        return new Solution(out, baseItems, loopItems);
    }

    /**
     * Recipes that make EMC: the output (times its count) is worth more than the cheapest ingredients. Only items with an explicit
     * value can do this, since a derived value is by construction no more than the cheapest recipe. Redeeming the ingredients
     * and depositing the result would gain energy, so these are listed for the player to fix.
     */
    public static List<String> profitable(Map<String, Long> values, Map<String, Long> explicit, Collection<Recipe> recipes) {
        TreeSet<String> out = new TreeSet<>();
        for (Recipe recipe : recipes) {
            Long result = explicit.get(recipe.output());
            if (result == null || result == 0) continue;
            long cost = cost(recipe, values);
            if (cost < 0) continue;
            long gain = result * recipe.count() - exactSum(recipe, values);
            // Rounding of derived values (a ninth of an ingot) can leave a few points either way; that is not worth reporting.
            if (gain > recipe.inputs().size()) out.add(recipe.output() + " x" + recipe.count() + " is worth " + result * recipe.count() + " but its ingredients cost " + exactSum(recipe, values));
        }
        return List.copyOf(out);
    }

    private static long exactSum(Recipe recipe, Map<String, Long> values) {
        long sum = 0;
        for (List<String> alternatives : recipe.inputs()) {
            long cheapest = Long.MAX_VALUE;
            for (String alternative : alternatives) {
                Long v = values.get(alternative);
                if (v != null && v > 0) cheapest = Math.min(cheapest, v);
            }
            sum += cheapest;
        }
        return sum;
    }

    /** Lowers every crafted item to its cheapest recipe, over and over, until no value changes. */
    private static void relax(List<Recipe> recipes, Map<String, Long> values) {
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            boolean changed = false;
            for (Recipe recipe : recipes) {
                long cost = cost(recipe, values);
                if (cost < 0) continue;
                Long current = values.get(recipe.output());
                if (current == null || cost < current) {
                    values.put(recipe.output(), cost);
                    changed = true;
                }
            }
            if (!changed) return;
        }
        throw new IllegalStateException("EMC values did not settle after " + MAX_PASSES + " passes");
    }

    /** The EMC one crafted item of this recipe is worth, or -1 when an ingredient has no value yet. */
    private static long cost(Recipe recipe, Map<String, Long> values) {
        long sum = 0;
        for (List<String> alternatives : recipe.inputs()) {
            long cheapest = Long.MAX_VALUE;
            for (String alternative : alternatives) {
                Long v = values.get(alternative);
                if (v != null && v > 0) cheapest = Math.min(cheapest, v);
            }
            if (cheapest == Long.MAX_VALUE) return -1;
            sum = Math.min(MAX_VALUE, sum + cheapest);
        }
        return Math.max(1, Math.round((double) sum / recipe.count()));
    }
}
