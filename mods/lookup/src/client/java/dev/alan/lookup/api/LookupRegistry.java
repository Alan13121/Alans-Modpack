package dev.alan.lookup.api;

import java.util.List;
import java.util.function.Supplier;

/** Handed to every {@link LookupPlugin} once, when the client starts. */
public interface LookupRegistry {
    /**
     * Adds a source of views. The supplier runs each time the index is rebuilt (after joining a world, after a
     * reload, and whenever an inventory screen opens), so it can read client-side data that arrives after
     * startup, such as values synced from the server.
     */
    void views(Supplier<List<RecipeView>> provider);
}
