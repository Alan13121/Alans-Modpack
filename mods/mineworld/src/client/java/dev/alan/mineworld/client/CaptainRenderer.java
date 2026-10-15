package dev.alan.mineworld.client;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PiglinRenderer;
import net.minecraft.client.renderer.entity.state.PiglinRenderState;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;

/** The guard captain looks like a piglin brute (vanilla only uses the brute texture for the brute entity type). */
final class CaptainRenderer extends PiglinRenderer {
    CaptainRenderer(EntityRendererProvider.Context context) {
        super(context, ModelLayers.PIGLIN_BRUTE, ModelLayers.PIGLIN_BRUTE, ModelLayers.PIGLIN_BRUTE_ARMOR, ModelLayers.PIGLIN_BRUTE_ARMOR);
    }

    @Override public void extractRenderState(AbstractPiglin entity, PiglinRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.isBrute = true;
    }
}
