package dev.alan.logistics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/**
 * One connected warehouse: every {@link NetworkNode} reachable through face-adjacent nodes, plus the vanilla
 * containers touching any of them and the storage cells among them. Rebuilt on demand, so it never goes stale.
 */
public final class Network {
    public enum Status { OK, NO_CONTROLLER, MULTIPLE_CONTROLLERS }

    /** Hard cap so a runaway cable line can't stall the server. */
    private static final int MAX_NODES = 4096;

    /** Hash key that treats stacks as equal when item and components match. */
    public record Key(ItemStack stack) {
        public Key { stack = stack.copyWithCount(1); }
        @Override public boolean equals(Object o) { return o instanceof Key k && ItemStack.isSameItemSameComponents(stack, k.stack); }
        @Override public int hashCode() { return ItemStack.hashItemAndComponents(stack); }
    }

    public final Status status;
    private final List<Container> containers;
    private final List<CellBlockEntity> cells;

    private Network(Status status, List<Container> containers, List<CellBlockEntity> cells) {
        this.status = status;
        this.containers = containers;
        this.cells = cells;
    }

    public static Network scan(Level level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        Set<BlockPos> containerPositions = new HashSet<>();
        List<Container> containers = new ArrayList<>();
        List<CellBlockEntity> cells = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        int controllers = 0;
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && seen.size() <= MAX_NODES) {
            BlockPos pos = queue.poll();
            if (level.getBlockState(pos).getBlock() instanceof ControllerBlock) controllers++;
            if (level.getBlockEntity(pos) instanceof CellBlockEntity cell) cells.add(cell);
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (!level.hasChunkAt(next)) continue;
                if (level.getBlockState(next).getBlock() instanceof NetworkNode) {
                    if (seen.add(next)) queue.add(next);
                } else if (isStorage(level.getBlockEntity(next)) && containerPositions.add(next)) {
                    containers.add((Container) level.getBlockEntity(next));
                }
            }
        }
        Status status = controllers == 0 ? Status.NO_CONTROLLER : controllers > 1 ? Status.MULTIPLE_CONTROLLERS : Status.OK;
        return new Network(status, containers, cells);
    }

    /** Only plain storage blocks are adopted, so hoppers, furnaces and dispensers are never fed by accident. */
    public static boolean isStorage(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity;
    }

    public boolean usable() { return status == Status.OK; }

    /** Every distinct item in the warehouse with its total count. */
    public Map<Key, Long> contents() {
        Map<Key, Long> map = new HashMap<>();
        for (Container c : containers)
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty()) map.merge(new Key(s), (long) s.getCount(), Long::sum);
            }
        for (CellBlockEntity cell : cells) cell.forEach((t, n) -> map.merge(new Key(t), (long) n, Long::sum));
        return map;
    }

    /** Takes up to {@code amount} of {@code template} out of the warehouse. */
    public ItemStack extract(ItemStack template, int amount) {
        ItemStack out = template.copyWithCount(0);
        for (Container c : containers) {
            for (int i = 0; i < c.getContainerSize() && out.getCount() < amount; i++) {
                ItemStack s = c.getItem(i);
                if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, template)) continue;
                int moved = Math.min(s.getCount(), amount - out.getCount());
                out.grow(moved);
                s.shrink(moved);
                if (s.isEmpty()) c.setItem(i, ItemStack.EMPTY);
                c.setChanged();
            }
        }
        for (CellBlockEntity cell : cells)
            if (out.getCount() < amount) out.grow(cell.extract(template, amount - out.getCount()));
        return out;
    }

    /**
     * Stores {@code stack} and returns what did not fit. Items go where the same item already lives first,
     * then into empty chest slots, and only then start a new entry in a cell.
     */
    public ItemStack insert(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (Container c : containers) mergeInto(c, rest);
        for (CellBlockEntity cell : cells) if (!rest.isEmpty() && cell.count(rest) > 0) cell.insert(rest, false);
        for (Container c : containers) fillEmpty(c, rest);
        for (CellBlockEntity cell : cells) cell.insert(rest, true);
        return rest;
    }

    private static void mergeInto(Container c, ItemStack rest) {
        for (int i = 0; i < c.getContainerSize() && !rest.isEmpty(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, rest)) continue;
            int room = Math.min(s.getMaxStackSize(), c.getMaxStackSize(s)) - s.getCount();
            int moved = Math.min(room, rest.getCount());
            if (moved > 0) { s.grow(moved); rest.shrink(moved); c.setChanged(); }
        }
    }

    private static void fillEmpty(Container c, ItemStack rest) {
        for (int i = 0; i < c.getContainerSize() && !rest.isEmpty(); i++) {
            if (!c.getItem(i).isEmpty() || !c.canPlaceItem(i, rest)) continue;
            int moved = Math.min(rest.getCount(), Math.min(rest.getMaxStackSize(), c.getMaxStackSize(rest)));
            if (moved > 0) { c.setItem(i, rest.copyWithCount(moved)); rest.shrink(moved); c.setChanged(); }
        }
    }
}
