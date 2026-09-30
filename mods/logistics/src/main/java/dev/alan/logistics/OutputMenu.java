package dev.alan.logistics;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Nine ghost slots: clicking one with an item on the cursor copies that item as a filter; an empty cursor clears it. */
public final class OutputMenu extends AbstractContainerMenu {
    private final Container filter;
    private final ContainerLevelAccess access;

    public OutputMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(OutputInterfaceBlockEntity.FILTER_SLOTS), ContainerLevelAccess.NULL);
    }

    public OutputMenu(int id, Inventory inventory, Container filter, ContainerLevelAccess access) {
        super(LogisticsMod.OUTPUT_MENU, id);
        this.filter = filter;
        this.access = access;
        for (int i = 0; i < OutputInterfaceBlockEntity.FILTER_SLOTS; i++)
            addSlot(new Slot(filter, i, 8 + i * 18, 22) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
            });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 74 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 132));
    }

    @Override public boolean stillValid(Player player) {
        return stillValid(access, player, LogisticsMod.OUTPUT_INTERFACE) || access == ContainerLevelAccess.NULL;
    }

    @Override public void clicked(int slotId, int button, ContainerInput type, Player player) {
        if (slotId >= 0 && slotId < OutputInterfaceBlockEntity.FILTER_SLOTS) {
            if (type == ContainerInput.PICKUP) {
                ItemStack carried = getCarried();
                filter.setItem(slotId, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < OutputInterfaceBlockEntity.FILTER_SLOTS || index >= slots.size()) return ItemStack.EMPTY;
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        int free = -1;
        for (int i = 0; i < OutputInterfaceBlockEntity.FILTER_SLOTS; i++) {
            ItemStack f = filter.getItem(i);
            if (!f.isEmpty() && ItemStack.isSameItemSameComponents(f, stack)) return ItemStack.EMPTY;
            if (f.isEmpty() && free < 0) free = i;
        }
        if (free >= 0) filter.setItem(free, stack.copyWithCount(1));
        return ItemStack.EMPTY;
    }
}
