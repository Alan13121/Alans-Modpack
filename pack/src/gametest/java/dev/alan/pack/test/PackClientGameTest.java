package dev.alan.pack.test;

import dev.alan.lookup.client.RecipeIndex;
import dev.alan.lookup.client.RecipeScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Lookup with every other mod loaded: their plugins must add categories without being told about each other. */
public final class PackClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            context.runOnClient(mc -> mc.gui.setScreen(new InventoryScreen(mc.player)));
            context.waitTicks(5);

            check(hasUse(context, Items.ZOMBIE_SPAWN_EGG, "shapeshift:form"), "shapeshift plugin lists the zombie form");
            check(hasUse(context, Items.IRON_INGOT, "alchemy_backpack:energy"), "alchemy plugin lists iron ingot energy");
            show(context, Items.ZOMBIE_SPAWN_EGG, "01-shapeshift-zombie-form");
            show(context, Items.CREEPER_SPAWN_EGG, "02-shapeshift-creeper-form");
            show(context, Items.IRON_INGOT, "03-alchemy-iron-ingot");
            show(context, Items.DIAMOND, "04-alchemy-diamond");
            // Diamond's tabs: crafting, ..., alchemy last. Click the last one.
            context.runOnClient(mc -> RecipeScreen.show(mc, mc.gui.screen(), Items.DIAMOND, true));
            context.waitTicks(3);
            int tabs = context.computeOnClient(mc -> RecipeIndex.using(Items.DIAMOND).stream().map(v -> v.categoryKey()).distinct().toList().size());
            double[] cursor = context.computeOnClient(mc -> {
                double scale = mc.getWindow().getGuiScale();
                double left = (mc.getWindow().getGuiScaledWidth() - 176) / 2.0;
                double top = (mc.getWindow().getGuiScaledHeight() - Math.min(mc.getWindow().getGuiScaledHeight() - 16, 24 + 24 + 8 + 3 * 60 + 28)) / 2.0;
                return new double[] {(left + 8 + (tabs - 1) * 24 + 10) * scale, (top + 20 + 10) * scale};
            });
            context.getInput().setCursorPos(cursor[0], cursor[1]);
            context.getInput().pressMouse(0);
            context.waitTicks(3);
            context.takeScreenshot("05-alchemy-diamond-tab");
        }
        warehouseStock(context);
    }

    /** Lookup with a warehouse terminal open: ingredients show the warehouse's stock. */
    private void warehouseStock(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.runOnClient(mc -> mc.gui.setScreen(new InventoryScreen(mc.player)));
            context.waitTicks(5);
            check(context.computeOnClient(mc -> dev.alan.lookup.client.Stock.of(new net.minecraft.world.item.ItemStack(Items.OAK_PLANKS))) == -1,
                "without a terminal there is no stock answer");
            context.runOnClient(mc -> mc.gui.setScreen(null));
            for (String command : new String[] {
                "fill -8 119 -8 8 119 8 minecraft:stone", "fill -8 120 -8 8 126 8 minecraft:air",
                "setblock 0 120 0 logistics:controller", "setblock 1 120 0 logistics:cable",
                "setblock 2 120 0 logistics:crafting_terminal[facing=south]", "setblock 1 120 1 minecraft:chest",
                "item replace block 1 120 1 container.0 with minecraft:oak_planks 9",
                "item replace block 1 120 1 container.1 with minecraft:oak_planks 3",
                "item replace block 1 120 1 container.2 with minecraft:iron_ingot 1",
                "tp @p 2.5 120 3.5 180 20",
            }) world.getServer().runCommand(command);
            context.waitTicks(20);
            context.getInput().pressKey(options -> options.keyUse);
            context.waitForScreen(dev.alan.logistics.client.CraftingTerminalScreen.class);
            context.waitTicks(15);
            check(context.computeOnClient(mc -> dev.alan.lookup.client.Stock.of(new net.minecraft.world.item.ItemStack(Items.OAK_PLANKS))) == 12,
                "the open terminal reports 12 planks");
            check(context.computeOnClient(mc -> dev.alan.lookup.client.Stock.of(new net.minecraft.world.item.ItemStack(Items.DIAMOND))) == 0,
                "items it does not hold report 0, not unknown");
            context.runOnClient(mc -> RecipeScreen.show(mc, mc.gui.screen(), Items.STICK, false));
            context.waitTicks(5);
            context.takeScreenshot("06-stock-sticks");
            context.runOnClient(mc -> RecipeScreen.show(mc, mc.gui.screen(), Items.IRON_PICKAXE, false));
            context.waitTicks(5);
            context.takeScreenshot("07-stock-iron-pickaxe");
        }
    }

    private static void show(ClientGameTestContext context, Item item, String screenshot) {
        context.runOnClient(mc -> RecipeScreen.show(mc, mc.gui.screen(), item, true));
        context.waitTicks(5);
        context.takeScreenshot(screenshot);
    }

    private static boolean hasUse(ClientGameTestContext context, Item item, String category) {
        return context.computeOnClient(mc -> RecipeIndex.using(item).stream().anyMatch(v -> v.categoryKey().equals(category)));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
