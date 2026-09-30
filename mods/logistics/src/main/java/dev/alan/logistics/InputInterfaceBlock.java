package dev.alan.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class InputInterfaceBlock extends BaseEntityBlock implements NetworkNode {
    public InputInterfaceBlock(Properties properties) { super(properties); }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InputInterfaceBlockEntity(pos, state);
    }

    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, LogisticsMod.INPUT_ENTITY, InputInterfaceBlockEntity::serverTick);
    }
}
