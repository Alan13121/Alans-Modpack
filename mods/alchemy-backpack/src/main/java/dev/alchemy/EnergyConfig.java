package dev.alchemy;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * The shape of {@code config/alchemy-backpack-energy.json}: a version, the value of base items ({@code default}) and the explicit
 * values. Items not listed are derived from their recipes by {@link EmcSolver}. Pure logic with no Minecraft types.
 */
final class EnergyConfig {
    static final int VERSION = 2;
    /** What an item that cannot be crafted is worth unless the file lists it. */
    static final long BASE_DEFAULT = 50;
    private static final long LEGACY_DEFAULT = 64;
    private static final long MAX_VALUE = 1_000_000_000L;

    private static final String[] LOGS = {"oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log", "dark_oak_log",
        "mangrove_log", "cherry_log", "pale_oak_log", "crimson_stem", "warped_stem"};
    private static final String[] BLOCKED = {"air", "barrier", "bedrock", "command_block", "chain_command_block",
        "repeating_command_block", "structure_block", "structure_void", "jigsaw", "light", "debug_stick", "spawner",
        "trial_spawner", "vault", "knowledge_book", "end_portal_frame", "reinforced_deepslate"};

    private EnergyConfig() {}

    record Loaded(long base, Map<String, Long> values) {}

    /** The explicit values of a fresh file. Crafted items (planks, blocks, tools...) are deliberately absent: they are derived. */
    static Map<String, Long> defaultValues() {
        Map<String, Long> v = new HashMap<>();
        put(v, 1, "dirt", "cobblestone", "sand", "gravel", "netherrack");
        put(v, 32, LOGS);
        put(v, 32, "coal", "wheat");
        put(v, 256, "iron_ingot");
        put(v, 2048, "gold_ingot");
        put(v, 8192, "diamond");
        put(v, 16384, "emerald");
        put(v, 65536, "netherite_ingot");
        // Items that cannot be crafted but gate progress; high enough that they cannot be bought cheaply. Interim values.
        put(v, 139264, "nether_star");
        put(v, 8192, "dragon_breath");
        put(v, 1_000_000, "dragon_egg");
        put(v, 100_000, "elytra");
        put(v, 20_000, "totem_of_undying");
        put(v, 20_000, "enchanted_golden_apple");
        put(v, 0, BLOCKED);
        v.putAll(bundled());
        return v;
    }

    /** The raw materials, drops, plants and loot-only items listed in data/alchemy_backpack/default_energy.json (id to EMC). */
    private static Map<String, Long> bundled() {
        try (var in = EnergyConfig.class.getResourceAsStream("/data/alchemy_backpack/default_energy.json")) {
            if (in == null) throw new IllegalStateException("default_energy.json is missing from the mod");
            Map<String, Long> out = new HashMap<>();
            for (var e : JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet())
                out.put(e.getKey(), e.getValue().getAsLong());
            return out;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot read default_energy.json", e);
        }
    }

    /** The table written by versions before 2; used to tell the player's own edits from the old generated values. */
    private static Map<String, Long> legacyValues() {
        Map<String, Long> v = new HashMap<>();
        put(v, 1, "dirt", "cobblestone", "stone", "sand", "gravel", "netherrack");
        put(v, 8, "oak_planks", "spruce_planks", "birch_planks");
        put(v, 4, "stick");
        put(v, 32, "oak_log", "spruce_log", "birch_log", "coal", "charcoal", "wheat");
        put(v, 256, "iron_ingot");
        put(v, 2304, "iron_block");
        put(v, 2048, "gold_ingot");
        put(v, 18432, "gold_block");
        put(v, 8192, "diamond");
        put(v, 73728, "diamond_block");
        put(v, 16384, "emerald");
        put(v, 147456, "emerald_block");
        put(v, 65536, "netherite_ingot");
        put(v, 0, BLOCKED);
        return v;
    }

    static JsonObject fresh() {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.addProperty("default", BASE_DEFAULT);
        root.add("values", toJson(defaultValues()));
        return root;
    }

    /**
     * Brings an older file up to date and returns it (the same object when it is already current). The player's own edits are
     * kept; values equal to the old generated table are dropped because crafted items are derived now; new defaults are added.
     */
    static JsonObject upgrade(JsonObject old) {
        if (old.has("version") && old.get("version").getAsInt() >= VERSION) return old;
        Map<String, Long> legacy = legacyValues();
        JsonObject values = new JsonObject();
        if (old.has("values")) {
            for (var e : old.getAsJsonObject("values").entrySet()) {
                long value = e.getValue().getAsLong();
                Long original = legacy.get(e.getKey());
                if (original == null || original != value) values.addProperty(e.getKey(), value);
            }
        }
        for (var e : defaultValues().entrySet()) if (!values.has(e.getKey())) values.addProperty(e.getKey(), e.getValue());
        long oldBase = old.has("default") ? old.get("default").getAsLong() : LEGACY_DEFAULT;
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.addProperty("default", oldBase == LEGACY_DEFAULT ? BASE_DEFAULT : oldBase);
        root.add("values", values);
        return root;
    }

    /**
     * Reads the config file, creating it when missing and upgrading an older one in place (the old file is kept next to it as
     * {@code .bak}).
     */
    static Loaded loadFrom(Path path) throws IOException {
        JsonObject root;
        if (!Files.exists(path)) {
            root = fresh();
            write(path, root);
        } else {
            JsonObject current = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            root = upgrade(current);
            if (root != current) {
                Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
                write(path, root);
            }
        }
        return parse(root);
    }

    private static void write(Path path, JsonObject root) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root));
    }

    static Loaded parse(JsonObject root) {
        long base = root.get("default").getAsLong();
        if (base < 1 || base > MAX_VALUE) throw new IllegalArgumentException("default must be 1.." + MAX_VALUE);
        Map<String, Long> values = new HashMap<>();
        for (var e : root.getAsJsonObject("values").entrySet()) {
            long value = e.getValue().getAsLong();
            if (value < 0 || value > MAX_VALUE) throw new IllegalArgumentException("Invalid value for " + e.getKey());
            values.put(e.getKey(), value);
        }
        return new Loaded(base, Map.copyOf(values));
    }

    private static JsonObject toJson(Map<String, Long> values) {
        JsonObject out = new JsonObject();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> out.addProperty(e.getKey(), e.getValue()));
        return out;
    }

    private static void put(Map<String, Long> map, long value, String... names) {
        for (String name : names) map.put("minecraft:" + name, value);
    }
}
