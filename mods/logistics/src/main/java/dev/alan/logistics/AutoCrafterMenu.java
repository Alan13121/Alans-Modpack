package dev.alan.logistics;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.recipebook.PlaceRecipeHelper;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.entity.player.StackedItemContents;
import org.jspecify.annotations.Nullable;

/**
 * Pattern grid (ghost slots, like the output interface's filter), a preview of the result, the keep-in-stock number
 * and upgrade slots. As a {@link RecipeBookMenu} it also takes Lookup's "+" button: the recipe's ingredients become
 * the pattern, using whichever allowed item the warehouse holds most of.
 * Slots: 0-8 pattern, 9 result preview, 10-13 upgrades, 14-49 inventory.
 */
public final class AutoCrafterMenu extends RecipeBookMenu {
    public static final int PATTERN_START = 0, RESULT = 9, UPGRADE_START = 10, UPGRADE_END = 14, INV_START = 14, INV_END = 50;
    /** {@link #clickMenuButton} ids from this value up change the keep-in-stock number: base + action. */
    public static final int KEEP_BUTTON = 3000;
    public static final int UP_ONE = 0, DOWN_ONE = 1, UP_MANY = 2, DOWN_MANY = 3, MANY = 16;

    public final UpgradeSlots upgrades;
    private final Container pattern;
    private final SimpleContainer preview = new SimpleContainer(1);
    private final ContainerData keep;
    private final ContainerLevelAccess access;
    private final @Nullable AutoCrafterBlockEntity crafter;
    private ItemStack lastPreview = ItemStack.EMPTY;

    public static AutoCrafterMenu client(int id, Inventory inventory) {
        return new AutoCrafterMenu(id, inventory, new SimpleContainer(9), new UpgradeSlots(() -> {}), new SimpleContainerData(1), ContainerLevelAccess.NULL, null);
    }

    public AutoCrafterMenu(int id, Inventory inventory, AutoCrafterBlockEntity be, ContainerLevelAccess access) {
        this(id, inventory, be.pattern(), be.upgrades(), be.keepData(), access, be);
    }

    private AutoCrafterMenu(int id, Inventory inventory, Container pattern, UpgradeSlots upgrades, ContainerData keep,
                            ContainerLevelAccess access, @Nullable AutoCrafterBlockEntity crafter) {
        super(LogisticsMod.AUTOCRAFTER_MENU, id);
        this.pattern = pattern;
        this.upgrades = upgrades;
        this.keep = keep;
        this.access = access;
        this.crafter = crafter;
        addDataSlots(keep);
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++)
            addSlot(new Slot(pattern, x + y * 3, 8 + x * 18, 22 + y * 18) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
            });
        addSlot(new Slot(preview, 0, 86, 40) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });
        for (int i = 0; i < UpgradeSlots.SLOTS; i++)
            addSlot(new Slot(upgrades, i, 8 + i * 18, 92) {
                @Override public boolean mayPlace(ItemStack stack) { return UpgradeSlots.isUpgrade(stack); }
            });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 128 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 186));
    }

    public int keep() { return keep.get(0); }

    @Override public boolean stillValid(Player player) {
        return stillValid(access, player, LogisticsMod.AUTOCRAFTER) || access == ContainerLevelAccess.NULL;
    }

    @Override public void broadcastChanges() {
        if (crafter != null && access != ContainerLevelAccess.NULL) {
            access.execute((level, pos) -> {
                if (level instanceof ServerLevel server) {
                    ItemStack now = crafter.preview(server);
                    if (!ItemStack.matches(now, lastPreview)) {
                        lastPreview = now.copy();
                        preview.setItem(0, now);
                    }
                }
            });
        }
        super.broadcastChanges();
    }

    @Override public void clicked(int slotId, int button, ContainerInput type, Player player) {
        if (slotId >= PATTERN_START && slotId < RESULT) {
            if (type == ContainerInput.PICKUP) {
                ItemStack carried = getCarried();
                pattern.setItem(slotId, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            }
            return;
        }
        if (slotId == RESULT) return;
        super.clicked(slotId, button, type, player);
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (id < KEEP_BUTTON || id >= KEEP_BUTTON + 4) return false;
        int action = id - KEEP_BUTTON;
        int step = action >= UP_MANY ? MANY : 1;
        keep.set(0, keep.get(0) + (action == UP_ONE || action == UP_MANY ? step : -step));
        return true;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || index < UPGRADE_START) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack before = stack.copy();
        if (index < UPGRADE_END) {
            if (!moveItemStackTo(stack, INV_START, INV_END, true)) return ItemStack.EMPTY;
        } else if (UpgradeSlots.isUpgrade(stack)) {
            if (!moveItemStackTo(stack, UPGRADE_START, UPGRADE_END, false)) return ItemStack.EMPTY;
        } else {
            // Any other item: make it the next free pattern slot, like a ghost click.
            for (int i = 0; i < 9; i++) {
                if (pattern.getItem(i).isEmpty()) { pattern.setItem(i, stack.copyWithCount(1)); break; }
            }
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return before;
    }

    // ---- RecipeBookMenu: Lookup's "+" sets the pattern ------------------------------------------------------

    @Override public PostPlaceAction handlePlacement(boolean useMaxItems, boolean allowDroppingItemsToClear, RecipeHolder<?> recipe,
                                                     ServerLevel level, Inventory inventory) {
        if (!(recipe.value() instanceof CraftingRecipe crafting) || crafting.placementInfo().isImpossibleToPlace()) return PostPlaceAction.NOTHING;
        Network network = access.evaluate((l, pos) -> Network.scan(l, pos), null);
        var info = crafting.placementInfo();
        List<Item> chosen = new ArrayList<>();
        for (var ingredient : info.ingredients()) {
            Item best = null;
            long bestStock = -1;
            for (var holder : ingredient.items().toList()) {
                long stock = network != null && network.usable() ? network.count(new ItemStack(holder.value())) : 0;
                if (stock > bestStock) { bestStock = stock; best = holder.value(); }
            }
            chosen.add(best);
        }
        for (int i = 0; i < 9; i++) pattern.setItem(i, ItemStack.EMPTY);
        PlaceRecipeHelper.placeRecipe(3, 3, crafting, info.slotsToIngredientIndex(), (ingredientIndex, gridIndex, x, y) -> {
            if (ingredientIndex != -1 && chosen.get(ingredientIndex) != null) pattern.setItem(gridIndex, new ItemStack(chosen.get(ingredientIndex)));
        });
        return PostPlaceAction.NOTHING;
    }

    @Override public void fillCraftSlotsStackedContents(StackedItemContents stackedContents) {}
    @Override public RecipeBookType getRecipeBookType() { return RecipeBookType.CRAFTING; }
}
