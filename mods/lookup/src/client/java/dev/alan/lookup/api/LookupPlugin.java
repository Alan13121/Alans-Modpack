package dev.alan.lookup.api;

/**
 * Entry point for other mods to add their own categories. Declare it in {@code fabric.mod.json}:
 * <pre>"entrypoints": {"lookup": ["com.example.MyLookupPlugin"]}</pre>
 * Lookup loads it only when it is installed, so the declaring mod does not need a hard dependency.
 * Everything vanilla-style (recipes, brewing, loot) is already covered; plugins are for things that are not
 * recipes, such as a mod's own conversions or item cards.
 */
public interface LookupPlugin {
    void register(LookupRegistry registry);
}
