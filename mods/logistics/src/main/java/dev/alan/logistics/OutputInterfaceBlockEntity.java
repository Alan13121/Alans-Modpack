package dev.alan.logistics;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
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
        Network network = Network.scan(level, pos);
        if (!network.usable()) return;
        int budget = upgrades.amount();
        for (Neighbours.Target target : targets) {
            for (int i = 0; i < FILTER_SLOTS && budget > 0; i++) {
                ItemStack wanted = filter.getItem(i);
                if (wanted.isEmpty()) continue;
                ItemStack taken = network.extract(wanted, Math.min(budget, wanted.getMaxStackSize()));
                if (taken.isEmpty()) continue;
                ItemStack rest = Neighbours.push(target, taken);
                budget -= taken.getCount() - rest.getCount();
                if (!rest.isEmpty()) network.insert(rest);
            }
        }
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) net.minecraft.world.Containers.dropContents(level, pos, upgrades);
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        upgrades.save(output);
        output.store("filter", ItemStack.OPTIONAL_CODEC.listOf(), filter.getItems());
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        upgrades.load(input);
        filter.clearContent();
        List<ItemStack> saved = input.read("filter", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < Math.min(saved.size(), FILTER_SLOTS); i++) filter.setItem(i, saved.get(i));
    }
}
