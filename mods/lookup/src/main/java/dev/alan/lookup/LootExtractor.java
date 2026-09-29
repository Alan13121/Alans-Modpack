package dev.alan.lookup;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Reads what every loot table can drop. Tables are turned back into JSON and walked there, because the entry
 * classes keep their contents private and mods add their own entry types; unknown entries are skipped but
 * their children are still searched.
 */
final class LootExtractor {
    private static final int MAX_DROPS = 60, MAX_DEPTH = 6, MAX_TAG_ITEMS = 8;

    private final MinecraftServer server;
    private final Map<Identifier, JsonObject> tables = new HashMap<>();

    private LootExtractor(MinecraftServer server) { this.server = server; }

    static List<LootSync.Source> extract(MinecraftServer server) {
        return new LootExtractor(server).run();
    }

    private List<LootSync.Source> run() {
        var provider = server.reloadableRegistries().lookup();
        var ops = provider.createSerializationContext(JsonOps.INSTANCE);
        provider.lookupOrThrow(Registries.LOOT_TABLE).listElements().forEach(ref ->
            LootTable.DIRECT_CODEC.encodeStart(ops, ref.value()).result()
                .filter(JsonElement::isJsonObject)
                .ifPresent(json -> tables.put(ref.key().identifier(), json.getAsJsonObject())));

        List<LootSync.Source> out = new ArrayList<>();
        tables.keySet().stream().sorted().forEach(id -> {
            String path = id.getPath();
            LootSync.Kind kind;
            Identifier source = id;
            if (path.startsWith("entities/")) {
                source = Identifier.fromNamespaceAndPath(id.getNamespace(), path.substring("entities/".length()));
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(source)) return;
                kind = LootSync.Kind.MOB;
            } else if (path.startsWith("blocks/")) {
                source = Identifier.fromNamespaceAndPath(id.getNamespace(), path.substring("blocks/".length()));
                if (!BuiltInRegistries.BLOCK.containsKey(source) || BuiltInRegistries.BLOCK.getValue(source).asItem() == Items.AIR) return;
                kind = LootSync.Kind.BLOCK;
            } else {
                kind = LootSync.Kind.TABLE;
            }
            Map<Identifier, LootSync.Drop> drops = new LinkedHashMap<>();
            walk(tables.get(id), 1f, 0, drops);
            if (drops.isEmpty()) return;
            if (kind == LootSync.Kind.BLOCK) {
                Identifier own = BuiltInRegistries.ITEM.getKey(BuiltInRegistries.BLOCK.getValue(source).asItem());
                // A block that only drops itself says nothing new.
                if (drops.size() == 1 && drops.containsKey(own)) return;
            }
            out.add(new LootSync.Source(kind, source, List.copyOf(drops.values())));
        });
        return out;
    }

    private void walk(JsonObject table, float chance, int depth, Map<Identifier, LootSync.Drop> drops) {
        if (depth > MAX_DEPTH || !table.has("pools") || !table.get("pools").isJsonArray()) return;
        for (JsonElement pool : table.getAsJsonArray("pools")) {
            if (!pool.isJsonObject()) continue;
            JsonObject p = pool.getAsJsonObject();
            entries(p.get("entries"), chance * chanceOf(p.get("conditions")), depth, drops);
        }
    }

    private void entries(JsonElement list, float chance, int depth, Map<Identifier, LootSync.Drop> drops) {
        if (list == null || !list.isJsonArray()) return;
        for (JsonElement element : list.getAsJsonArray()) {
            if (!element.isJsonObject() || drops.size() >= MAX_DROPS) continue;
            JsonObject entry = element.getAsJsonObject();
            float c = chance * chanceOf(entry.get("conditions"));
            String type = entry.has("type") ? entry.get("type").getAsString() : "";
            switch (type) {
                case "minecraft:item" -> add(drops, entry.get("name"), entry, c);
                case "minecraft:tag" -> {
                    Identifier tag = id(entry.get("name"));
                    if (tag == null) break;
                    int[] count = count(entry.get("functions"));
                    server.reloadableRegistries().lookup().lookupOrThrow(Registries.ITEM)
                        .get(TagKey.create(Registries.ITEM, tag)).ifPresent(set -> {
                            int n = 0;
                            for (var holder : set) {
                                if (n++ >= MAX_TAG_ITEMS) break;
                                put(drops, BuiltInRegistries.ITEM.getKey(holder.value()), count, c);
                            }
                        });
                }
                case "minecraft:loot_table" -> {
                    JsonElement value = entry.get("value");
                    if (value == null) break;
                    if (value.isJsonObject()) walk(value.getAsJsonObject(), c, depth + 1, drops);
                    else {
                        Identifier nested = id(value);
                        if (nested != null && tables.containsKey(nested)) walk(tables.get(nested), c, depth + 1, drops);
                    }
                }
                default -> entries(entry.get("children"), c, depth, drops);
            }
        }
    }

    private static void add(Map<Identifier, LootSync.Drop> drops, JsonElement name, JsonObject entry, float chance) {
        Identifier item = id(name);
        if (item == null) return;
        put(drops, item, count(entry.get("functions")), chance);
    }

    private static void put(Map<Identifier, LootSync.Drop> drops, Identifier item, int[] count, float chance) {
        Item resolved = BuiltInRegistries.ITEM.getValue(item);
        if (resolved == Items.AIR) return;
        drops.merge(item, new LootSync.Drop(item, count[0], count[1], chance),
            (a, b) -> new LootSync.Drop(item, Math.min(a.min(), b.min()), Math.max(a.max(), b.max()), Math.max(a.chance(), b.chance())));
    }

    private static Identifier id(JsonElement element) {
        return element != null && element.isJsonPrimitive() ? Identifier.tryParse(element.getAsString()) : null;
    }

    /** Multiplies the plain chances found among conditions; other conditions (killed by player, ...) are ignored. */
    private static float chanceOf(JsonElement conditions) {
        float chance = 1f;
        if (conditions == null || !conditions.isJsonArray()) return chance;
        for (JsonElement element : conditions.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject condition = element.getAsJsonObject();
            String type = condition.has("condition") ? condition.get("condition").getAsString() : "";
            if (type.equals("minecraft:random_chance")) chance *= number(condition.get("chance"), 1f);
            else if (type.equals("minecraft:random_chance_with_enchanted_bonus")) chance *= number(condition.get("unenchanted_chance"), 1f);
        }
        return Math.max(0f, Math.min(1f, chance));
    }

    /** {@code min, max} from the first set_count function; a lone drop is 1. */
    private static int[] count(JsonElement functions) {
        if (functions != null && functions.isJsonArray())
            for (JsonElement element : functions.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject function = element.getAsJsonObject();
                if (function.has("function") && function.get("function").getAsString().equals("minecraft:set_count") && function.has("count")) {
                    int[] range = range(function.get("count"));
                    return new int[] {Math.max(0, range[0]), Math.max(1, range[1])};
                }
            }
        return new int[] {1, 1};
    }

    private static int[] range(JsonElement provider) {
        if (provider.isJsonPrimitive()) {
            int n = (int) provider.getAsFloat();
            return new int[] {n, n};
        }
        if (!provider.isJsonObject()) return new int[] {1, 1};
        JsonObject o = provider.getAsJsonObject();
        String type = o.has("type") ? o.get("type").getAsString() : "";
        if (type.equals("minecraft:binomial") && o.has("n")) return new int[] {0, (int) number(o.get("n"), 1f)};
        if (o.has("min") && o.has("max")) return new int[] {(int) number(o.get("min"), 1f), (int) number(o.get("max"), 1f)};
        int v = (int) number(o.get("value"), 1f);
        return new int[] {v, v};
    }

    /** A plain number, or the fixed value of a constant/linear provider. */
    private static float number(JsonElement element, float fallback) {
        if (element == null) return fallback;
        if (element.isJsonPrimitive()) return element.getAsFloat();
        if (element.isJsonObject()) {
            JsonObject o = element.getAsJsonObject();
            for (String key : new String[] {"value", "base", "min"})
                if (o.has(key)) return number(o.get(key), fallback);
        }
        return fallback;
    }
}
