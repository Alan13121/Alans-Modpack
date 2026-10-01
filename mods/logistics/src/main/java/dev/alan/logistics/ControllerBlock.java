package dev.alan.logistics;

import org.jspecify.annotations.Nullable;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The brain of a warehouse network. Exactly one per network; right-click reports the network's state. */
public final class ControllerBlock extends Block implements NetworkNode {
    public ControllerBlock(Properties properties) { super(properties); }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            Warehouse network = Warehouse.at(level, pos);
            long[] items = {0};
            network.forEach((key, count) -> items[0] += count);
            player.sendOverlayMessage(switch (network.status) {
                case OK -> Component.translatable("logistics.controller.ok", network.typeCount(), items[0]);
                case MULTIPLE_CONTROLLERS -> Component.translatable("logistics.status.multiple");
                case MULTIPLE_CHANNELS -> Component.translatable("logistics.status.channels");
                case NO_CONTROLLER -> Component.translatable("logistics.status.none");
            });
        }
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
