package dev.alan.logistics;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Feeds the machines and hoppers touching it with the items on its filter list, taken from the warehouse.
 * An empty filter moves nothing, so a stray interface can never empty the warehouse.
 */
public final class OutputInterfaceBlockEntity extends BlockEntity {
    public static final int FILTER_SLOTS = 9;
    public static final int MAX_LEVEL = 9999;

    /** Per filter slot: keep this many of the item in the target (0 = no limit, fill it up). */
    private final int[] levels = new int[FILTER_SLOTS];
    private final ContainerData levelData = new ContainerData() {
        @Override public int get(int index) { return levels[index]; }
        @Override public void set(int index, int value) {
            levels[index] = Math.max(0, Math.min(MAX_LEVEL, value));
            OutputInterfaceBlockEntity.this.setChanged();
        }
        @Override public int getCount() { return FILTER_SLOTS; }
    };

    private final SimpleContainer filter = new SimpleContainer(FILTER_SLOTS) {
        @Override public void setChanged() {
            super.setChanged();
            OutputInterfaceBlockEntity.this.setChanged();
        }
    };

    public OutputInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(LogisticsMod.OUTPUT_ENTITY, pos, state);
    }

    private final UpgradeSlots upgrades = new UpgradeSlots(this::setChanged);

    public SimpleContainer filter() { return filter; }
    public UpgradeSlots upgrades() { return upgrades; }
    public ContainerData levels() { return levelData; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, OutputInterfaceBlockEntity self) {
        if ((level.getGameTime() + pos.hashCode()) % self.upgrades.interval() != 0) return;
        self.pulse(level, pos);
    }

    private void pulse(Level level, BlockPos pos) {
        boolean any = false;
        for (int i = 0; i < FILTER_SLOTS; i++) any |= !filter.getItem(i).isEmpty();
        if (!any) return;
        List<Neighbours.Target> targets = Neighbours.around(level, pos);
        if (targets.isEmpty()) return;
        Warehouse network = Warehouse.at(level, pos);
        if (!network.usable()) return;
        int budget = upgrades.amount();
        for (Neighbours.Target target : targets) {
            for (int i = 0; i < FILTER_SLOTS && budget > 0; i++) {
                ItemStack wanted = filter.getItem(i);
                if (wanted.isEmpty()) continue;
                int want = Math.min(budget, wanted.getMaxStackSize());
                if (levels[i] > 0) want = Math.min(want, levels[i] - countIn(target.container(), wanted));
                if (want <= 0) continue;
                if (!Neighbours.canAccept(target, wanted)) continue;
                ItemStack taken = network.extract(wanted, want);
                if (taken.isEmpty()) continue;
                ItemStack rest = pushInto(level, target, taken);
                budget -= taken.getCount() - rest.getCount();
                if (!rest.isEmpty()) network.insert(rest);
            }
        }
    }

    /** Pushes into the target; temporary containers (composter) take one item at a time, asked for anew each time. */
    private static ItemStack pushInto(Level level, Neighbours.Target target, ItemStack items) {
        if (!target.holder()) return Neighbours.push(target, items);
        ItemStack rest = items.copy();
        while (!rest.isEmpty()) {
            ItemStack left = Neighbours.push(Neighbours.refresh(level, target), rest.copyWithCount(1));
            if (!left.isEmpty()) break;
            rest.shrink(1);
        }
        return rest;
    }

    /** How many of the item the target holds in all its slots. */
    private static int countIn(net.minecraft.world.Container container, ItemStack item) {
        int n = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (ItemStack.isSameItemSameComponents(s, item)) n += s.getCount();
        }
        return n;
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) net.minecraft.world.Containers.dropContents(level, pos, upgrades);
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        upgrades.save(output);
        output.store("filter", ItemStack.OPTIONAL_CODEC.listOf(), filter.getItems());
        output.store("levels", com.mojang.serialization.Codec.INT.listOf(), java.util.Arrays.stream(levels).boxed().toList());
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        upgrades.load(input);
        filter.clearContent();
        List<ItemStack> saved = input.read("filter", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < Math.min(saved.size(), FILTER_SLOTS); i++) filter.setItem(i, saved.get(i));
        List<Integer> savedLevels = input.read("levels", com.mojang.serialization.Codec.INT.listOf()).orElse(List.of());
        for (int i = 0; i < FILTER_SLOTS; i++) levels[i] = i < savedLevels.size() ? Math.max(0, Math.min(MAX_LEVEL, savedLevels.get(i))) : 0;
    }
}
