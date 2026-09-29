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
