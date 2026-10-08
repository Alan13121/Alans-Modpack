package dev.alan.logistics;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The warehouse's door to the world. Whatever is put into it (hoppers, droppers, conduits) goes straight into the
 * warehouse. Whatever is on its stock list is kept ready in the stock slots, where a hopper or conduit can take it.
 */
public final class StorageInterfaceBlockEntity extends BlockEntity implements WorldlyContainer {
    public static final int FILTER_SLOTS = 9;
    /** Stock cap per slot; the stock slots hold one stack each. */
    public static final int MAX_LEVEL = 64;
    private static final int IN = 9, SIZE = IN + FILTER_SLOTS;
    private static final int[] ALL_SLOTS = java.util.stream.IntStream.range(0, SIZE).toArray();

    /** Slots 0..8 take items in (emptied into the warehouse at once), slots 9..17 hold the stock for each filter slot. */
    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    /** Per filter slot: keep this many ready (0 = a full stack). */
    private final int[] levels = new int[FILTER_SLOTS];
    private final ContainerData levelData = new ContainerData() {
        @Override public int get(int index) { return levels[index]; }
        @Override public void set(int index, int value) {
            levels[index] = Math.max(0, Math.min(MAX_LEVEL, value));
            StorageInterfaceBlockEntity.this.setChanged();
        }
        @Override public int getCount() { return FILTER_SLOTS; }
    };
    private final SimpleContainer filter = new SimpleContainer(FILTER_SLOTS) {
        @Override public void setChanged() {
            super.setChanged();
            StorageInterfaceBlockEntity.this.setChanged();
        }
    };
    private final UpgradeSlots upgrades = new UpgradeSlots(this::setChanged);
    private boolean busy;

    public StorageInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(LogisticsMod.INTERFACE_ENTITY, pos, state);
    }

    public SimpleContainer filter() { return filter; }
    public UpgradeSlots upgrades() { return upgrades; }
    public ContainerData levels() { return levelData; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StorageInterfaceBlockEntity self) {
        if ((level.getGameTime() + pos.hashCode()) % self.upgrades.interval() != 0) return;
        self.restock(level, pos);
    }

    /** Every change of the container is a chance to file the incoming items into the warehouse. */
    @Override public void setChanged() {
        super.setChanged();
        if (!busy) fileIncoming();
    }

    private void fileIncoming() {
        if (level == null || level.isClientSide()) return;
        boolean any = false;
        for (int i = 0; i < IN; i++) any |= !items.get(i).isEmpty();
        if (!any) return;
        busy = true;
        try {
            Warehouse warehouse = Warehouse.at(level, worldPosition);
            if (!warehouse.usable()) return;
            for (int i = 0; i < IN; i++) {
                ItemStack stack = items.get(i);
                if (stack.isEmpty()) continue;
                items.set(i, warehouse.insert(stack.copy()));
            }
            super.setChanged();
        } finally {
            busy = false;
        }
    }

    /** Brings each stock slot up to its level from the warehouse; an item that left the list goes back. */
    private void restock(Level level, BlockPos pos) {
        Warehouse warehouse = null;
        int budget = upgrades.amount();
        boolean changed = false;
        busy = true;
        try {
            for (int i = 0; i < FILTER_SLOTS; i++) {
                ItemStack wanted = filter.getItem(i);
                ItemStack held = items.get(IN + i);
                boolean same = !wanted.isEmpty() && ItemStack.isSameItemSameComponents(held, wanted);
                if (!held.isEmpty() && !same) {
                    if (warehouse == null) warehouse = Warehouse.at(level, pos);
                    if (!warehouse.usable()) return;
                    items.set(IN + i, warehouse.insert(held.copy()));
                    changed = true;
                    held = items.get(IN + i);
                    if (!held.isEmpty()) continue;
                }
                if (wanted.isEmpty() || budget <= 0) continue;
                int cap = levels[i] > 0 ? Math.min(levels[i], wanted.getMaxStackSize()) : wanted.getMaxStackSize();
                int want = Math.min(budget, cap - held.getCount());
                if (want <= 0) continue;
                if (warehouse == null) warehouse = Warehouse.at(level, pos);
                if (!warehouse.usable()) return;
                ItemStack taken = warehouse.extract(wanted, want);
                if (taken.isEmpty()) continue;
                if (held.isEmpty()) items.set(IN + i, taken); else held.grow(taken.getCount());
                budget -= taken.getCount();
                changed = true;
            }
        } finally {
            busy = false;
            if (changed) super.setChanged();
        }
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) return;
        Containers.dropContents(level, pos, upgrades);
        Containers.dropContents(level, pos, items);
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        upgrades.save(output);
        ContainerHelper.saveAllItems(output, items, false);
        output.store("filter", ItemStack.OPTIONAL_CODEC.listOf(), filter.getItems());
        output.store("levels", com.mojang.serialization.Codec.INT.listOf(), java.util.Arrays.stream(levels).boxed().toList());
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        upgrades.load(input);
        for (int i = 0; i < SIZE; i++) items.set(i, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        filter.clearContent();
        List<ItemStack> saved = input.read("filter", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < Math.min(saved.size(), FILTER_SLOTS); i++) filter.setItem(i, saved.get(i));
        List<Integer> savedLevels = input.read("levels", com.mojang.serialization.Codec.INT.listOf()).orElse(List.of());
        for (int i = 0; i < FILTER_SLOTS; i++) levels[i] = i < savedLevels.size() ? Math.max(0, Math.min(MAX_LEVEL, savedLevels.get(i))) : 0;
    }

    // WorldlyContainer: items go in through the first nine slots from any side and come out of the stock slots only.
    @Override public int[] getSlotsForFace(Direction direction) { return ALL_SLOTS; }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot < IN; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) { return slot < IN; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) { return slot >= IN; }
    @Override public int getContainerSize() { return SIZE; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack out = ContainerHelper.removeItem(items, slot, count);
        if (!out.isEmpty()) setChanged();
        return out;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack); setChanged(); }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }
    @Override public void clearContent() { for (int i = 0; i < SIZE; i++) items.set(i, ItemStack.EMPTY); }
}
