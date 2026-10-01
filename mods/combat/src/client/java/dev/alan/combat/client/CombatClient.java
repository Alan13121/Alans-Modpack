package dev.alan.combat.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alan.combat.CombatMod;
import dev.alan.combat.OpenTrinkets;
import dev.alan.combat.Trinkets;
import dev.alan.combat.UpgradeRules;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

public final class CombatClient implements ClientModInitializer {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(CombatMod.id("main"));
    public static final KeyMapping OPEN_TRINKETS = KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.combat.trinkets", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, CATEGORY));

    @Override public void onInitializeClient() {
        MenuScreens.register(CombatMod.MENU, TrinketScreen::new);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_TRINKETS.consumeClick())
                if (client.gui.screen() == null && client.player != null) ClientPlayNetworking.send(new OpenTrinkets());
        });
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            var upgrades = stack.get(CombatMod.UPGRADES);
            if (upgrades != null && upgrades.total() > 0)
                lines.add(Component.translatable("combat.upgrade.total", upgrades.total(), UpgradeRules.MAX_TOTAL).withStyle(ChatFormatting.GOLD));
            if (Trinkets.of(stack) != null) {
                var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
                lines.add(Component.translatable("item." + key.getNamespace() + "." + key.getPath() + ".desc").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("combat.trinket.hint").withStyle(ChatFormatting.DARK_GRAY));
            } else if (stack.is(CombatMod.TRINKET_BAG)) {
                lines.add(Component.translatable("item.combat.trinket_bag.desc").withStyle(ChatFormatting.GRAY));
            }
        });
    }
}
