package dev.alan.shapeshift.mixin;

import dev.alan.shapeshift.Ability;
import dev.alan.shapeshift.FormDefinitions;
import dev.alan.shapeshift.Forms;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets climbing forms treat any wall they push against like a ladder. Runs on both sides so movement agrees. */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void shapeshift$climbWalls(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Player player) || !player.horizontalCollision || player.isSpectator()) return;
        Forms.current(player).ifPresent(type -> {
            if (FormDefinitions.resolve(type, player.level().isClientSide()).has(Ability.CLIMB)) cir.setReturnValue(true);
        });
    }
}
