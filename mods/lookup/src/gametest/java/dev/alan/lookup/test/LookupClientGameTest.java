package dev.alan.lookup.test;

import dev.alan.lookup.GiveItem;
import dev.alan.lookup.client.Bookmarks;
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

            // Bookmarks: pinned items show in the top row of the list and survive in the config file.
            context.runOnClient(mc -> {
                Bookmarks.toggle(Items.DIAMOND);
                Bookmarks.toggle(Items.NETHER_STAR);
                mc.gui.setScreen(new InventoryScreen(mc.player));
            });
            context.waitTicks(5);
            check(context.computeOnClient(mc -> Bookmarks.contains(Items.DIAMOND)), "bookmark added");
            context.takeScreenshot("15-bookmarks");
            context.runOnClient(mc -> { Bookmarks.load(); });
            check(context.computeOnClient(mc -> Bookmarks.items().size()) == 2, "bookmarks reload from the config file");
            context.runOnClient(mc -> { Bookmarks.toggle(Items.DIAMOND); Bookmarks.toggle(Items.NETHER_STAR); });
            check(context.computeOnClient(mc -> Bookmarks.items().isEmpty()), "bookmarks removed");

            // Recipe fill: click "+" on a stick recipe and the ingredients land in the 2x2 grid.
            world.getServer().runCommand("give @p minecraft:oak_planks 8");
            context.waitTicks(10);
            context.runOnClient(mc -> mc.gui.setScreen(new InventoryScreen(mc.player)));
            context.waitTicks(3);
            context.runOnClient(mc -> RecipeScreen.show(mc, mc.gui.screen(), Items.STICK, false));
            context.waitTicks(3);
            double[] plus = context.computeOnClient(mc -> {
                var w = mc.getWindow();
                double left = (w.getGuiScaledWidth() - 176) / 2.0;
                double top = (w.getGuiScaledHeight() - Math.min(w.getGuiScaledHeight() - 16, 24 + 24 + 8 + 3 * 60 + 28)) / 2.0;
                return new double[] {(left + 32 + 94 + 9) * w.getGuiScale(), (top + 52 + 7) * w.getGuiScale()};
            });
            context.takeScreenshot("17-fill-button");
            context.getInput().setCursorPos(plus[0], plus[1]);
            context.getInput().pressMouse(0);
            context.waitTicks(10);
            check(context.computeOnClient(mc -> mc.gui.screen() instanceof InventoryScreen), "fill returns to the inventory");
            int filled = context.computeOnClient(mc -> {
                int n = 0;
                for (int i = 0; i < mc.player.inventoryMenu.getCraftSlots().getContainerSize(); i++)
                    if (!mc.player.inventoryMenu.getCraftSlots().getItem(i).isEmpty()) n++;
                return n;
            });
            check(filled == 2, "stick recipe fills two grid slots, got " + filled);
            context.takeScreenshot("18-filled-grid");
            context.runOnClient(mc -> mc.gui.setScreen(null));

            // Cheat mode: clicking gives items, but only when the server agrees.
            check(!LookupConfig.cheatMode(), "cheat mode starts off");
            context.runOnClient(mc -> mc.gui.setScreen(new InventoryScreen(mc.player)));
            context.runOnClient(mc -> ClientPlayNetworking.send(new GiveItem(new ItemStack(Items.DIAMOND, 64))));
            context.waitTicks(10);
            check(context.computeOnClient(mc -> mc.player.getInventory().countItem(Items.DIAMOND)) == 64, "cheat give delivers a stack");
            // Cheat toggle inside an inventory screen: the F9 key, then a click on the label.
            context.getInput().pressKey(dev.alan.lookup.client.LookupClient.TOGGLE_CHEAT);
            context.waitTicks(2);
            check(context.computeOnClient(mc -> LookupConfig.cheatMode()), "F9 turns cheat mode on inside a screen");
            context.runOnClient(mc -> mc.gui.toastManager().clear());
            context.waitTicks(2);
            context.takeScreenshot("10-cheat-mode");
            double[] label = context.computeOnClient(mc -> {
                var w = mc.getWindow();
                int gui = w.getGuiScaledWidth();
                int cols = Math.min((gui - 4 - ((gui - 176) / 2 + 176 + 8)) / 18, 12);
                return new double[] {(gui - 4 - cols * 18 + 6) * w.getGuiScale(), 7 * w.getGuiScale()};
            });
            context.getInput().setCursorPos(label[0], label[1]);
            context.getInput().pressMouse(0);
            context.waitTicks(2);
            check(!context.computeOnClient(mc -> LookupConfig.cheatMode()), "clicking the label turns cheat mode off");
            context.runOnClient(mc -> mc.gui.toastManager().clear());
            context.waitTicks(2);
            context.takeScreenshot("11-cheat-off");
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
