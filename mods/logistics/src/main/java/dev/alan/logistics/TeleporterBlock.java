package dev.alan.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** A teleporter pad: a network node. While OPEN it shows a beam and sends whoever steps on it to the chosen target. */
public final class TeleporterBlock extends NodeEntityBlock {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    public TeleporterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(OPEN); }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TeleporterBlockEntity(pos, state); }

    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, LogisticsMod.TELEPORTER_ENTITY, TeleporterBlockEntity::serverTick);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TeleporterBlockEntity be)
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> TeleporterMenu.server(id, inv, be, ContainerLevelAccess.create(level, pos)),
                Component.translatable("block.logistics.teleporter")));
        return InteractionResult.SUCCESS;
    }
}
