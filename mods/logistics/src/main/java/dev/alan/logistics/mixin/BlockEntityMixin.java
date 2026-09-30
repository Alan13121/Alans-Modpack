package dev.alan.logistics.mixin;

import dev.alan.logistics.ModCounted;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts {@code setChanged} calls so the warehouse can tell which containers need to be read again, instead of
 * reading every slot of every chest whenever anything is asked.
 */
@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin implements ModCounted {
    @Unique private int logistics$modCount;

    @Inject(method = "setChanged()V", at = @At("HEAD"))
    private void logistics$count(CallbackInfo ci) { logistics$modCount++; }

    @Override public int logistics$modCount() { return logistics$modCount; }
}
