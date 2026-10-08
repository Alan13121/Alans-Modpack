package dev.alan.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** The door between the warehouse and the outside world; right-click opens the stock list. */
public final class StorageInterfaceBlock extends NodeEntityBlock {
    public StorageInterfaceBlock(Properties properties) { super(properties); }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageInterfaceBlockEntity(pos, state);
    }

    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, LogisticsMod.INTERFACE_ENTITY, StorageInterfaceBlockEntity::serverTick);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof StorageInterfaceBlockEntity be)
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new InterfaceMenu(id, inv, LogisticsMod.INTERFACE_MENU, be.filter(), be.levels(), null, be.upgrades(),
                ContainerLevelAccess.create(level, pos), LogisticsMod.STORAGE_INTERFACE), Component.translatable("block.logistics.storage_interface")));
        return InteractionResult.SUCCESS;
    }
}
