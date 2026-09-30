package dev.alan.logistics;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Moves items between an interface block and the machines or hoppers touching it (never the warehouse's own chests). */
final class Neighbours {
    /** A container beside an interface; {@code face} is the side of that container the interface touches. */
    record Target(Container container, Direction face) {}

    private Neighbours() {}

    static List<Target> around(Level level, BlockPos pos) {
        List<Target> out = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            BlockPos next = pos.relative(dir);
            if (!level.hasChunkAt(next) || level.getBlockState(next).getBlock() instanceof NetworkNode) continue;
            BlockEntity be = level.getBlockEntity(next);
            if (be instanceof Container c && !Network.isStorage(be)) out.add(new Target(c, dir.getOpposite()));
        }
        return out;
    }

    private static int[] slotsFor(Container c, Direction face) {
        if (c instanceof WorldlyContainer w) return w.getSlotsForFace(face);
        int[] all = new int[c.getContainerSize()];
        for (int i = 0; i < all.length; i++) all[i] = i;
        return all;
    }

    private static boolean accepts(Target t, int slot, ItemStack stack) {
        if (!t.container.canPlaceItem(slot, stack)) return false;
        return !(t.container instanceof WorldlyContainer w) || w.canPlaceItemThroughFace(slot, stack, t.face);
    }

    /** Puts as much of {@code stack} as fits into the target and returns the rest. */
    static ItemStack push(Target t, ItemStack stack) {
        ItemStack rest = stack.copy();
        int[] slots = slotsFor(t.container, t.face);
        for (int slot : slots) {
            if (rest.isEmpty()) break;
            ItemStack cur = t.container.getItem(slot);
            if (cur.isEmpty() || !ItemStack.isSameItemSameComponents(cur, rest) || !accepts(t, slot, rest)) continue;
            int moved = Math.min(rest.getCount(), t.container.getMaxStackSize(cur) - cur.getCount());
            if (moved > 0) { cur.grow(moved); rest.shrink(moved); t.container.setChanged(); }
        }
        for (int slot : slots) {
            if (rest.isEmpty()) break;
            if (!t.container.getItem(slot).isEmpty() || !accepts(t, slot, rest)) continue;
            int moved = Math.min(rest.getCount(), t.container.getMaxStackSize(rest));
            t.container.setItem(slot, rest.copyWithCount(moved));
            rest.shrink(moved);
            t.container.setChanged();
        }
        return rest;
    }

    /**
     * Slots a machine would hand to a hopper underneath it (a furnace's result, a brewing stand's bottles), so the
     * interface takes outputs and never a machine's raw ingredients. Plain containers are left alone.
     */
    static int[] takeableSlots(Target t) {
        return t.container instanceof WorldlyContainer w ? w.getSlotsForFace(Direction.DOWN) : new int[0];
    }

    static boolean canTake(Target t, int slot, ItemStack stack) {
        return t.container instanceof WorldlyContainer w && w.canTakeItemThroughFace(slot, stack, Direction.DOWN);
    }
}
