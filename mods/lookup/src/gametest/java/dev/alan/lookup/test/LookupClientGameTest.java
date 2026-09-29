package dev.alan.lookup.test;

import dev.alan.lookup.GiveItem;
import dev.alan.lookup.client.ItemList;
import dev.alan.lookup.client.LookupConfig;
import dev.alan.lookup.client.RecipeIndex;
import dev.alan.lookup.client.RecipeScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Opens the inventory with the item list, then recipe and use pages, and screenshots each. */
public final class LookupClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            check(RecipeIndex.size() > 500, "recipes synced from the server, got " + RecipeIndex.size());

            context.runOnClient(mc -> mc.gui.setScreen(new InventoryScreen(mc.player)));
            context.waitTicks(10);
            context.takeScreenshot("01-inventory-list");

            check(count(context, false) > 0, "sticks have a crafting recipe");
            context.runOnClient(mc -> RecipeScreen.show(mc, mc.gui.screen(), Items.STICK, false));
            context.waitTicks(5);
            context.takeScreenshot("02-recipes-stick");

            context.runOnClient(mc -> RecipeScreen.show(mc, mc.gui.screen(), Items.IRON_INGOT, true));
            context.waitTicks(5);
            context.takeScreenshot("03-uses-iron-ingot");

            context.runOnClient(mc -> RecipeScreen.show(mc, mc.gui.screen(), Items.IRON_INGOT, false));
            context.waitTicks(5);
            context.takeScreenshot("04-recipes-iron-ingot");

            context.runOnClient(mc -> RecipeScreen.show(mc, mc.gui.screen(), Items.STONE_BRICKS, false));
            context.waitTicks(5);
            context.takeScreenshot("05-recipes-stone-bricks");

            context.runOnClient(mc -> RecipeScreen.show(mc, mc.gui.screen(), Items.SPLASH_POTION, false));
            context.waitTicks(5);
            context.takeScreenshot("06-recipes-splash-potion");
            context.runOnClient(mc -> RecipeScreen.show(mc, mc.gui.screen(), Items.NETHER_WART, true));
            context.waitTicks(5);
            context.takeScreenshot("07-uses-nether-wart");
            context.runOnClient(mc -> RecipeScreen.show(mc, mc.gui.screen(), Items.SMITHING_TABLE, false));
            context.waitTicks(5);
            context.takeScreenshot("08-recipes-smithing-table");
            context.runOnClient(mc -> RecipeScreen.show(mc, mc.gui.screen(), Items.NETHERITE_INGOT, true));
            context.waitTicks(5);
            context.takeScreenshot("09-uses-netherite-ingot");

            // Loot: the server reads loot tables and the client lists mob, block and chest drops.
            context.runOnClient(mc -> RecipeIndex.refreshPluginsForTest());
            check(hasCategory(context, Items.ROTTEN_FLESH, "loot:mob"), "zombies' rotten flesh has a mob drop view");
            check(hasCategory(context, Items.COBBLESTONE, "loot:block"), "stone drops cobblestone");
            check(hasCategory(context, Items.SADDLE, "loot:table"), "saddle appears in a chest table");
            check(hasCategory(context, Items.DIAMOND, "loot:block"), "diamond ore drops diamonds");
            for (var entry : new Object[][] {{Items.ROTTEN_FLESH, "11-loot-mob-rotten-flesh"}, {Items.COBBLESTONE, "12-loot-block-cobblestone"},
                                             {Items.SADDLE, "13-loot-table-saddle"}, {Items.ZOMBIE_SPAWN_EGG, "14-uses-zombie-egg"}}) {
                boolean uses = entry[0] == Items.ZOMBIE_SPAWN_EGG;
                context.runOnClient(mc -> RecipeScreen.show(mc, mc.gui.screen(), (net.minecraft.world.item.Item) entry[0], uses));
                context.waitTicks(5);
                context.takeScreenshot((String) entry[1]);
            }

            // Search terms: name, @mod, #tag, $tooltip.
            check(matches(context, "pickaxe") > 0, "name search finds pickaxes");
            check(matches(context, "@minecraft") > 500, "@minecraft finds vanilla items");
            check(matches(context, "@nosuchmod") == 0, "unknown mod finds nothing");
            check(matches(context, "#logs") > 0, "#logs finds log items");
            check(matches(context, "#logs birch") > 0 && matches(context, "#logs birch") < matches(context, "#logs"), "terms combine");
            check(matches(context, "$attack") > 0, "$ searches tooltip text");

            // Cheat mode: clicking gives items, but only when the server agrees.
            check(!LookupConfig.cheatMode(), "cheat mode starts off");
            context.runOnClient(mc -> mc.gui.setScreen(new InventoryScreen(mc.player)));
            context.runOnClient(mc -> ClientPlayNetworking.send(new GiveItem(new ItemStack(Items.DIAMOND, 64))));
            context.waitTicks(10);
            check(context.computeOnClient(mc -> mc.player.getInventory().countItem(Items.DIAMOND)) == 64, "cheat give delivers a stack");
            context.runOnClient(mc -> LookupConfig.setCheatMode(true));
            context.takeScreenshot("10-cheat-mode");
            context.runOnClient(mc -> LookupConfig.setCheatMode(false));
        }
    }

    private static boolean hasCategory(ClientGameTestContext context, net.minecraft.world.item.Item item, String prefix) {
        return context.computeOnClient(mc -> RecipeIndex.producing(item).stream().anyMatch(v -> v.categoryKey().startsWith(prefix)));
    }

    private static int matches(ClientGameTestContext context, String query) {
        return context.computeOnClient(mc -> ItemList.filter(ItemList.all(mc.level), query).size());
    }

    private static int count(ClientGameTestContext context, boolean uses) {
        return context.computeOnClient(mc -> uses ? RecipeIndex.using(Items.STICK).size() : RecipeIndex.producing(Items.STICK).size());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
