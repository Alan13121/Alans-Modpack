package dev.alan.lookup.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alan.lookup.BrewingSync;
import dev.alan.lookup.LookupMod;
import dev.alan.lookup.LootSync;
import dev.alan.lookup.RecipeSync;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public final class LookupClient implements ClientModInitializer {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(LookupMod.id("main"));
    public static final KeyMapping SHOW_RECIPES = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.lookup.recipes", InputConstants.Type.KEYBOARD, InputConstants.KEY_R, CATEGORY));
    public static final KeyMapping SHOW_USES = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.lookup.uses", InputConstants.Type.KEYBOARD, InputConstants.KEY_U, CATEGORY));

    public static final KeyMapping BOOKMARK = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.lookup.bookmark", InputConstants.Type.KEYBOARD, InputConstants.KEY_A, CATEGORY));
    public static final KeyMapping TOGGLE_CHEAT = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.lookup.cheat", InputConstants.Type.KEYBOARD, InputConstants.KEY_F9, CATEGORY));

    /** Works from the world (via the key mapping) and from inventory screens (via the overlay). */
    static void toggleCheat(net.minecraft.client.Minecraft client) {
        LookupConfig.setCheatMode(!LookupConfig.cheatMode());
        if (client.player != null) client.player.sendOverlayMessage(Component.translatable(
            LookupConfig.cheatMode() ? "lookup.cheat.on" : "lookup.cheat.off"));
    }

    @Override public void onInitializeClient() {
        LookupConfig.load();
        Bookmarks.load();
        LookupPlugins.load();
        // Loot goes through the same plugin door other mods use.
        RecipeIndex.addProvider(LootViews::views);
        ClientPlayNetworking.registerGlobalReceiver(LootSync.TYPE, (payload, context) -> LootViews.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(BrewingSync.TYPE, (payload, context) -> RecipeIndex.receiveBrewing(payload));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE_CHEAT.consumeClick()) toggleCheat(client);
        });
        ClientPlayNetworking.registerGlobalReceiver(RecipeSync.TYPE, (payload, context) -> RecipeIndex.receive(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            RecipeIndex.clear();
            LootViews.clear();
            ItemList.clear();
        });
        ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container)) return;
            var overlay = new ItemListOverlay(mc, container, width, height);
            ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, a) -> overlay.extract(g, mouseX, mouseY));
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !overlay.mouseClicked(event));
            ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, scrollX, scrollY) -> !overlay.mouseScrolled(x, y, scrollY));
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
                var mouse = mc.mouseHandler;
                double x = mouse.xpos() * width / mc.getWindow().getScreenWidth();
                double y = mouse.ypos() * height / mc.getWindow().getScreenHeight();
                return !overlay.keyPressed(event, x, y);
            });
            ScreenKeyboardEvents.allowCharType(screen).register((s, event) -> !overlay.charTyped(event));
        });
    }
}
