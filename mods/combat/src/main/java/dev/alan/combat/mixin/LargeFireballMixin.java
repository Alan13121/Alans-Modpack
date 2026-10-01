package dev.alan.combat.mixin;

import dev.alan.combat.CombatMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A big fireball launched by the Form King explodes without touching blocks, whatever the mob griefing rule says. */
@Mixin(LargeFireball.class)
public abstract class LargeFireballMixin {
    /** Blast power of the boss's fireball. */
    private static final float POWER = 1.5f;

    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void combat$harmlessToBlocks(HitResult result, CallbackInfo ci) {
        LargeFireball ball = (LargeFireball) (Object) this;
        if (!ball.hasAttached(CombatMod.BOSS_SHOT)) return;
        if (ball.level() instanceof ServerLevel level) {
            level.explode(ball, ball.getX(), ball.getY(), ball.getZ(), POWER, Level.ExplosionInteraction.NONE);
            ball.discard();
        }
        ci.cancel();
    }
}
