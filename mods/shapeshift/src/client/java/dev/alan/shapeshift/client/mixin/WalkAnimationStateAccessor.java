package dev.alan.shapeshift.client.mixin;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WalkAnimationState.class)
public interface WalkAnimationStateAccessor {
    @Accessor("speedOld") float shapeshift$getSpeedOld();
    @Accessor("speedOld") void shapeshift$setSpeedOld(float value);
    @Accessor("speed") float shapeshift$getSpeed();
    @Accessor("speed") void shapeshift$setSpeed(float value);
    @Accessor("position") float shapeshift$getPosition();
    @Accessor("position") void shapeshift$setPosition(float value);
    @Accessor("positionScale") float shapeshift$getPositionScale();
    @Accessor("positionScale") void shapeshift$setPositionScale(float value);
}
