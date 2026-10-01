package dev.alan.logistics;

import java.util.function.Predicate;
import java.util.function.Supplier;
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
    private final Predicate<Player> valid;
    public final EnergyData energy;

    public TerminalMenu(int id, Inventory inventory) { this(id, inventory, ContainerLevelAccess.NULL); }

    public TerminalMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        this(id, inventory, access, new WarehouseLink(access), p -> stillValid(access, p, LogisticsMod.TERMINAL),
            inventory.player.level().isClientSide() ? new EnergyData(0) : new EnergyData(() -> access.evaluate((level, pos) -> Energy.availableAt(level, pos), -1L)));
    }

    /** The wireless terminal: no block, the warehouse is found through the channel and {@code valid} keeps checking the range. */
    public TerminalMenu(int id, Inventory inventory, Supplier<Warehouse> source, Supplier<Long> energy, Predicate<Player> valid) {
        this(id, inventory, ContainerLevelAccess.NULL, new WarehouseLink(source), valid, new EnergyData(energy::get));
    }

    private TerminalMenu(int id, Inventory inventory, ContainerLevelAccess access, WarehouseLink warehouse, Predicate<Player> valid, EnergyData energy) {
        super(LogisticsMod.TERMINAL_MENU, id);
        this.access = access;
        this.inventory = inventory;
        this.warehouse = warehouse;
        this.valid = access == ContainerLevelAccess.NULL && inventory.player.level().isClientSide() ? p -> true : valid;
        this.energy = energy;
        addDataSlots(energy);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 152 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 210));
    }

    @Override public WarehouseLink warehouse() { return warehouse; }

    @Override public boolean stillValid(Player player) { return valid.test(player); }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (inventory.player instanceof ServerPlayer player) warehouse.tick(player, containerId);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || player.level().isClientSide()) return ItemStack.EMPTY;
        return warehouse.quickInsert(slots.get(index));
    }
}
