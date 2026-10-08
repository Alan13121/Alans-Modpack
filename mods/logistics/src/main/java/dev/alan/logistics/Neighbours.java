package dev.alan.logistics;

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
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Reaches the containers beside a conduit and moves items in and out of them, honouring the vanilla face rules. */
final class Neighbours {
    /**
     * A container beside a conduit; {@code face} is the side of that container the interface touches. Blocks such
     * as the composter hand out a fresh temporary container on every request ({@code holder}), and it stops accepting
     * items after one change, so they must be asked for again after each item.
     */
    record Target(Container container, Direction face, BlockPos pos, boolean holder) {}

    private Neighbours() {}

    /** Whether a conduit can plug into the block at {@code pos}: a container, but no network part except the storage interface. */
    static boolean connectable(LevelReader level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        if (block instanceof WorldlyContainerHolder) return true;
        if (block instanceof NetworkNode && !(block instanceof StorageInterfaceBlock)) return false;
        return level.getBlockEntity(pos) instanceof Container;
    }

    /** The container on the {@code dir} side of {@code from} as seen from a conduit there, or null. */
    static @Nullable Target at(Level level, BlockPos from, Direction dir) {
        BlockPos next = from.relative(dir);
        if (!level.hasChunk(next.getX() >> 4, next.getZ() >> 4) || !connectable(level, next)) return null;
        BlockState state = level.getBlockState(next);
        if (state.getBlock() instanceof WorldlyContainerHolder holder)
            return new Target(holder.getContainer(state, level, next), dir.getOpposite(), next, true);
        if (state.getBlock() instanceof ChestBlock chest) {
            Container both = ChestBlock.getContainer(chest, state, level, next, true);
            if (both != null) return new Target(both, dir.getOpposite(), next, false);
        }
        return new Target((Container) level.getBlockEntity(next), dir.getOpposite(), next, false);
    }

    /** Pushes into the target; temporary containers (composter) take one item at a time, asked for anew each time. */
    static ItemStack pushAll(Level level, Target target, ItemStack items) {
        if (!target.holder()) return push(target, items);
        ItemStack rest = items.copy();
        while (!rest.isEmpty()) {
            ItemStack left = push(refresh(level, target), rest.copyWithCount(1));
            if (!left.isEmpty()) break;
            rest.shrink(1);
        }
        return rest;
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
     * Slots a machine would hand to a hopper underneath it (a furnace's result, a brewing stand's bottles), so a
     * conduit takes outputs and never a machine's raw ingredients. Plain containers (chests, droppers) give every slot.
     */
    static int[] takeableSlots(Target t) {
        return slotsFor(t.container, Direction.DOWN);
    }

    static boolean canTake(Target t, int slot, ItemStack stack) {
        if (t.container instanceof WorldlyContainer w && !w.canTakeItemThroughFace(slot, stack, Direction.DOWN)) return false;
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
}
