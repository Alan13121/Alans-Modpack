package dev.alan.combat.mixin;

import dev.alan.combat.CombatMod;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A small fireball launched by the Form King never sets fire to the block it hits. */
@Mixin(SmallFireball.class)
public abstract class SmallFireballMixin {
    @Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true)
    private void combat$noFire(BlockHitResult result, CallbackInfo ci) {
        if (((SmallFireball) (Object) this).hasAttached(CombatMod.BOSS_SHOT)) ci.cancel();
    }
}
