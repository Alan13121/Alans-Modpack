package dev.alan.lookup.client;

import dev.alan.lookup.LookupMod;
import dev.alan.lookup.api.LookupPlugin;
import dev.alan.lookup.api.LookupRegistry;
import dev.alan.lookup.api.RecipeView;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.loader.api.FabricLoader;

/** Finds every mod's {@code lookup} entry point and hands it the registry. */
final class LookupPlugins {
    private LookupPlugins() {}

    static void load() {
        LookupRegistry registry = new LookupRegistry() {
            @Override public void views(Supplier<List<RecipeView>> provider) { RecipeIndex.addProvider(provider); }
            @Override public void stock(ToLongFunction<ItemStack> provider) { Stock.add(provider); }
        };
        for (var container : FabricLoader.getInstance().getEntrypointContainers("lookup", LookupPlugin.class)) {
            try {
                container.getEntrypoint().register(registry);
            } catch (Throwable t) {
                LookupMod.LOG.error("Lookup plugin from {} failed to register", container.getProvider().getMetadata().getId(), t);
            }
        }
    }
}
