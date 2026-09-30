package dev.alan.mineworld.client;

import dev.alan.mineworld.MineWorldMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;

public final class MineWorldClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        // A world cauldron item names the world it leads to (its custom name, or the id when it has none).
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            String id = stack.get(MineWorldMod.WORLD_ID);
            if (id == null || !stack.is(MineWorldMod.WORLD_CAULDRON.asItem())) return;
            Component name = stack.has(DataComponents.CUSTOM_NAME) ? stack.get(DataComponents.CUSTOM_NAME) : Component.literal(id);
            lines.add(Component.translatable("mineworld.tooltip.world", name).withStyle(ChatFormatting.GRAY));
        });
    }
}
