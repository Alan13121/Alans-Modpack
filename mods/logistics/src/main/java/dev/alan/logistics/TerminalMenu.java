package dev.alan.logistics;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The terminal has no real container slots: the warehouse grid is drawn and clicked by the screen and driven through
 * {@link TerminalAction}. The menu only holds the player's inventory.
 */
public final class TerminalMenu extends AbstractContainerMenu implements WarehouseMenu {
    private final ContainerLevelAccess access;
    private final Inventory inventory;
    private final WarehouseLink warehouse;

    public TerminalMenu(int id, Inventory inventory) { this(id, inventory, ContainerLevelAccess.NULL); }

    public TerminalMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(LogisticsMod.TERMINAL_MENU, id);
        this.access = access;
        this.inventory = inventory;
        this.warehouse = new WarehouseLink(access);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 152 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 210));
    }

    @Override public WarehouseLink warehouse() { return warehouse; }

    @Override public boolean stillValid(Player player) { return stillValid(access, player, LogisticsMod.TERMINAL); }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (inventory.player instanceof ServerPlayer player) warehouse.tick(player, containerId);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || player.level().isClientSide()) return ItemStack.EMPTY;
        return warehouse.quickInsert(slots.get(index));
    }
}
