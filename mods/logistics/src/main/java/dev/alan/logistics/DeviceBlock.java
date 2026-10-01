package dev.alan.logistics;

import java.util.function.Supplier;
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

/**
 * Channel block, antenna and generators: a network node with a block entity that opens a {@link DeviceMenu}
 * (solar panels have none and only report their energy).
 */
public final class DeviceBlock extends NodeEntityBlock {
    private final DeviceMenu.Kind kind;
    private final Supplier<BlockEntityType<? extends BlockEntity>> type;
    private final @Nullable Ticker ticker;

    /** Erased ticker so one block class serves every device. */
    public interface Ticker { void tick(Level level, BlockPos pos, BlockState state, BlockEntity entity); }

    public DeviceBlock(Properties properties, DeviceMenu.@Nullable Kind kind, Supplier<BlockEntityType<? extends BlockEntity>> type, @Nullable Ticker ticker) {
        super(properties);
        this.kind = kind;
        this.type = type;
        this.ticker = ticker;
    }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return type.get().create(pos, state); }

    @SuppressWarnings("unchecked")
    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> t) {
        if (level.isClientSide() || ticker == null || t != type.get()) return null;
        return (lvl, pos, st, be) -> ticker.tick(lvl, pos, st, be);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return InteractionResult.PASS;
        if (kind == null) {
            long energy = Energy.availableAt(level, pos);
            player.sendOverlayMessage(energy < 0 ? Component.translatable("logistics.energy.none") : Component.translatable("logistics.energy", energy));
        } else {
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> DeviceMenu.server(id, inv, kind, be, ContainerLevelAccess.create(level, pos), this),
                getName()));
        }
        return InteractionResult.SUCCESS;
    }
}
