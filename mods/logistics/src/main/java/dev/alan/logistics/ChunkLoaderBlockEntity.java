package dev.alan.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Forces its chunks on its first tick (and again now and then, in case someone un-forced them) and releases them when removed. */
public final class ChunkLoaderBlockEntity extends BlockEntity {
    private static final int REAPPLY_TICKS = 600;

    public ChunkLoaderBlockEntity(BlockPos pos, BlockState state) { super(LogisticsMod.LOADER_ENTITY, pos, state); }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChunkLoaderBlockEntity self) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        boolean unregistered = level.getGameTime() % 20 == 0
            && !TransmissionData.get(serverLevel.getServer()).hasLoader(net.minecraft.core.GlobalPos.of(level.dimension(), pos));
        if (unregistered || (level.getGameTime() + pos.hashCode()) % REAPPLY_TICKS == 0) ChunkLoaders.apply(serverLevel, pos);
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) ChunkLoaders.release(serverLevel, pos);
    }
}
