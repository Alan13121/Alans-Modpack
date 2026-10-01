package dev.alan.combat.mixin;

import dev.alan.combat.BowEffects;
import dev.alan.combat.CombatMod;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Arrows from a "no drop" bow ignore gravity, so give them a lifetime. Only arrows carrying our upgrades are touched. */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void combat$limitLifetime(CallbackInfo ci) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (!arrow.level().isClientSide() && arrow.isNoGravity() && arrow.tickCount > BowEffects.NO_DROP_LIFETIME
            && arrow.hasAttached(CombatMod.ARROW_MODS)) arrow.discard();
    }
}
