package dev.alan.lookup.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongFunction;
import net.minecraft.world.item.ItemStack;

/** Item counts supplied by other mods (see {@code LookupRegistry.stock}); shown on recipe pages. */
public final class Stock {
    private static final List<ToLongFunction<ItemStack>> providers = new ArrayList<>();

    private Stock() {}

    static void add(ToLongFunction<ItemStack> provider) { providers.add(provider); }

    /** Total stock of the item across providers, or -1 if no provider has an answer. */
    public static long of(ItemStack stack) {
        long total = -1;
        for (var provider : providers) {
            long n;
            try {
                n = provider.applyAsLong(stack);
            } catch (RuntimeException e) {
                continue;
            }
            if (n >= 0) total = Math.max(total, 0) + n;
        }
        return total;
    }

    /** "999", "1.2k", "34k", "1.5M". */
    static String compact(long n) {
        if (n < 1000) return Long.toString(n);
        if (n < 100_000) return n < 10_000 ? (n / 1000) + "." + (n % 1000) / 100 + "k" : (n / 1000) + "k";
        if (n < 1_000_000) return (n / 1000) + "k";
        return (n / 1_000_000) + "." + (n % 1_000_000) / 100_000 + "M";
    }
}
