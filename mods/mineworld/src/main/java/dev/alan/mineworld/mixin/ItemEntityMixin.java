package dev.alan.mineworld.mixin;

import dev.alan.mineworld.CauldronRecipe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void mineworld$cauldron(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.level() instanceof ServerLevel level && !self.isRemoved() && self.tickCount % 4 == 0) {
            CauldronRecipe.tryAccept(level, self);
        }
    }
}
