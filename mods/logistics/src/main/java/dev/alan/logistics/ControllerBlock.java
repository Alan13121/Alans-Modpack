package dev.alan.logistics;

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
            Network network = Network.scan(level, pos);
            long items = 0;
            for (long n : network.contents().values()) items += n;
            player.sendOverlayMessage(switch (network.status) {
                case OK -> Component.translatable("logistics.controller.ok", network.contents().size(), items);
                case MULTIPLE_CONTROLLERS -> Component.translatable("logistics.status.multiple");
                case NO_CONTROLLER -> Component.translatable("logistics.status.none");
            });
        }
        return InteractionResult.SUCCESS;
    }
}
