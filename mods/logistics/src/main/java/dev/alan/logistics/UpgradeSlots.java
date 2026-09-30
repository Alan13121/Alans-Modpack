package dev.alan.logistics;

import java.util.List;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The upgrade slots of an input or output interface. Redstone makes it pulse more often, quartz makes each pulse
 * move more items. Each type counts up to {@link #MAX_EFFECT} pieces; slots hold {@link #MAX_PER_SLOT} at most.
 */
public final class UpgradeSlots extends SimpleContainer {
    public static final int SLOTS = 4, MAX_PER_SLOT = 8, MAX_EFFECT = 16;
    public static final int BASE_INTERVAL = 10, BASE_AMOUNT = 32, AMOUNT_PER_QUARTZ = 14;

    private final Runnable onChange;

    public UpgradeSlots(Runnable onChange) {
        super(SLOTS);
        this.onChange = onChange;
    }

    public static boolean isUpgrade(ItemStack stack) { return stack.is(Items.REDSTONE) || stack.is(Items.QUARTZ); }

    @Override public int getMaxStackSize() { return MAX_PER_SLOT; }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return isUpgrade(stack); }

    @Override public void setChanged() {
        super.setChanged();
        onChange.run();
    }

    private int count(Item item) {
        int n = 0;
        for (int i = 0; i < SLOTS; i++) if (getItem(i).is(item)) n += getItem(i).getCount();
        return Math.min(n, MAX_EFFECT);
    }

    /** Game ticks between pulses: 10 with no redstone, down to 1 with 16. */
    public int interval() { return Math.max(1, BASE_INTERVAL - (count(Items.REDSTONE) * (BASE_INTERVAL - 1) + MAX_EFFECT - 1) / MAX_EFFECT); }

    /** Items moved per pulse: 32 with no quartz, up to 256 with 16. */
    public int amount() { return BASE_AMOUNT + count(Items.QUARTZ) * AMOUNT_PER_QUARTZ; }

    public void save(ValueOutput output) {
        output.store("upgrades", ItemStack.OPTIONAL_CODEC.listOf(), getItems());
    }

    public void load(ValueInput input) {
        clearContent();
        List<ItemStack> saved = input.read("upgrades", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < Math.min(saved.size(), SLOTS); i++) setItem(i, saved.get(i));
    }
}
