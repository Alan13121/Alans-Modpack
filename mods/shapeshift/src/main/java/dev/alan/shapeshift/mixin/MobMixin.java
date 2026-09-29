package dev.alan.shapeshift.mixin;

import dev.alan.shapeshift.Reactions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mobs consult canAttack both when choosing a target and every time they read their current one,
 * so returning false here makes them ignore a friendly shapeshifted player and drop them if already chasing.
 */
@Mixin(Mob.class)
abstract class MobMixin {
    @Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
    private void shapeshift$ignoreFriendlyForms(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        Mob self = (Mob) (Object) this;
        if (target instanceof Player player && !self.level().isClientSide() && Reactions.friendly(self, player))
            cir.setReturnValue(false);
    }
}
