package dev.alan.shapeshift;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

/** Server-side settings from {@code config/shapeshift.json}. Changes apply after a restart. */
public final class ShapeshiftConfig {
    private static int cooldownSeconds = 0;
    private static int explodeCooldownSeconds = 0;
    private static boolean explosionsBreakBlocks = true;
    private static boolean monstersIgnoreMonsterForms = true;
    private static boolean golemsHuntMonsterForms = true;
    private static Set<String> blocked = Set.of();
    private ShapeshiftConfig() {}
    public static int cooldownTicks() { return cooldownSeconds * 20; }
    /** After a creeper explosion, how long before the player may transform again. */
    public static int explodeCooldownTicks() { return explodeCooldownSeconds * 20; }
    /** Also subject to the vanilla mob_griefing game rule. */
    public static boolean explosionsBreakBlocks() { return explosionsBreakBlocks; }
    /** Hostile mobs leave players in a hostile form alone (until attacked). */
    public static boolean monstersIgnoreMonsterForms() { return monstersIgnoreMonsterForms; }
    /** Iron and snow golems attack players in a hostile form. */
    public static boolean golemsHuntMonsterForms() { return golemsHuntMonsterForms; }
    public static boolean isBlocked(String entityId) { return blocked.contains(entityId); }
    /** Missing flags default to true so older config files keep working. */
    private static boolean flag(JsonObject root, String key) {
        return !root.has(key) || root.get(key).getAsBoolean();
    }

    public static void load() {
        var path = FabricLoader.getInstance().getConfigDir().resolve("shapeshift.json");
        try {
            if (!Files.exists(path)) {
                var root = new JsonObject();
                root.addProperty("cooldown_seconds", 0);
                root.addProperty("explode_cooldown_seconds", 0);
                root.addProperty("explosions_break_blocks", true);
                root.addProperty("monsters_ignore_monster_forms", true);
                root.addProperty("golems_hunt_monster_forms", true);
                var list = new JsonArray();
                for (String id : new String[] {"ender_dragon", "wither", "warden", "elder_guardian", "armor_stand", "mannequin"})
                    list.add("minecraft:" + id);
                root.add("blocked_entities", list);
                Files.createDirectories(path.getParent());
                Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root));
            }
            var root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            int cooldown = root.has("cooldown_seconds") ? root.get("cooldown_seconds").getAsInt() : 0;
            if (cooldown < 0 || cooldown > 3600) throw new IllegalArgumentException("cooldown_seconds must be 0..3600");
            int explodeCooldown = root.has("explode_cooldown_seconds") ? root.get("explode_cooldown_seconds").getAsInt() : 0;
            if (explodeCooldown < 0 || explodeCooldown > 3600) throw new IllegalArgumentException("explode_cooldown_seconds must be 0..3600");
            boolean breakBlocks = flag(root, "explosions_break_blocks");
            boolean monsterPeace = flag(root, "monsters_ignore_monster_forms");
            boolean golemsHunt = flag(root, "golems_hunt_monster_forms");
            var loaded = new HashSet<String>();
            if (root.has("blocked_entities"))
                for (var e : root.getAsJsonArray("blocked_entities")) loaded.add(e.getAsString());
            cooldownSeconds = cooldown;
            explodeCooldownSeconds = explodeCooldown;
            explosionsBreakBlocks = breakBlocks;
            monstersIgnoreMonsterForms = monsterPeace;
            golemsHuntMonsterForms = golemsHunt;
            blocked = Set.copyOf(loaded);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load " + path + "; fix the shapeshift configuration", e);
        }
    }
}
