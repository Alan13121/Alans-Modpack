package dev.alan.logistics;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.level.block.state.BlockState;
import dev.alan.logistics.mixin.BrewingStandAccessor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Moves items between an interface block and the machines or hoppers touching it (never the warehouse's own chests). */
final class Neighbours {
    /**
     * A container beside an interface; {@code face} is the side of that container the interface touches. Blocks such
     * as the composter hand out a fresh temporary container on every request ({@code holder}), and it stops accepting
     * items after one change, so they must be asked for again after each item.
     */
    record Target(Container container, Direction face, BlockPos pos, boolean holder) {}

    private Neighbours() {}

    static List<Target> around(Level level, BlockPos pos) {
        List<Target> out = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            BlockPos next = pos.relative(dir);
            if (!level.hasChunkAt(next) || level.getBlockState(next).getBlock() instanceof NetworkNode) continue;
            BlockState state = level.getBlockState(next);
            if (state.getBlock() instanceof WorldlyContainerHolder holder) {
                out.add(new Target(holder.getContainer(state, level, next), dir.getOpposite(), next, true));
                continue;
            }
            BlockEntity be = level.getBlockEntity(next);
            if (be instanceof Container c && !Network.isStorage(be)) out.add(new Target(c, dir.getOpposite(), next, false));
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
        if (!(t.container instanceof WorldlyContainer w) || !w.canTakeItemThroughFace(slot, stack, Direction.DOWN)) return false;
        return !(t.container instanceof BrewingStandBlockEntity stand) || brewedPotionReady(stand, slot, stack);
    }

    /**
     * A brewing stand's potion slots are only emptied when the stand is idle and the ingredient in it (if any) can no
     * longer change that potion, so a finished batch is collected while a batch that still has a reaction waiting is
     * left alone. Plain water bottles are never taken (they are the raw material).
     */
    private static boolean brewedPotionReady(BrewingStandBlockEntity stand, int slot, ItemStack stack) {
        if (slot > 2) return false;
        if (((BrewingStandAccessor) stand).logistics$dataAccess().get(BrewingStandBlockEntity.DATA_BREW_TIME) > 0) return false;
        if (stand.getLevel() instanceof ServerLevel level && !stand.getItem(3).isEmpty()
            && level.getServer().getRecipeManager().getRecipeFor(RecipeType.BREWING, new BrewingInput(stack, stand.getItem(3)), level).isPresent()) return false;
        var contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents == null || !contents.is(Potions.WATER);
    }

    /** A fresh copy of a temporary container (composter), or the same container for a normal block entity. */
    static Target refresh(Level level, Target t) {
        if (!t.holder) return t;
        BlockState state = level.getBlockState(t.pos);
        if (!(state.getBlock() instanceof WorldlyContainerHolder holder)) return t;
        return new Target(holder.getContainer(state, level, t.pos), t.face, t.pos, true);
    }

    /** Whether any slot of the target would take some of this item right now. */
    static boolean canAccept(Target t, ItemStack stack) {
        for (int slot : slotsFor(t.container, t.face)) {
            if (!accepts(t, slot, stack)) continue;
            ItemStack cur = t.container.getItem(slot);
            if (cur.isEmpty() || (ItemStack.isSameItemSameComponents(cur, stack) && cur.getCount() < t.container.getMaxStackSize(cur))) return true;
        }
        return false;
    }
}
