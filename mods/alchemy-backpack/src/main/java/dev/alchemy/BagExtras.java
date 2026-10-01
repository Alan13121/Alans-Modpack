package dev.alchemy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Lets other mods put extra slots into the backpack menu. A factory gets the backpack stack and returns the slot (any
 * container backed by that stack, e.g. through a data component). Extra slots come after the player's inventory,
 * from index {@link #FIRST_INDEX}; shift-click moves items into whichever extra slot accepts them.
 */
public final class BagExtras {
    /** Menu slot index of the first extra slot (input slot, then 36 inventory slots). */
    public static final int FIRST_INDEX = 37;
    private static final List<Function<ItemStack, Slot>> SLOTS = new ArrayList<>();

    private BagExtras() {}

    public static void addSlot(Function<ItemStack, Slot> factory) { SLOTS.add(factory); }

    static List<Function<ItemStack, Slot>> slots() { return SLOTS; }
}
