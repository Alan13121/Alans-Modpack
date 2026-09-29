package dev.alan.shapeshift.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.alan.shapeshift.client.FormBodies;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Draws shapeshifted players as their form in the world.
 * Only the world pass is changed; the first-person hand pass still uses the player state.
 */
@Mixin(LevelExtractor.class)
abstract class LevelExtractorMixin {
    @WrapOperation(method = "extractVisibleEntities", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"))
    private EntityRenderState shapeshift$renderForm(LevelExtractor self, Entity entity, float partialTicks, Operation<EntityRenderState> original) {
        if (entity instanceof AbstractClientPlayer player) {
            EntityRenderState form = FormBodies.extract(player, partialTicks);
            if (form != null) return form;
        }
        return original.call(self, entity, partialTicks);
    }
}
