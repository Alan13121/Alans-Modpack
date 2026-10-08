package dev.alan.logistics;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Carries items between containers, nothing else: it does not join the warehouse network. Every side that touches
 * another conduit or a container grows an arm; the arm to a container is a connector that an empty hand cycles
 * through off, input (takes items out of that container) and output (puts items into it). Connectors of the same
 * colour, anywhere on the same run of conduits, trade with each other.
 */
public final class ConduitBlock extends BaseEntityBlock {
    /** What one side of the conduit is doing; also picks the arm's model. */
    public enum Side implements StringRepresentable {
        NONE("none"), LINK("link"), IN("in"), OUT("out");

        private final String name;
        Side(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }

    public static final Map<Direction, EnumProperty<Side>> SIDES = new EnumMap<>(Direction.class);
    static {
        for (Direction dir : Direction.values()) SIDES.put(dir, EnumProperty.create(dir.getName(), Side.class));
    }

    private static final VoxelShape CORE = net.minecraft.world.level.block.Block.box(5, 5, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> ARM_SHAPES = Map.of(
        Direction.NORTH, net.minecraft.world.level.block.Block.box(5, 5, 0, 11, 11, 5), Direction.SOUTH, net.minecraft.world.level.block.Block.box(5, 5, 11, 11, 11, 16),
        Direction.WEST, net.minecraft.world.level.block.Block.box(0, 5, 5, 5, 11, 11), Direction.EAST, net.minecraft.world.level.block.Block.box(11, 5, 5, 16, 11, 11),
        Direction.DOWN, net.minecraft.world.level.block.Block.box(5, 0, 5, 11, 5, 11), Direction.UP, net.minecraft.world.level.block.Block.box(5, 11, 5, 11, 16, 11));

    public ConduitBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (EnumProperty<Side> side : SIDES.values()) state = state.setValue(side, Side.NONE);
        registerDefaultState(state);
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        for (EnumProperty<Side> side : SIDES.values()) builder.add(side);
    }

    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ConduitBlockEntity(pos, state); }

    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, LogisticsMod.CONDUIT_ENTITY, ConduitBlockEntity::serverTick);
    }

    static boolean connectsTo(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof ConduitBlock || Neighbours.connectable(level, pos);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction dir : Direction.values())
            state = state.setValue(SIDES.get(dir), connectsTo(context.getLevel(), context.getClickedPos().relative(dir)) ? Side.LINK : Side.NONE);
        return state;
    }

    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                               Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        EnumProperty<Side> property = SIDES.get(direction);
        if (!connectsTo(level, neighbourPos)) return state.setValue(property, Side.NONE);
        // A run of conduits has no modes; a side that just got connected to a container starts switched off.
        if (neighbourState.getBlock() instanceof ConduitBlock || state.getValue(property) == Side.NONE) return state.setValue(property, Side.LINK);
        return state;
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        for (Direction dir : Direction.values())
            if (state.getValue(SIDES.get(dir)) != Side.NONE) shape = Shapes.or(shape, ARM_SHAPES.get(dir));
        return shape;
    }

    /** The side of the conduit the player aimed at: the arm when the hit is out in one, else the face that was hit. */
    static Direction sideAt(BlockPos pos, BlockHitResult hit) {
        Vec3 local = hit.getLocation().subtract(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        double ax = Math.abs(local.x), ay = Math.abs(local.y), az = Math.abs(local.z);
        double max = Math.max(ax, Math.max(ay, az));
        if (max <= 0.1875 + 1.0E-4) return hit.getDirection();
        if (max == ax) return local.x > 0 ? Direction.EAST : Direction.WEST;
        if (max == ay) return local.y > 0 ? Direction.UP : Direction.DOWN;
        return local.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                    InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        DyeColor dye = stack.get(DataComponents.DYE);
        boolean upgrade = stack.is(Items.REDSTONE) || stack.is(Items.QUARTZ);
        if (dye == null && !upgrade) return InteractionResult.PASS;
        Direction side = sideAt(pos, hit);
        if (!isConnector(level, state, pos, side)) return InteractionResult.PASS;
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof ConduitBlockEntity be)) return InteractionResult.SUCCESS;
        if (dye != null) {
            if (be.color(side) == dye.getId()) return InteractionResult.SUCCESS;
            be.setColor(side, dye.getId());
        } else if (!be.addUpgrade(side, stack.is(Items.REDSTONE))) {
            return InteractionResult.SUCCESS;
        }
        stack.consume(1, player);
        report(player, be, state, side);
        return InteractionResult.SUCCESS;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Direction side = sideAt(pos, hit);
        if (!isConnector(level, state, pos, side)) return InteractionResult.PASS;
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof ConduitBlockEntity be)) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) {
            if (be.takeUpgrades(side, level, pos)) player.sendOverlayMessage(Component.translatable("logistics.conduit.refund", faceName(side)));
            return InteractionResult.SUCCESS;
        }
        Side next = switch (state.getValue(SIDES.get(side))) {
            case LINK -> Side.IN;
            case IN -> Side.OUT;
            default -> Side.LINK;
        };
        level.setBlock(pos, state.setValue(SIDES.get(side), next), 3);
        be.setChanged();
        report(player, be, level.getBlockState(pos), side);
        return InteractionResult.SUCCESS;
    }

    /** A side with an arm that leads into a container (not into another conduit) can be configured. */
    private static boolean isConnector(Level level, BlockState state, BlockPos pos, Direction side) {
        return state.getValue(SIDES.get(side)) != Side.NONE && !(level.getBlockState(pos.relative(side)).getBlock() instanceof ConduitBlock);
    }

    static Component faceName(Direction side) { return Component.translatable("logistics.conduit.face." + side.getName()); }

    private static void report(Player player, ConduitBlockEntity be, BlockState state, Direction side) {
        String mode = state.getValue(SIDES.get(side)).getSerializedName();
        player.sendOverlayMessage(Component.translatable("logistics.conduit.status", faceName(side),
            Component.translatable("logistics.conduit.mode." + (mode.equals("link") ? "none" : mode)),
            Component.translatable("color.minecraft." + DyeColor.byId(be.color(side)).getName())));
    }
}
