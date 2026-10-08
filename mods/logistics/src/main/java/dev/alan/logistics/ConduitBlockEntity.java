package dev.alan.logistics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Per-side settings of a conduit (colour and upgrades; the mode lives in the block state) and the transfer itself:
 * every input side pulls from its container and hands the items to the output sides of the same colour that can be
 * reached over connected conduits, taking turns between them.
 */
public final class ConduitBlockEntity extends BlockEntity {
    public static final int BASE_AMOUNT = 4, AMOUNT_PER_QUARTZ = 2, MAX_CONDUITS = 256;
    private static final Direction[] DIRS = Direction.values();

    private final int[] colors = new int[6];
    private final int[] redstone = new int[6];
    private final int[] quartz = new int[6];
    /** Where the next hand-out starts among the outputs; not saved. */
    private int cursor;

    public ConduitBlockEntity(BlockPos pos, BlockState state) {
        super(LogisticsMod.CONDUIT_ENTITY, pos, state);
    }

    public int color(Direction side) { return colors[side.ordinal()]; }
    public void setColor(Direction side, int color) { colors[side.ordinal()] = color; setChanged(); }
    public int interval(Direction side) { return UpgradeSlots.intervalFor(redstone[side.ordinal()]); }
    public int amount(Direction side) { return BASE_AMOUNT + Math.min(quartz[side.ordinal()], UpgradeSlots.MAX_EFFECT) * AMOUNT_PER_QUARTZ; }

    /** Adds one redstone (faster) or quartz (more per trip) to a side; false when it is already at the maximum. */
    public boolean addUpgrade(Direction side, boolean speed) {
        int[] counts = speed ? redstone : quartz;
        if (counts[side.ordinal()] >= UpgradeSlots.MAX_EFFECT) return false;
        counts[side.ordinal()]++;
        setChanged();
        return true;
    }

    /** Drops the upgrades of one side next to the conduit. */
    public boolean takeUpgrades(Direction side, Level level, BlockPos pos) {
        int i = side.ordinal();
        if (redstone[i] == 0 && quartz[i] == 0) return false;
        if (redstone[i] > 0) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(Items.REDSTONE, redstone[i]));
        if (quartz[i] > 0) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(Items.QUARTZ, quartz[i]));
        redstone[i] = 0;
        quartz[i] = 0;
        setChanged();
        return true;
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) for (Direction dir : DIRS) takeUpgrades(dir, level, pos);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ConduitBlockEntity self) {
        for (Direction dir : DIRS) {
            if (state.getValue(ConduitBlock.SIDES.get(dir)) != ConduitBlock.Side.IN) continue;
            if (Math.floorMod(level.getGameTime() + pos.hashCode() + dir.ordinal(), self.interval(dir)) != 0) continue;
            self.pull(level, pos, dir);
        }
    }

    private void pull(Level level, BlockPos pos, Direction dir) {
        Neighbours.Target source = Neighbours.at(level, pos, dir);
        if (source == null) return;
        List<Neighbours.Target> outputs = outputs(level, pos, dir, source.pos());
        if (outputs.isEmpty()) return;
        int budget = amount(dir);
        for (int slot : Neighbours.takeableSlots(source)) {
            if (budget <= 0) return;
            ItemStack stack = source.container().getItem(slot);
            if (stack.isEmpty() || !Neighbours.canTake(source, slot, stack)) continue;
            int offered = Math.min(stack.getCount(), budget);
            int moved = 0, start = cursor;
            for (int k = 0; k < outputs.size() && moved < offered; k++) {
                int index = Math.floorMod(start + k, outputs.size());
                ItemStack rest = Neighbours.pushAll(level, outputs.get(index), stack.copyWithCount(offered - moved));
                int did = offered - moved - rest.getCount();
                if (did > 0) {
                    moved += did;
                    cursor = index + 1;
                }
            }
            if (moved <= 0) continue;
            stack.shrink(moved);
            if (stack.isEmpty()) source.container().setItem(slot, ItemStack.EMPTY);
            source.container().setChanged();
            budget -= moved;
        }
    }

    /** The containers behind output sides of this input's colour, on every conduit connected to this one. */
    private List<Neighbours.Target> outputs(Level level, BlockPos start, Direction inputSide, BlockPos sourcePos) {
        int color = color(inputSide);
        List<Neighbours.Target> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            BlockState state = level.getBlockState(at);
            if (!(state.getBlock() instanceof ConduitBlock) || !(level.getBlockEntity(at) instanceof ConduitBlockEntity conduit)) continue;
            for (Direction dir : DIRS) {
                ConduitBlock.Side side = state.getValue(ConduitBlock.SIDES.get(dir));
                if (side == ConduitBlock.Side.NONE) continue;
                BlockPos next = at.relative(dir);
                if (!level.hasChunk(next.getX() >> 4, next.getZ() >> 4)) continue;
                if (level.getBlockState(next).getBlock() instanceof ConduitBlock) {
                    if (seen.size() < MAX_CONDUITS && seen.add(next)) queue.add(next);
                } else if (side == ConduitBlock.Side.OUT && conduit.colors[dir.ordinal()] == color) {
                    Neighbours.Target target = Neighbours.at(level, at, dir);
                    if (target != null && !target.pos().equals(sourcePos)) found.add(target);
                }
            }
        }
        return found;
    }

    private static void store(ValueOutput output, String key, int[] values) {
        output.store(key, Codec.INT.listOf(), java.util.Arrays.stream(values).boxed().toList());
    }

    private static void read(ValueInput input, String key, int[] values, int max) {
        List<Integer> saved = input.read(key, Codec.INT.listOf()).orElse(List.of());
        for (int i = 0; i < 6; i++) values[i] = i < saved.size() ? Math.max(0, Math.min(max, saved.get(i))) : 0;
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        store(output, "colors", colors);
        store(output, "redstone", redstone);
        store(output, "quartz", quartz);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        read(input, "colors", colors, 15);
        read(input, "redstone", redstone, UpgradeSlots.MAX_EFFECT);
        read(input, "quartz", quartz, UpgradeSlots.MAX_EFFECT);
    }
}
