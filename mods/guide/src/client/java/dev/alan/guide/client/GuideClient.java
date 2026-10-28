package dev.alan.guide.client;

import dev.alan.guide.GuideMod;
import dev.alan.guide.OpenHandbook;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

public final class GuideClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        GuideMod.openHandbook = () -> Minecraft.getInstance().gui.setScreen(new HandbookScreen());
        ClientPlayNetworking.registerGlobalReceiver(OpenHandbook.TYPE, (payload, context) -> GuideMod.openHandbook.run());
        // A handbook button in the top-left corner of the player inventory; closing the handbook returns to the inventory.
        ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
            if (!(screen instanceof InventoryScreen)) return;
            Screens.getWidgets(screen).add(Button.builder(Component.translatable("guide.handbook.button"),
                b -> mc.gui.setScreen(new HandbookScreen(screen))).bounds(4, 4, 56, 20).build());
        });
    }
}
