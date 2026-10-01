package dev.alan.combat;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** What an upgraded bow's arrows do in flight and on a hit. The upgrades travel on the arrow as an attachment. */
public final class BowEffects {
    /** Extra draw ticks gained per tick, per level of draw speed (3 levels = 1.75x as fast). */
    public static final double DRAW_SPEED_PER_LEVEL = 0.25;
    /** A gravity-free arrow that has not hit anything by now is removed, so it cannot fly on forever. */
    public static final int NO_DROP_LIFETIME = 200;

    private BowEffects() {}

    /** Called when an arrow enters the world: copy the bow's upgrades onto it and apply the ones that act at once. */
    public static void onArrowLoaded(AbstractArrow arrow) {
        var weapon = arrow.getWeaponItem();
        if (weapon == null || !weapon.is(Items.BOW)) return;
        Upgrades upgrades = weapon.getOrDefault(CombatMod.BOW_UPGRADES, Upgrades.EMPTY);
        if (upgrades.total() == 0) return;
        arrow.setAttached(CombatMod.ARROW_MODS, upgrades);
        if (upgrades.level(BowUpgrades.Mod.NO_DROP.key) > 0) arrow.setNoGravity(true);
        if (upgrades.level(BowUpgrades.Mod.BURN.key) > 0) arrow.igniteForSeconds(100);
    }

    /**
     * Called after a living entity took damage. If an upgraded arrow did it, the upgrades fire once.
     * They are used up first, because the explosion's own damage is credited to the same arrow.
     */
    public static void onDamaged(LivingEntity target, AbstractArrow arrow, float damageTaken) {
        Upgrades upgrades = arrow.getAttached(CombatMod.ARROW_MODS);
        if (upgrades == null || damageTaken <= 0 || !(target.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        arrow.removeAttached(CombatMod.ARROW_MODS);
        int burn = upgrades.level(BowUpgrades.Mod.BURN.key);
        int slow = upgrades.level(BowUpgrades.Mod.SLOW.key);
        int explode = upgrades.level(BowUpgrades.Mod.EXPLODE.key);
        if (burn > 0) target.igniteForSeconds(3 * burn);
        if (slow > 0) target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60 * slow, slow - 1));
        if (explode > 0) level.explode(arrow, target.getX(), target.getY(0.5), target.getZ(), 1.0f + 0.5f * explode,
            Level.ExplosionInteraction.NONE);
    }
}
