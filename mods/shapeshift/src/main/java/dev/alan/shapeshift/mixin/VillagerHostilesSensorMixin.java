package dev.alan.shapeshift.mixin;

import dev.alan.shapeshift.Reactions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Villagers panic at players whose form scares them (zombie forms by default), the same way they panic at zombies. */
@Mixin(VillagerHostilesSensor.class)
abstract class VillagerHostilesSensorMixin {
    private static final double PANIC_DISTANCE = 8;

    @Inject(method = "isMatchingEntity", at = @At("HEAD"), cancellable = true)
    private void shapeshift$scaryForms(ServerLevel level, LivingEntity body, LivingEntity mob, CallbackInfoReturnable<Boolean> cir) {
        if (mob instanceof Player player && body instanceof Mob villager && !player.isCreative() && !player.isSpectator()
            && Reactions.scares(villager, player))
            cir.setReturnValue(player.distanceToSqr(body) <= PANIC_DISTANCE * PANIC_DISTANCE);
    }
}
