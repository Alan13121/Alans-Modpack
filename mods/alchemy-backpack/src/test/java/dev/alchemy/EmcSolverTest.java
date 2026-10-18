package dev.alchemy;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import dev.alchemy.EmcSolver.Recipe;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmcSolverTest {
    private static Recipe recipe(String out, int count, String... inputs) {
        return new Recipe(java.util.Arrays.stream(inputs).map(List::of).toList(), out, count);
    }

    private static long solve(String item, Map<String, Long> explicit, List<Recipe> recipes, String... extra) {
        var items = new java.util.ArrayList<>(List.of(extra));
        items.add(item);
        return EmcSolver.solve(items, explicit, recipes, 50).values().get(item);
    }

    @Test void itemsNothingCanMakeAreBaseItems() {
        var solution = EmcSolver.solve(List.of("ore", "gem"), Map.of("gem", 100L), List.of(), 50);
        assertEquals(50, solution.values().get("ore"));
        assertEquals(100, solution.values().get("gem"));
        assertEquals(java.util.Set.of("ore"), solution.baseItems());
    }

    @Test void craftedItemsSumTheirIngredientsAndSplitByOutputCount() {
        var recipes = List.of(recipe("planks", 4, "log"), recipe("stick", 4, "planks", "planks"));
        var explicit = Map.of("log", 32L);
        assertEquals(8, solve("planks", explicit, recipes, "log"));
        assertEquals(4, solve("stick", explicit, recipes, "log", "planks"));
    }

    @Test void explicitValuesBeatRecipes() {
        var recipes = List.of(recipe("planks", 4, "log"));
        assertEquals(99, solve("planks", Map.of("log", 32L, "planks", 99L), recipes, "log"));
    }

    @Test void theCheapestRecipeWins() {
        var recipes = List.of(recipe("x", 1, "a", "a"), recipe("x", 1, "b"));
        assertEquals(30, solve("x", Map.of("a", 100L, "b", 30L), recipes, "a", "b"));
    }

    @Test void tagsUseTheirCheapestMember() {
        var recipes = List.of(new Recipe(List.of(List.of("oak", "gold")), "boat", 1));
        assertEquals(10, solve("boat", Map.of("oak", 10L, "gold", 2000L), recipes, "oak", "gold"));
    }

    @Test void storageBlocksAndIngotsDoNotLoop() {
        var recipes = List.of(recipe("block", 1, "ingot", "ingot", "ingot", "ingot", "ingot", "ingot", "ingot", "ingot", "ingot"),
            new Recipe(List.of(List.of("block")), "ingot", 9));
        var explicit = Map.of("ingot", 256L);
        assertEquals(2304, solve("block", explicit, recipes, "ingot"));
        assertEquals(256, solve("ingot", explicit, recipes, "block"));
        var noIngotValue = EmcSolver.solve(List.of("block", "ingot"), Map.of(), recipes, 50).values();
        assertTrue(noIngotValue.get("block") > 0 && noIngotValue.get("ingot") > 0);
    }

    @Test void aLoopThroughSmeltedGearDoesNotPoisonTheResult() {
        // ingot -> helmet -> (smelt) nugget -> ingot, and ore -> (smelt) ingot gives the loop a way in.
        var recipes = List.of(
            recipe("helmet", 1, "ingot", "ingot", "ingot", "ingot", "ingot"),
            recipe("nugget", 1, "helmet"),
            new Recipe(List.of(List.of("ingot")), "nugget", 9),
            recipe("ingot", 1, "nugget", "nugget", "nugget", "nugget", "nugget", "nugget", "nugget", "nugget", "nugget"),
            recipe("ingot", 1, "ore"));
        var items = List.of("helmet", "nugget", "ingot", "ore");
        var solution = EmcSolver.solve(items, Map.of(), recipes, 50);
        assertEquals(50, solution.values().get("ingot"));
        assertEquals(250, solution.values().get("helmet"));
        assertEquals(6, solution.values().get("nugget"));
        assertEquals(java.util.Set.of("ore"), solution.baseItems());
        assertTrue(solution.loopItems().isEmpty());
        assertEquals(solution.values(), EmcSolver.solve(List.of("ore", "ingot", "nugget", "helmet"), Map.of(), recipes, 50).values());
    }

    @Test void aLoopWithNoWayInSeedsTheUnitItemAndReportsIt() {
        var recipes = List.of(recipe("block", 1, "dust", "dust", "dust", "dust", "dust", "dust", "dust", "dust", "dust"),
            new Recipe(List.of(List.of("block")), "dust", 9));
        var solution = EmcSolver.solve(List.of("block", "dust"), Map.of(), recipes, 50);
        assertEquals(50, solution.values().get("dust"));
        assertEquals(450, solution.values().get("block"));
        assertEquals(java.util.Set.of("dust"), solution.loopItems());
    }

    @Test void recipesThatMakeEnergyAreReported() {
        var recipes = List.of(recipe("ingot", 1, "ore"), recipe("plank", 4, "log"));
        var explicit = Map.of("ore", 10L, "ingot", 256L, "log", 32L, "plank", 8L);
        var values = EmcSolver.solve(List.of("ore", "ingot", "log", "plank"), explicit, recipes, 50).values();
        var found = EmcSolver.profitable(values, explicit, recipes);
        assertEquals(1, found.size());
        assertTrue(found.getFirst().startsWith("ingot x1"));
    }

    @Test void blockedIngredientsMakeARecipeUnusable() {
        var recipes = List.of(recipe("tool", 1, "bedrock"));
        var solution = EmcSolver.solve(List.of("tool", "bedrock"), Map.of("bedrock", 0L), recipes, 50);
        assertEquals(0, solution.values().get("bedrock"));
        assertEquals(50, solution.values().get("tool"));
    }

    @Test void blockedResultsStayBlocked() {
        var recipes = List.of(recipe("barrier", 1, "glass"));
        assertEquals(0, solve("barrier", Map.of("barrier", 0L, "glass", 10L), recipes, "glass"));
    }

    @Test void valuesAreCappedAndNeverBelowOne() {
        var recipes = List.of(recipe("huge", 1, "big", "big"), recipe("crumb", 64, "dust"));
        var explicit = Map.of("big", 900_000_000L, "dust", 1L);
        assertEquals(1_000_000_000L, solve("huge", explicit, recipes, "big"));
        assertEquals(1, solve("crumb", explicit, recipes, "dust"));
    }

    @Test void chainsResolveRegardlessOfItemOrder() {
        var recipes = List.of(recipe("c", 1, "b"), recipe("b", 1, "a"));
        var solution = EmcSolver.solve(List.of("c", "b", "a"), Map.of("a", 7L), recipes, 50).values();
        assertEquals(7, solution.get("b"));
        assertEquals(7, solution.get("c"));
    }

    @Test void freshConfigIsCurrentAndKeepsProgressItemsExpensive() {
        var loaded = EnergyConfig.parse(EnergyConfig.fresh());
        assertEquals(50, loaded.base());
        assertTrue(loaded.values().get("minecraft:nether_star") >= 100_000);
        assertTrue(loaded.values().get("minecraft:dragon_breath") > 1_000);
        assertEquals(0, loaded.values().get("minecraft:bedrock"));
        assertFalse(loaded.values().containsKey("minecraft:oak_planks"));
        assertFalse(loaded.values().containsKey("minecraft:iron_block"));
        var fresh = EnergyConfig.fresh();
        assertSame(fresh, EnergyConfig.upgrade(fresh));
    }

    @Test void bundledTableCoversRawMaterialsAndBlocksSpawnEggs() {
        var v = EnergyConfig.parse(EnergyConfig.fresh()).values();
        assertEquals(128, v.get("minecraft:copper_ingot"));
        assertEquals(64, v.get("minecraft:redstone"));
        assertEquals(96, v.get("minecraft:lapis_lazuli"));
        assertEquals(256, v.get("minecraft:quartz"));
        assertEquals(0, v.get("minecraft:wither_spawn_egg"));
        assertEquals(0, v.get("mineworld:ore_worm_spawn_egg"));
        assertEquals(4096, v.get("mineworld:bounty_proof"));
        assertEquals(v.get("minecraft:iron_ingot"), v.get("minecraft:iron_ore"));
        assertEquals(v.get("minecraft:gold_ingot"), v.get("minecraft:gold_ore"));
        assertEquals(v.get("minecraft:gold_ingot"), v.get("minecraft:nether_gold_ore"));
        assertEquals(v.get("minecraft:copper_ingot") * 3, v.get("minecraft:copper_ore"));
    }

    @Test void upgradeKeepsPlayerEditsAndDropsOldGeneratedValues() {
        var old = new JsonObject();
        old.addProperty("default", 64);
        var values = new JsonObject();
        values.addProperty("minecraft:iron_ingot", 256);
        values.addProperty("minecraft:oak_planks", 8);
        values.addProperty("minecraft:iron_block", 2304);
        values.addProperty("minecraft:diamond", 5000);
        values.addProperty("minecraft:gold_nugget", 300);
        old.add("values", values);

        var upgraded = EnergyConfig.upgrade(old);
        var loaded = EnergyConfig.parse(upgraded);
        assertEquals(EnergyConfig.VERSION, upgraded.get("version").getAsInt());
        assertEquals(50, loaded.base());
        assertEquals(5000, loaded.values().get("minecraft:diamond"));
        assertEquals(300, loaded.values().get("minecraft:gold_nugget"));
        assertEquals(256, loaded.values().get("minecraft:iron_ingot"));
        assertFalse(loaded.values().containsKey("minecraft:oak_planks"));
        assertFalse(loaded.values().containsKey("minecraft:iron_block"));
        assertTrue(loaded.values().containsKey("minecraft:nether_star"));
    }

    @Test void loadingALegacyFileUpgradesItOnDiskAndKeepsABackup(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var file = dir.resolve("config").resolve("alchemy-backpack-energy.json");
        java.nio.file.Files.createDirectories(file.getParent());
        var legacy = "{\"default\":64,\"values\":{\"minecraft:oak_planks\":8,\"minecraft:iron_block\":2304,"
            + "\"minecraft:gold_nugget\":300,\"minecraft:cobblestone\":7,\"minecraft:bedrock\":0}}";
        java.nio.file.Files.writeString(file, legacy);

        var loaded = EnergyConfig.loadFrom(file);

        assertEquals(50, loaded.base());
        assertEquals(300, loaded.values().get("minecraft:gold_nugget"));
        assertEquals(7, loaded.values().get("minecraft:cobblestone"));
        assertEquals(0, loaded.values().get("minecraft:bedrock"));
        assertFalse(loaded.values().containsKey("minecraft:oak_planks"));
        assertTrue(loaded.values().containsKey("minecraft:nether_star"));
        assertEquals(legacy, java.nio.file.Files.readString(dir.resolve("config").resolve("alchemy-backpack-energy.json.bak")));
        var onDisk = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
        assertEquals(EnergyConfig.VERSION, onDisk.get("version").getAsInt());
        assertEquals(loaded.values(), EnergyConfig.loadFrom(file).values());
        assertFalse(java.nio.file.Files.exists(dir.resolve("config").resolve("alchemy-backpack-energy.json.bak.bak")));
    }

    @Test void loadingWithNoFileWritesTheDefaults(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var file = dir.resolve("alchemy-backpack-energy.json");
        var loaded = EnergyConfig.loadFrom(file);
        assertTrue(java.nio.file.Files.exists(file));
        assertEquals(50, loaded.base());
        assertFalse(java.nio.file.Files.exists(dir.resolve("alchemy-backpack-energy.json.bak")));
    }

    @Test void upgradeKeepsACustomBaseValue() {
        var old = new JsonObject();
        old.addProperty("default", 10);
        old.add("values", new JsonObject());
        assertEquals(10, EnergyConfig.parse(EnergyConfig.upgrade(old)).base());
    }

    @Test void parseRejectsBadNumbers() {
        var bad = EnergyConfig.fresh();
        bad.addProperty("default", 0);
        assertThrows(IllegalArgumentException.class, () -> EnergyConfig.parse(bad));
        var negative = EnergyConfig.fresh();
        negative.getAsJsonObject("values").addProperty("minecraft:dirt", -1);
        assertThrows(IllegalArgumentException.class, () -> EnergyConfig.parse(negative));
    }
}
