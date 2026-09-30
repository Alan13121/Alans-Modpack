package dev.alan.logistics;

import org.jspecify.annotations.Nullable;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.Level;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Links nodes together and to the chests beside it; arms show what it is connected to. */
public final class CableBlock extends Block implements NetworkNode {
    public static final Map<Direction, BooleanProperty> ARMS = Map.of(
        Direction.NORTH, BlockStateProperties.NORTH, Direction.EAST, BlockStateProperties.EAST,
        Direction.SOUTH, BlockStateProperties.SOUTH, Direction.WEST, BlockStateProperties.WEST,
        Direction.UP, BlockStateProperties.UP, Direction.DOWN, BlockStateProperties.DOWN);

    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> ARM_SHAPES = Map.of(
        Direction.NORTH, Block.box(5, 5, 0, 11, 11, 5), Direction.SOUTH, Block.box(5, 5, 11, 11, 11, 16),
        Direction.WEST, Block.box(0, 5, 5, 5, 11, 11), Direction.EAST, Block.box(11, 5, 5, 16, 11, 11),
        Direction.DOWN, Block.box(5, 0, 5, 11, 5, 11), Direction.UP, Block.box(5, 11, 5, 11, 16, 11));

    public CableBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty arm : ARMS.values()) state = state.setValue(arm, false);
        registerDefaultState(state);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        for (BooleanProperty arm : ARMS.values()) builder.add(arm);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction dir : Direction.values()) {
            BlockPos next = context.getClickedPos().relative(dir);
            state = state.setValue(ARMS.get(dir), connectsTo(context.getLevel(), next));
        }
        return state;
    }

    static boolean connectsTo(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof NetworkNode || Network.isStorage(level.getBlockEntity(pos));
    }

    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                               Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (level instanceof Level l) Network.invalidate(l, pos);
        return state.setValue(ARMS.get(direction), connectsTo(level, neighbourPos));
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        for (Direction dir : Direction.values())
            if (state.getValue(ARMS.get(dir))) shape = Shapes.or(shape, ARM_SHAPES.get(dir));
        return shape;
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
