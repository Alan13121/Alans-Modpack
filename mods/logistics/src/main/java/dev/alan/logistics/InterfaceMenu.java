package dev.alan.logistics;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * Menu of an input or output interface: four upgrade slots, and for the output interface also nine ghost slots
 * where clicking with an item copies it as a filter and clicking with an empty cursor clears it.
 */
public final class InterfaceMenu extends AbstractContainerMenu {
    public static final int FILTER_SLOTS = 9;
    /** {@link #clickMenuButton} ids from this value up change a filter slot's level: base + slot * 4 + action. */
    public static final int LEVEL_BUTTON = 1000;
    public static final int UP_ONE = 0, DOWN_ONE = 1, UP_MANY = 2, DOWN_MANY = 3, MANY = 16;

    /** {@link #clickMenuButton} id that flips the interface's on/off option (the farm's bone-meal use). */
    public static final int TOGGLE_BUTTON = 2000;

    public static int levelButton(int slot, int action) { return LEVEL_BUTTON + slot * 4 + action; }

    public final boolean hasFilter, hasToggle;
    public final UpgradeSlots upgrades;
    /** Screen-relative layout, shared with the screen. */
    public final int upgradeY, labelY, imageHeight;
    private final @Nullable Container filter;
    private final @Nullable ContainerData levels;
    private final @Nullable ContainerData toggle;
    private final ContainerLevelAccess access;
    private final Block block;
    private final int filterEnd, upgradeStart, upgradeEnd, inventoryStart, inventoryEnd;

    /** Client-side constructor used by the menu types. */
    public static InterfaceMenu input(int id, Inventory inventory) {
        return new InterfaceMenu(id, inventory, LogisticsMod.INPUT_MENU, null, null, null, new UpgradeSlots(() -> {}), ContainerLevelAccess.NULL, LogisticsMod.INPUT_INTERFACE);
    }

    public static InterfaceMenu output(int id, Inventory inventory) {
        return new InterfaceMenu(id, inventory, LogisticsMod.OUTPUT_MENU, new net.minecraft.world.SimpleContainer(FILTER_SLOTS), new SimpleContainerData(FILTER_SLOTS), null,
            new UpgradeSlots(() -> {}), ContainerLevelAccess.NULL, LogisticsMod.OUTPUT_INTERFACE);
    }

    public static InterfaceMenu farm(int id, Inventory inventory) {
        return new InterfaceMenu(id, inventory, LogisticsMod.FARM_MENU, null, null, new SimpleContainerData(1), new UpgradeSlots(() -> {}),
            ContainerLevelAccess.NULL, LogisticsMod.FARM_INTERFACE);
    }

    public InterfaceMenu(int id, Inventory inventory, MenuType<InterfaceMenu> type, @Nullable Container filter, @Nullable ContainerData levels, @Nullable ContainerData toggle, UpgradeSlots upgrades,
                         ContainerLevelAccess access, Block block) {
        super(type, id);
        this.filter = filter;
        this.levels = levels;
        this.toggle = toggle;
        this.hasToggle = toggle != null;
        if (levels != null) addDataSlots(levels);
        if (toggle != null) addDataSlots(toggle);
        this.hasFilter = filter != null;
        this.upgrades = upgrades;
        this.access = access;
        this.block = block;
        this.upgradeY = hasFilter ? 76 : hasToggle ? 70 : 36;
        this.labelY = upgradeY + 24;
        int inventoryY = labelY + 12;
        this.imageHeight = inventoryY + 76 + 8;
        if (hasFilter)
            for (int i = 0; i < FILTER_SLOTS; i++)
                addSlot(new Slot(filter, i, 8 + i * 18, 22) {
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                    @Override public boolean mayPickup(Player player) { return false; }
                });
        this.filterEnd = slots.size();
        this.upgradeStart = slots.size();
        for (int i = 0; i < UpgradeSlots.SLOTS; i++)
            addSlot(new Slot(upgrades, i, 8 + i * 18, upgradeY) {
                @Override public boolean mayPlace(ItemStack stack) { return UpgradeSlots.isUpgrade(stack); }
            });
        this.upgradeEnd = slots.size();
        this.inventoryStart = slots.size();
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, inventoryY + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, inventoryY + 58));
        this.inventoryEnd = slots.size();
    }

    /** The keep-in-stock level of a filter slot; 0 means no limit. */
    public int level(int slot) { return levels == null ? 0 : levels.get(slot); }

    public boolean toggleOn() { return toggle != null && toggle.get(0) != 0; }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (id == TOGGLE_BUTTON && toggle != null) {
            toggle.set(0, toggle.get(0) == 0 ? 1 : 0);
            return true;
        }
        if (levels == null || id < LEVEL_BUTTON || id >= TOGGLE_BUTTON) return false;
        int slot = (id - LEVEL_BUTTON) / 4, action = (id - LEVEL_BUTTON) % 4;
        if (slot < 0 || slot >= FILTER_SLOTS || filter.getItem(slot).isEmpty()) return false;
        int step = action >= UP_MANY ? MANY : 1;
        levels.set(slot, levels.get(slot) + (action == UP_ONE || action == UP_MANY ? step : -step));
        return true;
    }

    @Override public boolean stillValid(Player player) {
        return stillValid(access, player, block) || access == ContainerLevelAccess.NULL;
    }

    @Override public void clicked(int slotId, int button, ContainerInput type, Player player) {
        if (hasFilter && slotId >= 0 && slotId < filterEnd) {
            if (type == ContainerInput.PICKUP) {
                ItemStack carried = getCarried();
                filter.setItem(slotId, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
                if (levels != null) levels.set(slotId, 0);
            }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < filterEnd && hasFilter || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack before = stack.copy();
        if (index >= upgradeStart && index < upgradeEnd) {
            if (!moveItemStackTo(stack, inventoryStart, inventoryEnd, true)) return ItemStack.EMPTY;
        } else if (UpgradeSlots.isUpgrade(stack)) {
            if (!moveItemStackTo(stack, upgradeStart, upgradeEnd, false)) return ItemStack.EMPTY;
        } else if (hasFilter) {
            int free = -1;
            for (int i = 0; i < FILTER_SLOTS; i++) {
                ItemStack f = filter.getItem(i);
                if (!f.isEmpty() && ItemStack.isSameItemSameComponents(f, stack)) return ItemStack.EMPTY;
                if (f.isEmpty() && free < 0) free = i;
            }
            if (free >= 0) {
                filter.setItem(free, stack.copyWithCount(1));
                if (levels != null) levels.set(free, 0);
            }
            return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return before;
    }
}
