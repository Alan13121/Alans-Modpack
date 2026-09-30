package dev.alan.mineworld.mixin;

import dev.alan.mineworld.DynamicLevels;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every mine world has its own seed, so the ore layouts differ. */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void mineworld$seed(CallbackInfoReturnable<Long> cir) {
        Long seed = DynamicLevels.seedOf(((ServerLevel) (Object) this).dimension());
        if (seed != null) cir.setReturnValue(seed);
    }
}
