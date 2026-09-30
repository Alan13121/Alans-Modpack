package dev.alan.logistics.mixin;

import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the brewing stand's progress (data 0 is the remaining brew time) so finished batches can be told from running ones. */
@Mixin(BrewingStandBlockEntity.class)
public interface BrewingStandAccessor {
    @Accessor("dataAccess")
    ContainerData logistics$dataAccess();
}
