package dev.alan.lookup.api;

import java.util.List;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import net.minecraft.world.item.ItemStack;

/** Handed to every {@link LookupPlugin} once, when the client starts. */
public interface LookupRegistry {
    /**
     * Adds a source of views. The supplier runs each time the index is rebuilt (after joining a world, after a
     * reload, and whenever an inventory screen opens), so it can read client-side data that arrives after
     * startup, such as values synced from the server.
     */
    void views(Supplier<List<RecipeView>> provider);

    /**
     * Adds a source of item counts ("stock") that recipe pages show under each ingredient, e.g. what a storage
     * network holds. Return the amount of that item you can supply, or -1 when you have no answer right now (for
     * instance no storage screen is open); then nothing is drawn. Several providers are added together.
     */
    void stock(ToLongFunction<ItemStack> provider);
}
