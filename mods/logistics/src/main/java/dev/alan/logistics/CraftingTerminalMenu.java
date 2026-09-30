package dev.alan.logistics;

import java.util.List;
import java.util.Optional;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Crafting table + warehouse grid. Whenever a craft empties a grid slot, the slot is refilled with the same
 * ingredient from the warehouse, so shift-clicking the result crafts until the warehouse runs dry.
 * Slot order matches vanilla: 0 result, 1-9 grid, 10-45 player inventory.
 */
public final class CraftingTerminalMenu extends AbstractCraftingMenu implements WarehouseMenu {
    public static final int GRID_START = 1, GRID_END = 10, INV_START = 10, INV_END = 46;
    /** {@link #clickMenuButton} id that stores the crafting grid in the warehouse. */
    public static final int STORE_GRID = 0;

    private final ContainerLevelAccess access;
    private final Player player;
    private final WarehouseLink warehouse;
    private boolean placingRecipe;

    public CraftingTerminalMenu(int id, Inventory inventory) { this(id, inventory, ContainerLevelAccess.NULL); }

    public CraftingTerminalMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(LogisticsMod.CRAFTING_TERMINAL_MENU, id, 3, 3);
        this.access = access;
        this.player = inventory.player;
        this.warehouse = new WarehouseLink(access);
        addResultSlot(player, 218, 112);
        addCraftingGridSlots(200, 40);
        addStandardInventorySlots(inventory, 8, 152);
    }

    @Override public WarehouseLink warehouse() { return warehouse; }

    @Override public boolean stillValid(Player player) { return stillValid(access, player, LogisticsMod.CRAFTING_TERMINAL); }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer sp) warehouse.tick(sp, containerId);
    }

    @Override public void slotsChanged(net.minecraft.world.Container container) {
        if (placingRecipe) return;
        access.execute((level, pos) -> {
            if (level instanceof ServerLevel serverLevel)
                updateResult( serverLevel, player, craftSlots, resultSlots, null);
        });
    }

    @Override protected void beginPlacingRecipe() { placingRecipe = true; }

    @Override protected void finishPlacingRecipe(ServerLevel level, RecipeHolder<CraftingRecipe> recipe) {
        placingRecipe = false;
        updateResult( level, player, craftSlots, resultSlots, recipe);
    }

    /** Vanilla's crafting-table recompute (protected in CraftingMenu, so copied here). */
    private void updateResult(ServerLevel level, Player player, CraftingContainer container, ResultContainer results, RecipeHolder<CraftingRecipe> hint) {
        CraftingInput input = container.asCraftInput();
        ServerPlayer serverPlayer = (ServerPlayer) player;
        ItemStack result = ItemStack.EMPTY;
        Optional<RecipeHolder<CraftingRecipe>> found = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level, hint);
        if (found.isPresent() && results.setRecipeUsed(serverPlayer, found.get())) {
            ItemStack assembled = found.get().value().assemble(input);
            if (assembled.isItemEnabled(level.enabledFeatures())) result = assembled;
        }
        results.setItem(0, result);
        setRemoteSlot(0, result);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), 0, result));
    }

    // ---- refilling from the warehouse --------------------------------------------------------------------

    private ItemStack[] snapshotGrid() {
        ItemStack[] grid = new ItemStack[craftSlots.getContainerSize()];
        for (int i = 0; i < grid.length; i++) grid[i] = craftSlots.getItem(i).copy();
        return grid;
    }

    /** Refills every grid slot that the last craft emptied with the same ingredient. */
    private void refill(ItemStack[] before) {
        if (player.level().isClientSide()) return;
        Network network = warehouse.network();
        if (network == null || !network.usable()) return;
        for (int i = 0; i < before.length; i++) {
            if (before[i].isEmpty() || !craftSlots.getItem(i).isEmpty()) continue;
            ItemStack taken = network.extract(before[i], Math.min(before[i].getCount(), before[i].getMaxStackSize()));
            if (!taken.isEmpty()) craftSlots.setItem(i, taken);
        }
        warehouse.markDirty();
    }

    @Override public void clicked(int slotId, int button, ContainerInput type, Player player) {
        if (slotId != 0 || player.level().isClientSide()) { super.clicked(slotId, button, type, player); return; }
        ItemStack[] before = snapshotGrid();
        super.clicked(slotId, button, type, player);
        refill(before);
    }

    /** Moves the whole crafting grid into the warehouse; anything that does not fit stays in the grid. */
    private void storeGrid() {
        Network network = warehouse.network();
        if (network == null || !network.usable()) return;
        for (int i = 0; i < craftSlots.getContainerSize(); i++) {
            ItemStack stack = craftSlots.getItem(i);
            if (stack.isEmpty()) continue;
            ItemStack rest = network.insert(stack);
            craftSlots.setItem(i, rest.isEmpty() ? ItemStack.EMPTY : rest);
        }
        warehouse.markDirty();
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (id != STORE_GRID || player.level().isClientSide()) return false;
        storeGrid();
        broadcastChanges();
        return true;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> {
            storeGrid();
            clearContainer(player, craftSlots);
        });
    }

    // ---- shift-click ---------------------------------------------------------------------------------------

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack clicked = stack.copy();
        if (index == 0) {
            ItemStack[] before = snapshotGrid();
            stack.getItem().onCraftedBy(stack, player);
            if (!moveItemStackTo(stack, INV_START, INV_END, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, clicked);
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
            if (stack.getCount() == clicked.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, stack);
            if (!stack.isEmpty()) player.drop(stack, false, Prediction.PREDICTED);
            refill(before);
            return clicked;
        }
        if (index < GRID_END) {
            // Grid slot: back into the inventory.
            if (!moveItemStackTo(stack, INV_START, INV_END, false)) return ItemStack.EMPTY;
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
            return clicked;
        }
        // Player inventory: into the warehouse.
        return player.level().isClientSide() ? ItemStack.EMPTY : warehouse.quickInsert(slot);
    }

    @Override public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return target.container != resultSlots && super.canTakeItemForPickAll(carried, target);
    }

    @Override public Slot getResultSlot() { return slots.get(0); }
    @Override public List<Slot> getInputGridSlots() { return slots.subList(GRID_START, GRID_END); }
    @Override public RecipeBookType getRecipeBookType() { return RecipeBookType.CRAFTING; }
    @Override protected Player owner() { return player; }
}
