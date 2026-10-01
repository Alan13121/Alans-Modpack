package dev.alan.combat;

import dev.alan.shapeshift.api.SkillHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Items;

/**
 * Ties gear to shapeshift abilities. Only touched when the shapeshift mod is installed, so nothing here is
 * loaded otherwise.
 */
final class ShapeshiftLink {
    private ShapeshiftLink() {}

    static void register() {
        SkillHooks.registerScaler(player ->
            new SkillHooks.Scale(SkillMath.power(emeraldLevels(player)), SkillMath.cooldown(Trinkets.wornSkillFocus(player))));
        // The skeleton's free arrow carries the upgrades of the bow in hand, as if it had been shot from it.
        SkillHooks.registerLaunch((player, projectile) -> {
            if (!(projectile instanceof AbstractArrow arrow)) return;
            for (InteractionHand hand : InteractionHand.values()) {
                var held = player.getItemInHand(hand);
                if (held.is(Items.BOW)) {
                    BowEffects.attach(arrow, held.getOrDefault(CombatMod.BOW_UPGRADES, Upgrades.EMPTY));
                    return;
                }
            }
        });
    }

    /** Emerald upgrade levels over all worn armor pieces. */
    static int emeraldLevels(Player player) {
        int levels = 0;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            levels += player.getItemBySlot(slot).getOrDefault(CombatMod.UPGRADES, Upgrades.EMPTY).level(Ore.EMERALD.key);
        return levels;
    }
}
