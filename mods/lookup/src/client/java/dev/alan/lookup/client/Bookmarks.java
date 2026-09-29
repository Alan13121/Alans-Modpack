package dev.alan.lookup.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import dev.alan.lookup.LookupMod;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Items the player pinned to the top row of the list. Saved to {@code config/lookup_bookmarks.json}. */
public final class Bookmarks {
    private static final List<Item> items = new ArrayList<>();

    private Bookmarks() {}

    public static List<Item> items() { return items; }
    public static boolean contains(Item item) { return items.contains(item); }

    /** Adds the item, or removes it when it is already pinned. */
    public static void toggle(Item item) {
        if (!items.remove(item)) items.add(item);
        save();
    }

    private static java.nio.file.Path path() { return FabricLoader.getInstance().getConfigDir().resolve("lookup_bookmarks.json"); }

    private static void save() {
        var array = new JsonArray();
        for (Item item : items) array.add(BuiltInRegistries.ITEM.getKey(item).toString());
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), new GsonBuilder().setPrettyPrinting().create().toJson(array));
        } catch (Exception e) {
            LookupMod.LOG.warn("Cannot write {}", path(), e);
        }
    }

    /** A damaged bookmarks file only loses the bookmarks; it must never stop the game from starting. */
    public static void load() {
        items.clear();
        try {
            if (!Files.exists(path())) return;
            for (var element : JsonParser.parseString(Files.readString(path())).getAsJsonArray()) {
                Identifier id = Identifier.tryParse(element.getAsString());
                Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
                if (item != Items.AIR && !items.contains(item)) items.add(item);
            }
        } catch (Exception e) {
            LookupMod.LOG.warn("Ignoring unreadable {}", path(), e);
            items.clear();
        }
    }
}
