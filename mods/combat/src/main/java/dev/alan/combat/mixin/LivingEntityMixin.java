package dev.alan.combat.mixin;

import dev.alan.combat.BowEffects;
import dev.alan.combat.BowUpgrades;
import dev.alan.combat.CombatMod;
import dev.alan.combat.Upgrades;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draw speed: while a bow with that upgrade is being drawn, the use timer runs extra ticks. Releasing, the pull-back
 * animation and the arrow power all read the timer, so one change speeds up all of them on both sides.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow protected int useItemRemaining;
    @Shadow public abstract boolean isUsingItem();
    @Shadow public abstract net.minecraft.world.item.ItemStack getUseItem();
    /** Fraction of an extra tick not yet applied. */
    @Unique private double combat$drawCarry;

    @Inject(method = "tick", at = @At("TAIL"))
    private void combat$drawFaster(CallbackInfo ci) {
        var item = isUsingItem() ? getUseItem() : null;
        if (item == null || !item.is(Items.BOW)) {
            combat$drawCarry = 0;
            return;
        }
        int level = item.getOrDefault(CombatMod.BOW_UPGRADES, Upgrades.EMPTY).level(BowUpgrades.Mod.DRAW.key);
        if (level <= 0) return;
        combat$drawCarry += level * BowEffects.DRAW_SPEED_PER_LEVEL;
        while (combat$drawCarry >= 1 && useItemRemaining > 1) {
            combat$drawCarry -= 1;
            useItemRemaining--;
        }
    }
}
