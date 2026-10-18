package dev.alchemy;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public final class EnergyValues {
    public static final long MAX_VALUE = 1_000_000_000L;
    private static Map<String, Long> explicit = Map.of();
    private static Map<String, Long> serverValues = Map.of();
    private static Map<String, Long> clientValues = Map.of();
    private static Set<String> baseItems = Set.of(), loopItems = Set.of();
    private static java.util.List<String> profitable = java.util.List.of();
    private static long serverDefault = EnergyConfig.BASE_DEFAULT, clientDefault = EnergyConfig.BASE_DEFAULT;
    public static String id(Item item) { return BuiltInRegistries.ITEM.getKey(item).toString(); }
    public static long value(Item item, boolean client) {
        if (item == AlchemyMod.BACKPACK) return 0;
        return (client ? clientValues : serverValues).getOrDefault(id(item), client ? clientDefault : serverDefault);
    }
    public static boolean canConvert(ItemStack stack, boolean client) {
        return !stack.isEmpty() && value(stack.getItem(), client) > 0
            && ItemStack.isSameItemSameComponents(stack, stack.getItem().getDefaultInstance());
    }
    public static Map<String, Long> values() { return serverValues; }
    public static long defaultValue() { return serverDefault; }
    /** Items nothing can craft and the config does not list; they are worth {@link #defaultValue()}. */
    public static Set<String> baseItems() { return baseItems; }
    /** Items whose only recipes need themselves (ingot and block with neither listed); one of them got the default value. */
    public static Set<String> loopItems() { return loopItems; }
    /** Recipes whose result is worth more than their ingredients because of an explicit value; see {@link EmcSolver#profitable}. */
    public static java.util.List<String> profitable() { return profitable; }
    public static void sync(long fallback, Map<String, Long> values) {
        clientDefault = fallback;
        clientValues = Map.copyOf(values);
    }

    /** Reads the config, upgrading an older file in place (the old one is kept as .bak). Until recipes load only listed items have values. */
    public static void load() {
        var path = FabricLoader.getInstance().getConfigDir().resolve("alchemy-backpack-energy.json");
        try {
            var loaded = EnergyConfig.loadFrom(path);
            explicit = loaded.values();
            serverDefault = loaded.base();
            serverValues = explicit;
            baseItems = Set.of();
            loopItems = Set.of();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load " + path + "; fix the energy configuration", e);
        }
    }

    /** Derives every item's value from the server's recipes. Run after the recipes load, and again on every data pack reload. */
    public static void recompute(MinecraftServer server) {
        var items = new ArrayList<String>();
        for (Item item : BuiltInRegistries.ITEM) items.add(id(item));
        var recipes = RecipeEmc.extract(server.getRecipeManager());
        var solution = EmcSolver.solve(items, explicit, recipes, serverDefault);
        serverValues = Map.copyOf(solution.values());
        profitable = EmcSolver.profitable(serverValues, explicit, recipes);
        baseItems = Set.copyOf(solution.baseItems());
        loopItems = Set.copyOf(solution.loopItems());
    }

}
