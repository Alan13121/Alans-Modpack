package dev.alan.logistics;

import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.phys.BlockHitResult;
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

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof InputInterfaceBlockEntity be)
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new InterfaceMenu(id, inv, LogisticsMod.INPUT_MENU, null, null, null, be.upgrades(),
                ContainerLevelAccess.create(level, pos), LogisticsMod.INPUT_INTERFACE), Component.translatable("block.logistics.input_interface")));
        return InteractionResult.SUCCESS;
    }

    // A change next to (or in) the network makes the cached layout stale.
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        Network.invalidate(level, pos);
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                             @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        Network.invalidate(level, pos);
    }

    @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Network.invalidate(level, pos);
    }
}
