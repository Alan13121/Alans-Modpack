package dev.alan.shapeshift.client.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Creeper.class)
public interface CreeperAccessor {
    @Accessor("swell") void shapeshift$setSwell(int value);
    @Accessor("oldSwell") void shapeshift$setOldSwell(int value);
    @Accessor("maxSwell") int shapeshift$getMaxSwell();
}
