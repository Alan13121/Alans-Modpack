package dev.alan.logistics;

import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Virtual storage. Each tier holds twice the item types and four times the amount per type of the one below. */
public final class CellBlock extends BaseEntityBlock implements NetworkNode {
    public static final int TIERS = 4;

    private final int tier;

    public CellBlock(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public int tier() { return tier; }
    public int maxTypes() { return maxTypes(tier); }
    public int maxPerType() { return maxPerType(tier); }

    public static int maxTypes(int tier) { return 64 << (tier - 1); }
    public static int maxPerType(int tier) { return 10_000 << (2 * (tier - 1)); }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CellBlockEntity(pos, state);
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
