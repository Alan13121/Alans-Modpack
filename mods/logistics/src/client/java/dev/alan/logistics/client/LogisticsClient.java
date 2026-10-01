package dev.alan.logistics.client;

import dev.alan.logistics.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;

public final class LogisticsClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        if (LogisticsMod.ALCHEMY) BagLabel.register();
        MenuScreens.register(LogisticsMod.TERMINAL_MENU, TerminalScreen::new);
        MenuScreens.register(LogisticsMod.CRAFTING_TERMINAL_MENU, CraftingTerminalScreen::new);
        MenuScreens.register(LogisticsMod.OUTPUT_MENU, InterfaceScreen::new);
        MenuScreens.register(LogisticsMod.INPUT_MENU, InterfaceScreen::new);
        MenuScreens.register(LogisticsMod.FARM_MENU, InterfaceScreen::new);
        MenuScreens.register(LogisticsMod.AUTOCRAFTER_MENU, AutoCrafterScreen::new);
        MenuScreens.register(LogisticsMod.CHANNEL_MENU, DeviceScreen::new);
        MenuScreens.register(LogisticsMod.ANTENNA_MENU, DeviceScreen::new);
        MenuScreens.register(LogisticsMod.COAL_MENU, DeviceScreen::new);
        MenuScreens.register(LogisticsMod.TELEPORTER_MENU, TeleporterScreen::new);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(LogisticsMod.TELEPORTER_ENTITY,
            context -> new net.minecraft.client.renderer.blockentity.BeaconRenderer<TeleporterBlockEntity>());
        ClientPlayNetworking.registerGlobalReceiver(ChannelSync.TYPE, (payload, context) ->
            context.client().execute(() -> ClientChannels.set(payload.channels())));
        ClientPlayNetworking.registerGlobalReceiver(TeleporterList.TYPE, (payload, context) ->
            context.client().execute(() -> {
                var menu = context.client().player == null ? null : context.client().player.containerMenu;
                if (menu instanceof TeleporterMenu teleporter && menu.containerId == payload.containerId()) teleporter.receive(payload);
            }));
        ClientPlayNetworking.registerGlobalReceiver(TerminalSnapshot.TYPE, (payload, context) ->
            context.client().execute(() -> {
                var menu = context.client().player == null ? null : context.client().player.containerMenu;
                if (menu instanceof WarehouseMenu warehouse && menu.containerId == payload.containerId())
                    warehouse.warehouse().receive(payload);
            }));
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            int channel = stack.getOrDefault(LogisticsMod.CHANNEL, 0);
            if (channel > 0) lines.add(Component.translatable("logistics.channel.tooltip", ClientChannels.label(channel)).withStyle(ChatFormatting.AQUA));
            var data = stack.get(LogisticsMod.CELL_DATA);
            if (data == null || !(stack.getItem() instanceof net.minecraft.world.item.BlockItem item
                && item.getBlock() instanceof dev.alan.logistics.CellBlock cell)) return;
            lines.add(Component.translatable("logistics.cell.stored", data.total(), data.entries().size(), cell.maxTypes())
                .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("logistics.cell.capacity", cell.maxPerType()).withStyle(ChatFormatting.DARK_GRAY));
        });
    }
}
