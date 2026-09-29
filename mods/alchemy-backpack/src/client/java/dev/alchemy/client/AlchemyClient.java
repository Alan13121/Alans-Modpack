package dev.alchemy.client;

import dev.alchemy.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;

public final class AlchemyClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        MenuScreens.register(AlchemyMod.MENU, BagScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(EnergySync.TYPE, (payload, context) ->
            context.client().execute(() -> EnergyValues.sync(payload.fallback(), payload.values())));
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (stack.is(AlchemyMod.BACKPACK)) {
                BagData data = stack.getOrDefault(AlchemyMod.DATA, BagData.EMPTY);
                lines.add(Component.translatable("alchemy.balance", data.energy()).withStyle(ChatFormatting.GOLD));
                lines.add(Component.translatable("alchemy.learned", data.learned().size()).withStyle(ChatFormatting.AQUA));
                lines.add(Component.translatable("alchemy.open").withStyle(ChatFormatting.GRAY));
            } else {
                long value = EnergyValues.value(stack.getItem(), true);
                lines.add(Component.translatable("alchemy.value", value).withStyle(ChatFormatting.GOLD));
                if (stack.getCount() > 1)
                    lines.add(Component.translatable("alchemy.stack_value", value * stack.getCount()).withStyle(ChatFormatting.GRAY));
                if (!EnergyValues.canConvert(stack, true))
                    lines.add(Component.translatable(value == 0 ? "alchemy.blocked" : "alchemy.special").withStyle(ChatFormatting.DARK_GRAY));
            }
        });
    }
}
