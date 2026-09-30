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
        MenuScreens.register(LogisticsMod.TERMINAL_MENU, TerminalScreen::new);
        MenuScreens.register(LogisticsMod.CRAFTING_TERMINAL_MENU, CraftingTerminalScreen::new);
        MenuScreens.register(LogisticsMod.OUTPUT_MENU, OutputScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(TerminalSnapshot.TYPE, (payload, context) ->
            context.client().execute(() -> {
                var menu = context.client().player == null ? null : context.client().player.containerMenu;
                if (menu instanceof WarehouseMenu warehouse && menu.containerId == payload.containerId())
                    warehouse.warehouse().receive(payload);
            }));
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            var data = stack.get(LogisticsMod.CELL_DATA);
            if (data == null || !stack.is(LogisticsMod.CELL.asItem())) return;
            lines.add(Component.translatable("logistics.cell.stored", data.total(), data.entries().size(),
                dev.alan.logistics.CellBlockEntity.MAX_TYPES).withStyle(ChatFormatting.GRAY));
        });
    }
}
