package dev.alan.shapeshift.mixin;

import dev.alan.shapeshift.Forms;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives a shapeshifted player the hitbox and eye height of their form, in every pose except sleeping and dying. */
@Mixin(Avatar.class)
abstract class AvatarMixin {
    @Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
    private void shapeshift$formDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (pose == Pose.SLEEPING || pose == Pose.DYING) return;
        if ((Object) this instanceof Player player)
            Forms.current(player).ifPresent(type -> cir.setReturnValue(Forms.dimensions(type)));
    }
}
