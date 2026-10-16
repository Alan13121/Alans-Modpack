package dev.alan.mineworld.client;

import dev.alan.mineworld.MineWorldMod;
import dev.alan.mineworld.city.City;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.PiglinRenderer;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;

public final class MineWorldClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        // City residents reuse the vanilla models and textures.
        EntityRenderers.register(City.ZOMBIE, ZombieRenderer::new);
        EntityRenderers.register(City.SKELETON, SkeletonRenderer::new);
        EntityRenderers.register(City.PIGLIN, context -> new PiglinRenderer(context, ModelLayers.PIGLIN, ModelLayers.PIGLIN_BABY,
            ModelLayers.PIGLIN_ARMOR, ModelLayers.PIGLIN_BABY_ARMOR));
        EntityRenderers.register(City.CAPTAIN, CaptainRenderer::new);
        EntityRenderers.register(City.CREEPER, CreeperRenderer::new);
        EntityRenderers.register(City.ENDERMAN, EndermanRenderer::new);
        MobRenderers.register();
        TradeLockButton.register();
        // A world cauldron item names the world it leads to (its custom name, or the id when it has none).
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            String id = stack.get(MineWorldMod.WORLD_ID);
            if (id == null || !stack.is(MineWorldMod.WORLD_CAULDRON.asItem())) return;
            Component name = stack.has(DataComponents.CUSTOM_NAME) ? stack.get(DataComponents.CUSTOM_NAME) : Component.literal(id);
            lines.add(Component.translatable("mineworld.tooltip.world", name).withStyle(ChatFormatting.GRAY));
        });
    }
}
