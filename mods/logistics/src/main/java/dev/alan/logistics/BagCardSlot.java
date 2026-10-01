package dev.alan.logistics;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The channel-card slot of an alchemy backpack menu. The card lives in the backpack's {@link LogisticsMod#BAG_CARD}
 * component. A client-side menu does not know its backpack (the stack is empty), so there the slot just holds what
 * the server sends.
 */
public final class BagCardSlot extends Slot {
    public static final int X = 150, Y = 67;

    public BagCardSlot(ItemStack bag) { super(new CardContainer(bag), 0, X, Y); }

    @Override public boolean mayPlace(ItemStack stack) { return ChannelCardItem.isCard(stack); }
    @Override public int getMaxStackSize() { return 1; }

    private static final class CardContainer implements Container {
        private final ItemStack bag;
        private ItemStack local = ItemStack.EMPTY;

        CardContainer(ItemStack bag) { this.bag = bag; }

        private boolean attached() { return !bag.isEmpty(); }

        @Override public int getContainerSize() { return 1; }
        @Override public boolean isEmpty() { return getItem(0).isEmpty(); }
        @Override public ItemStack getItem(int slot) {
            if (!attached()) return local;
            ItemStack card = bag.get(LogisticsMod.BAG_CARD);
            return card == null ? ItemStack.EMPTY : card;
        }
        @Override public ItemStack removeItem(int slot, int count) {
            ItemStack card = getItem(0);
            if (card.isEmpty() || count <= 0) return ItemStack.EMPTY;
            ItemStack out = card.copy();
            setItem(0, ItemStack.EMPTY);
            return out;
        }
        @Override public ItemStack removeItemNoUpdate(int slot) {
            ItemStack card = getItem(0).copy();
            setItem(0, ItemStack.EMPTY);
            return card;
        }
        @Override public void setItem(int slot, ItemStack stack) {
            if (!attached()) {
                local = stack;
            } else if (stack.isEmpty()) {
                bag.remove(LogisticsMod.BAG_CARD);
            } else {
                bag.set(LogisticsMod.BAG_CARD, stack.copyWithCount(1));
            }
        }
        @Override public void setChanged() {}
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clearContent() { setItem(0, ItemStack.EMPTY); }
    }
}
