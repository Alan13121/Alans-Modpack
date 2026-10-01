package dev.alan.combat.mixin;

import dev.alan.combat.BowEffects;
import dev.alan.combat.BowUpgrades;
import dev.alan.combat.CombatMod;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.alan.combat.Trinket;
import dev.alan.combat.Trinkets;
import dev.alan.combat.Upgrades;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
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

    @Shadow protected abstract void dropFromLootTable(ServerLevel level, DamageSource source, boolean playerKilled);

    /** Blast ward: explosions hurt a wearer less. */
    @WrapMethod(method = "hurtServer")
    private boolean combat$blastWard(ServerLevel level, DamageSource source, float amount, Operation<Boolean> original) {
        if ((Object) this instanceof Player player && source.is(DamageTypeTags.IS_EXPLOSION) && Trinkets.has(player, Trinket.Perk.BLAST_WARD))
            amount *= Trinkets.BLAST_WARD_FACTOR;
        return original.call(level, source, amount);
    }

    /** Hunter charm: a player's kill sometimes yields its loot a second time. */
    @Inject(method = "dropAllDeathLoot", at = @At("TAIL"))
    private void combat$hunterLoot(ServerLevel level, DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player || !(source.getEntity() instanceof ServerPlayer killer)) return;
        if (self.getRandom().nextFloat() < Trinkets.HUNTER_CHANCE && Trinkets.has(killer, Trinket.Perk.HUNTER))
            dropFromLootTable(level, source, true);
    }

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
