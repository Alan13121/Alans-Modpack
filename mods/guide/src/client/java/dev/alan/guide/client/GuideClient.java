package dev.alan.guide.client;

import dev.alan.guide.GuideMod;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;

public final class GuideClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        GuideMod.openHandbook = () -> Minecraft.getInstance().gui.setScreen(new HandbookScreen());
    }
}
