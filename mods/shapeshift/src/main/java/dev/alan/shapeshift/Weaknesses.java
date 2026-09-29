package dev.alan.shapeshift;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EquipmentSlot;

/** Server-side weaknesses, checked every tick like the vanilla mobs they come from. */
final class Weaknesses {
    /** Out of water, air drops by this much per tick on top of vanilla's +4 refill: net -1, so 15 s from full. */
    private static final int DRY_AIR_LOSS = 5;
    private Weaknesses() {}

    static void tick(ServerPlayer player, FormDefinitions.Resolved form) {
        if (player.isCreative() || player.isSpectator() || !player.isAlive()) return;
        if (form.has(Weakness.BURNS_IN_DAYLIGHT) && isSunBurnTick(player)) {
            var helmet = player.getItemBySlot(EquipmentSlot.HEAD);
            if (helmet.isEmpty()) player.igniteForSeconds(8);
            else if (helmet.isDamageableItem()) helmet.hurtAndBreak(player.getRandom().nextInt(2), player, EquipmentSlot.HEAD);
        }
        if (form.has(Weakness.WATER_DAMAGE) && player.isInWaterOrRain())
            player.hurtServer(player.level(), player.damageSources().drown(), 1);
        if (form.has(Weakness.NEEDS_WATER) && !player.isInWaterOrRain()) {
            int air = player.getAirSupply() - DRY_AIR_LOSS;
            if (air <= -20) {
                air = 0;
                player.hurtServer(player.level(), player.damageSources().dryOut(), 2);
            }
            player.setAirSupply(air);
        }
    }

    /** Same test zombies use (Mob.isSunBurnTick, including its deprecated brightness call): bright, open sky, dry, and a random roll. */
    @SuppressWarnings("deprecation")
    private static boolean isSunBurnTick(ServerPlayer player) {
        var level = player.level();
        if (!level.environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, player.position())) return false;
        float brightness = player.getLightLevelDependentMagicValue();
        boolean sheltered = player.isInWaterOrRain() || player.isInPowderSnow || player.wasInPowderSnow;
        return brightness > 0.5f && player.getRandom().nextFloat() * 30f < (brightness - 0.4f) * 2f && !sheltered
            && level.canSeeSky(BlockPos.containing(player.getX(), player.getEyeY(), player.getZ()));
    }
}
