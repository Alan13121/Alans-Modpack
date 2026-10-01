package dev.alchemy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class BagMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final SimpleContainer input = new SimpleContainer(1);
    private final DataSlot bagSlot = DataSlot.standalone();
    private final ItemStack original;
    public BagMenu(int id, Inventory inventory) { this(id, inventory, -1); }
    public BagMenu(int id, Inventory inventory, int selected) {
        super(AlchemyMod.MENU, id);
        this.inventory = inventory;
        this.original = selected < 0 ? ItemStack.EMPTY : inventory.getItem(selected);
        bagSlot.set(selected);
        addDataSlot(bagSlot);
        addSlot(new Slot(input, 0, 26, 67) {
            @Override public boolean mayPlace(ItemStack stack) {
                return EnergyValues.canConvert(stack, inventory.player.level().isClientSide());
            }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addPlayerSlot(col + row * 9 + 9, 12 + col * 18, 151 + row * 18);
        for (int col = 0; col < 9; col++) addPlayerSlot(col, 12 + col * 18, 209);
        for (var factory : BagExtras.slots()) addSlot(factory.apply(original));
    }
    private void addPlayerSlot(int index, int x, int y) {
        addSlot(new Slot(inventory, index, x, y) {
            @Override public boolean mayPickup(Player player) { return index != bagSlot.get(); }
            @Override public boolean mayPlace(ItemStack stack) { return index != bagSlot.get(); }
        });
    }
    /** The backpack stack this menu was opened on (works on the client too, where {@code original} is not known). */
    public ItemStack bag() {
        int i = bagSlot.get();
        return i >= 0 && i < 9 ? inventory.getItem(i) : ItemStack.EMPTY;
    }
    public BagData data() {
        int i = bagSlot.get();
        return i >= 0 && i < 9 ? inventory.getItem(i).getOrDefault(AlchemyMod.DATA, BagData.EMPTY) : BagData.EMPTY;
    }
    @Override public boolean stillValid(Player player) {
        return player.isAlive() && (player.level().isClientSide() ||
            bagSlot.get() >= 0 && inventory.getItem(bagSlot.get()) == original && original.is(AlchemyMod.BACKPACK));
    }
    @Override public void clicked(int index, int button, ContainerInput type, Player player) {
        if (!stillValid(player)) return;
        // Block number-key swaps involving the backpack, including offhand-key swaps.
        if (type == ContainerInput.SWAP && button == bagSlot.get()) return;
        if (index >= 0 && index < slots.size()) {
            Slot slot = slots.get(index);
            if (slot.container == inventory && slot.getContainerSlot() == bagSlot.get()) return;
        }
        super.clicked(index, button, type, player);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !stillValid(player)) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index >= BagExtras.FIRST_INDEX) {
            if (!moveItemStackTo(stack, 1, BagExtras.FIRST_INDEX, true)) return ItemStack.EMPTY;
        } else if (index == 0) {
            if (!moveItemStackTo(stack, 1, BagExtras.FIRST_INDEX, true)) return ItemStack.EMPTY;
        } else if (moveToExtras(stack)) {
            // An extra slot (such as the channel card) took it.
        } else {
            if (!EnergyValues.canConvert(stack, player.level().isClientSide()) || !moveItemStackTo(stack, 0, 1, false))
                return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return before;
    }
    private boolean moveToExtras(ItemStack stack) {
        for (int i = BagExtras.FIRST_INDEX; i < slots.size(); i++)
            if (slots.get(i).mayPlace(stack) && moveItemStackTo(stack, i, i + 1, false)) return true;
        return false;
    }
    @Override public void broadcastChanges() {
        if (!inventory.player.level().isClientSide() && stillValid(inventory.player)) absorb();
        super.broadcastChanges();
    }
    private void absorb() {
        ItemStack stack = input.getItem(0);
        if (!EnergyValues.canConvert(stack, false)) return;
        try {
            BagData next = data().deposit(EnergyValues.id(stack.getItem()), EnergyValues.value(stack.getItem(), false), stack.getCount());
            original.set(AlchemyMod.DATA, next);
            input.setItem(0, ItemStack.EMPTY);
            inventory.setChanged();
            if (inventory.player instanceof net.minecraft.server.level.ServerPlayer owner) {
                Guide.grant(owner, "ch2/first_deposit");
                if (next.learned().size() >= 20) Guide.grant(owner, "ch2/scholar");
            }
        } catch (ArithmeticException overflow) {
            // Keep the input untouched if the long energy balance would overflow.
        }
    }
    @Override public boolean clickMenuButton(Player player, int action) {
        if (player.level().isClientSide() || !stillValid(player) || action < 100) return false;
        int index = (action - 100) / 2;
        BagData state = data();
        if (index < 0 || index >= state.learned().size()) return false;
        Identifier id = Identifier.tryParse(state.learned().get(index));
        if (id == null) return false;
        var optional = BuiltInRegistries.ITEM.getOptional(id);
        if (optional.isEmpty()) return false;
        var item = optional.get();
        long value = EnergyValues.value(item, false);
        if (value <= 0 || item == AlchemyMod.BACKPACK) return false;
        int requested = (action - 100) % 2 == 0 ? 1 : item.getDefaultMaxStackSize();
        var result = item.getDefaultInstance();
        int space = 0;
        for (int i = 0; i < 36; i++) {
            if (i == bagSlot.get()) continue;
            ItemStack current = inventory.getItem(i);
            if (current.isEmpty()) space += result.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(current, result))
                space += Math.max(0, Math.min(current.getMaxStackSize(), inventory.getMaxStackSize()) - current.getCount());
        }
        int count = (int) Math.min(Math.min(requested, space), state.energy() / value);
        if (count <= 0) {
            player.sendOverlayMessage(Component.translatable(space == 0 ? "alchemy.full" : "alchemy.insufficient"));
            return false;
        }
        BagData next = state.withdraw(value, count);
        result.setCount(count);
        // We preflight capacity, and this entire operation runs on the server thread.
        inventory.add(result);
        int inserted = count - result.getCount();
        if (inserted == count) original.set(AlchemyMod.DATA, next);
        else if (inserted > 0) original.set(AlchemyMod.DATA, state.withdraw(value, inserted));
        inventory.setChanged();
        broadcastChanges();
        if (inserted > 0 && player instanceof net.minecraft.server.level.ServerPlayer owner) Guide.grant(owner, "ch2/redeem");
        return inserted > 0;
    }
    @Override public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) clearContainer(player, input);
    }
}
