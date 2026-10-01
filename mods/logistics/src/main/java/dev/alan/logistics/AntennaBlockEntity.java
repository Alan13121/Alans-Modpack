package dev.alan.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Lets wireless terminals reach the network's channel: 10 blocks, plus 5 for every redstone block inside. */
public final class AntennaBlockEntity extends BlockEntity implements DeviceEntity {
    public static final int BASE_RANGE = 10, RANGE_PER_BLOCK = 5, MAX_BLOCKS = 10;

    private final SimpleContainer boost = new SimpleContainer(1) {
        @Override public int getMaxStackSize() { return MAX_BLOCKS; }
        @Override public void setChanged() {
            super.setChanged();
            AntennaBlockEntity.this.setChanged();
        }
    };

    public AntennaBlockEntity(BlockPos pos, BlockState state) { super(LogisticsMod.ANTENNA_ENTITY, pos, state); }

    public int range() { return BASE_RANGE + RANGE_PER_BLOCK * Math.min(MAX_BLOCKS, boost.getItem(0).getCount()); }

    @Override public SimpleContainer slot() { return boost; }
    @Override public int extra(int index) { return range(); }

    @Override public void setLevel(net.minecraft.world.level.Level level) {
        super.setLevel(level);
        if (!level.isClientSide()) Grid.add(this);
    }

    @Override public void clearRemoved() {
        super.clearRemoved();
        if (level != null && !level.isClientSide()) Grid.add(this);
    }

    @Override public void setRemoved() {
        super.setRemoved();
        Grid.remove(this);
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) net.minecraft.world.Containers.dropContents(level, pos, boost);
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("boost", ItemStack.OPTIONAL_CODEC, boost.getItem(0));
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        boost.setItem(0, input.read("boost", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
    }
}
