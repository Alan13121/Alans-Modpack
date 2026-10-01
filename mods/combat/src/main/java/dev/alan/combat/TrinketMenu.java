package dev.alan.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The trinket screen: one slot per kind of trinket, of which only the first {@link Trinkets#slotCount(Player)} take items.
 * The server side writes every change back to the player's {@link CombatMod#SLOTS} attachment.
 */
public final class TrinketMenu extends AbstractContainerMenu {
    public static final int COLUMNS = 6;
    private final SimpleContainer trinkets;
    private final Player player;
    private final int total = Trinkets.total();
    /** Set once the saved trinkets are in, so loading them does not write them straight back. */
    private boolean loaded;

    public TrinketMenu(int id, Inventory inventory) {
        super(CombatMod.MENU, id);
        this.player = inventory.player;
        this.trinkets = new SimpleContainer(total) {
            @Override public void setChanged() {
                super.setChanged();
                if (loaded && player instanceof ServerPlayer serverPlayer) save(serverPlayer);
            }
        };
        if (player instanceof ServerPlayer serverPlayer) {
            TrinketSlots saved = serverPlayer.getAttachedOrElse(CombatMod.SLOTS, TrinketSlots.EMPTY);
            for (int i = 0; i < total; i++) {
                Trinket trinket = Trinkets.byId(saved.at(i));
                if (trinket != null) trinkets.setItem(i, new ItemStack(trinket.item()));
            }
        }
        loaded = true;
        for (int i = 0; i < total; i++) {
            final int index = i;
            addSlot(new Slot(trinkets, i, left(i), top(i)) {
                @Override public boolean mayPlace(ItemStack stack) { return canPlace(index, stack); }
                @Override public int getMaxStackSize() { return 1; }
            });
        }
        int inventoryTop = top(0) + rows() * 18 + 26;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, inventoryTop + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, inventoryTop + 58));
    }

    public static int rows() { return Math.max(1, (Trinkets.total() + COLUMNS - 1) / COLUMNS); }
    public static int left(int index) {
        int inRow = Math.min(COLUMNS, Trinkets.total());
        return (176 - inRow * 18) / 2 + (index % COLUMNS) * 18 + 1;
    }
    public static int top(int index) { return 24 + (index / COLUMNS) * 18; }
    /** Height of the whole screen, shared with the client screen. */
    public static int height() { return 24 + rows() * 18 + 26 + 76 + 8; }

    private boolean canPlace(int index, ItemStack stack) {
        Trinket trinket = Trinkets.of(stack);
        if (trinket == null || index >= Trinkets.slotCount(player)) return false;
        for (int i = 0; i < total; i++) if (i != index && stack.is(trinkets.getItem(i).getItem())) return false;
        return true;
    }

    private void save(ServerPlayer owner) {
        TrinketSlots next = TrinketSlots.EMPTY;
        for (int i = 0; i < total; i++) next = next.with(i, Trinkets.idOf(trinkets.getItem(i)));
        owner.setAttached(CombatMod.SLOTS, next);
        Trinkets.refresh(owner);
    }

    @Override public boolean stillValid(Player player) { return player.isAlive(); }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        boolean moved = index < total
            ? moveItemStackTo(stack, total, slots.size(), true)
            : moveItemStackTo(stack, 0, total, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return before;
    }
}
