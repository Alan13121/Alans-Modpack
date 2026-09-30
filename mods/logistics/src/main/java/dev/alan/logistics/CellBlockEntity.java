package dev.alan.logistics;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Virtual storage: up to {@link #MAX_TYPES} item types, {@link #MAX_PER_TYPE} of each. */
public final class CellBlockEntity extends BlockEntity {
    public static final int MAX_TYPES = 64;
    public static final int MAX_PER_TYPE = 10_000;

    private static final class Slot {
        final ItemStack template;
        int count;
        Slot(ItemStack template, int count) { this.template = template; this.count = count; }
    }

    private final List<Slot> slots = new ArrayList<>();

    public CellBlockEntity(BlockPos pos, BlockState state) {
        super(LogisticsMod.CELL_ENTITY, pos, state);
    }

    public CellData data() {
        List<CellData.Entry> out = new ArrayList<>();
        for (Slot s : slots) out.add(new CellData.Entry(s.template, s.count));
        return new CellData(out);
    }

    private void load(CellData data) {
        slots.clear();
        for (CellData.Entry e : data.entries())
            if (!e.stack().isEmpty() && e.count() > 0 && slots.size() < MAX_TYPES)
                slots.add(new Slot(e.stack().copyWithCount(1), Math.min(e.count(), MAX_PER_TYPE)));
    }

    public void forEach(java.util.function.BiConsumer<ItemStack, Integer> consumer) {
        for (Slot s : slots) consumer.accept(s.template, s.count);
    }

    /** Amount currently stored that matches {@code template}. */
    public int count(ItemStack template) {
        for (Slot s : slots) if (ItemStack.isSameItemSameComponents(s.template, template)) return s.count;
        return 0;
    }

    /** Inserts as much of {@code stack} as fits (shrinking it); {@code createNew} allows starting a new type. */
    public void insert(ItemStack stack, boolean createNew) {
        if (stack.isEmpty()) return;
        for (Slot s : slots) {
            if (!ItemStack.isSameItemSameComponents(s.template, stack)) continue;
            int moved = Math.min(stack.getCount(), MAX_PER_TYPE - s.count);
            if (moved > 0) { s.count += moved; stack.shrink(moved); setChanged(); }
            return;
        }
        if (createNew && slots.size() < MAX_TYPES) {
            int moved = Math.min(stack.getCount(), MAX_PER_TYPE);
            slots.add(new Slot(stack.copyWithCount(1), moved));
            stack.shrink(moved);
            setChanged();
        }
    }

    /** Removes up to {@code amount} matching items and returns how many were taken. */
    public int extract(ItemStack template, int amount) {
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            if (!ItemStack.isSameItemSameComponents(s.template, template)) continue;
            int taken = Math.min(amount, s.count);
            s.count -= taken;
            if (s.count <= 0) slots.remove(i);
            if (taken > 0) setChanged();
            return taken;
        }
        return 0;
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("cell", CellData.CODEC, data());
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        load(input.read("cell", CellData.CODEC).orElse(CellData.EMPTY));
    }

    @Override protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(LogisticsMod.CELL_DATA, data());
    }

    @Override protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        load(components.getOrDefault(LogisticsMod.CELL_DATA, CellData.EMPTY));
    }
}
