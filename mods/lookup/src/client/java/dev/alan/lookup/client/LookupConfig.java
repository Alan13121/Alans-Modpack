package dev.alan.lookup.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.alan.lookup.LookupMod;
import java.nio.file.Files;
import net.fabricmc.loader.api.FabricLoader;

/** Client settings from {@code config/lookup.json}. The cheat key rewrites the file when it toggles cheat mode. */
public final class LookupConfig {
    private static boolean cheatMode = false;

    private LookupConfig() {}

    /** Click an item in the list to receive it. The server still only allows creative players, operators and the world owner. */
    public static boolean cheatMode() { return cheatMode; }

    public static void setCheatMode(boolean enabled) {
        cheatMode = enabled;
        save();
    }

    private static java.nio.file.Path path() { return FabricLoader.getInstance().getConfigDir().resolve("lookup.json"); }

    private static void save() {
        var root = new JsonObject();
        root.addProperty("cheat_mode", cheatMode);
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception e) {
            LookupMod.LOG.warn("Cannot write {}", path(), e);
        }
    }

    public static void load() {
        try {
            if (!Files.exists(path())) {
                save();
                return;
            }
            var root = JsonParser.parseString(Files.readString(path())).getAsJsonObject();
            cheatMode = root.has("cheat_mode") && root.get("cheat_mode").getAsBoolean();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load " + path() + "; fix the lookup configuration", e);
        }
    }
}
