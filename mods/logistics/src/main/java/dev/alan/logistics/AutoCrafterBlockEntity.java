package dev.alan.logistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Crafts on its own from the warehouse: a 3x3 pattern names the ingredients, and whenever the warehouse holds fewer
 * than {@code keep} of the result it takes one set of ingredients, crafts, and puts the result (and leftovers such as
 * empty buckets) back. Upgrades set how often it works and how many crafts it makes per pass.
 */
public final class AutoCrafterBlockEntity extends BlockEntity {
    public static final int GRID = 3, MAX_KEEP = 9999, DEFAULT_KEEP = 64;

    private final SimpleContainer pattern = new SimpleContainer(GRID * GRID) {
        @Override public void setChanged() {
            super.setChanged();
            AutoCrafterBlockEntity.this.setChanged();
        }
    };
    private final UpgradeSlots upgrades = new UpgradeSlots(this::setChanged);
    private int keep = DEFAULT_KEEP;
    private final ContainerData keepData = new ContainerData() {
        @Override public int get(int index) { return keep; }
        @Override public void set(int index, int value) {
            keep = Math.max(0, Math.min(MAX_KEEP, value));
            AutoCrafterBlockEntity.this.setChanged();
        }
        @Override public int getCount() { return 1; }
    };

    public AutoCrafterBlockEntity(BlockPos pos, BlockState state) {
        super(LogisticsMod.AUTOCRAFTER_ENTITY, pos, state);
    }

    public SimpleContainer pattern() { return pattern; }
    public UpgradeSlots upgrades() { return upgrades; }
    public ContainerData keepData() { return keepData; }

    /** The recipe the pattern spells out, if any. */
    public static Optional<RecipeHolder<CraftingRecipe>> recipeFor(ServerLevel level, List<ItemStack> layout) {
        CraftingInput input = CraftingInput.ofPositioned(GRID, GRID, layout).input();
        if (input.isEmpty()) return Optional.empty();
        return level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
    }

    private List<ItemStack> layout() {
        List<ItemStack> layout = new ArrayList<>();
        for (int i = 0; i < pattern.getContainerSize(); i++) layout.add(pattern.getItem(i).copy());
        return layout;
    }

    /** What one craft would produce right now (for the menu's preview), or empty. */
    public ItemStack preview(ServerLevel level) {
        List<ItemStack> layout = layout();
        var found = recipeFor(level, layout);
        return found.map(h -> h.value().assemble(CraftingInput.ofPositioned(GRID, GRID, layout).input())).orElse(ItemStack.EMPTY);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AutoCrafterBlockEntity self) {
        if (!(level instanceof ServerLevel server) || (level.getGameTime() + pos.hashCode()) % self.upgrades.interval() != 0) return;
        self.pulse(server, pos);
    }

    private void pulse(ServerLevel level, BlockPos pos) {
        List<ItemStack> layout = layout();
        if (layout.stream().allMatch(ItemStack::isEmpty)) return;
        var found = recipeFor(level, layout);
        if (found.isEmpty()) return;
        Network network = Network.scan(level, pos);
        if (!network.usable()) return;
        int crafts = Math.max(1, upgrades.amount() / UpgradeSlots.BASE_AMOUNT);
        for (int c = 0; c < crafts; c++) if (!craftOnce(level, pos, network, found.get(), layout)) break;
    }

    /** One craft; false when the warehouse already holds enough or lacks an ingredient. */
    private boolean craftOnce(ServerLevel level, BlockPos pos, Network network, RecipeHolder<CraftingRecipe> holder, List<ItemStack> layout) {
        CraftingInput preview = CraftingInput.ofPositioned(GRID, GRID, layout).input();
        ItemStack sample = holder.value().assemble(preview);
        if (sample.isEmpty() || network.count(sample) >= keep) return false;
        // Each pattern slot stands for one of its item; the same item in several slots needs that many.
        java.util.Map<Network.Key, Integer> need = new java.util.LinkedHashMap<>();
        for (ItemStack stack : layout) if (!stack.isEmpty()) need.merge(new Network.Key(stack), 1, Integer::sum);
        for (var e : need.entrySet()) if (network.count(e.getKey().stack()) < e.getValue()) return false;
        List<ItemStack> taken = new ArrayList<>();
        for (ItemStack slot : layout) {
            if (slot.isEmpty()) { taken.add(ItemStack.EMPTY); continue; }
            ItemStack one = network.extract(slot, 1);
            if (one.isEmpty()) {
                for (ItemStack back : taken) if (!back.isEmpty()) network.insert(back);
                return false;
            }
            taken.add(one);
        }
        CraftingInput input = CraftingInput.ofPositioned(GRID, GRID, taken).input();
        ItemStack result = holder.value().assemble(input);
        NonNullList<ItemStack> leftovers = holder.value().getRemainingItems(input);
        store(level, pos, network, result);
        for (ItemStack rest : leftovers) if (!rest.isEmpty()) store(level, pos, network, rest);
        return true;
    }

    private static void store(ServerLevel level, BlockPos pos, Network network, ItemStack stack) {
        ItemStack rest = network.insert(stack);
        if (!rest.isEmpty()) Block.popResource(level, pos, rest);
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) net.minecraft.world.Containers.dropContents(level, pos, upgrades);
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        upgrades.save(output);
        output.store("pattern", ItemStack.OPTIONAL_CODEC.listOf(), pattern.getItems());
        output.putInt("keep", keep);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        upgrades.load(input);
        pattern.clearContent();
        List<ItemStack> saved = input.read("pattern", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < Math.min(saved.size(), pattern.getContainerSize()); i++) pattern.setItem(i, saved.get(i));
        keep = Math.max(0, Math.min(MAX_KEEP, input.getIntOr("keep", DEFAULT_KEEP)));
    }
}
