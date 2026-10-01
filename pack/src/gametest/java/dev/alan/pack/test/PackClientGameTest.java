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

            // Mine World: the creation card shows up from the cauldron, the world cauldron and the ores.
            check(hasUse(context, Items.CAULDRON, "mineworld:create"), "mineworld card is a use of the cauldron");
            check(hasUse(context, Items.RAW_IRON, "mineworld:create") && hasUse(context, Items.IRON_INGOT, "mineworld:create"),
                "raw iron and iron ingot both open the mineworld card");
            check(context.computeOnClient(mc -> RecipeIndex.producing(dev.alan.mineworld.MineWorldMod.WORLD_CAULDRON.asItem()).stream()
                .anyMatch(v -> v.categoryKey().equals("mineworld:create"))), "mineworld card is a recipe of the world cauldron");
            show(context, Items.CAULDRON, "08-mineworld-cauldron-use");
            context.runOnClient(mc -> RecipeScreen.show(mc, mc.gui.screen(), dev.alan.mineworld.MineWorldMod.WORLD_CAULDRON.asItem(), false));
            context.waitTicks(5);
            context.takeScreenshot("09-mineworld-world-cauldron-recipe");
            var named = new net.minecraft.world.item.ItemStack(dev.alan.mineworld.MineWorldMod.WORLD_CAULDRON);
            named.set(dev.alan.mineworld.MineWorldMod.WORLD_ID, "w7");
            named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Deep Pit"));
            String tooltip = context.computeOnClient(mc -> named.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.EMPTY,
                mc.player, net.minecraft.world.item.TooltipFlag.NORMAL).stream().map(c -> c.getString()).reduce("", (a, b) -> a + "|" + b));
            check(tooltip.contains("Deep Pit") && (tooltip.contains("礦世界") || tooltip.contains("Mine world")), "tooltip names the world: " + tooltip);
        }
        warehouseStock(context);
        backpackCard(context);
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

    /** The alchemy backpack gets a channel-card slot from the logistics mod, and its energy joins that channel. */
    private void backpackCard(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().get(0);
                var bag = new net.minecraft.world.item.ItemStack(dev.alchemy.AlchemyMod.BACKPACK);
                bag.set(dev.alchemy.AlchemyMod.DATA, new dev.alchemy.BagData(100, java.util.List.of()));
                player.getInventory().setItem(0, bag);
                var card = new net.minecraft.world.item.ItemStack(dev.alan.logistics.LogisticsMod.CHANNEL_CARD);
                card.set(dev.alan.logistics.LogisticsMod.CHANNEL, 5);
                player.getInventory().setItem(1, card);
            });
            context.waitTicks(5);
            context.getInput().pressKey(options -> options.keyUse);
            context.waitForScreen(dev.alchemy.client.BagScreen.class);
            context.waitTicks(5);
            check(context.computeOnClient(mc -> mc.player.containerMenu.slots.size()) == 38, "the backpack menu has the extra card slot");
            int cardSlot = context.computeOnClient(mc -> {
                for (var st : mc.player.containerMenu.slots)
                    if (st.index > 0 && st.index < 37 && st.getItem().is(dev.alan.logistics.LogisticsMod.CHANNEL_CARD)) return st.index;
                return -1;
            });
            check(cardSlot > 0, "the card is in the inventory part of the menu");
            context.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, cardSlot, 0,
                net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, mc.player));
            context.waitTicks(10);
            context.takeScreenshot("10-backpack-card-slot");
            check(world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().get(0);
                var bag = player.getInventory().getItem(0);
                var card = bag.get(dev.alan.logistics.LogisticsMod.BAG_CARD);
                return card != null && dev.alan.logistics.ChannelCardItem.channelOf(card) == 5;
            }), "shift-click put the card into the backpack");
            check(world.getServer().computeOnServer(server -> {
                var level = server.overworld();
                var channels = java.util.Set.of(5);
                boolean seen = dev.alan.logistics.Energy.available(level, channels) == 100 && dev.alan.logistics.Energy.available(level, java.util.Set.of(6)) == 0;
                boolean spent = dev.alan.logistics.Energy.spend(level, channels, 30)
                    && server.getPlayerList().getPlayers().get(0).getInventory().getItem(0).get(dev.alchemy.AlchemyMod.DATA).energy() == 70;
                return seen && spent && !dev.alan.logistics.Energy.spend(level, channels, 500);
            }), "the backpack's energy counts for channel 5 and can be spent");
            // Picking a channel directly wins over the card; the backpack screen cannot create channels.
            int pack = world.getServer().computeOnServer(server -> dev.alan.logistics.ChannelRegistry.get(server)
                .create("Pack", java.util.UUID.randomUUID(), "someone", true).id());
            context.runOnClient(mc -> {
                var send = (java.util.function.Consumer<dev.alan.logistics.ChannelAction>) net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking::send;
                send.accept(new dev.alan.logistics.ChannelAction(mc.player.containerMenu.containerId, dev.alan.logistics.ChannelAction.Kind.CREATE, 0, "Nope", true));
                send.accept(new dev.alan.logistics.ChannelAction(mc.player.containerMenu.containerId, dev.alan.logistics.ChannelAction.Kind.SELECT, pack, "", false));
            });
            context.waitTicks(10);
            context.takeScreenshot("11-backpack-channel-picker");
            check(world.getServer().computeOnServer(server -> {
                var bag = server.getPlayerList().getPlayers().get(0).getInventory().getItem(0);
                var level = server.overworld();
                return bag.getOrDefault(dev.alan.logistics.LogisticsMod.BAG_CHANNEL, 0) == pack
                    && dev.alan.logistics.Energy.available(level, java.util.Set.of(pack)) == 70
                    && dev.alan.logistics.Energy.available(level, java.util.Set.of(5)) == 0
                    && dev.alan.logistics.ChannelRegistry.get(server).countOwned(server.getPlayerList().getPlayers().get(0).getUUID()) == 0;
            }), "selecting a public channel from the backpack works and creating one there does not");
            context.runOnClient(mc -> mc.gui.setScreen(null));
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
