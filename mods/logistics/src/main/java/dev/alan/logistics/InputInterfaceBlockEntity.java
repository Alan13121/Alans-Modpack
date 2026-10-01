package dev.alan.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Accepts items from hoppers and droppers into a small buffer and files them into the warehouse. It also empties the
 * outputs of machines touching it (furnace results and the like).
 */
public final class InputInterfaceBlockEntity extends BlockEntity implements WorldlyContainer {
    private static final int SIZE = 9;
    private static final int[] ALL_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};

    private final NonNullList<ItemStack> buffer = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final UpgradeSlots upgrades = new UpgradeSlots(this::setChanged);

    public UpgradeSlots upgrades() { return upgrades; }

    public InputInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(LogisticsMod.INPUT_ENTITY, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, InputInterfaceBlockEntity self) {
        if ((level.getGameTime() + pos.hashCode()) % self.upgrades.interval() != 0) return;
        self.pulse(level, pos);
    }

    private void pulse(Level level, BlockPos pos) {
        Warehouse network = null;
        int budget = upgrades.amount();
        for (int i = 0; i < SIZE && budget > 0; i++) {
            ItemStack stack = buffer.get(i);
            if (stack.isEmpty()) continue;
            if (network == null) network = Warehouse.at(level, pos);
            if (!network.usable()) return;
            int offered = Math.min(stack.getCount(), budget);
            ItemStack rest = network.insert(stack.copyWithCount(offered));
            int moved = offered - rest.getCount();
            stack.shrink(moved);
            if (stack.isEmpty()) buffer.set(i, ItemStack.EMPTY);
            budget -= moved;
            setChanged();
        }
        for (Neighbours.Target t : Neighbours.around(level, pos)) {
            for (int slot : Neighbours.takeableSlots(t)) {
                ItemStack stack = t.container().getItem(slot);
                if (budget <= 0) return;
                if (stack.isEmpty() || !Neighbours.canTake(t, slot, stack)) continue;
                if (network == null) network = Warehouse.at(level, pos);
                if (!network.usable()) return;
                int offered = Math.min(stack.getCount(), budget);
                int moved = offered - network.insert(stack.copyWithCount(offered)).getCount();
                if (moved <= 0) continue;
                stack.shrink(moved);
                if (stack.isEmpty()) t.container().setItem(slot, ItemStack.EMPTY);
                t.container().setChanged();
                budget -= moved;
            }
        }
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, buffer, false);
        upgrades.save(output);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        buffer.clear();
        for (int i = 0; i < SIZE; i++) buffer.set(i, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, buffer);
        upgrades.load(input);
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) net.minecraft.world.Containers.dropContents(level, pos, upgrades);
    }

    // WorldlyContainer: hoppers and droppers feed the buffer from any side; nothing can take items back out.
    @Override public int[] getSlotsForFace(Direction direction) { return ALL_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) { return true; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) { return false; }
    @Override public int getContainerSize() { return SIZE; }
    @Override public boolean isEmpty() { return buffer.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return buffer.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack out = ContainerHelper.removeItem(buffer, slot, count);
        if (!out.isEmpty()) setChanged();
        return out;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(buffer, slot); }
    @Override public void setItem(int slot, ItemStack stack) { buffer.set(slot, stack); setChanged(); }
    @Override public boolean stillValid(Player player) { return net.minecraft.world.Container.stillValidBlockEntity(this, player); }
    @Override public void clearContent() { buffer.clear(); for (int i = 0; i < SIZE; i++) buffer.set(i, ItemStack.EMPTY); }
}
