package dev.alchemy.test;

import dev.alchemy.EnergyValues;
import java.nio.file.Files;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.Items;

/** Checks EMC values derived from the real recipes, their sync to the client, a reload, and /emc dump. */
public final class EmcClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);

            // Server side: listed bases, crafted items derived from them, blocked and progress items.
            check(value(server, "minecraft:oak_log") == 32, "a listed log keeps its value");
            check(value(server, "minecraft:oak_planks") == 8, "planks are a quarter of a log");
            check(value(server, "minecraft:stick") == 4, "sticks are derived from planks");
            check(value(server, "minecraft:iron_block") == 2304, "an iron block is nine ingots");
            check(value(server, "minecraft:iron_ingot") == 256, "the listed ingot value is not replaced by the block recipe");
            check(value(server, "minecraft:diamond_pickaxe") == 3 * 8192 + 2 * 4, "a pickaxe is its diamonds and sticks");
            check(value(server, "minecraft:stone") == 1, "smelting cobblestone makes stone worth the same");
            check(value(server, "minecraft:bedrock") == 0, "bedrock stays blocked");
            check(value(server, "minecraft:nether_star") >= 100_000, "a nether star is expensive, not a base item");
            check(value(server, "minecraft:ender_pearl") == 1024, "a listed raw material has its own value");
            check(value(server, "minecraft:copper_ingot") == 128 && value(server, "minecraft:redstone") == 64
                && value(server, "minecraft:lapis_lazuli") == 96 && value(server, "minecraft:quartz") == 256, "the raw minerals are listed");
            check(value(server, "minecraft:iron_ore") == value(server, "minecraft:iron_ingot")
                && value(server, "minecraft:gold_ore") == value(server, "minecraft:gold_ingot"), "ores are worth what they smelt into");
            check(value(server, "minecraft:copper_helmet") == 5 * 128, "copper armor derives from the copper ingot");
            check(value(server, "minecraft:zombie_spawn_egg") == 0 && value(server, "minecraft:wither_spawn_egg") == 0, "spawn eggs cannot be converted");
            check(server.computeOnServer(s -> EnergyValues.baseItems().stream().allMatch(id -> EnergyValues.values().get(id) == EnergyValues.defaultValue())
                && !EnergyValues.baseItems().contains("minecraft:oak_planks")), "base items are worth the default and planks are not base items");
            check(server.computeOnServer(s -> EnergyValues.loopItems().isEmpty()), "no recipe loop is left without a listed value");
            var profitable = server.computeOnServer(s -> EnergyValues.profitable());
            check(profitable.isEmpty(), "no recipe makes energy: " + profitable);
            check(server.computeOnServer(s -> EnergyValues.values().size() > 1000), "every item has a value");

            // The client got the same numbers when it joined.
            check(context.computeOnClient(mc -> EnergyValues.value(Items.OAK_PLANKS, true)) == 8, "the client has the derived planks value");
            check(context.computeOnClient(mc -> EnergyValues.value(Items.DIAMOND_PICKAXE, true)) == 3 * 8192 + 2 * 4, "the client has the pickaxe value");

            // A reload recomputes and resends.
            server.runCommand("reload");
            context.waitTicks(100);
            check(value(server, "minecraft:oak_planks") == 8, "values survive a reload");
            check(context.computeOnClient(mc -> EnergyValues.value(Items.STICK, true)) == 4, "the client still has values after a reload");

            // /emc dump writes the base items and every value.
            server.runCommand("emc dump");
            context.waitTicks(10);
            var file = FabricLoader.getInstance().getGameDir().resolve("emc-dump.txt");
            try {
                var text = Files.readString(file);
                check(text.contains("# Base items") && text.contains("# Loop items") && text.contains("# Profitable recipes"), "the dump has its sections");
                check(text.contains("minecraft:oak_planks = 8"), "the dump lists every value");
            } catch (java.io.IOException e) {
                throw new AssertionError("the dump file could not be read: " + e, e);
            }
        }
    }

    private static long value(TestServerContext server, String id) {
        return server.computeOnServer(s -> EnergyValues.values().getOrDefault(id, -1L));
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError("FAILED: " + what);
        System.out.println("EMC TEST OK: " + what);
    }
}
