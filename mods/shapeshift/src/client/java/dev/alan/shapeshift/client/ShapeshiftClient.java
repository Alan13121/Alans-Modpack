package dev.alan.shapeshift.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alan.shapeshift.FormDefinitions;
import dev.alan.shapeshift.FormSync;
import dev.alan.shapeshift.ShapeshiftMod;
import dev.alan.shapeshift.UseAbility;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public final class ShapeshiftClient implements ClientModInitializer {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(ShapeshiftMod.id("main"));
    public static final KeyMapping OPEN_MENU = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.shapeshift.menu", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, CATEGORY));
    public static final KeyMapping USE_ABILITY = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.shapeshift.ability", InputConstants.Type.KEYBOARD, InputConstants.KEY_R, CATEGORY));
    /** Last form seen per player, so hitboxes are refreshed when a synced form changes. */
    private static final Map<Player, String> seenForms = new WeakHashMap<>();

    @Override public void onInitializeClient() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, ShapeshiftMod.id("form"), FormHud::extract);
        ClientPlayNetworking.registerGlobalReceiver(FormSync.TYPE, (payload, context) ->
            FormDefinitions.setClient(payload.definitions()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_MENU.consumeClick())
                if (client.gui.screen() == null && client.player != null) client.gui.setScreen(new FormScreen());
            while (USE_ABILITY.consumeClick())
                if (client.player != null && client.player.hasAttached(ShapeshiftMod.FORM)) ClientPlayNetworking.send(new UseAbility());
            if (client.level == null) return;
            for (Player player : client.level.players()) {
                String form = player.getAttached(ShapeshiftMod.FORM);
                if (!seenForms.containsKey(player) || !Objects.equals(seenForms.get(player), form)) {
                    seenForms.put(player, form);
                    player.refreshDimensions();
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            seenForms.clear();
            FormDefinitions.setClient(Map.of());
            FormBodies.clear();
        });
    }

    static Minecraft mc() { return Minecraft.getInstance(); }
}
