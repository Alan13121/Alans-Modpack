package dev.alan.mineworld.mixin;

import dev.alan.mineworld.DynamicLevels;
import dev.alan.mineworld.city.City;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Beds in a monster city (the inn) set the respawn point instead of exploding like everywhere else in a mine world.
 * Sleeping is still impossible (the time is fixed). The same rule is checked again when the player respawns.
 */
@Mixin(AbstractBedBlock.class)
abstract class AbstractBedBlockMixin {
    @Unique private static final BedRule INN = new BedRule(BedRule.Rule.NEVER, BedRule.Rule.ALWAYS, false, false,
        Optional.of(Component.translatable("mineworld.inn.spawn_set")));

    @Inject(method = "getBedRule", at = @At("HEAD"), cancellable = true)
    private void mineworld$innRule(Level level, BlockPos pos, CallbackInfoReturnable<BedRule> cir) {
        if (level instanceof ServerLevel server && DynamicLevels.worldId(server) != null && City.contains(server, pos)) {
            cir.setReturnValue(INN);
        }
    }
}
