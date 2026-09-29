package dev.alchemy;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.nio.file.Files;
import java.util.Map;
import java.util.HashMap;

public final class EnergyValues {
    public static final long MAX_VALUE = 1_000_000_000L;
    private static Map<String, Long> serverValues = Map.of();
    private static Map<String, Long> clientValues = Map.of();
    private static long serverDefault = 64, clientDefault = 64;
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
    public static void sync(long fallback, Map<String, Long> values) {
        clientDefault = fallback;
        clientValues = Map.copyOf(values);
    }
    public static void load() {
        var path = FabricLoader.getInstance().getConfigDir().resolve("alchemy-backpack-energy.json");
        try {
            if (!Files.exists(path)) {
                var root = new JsonObject();
                root.addProperty("default", 64);
                var values = new JsonObject();
                add(values, 1, "dirt", "cobblestone", "stone", "sand", "gravel", "netherrack");
                add(values, 8, "oak_planks", "spruce_planks", "birch_planks");
                add(values, 4, "stick");
                add(values, 32, "oak_log", "spruce_log", "birch_log", "coal", "charcoal", "wheat");
                add(values, 256, "iron_ingot"); add(values, 2304, "iron_block");
                add(values, 2048, "gold_ingot"); add(values, 18432, "gold_block");
                add(values, 8192, "diamond"); add(values, 73728, "diamond_block");
                add(values, 16384, "emerald"); add(values, 147456, "emerald_block");
                add(values, 65536, "netherite_ingot");
                add(values, 0, "air", "barrier", "bedrock", "command_block", "chain_command_block", "repeating_command_block", "structure_block", "structure_void", "jigsaw", "light", "debug_stick", "spawner", "trial_spawner", "vault", "knowledge_book", "end_portal_frame", "reinforced_deepslate");
                root.add("values", values);
                Files.createDirectories(path.getParent());
                Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root));
            }
            var root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            long fallback = root.get("default").getAsLong();
            if (fallback < 1 || fallback > MAX_VALUE) throw new IllegalArgumentException("default must be 1.." + MAX_VALUE);
            var loaded = new HashMap<String, Long>();
            for (var e : root.getAsJsonObject("values").entrySet()) {
                long value = e.getValue().getAsLong();
                if (value < 0 || value > MAX_VALUE) throw new IllegalArgumentException("Invalid value for " + e.getKey());
                loaded.put(e.getKey(), value);
            }
            serverDefault = fallback; serverValues = Map.copyOf(loaded);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load " + path + "; fix the energy configuration", e);
        }
    }
    private static void add(JsonObject o, long value, String... names) {
        for (String name : names) o.addProperty("minecraft:" + name, value);
    }
}
