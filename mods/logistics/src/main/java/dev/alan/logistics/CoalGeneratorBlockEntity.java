package dev.alan.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Burns coal into energy for its network's channel: 4 EMC a second, 800 EMC per coal. Pauses while there is nowhere to put it. */
public final class CoalGeneratorBlockEntity extends BlockEntity implements DeviceEntity {
    /** One coal burns 4000 ticks (200 s) at 4 EMC/s = 800 EMC. */
    public static final int COAL_TICKS = 4000;
    private static final int TICKS_PER_EMC = 5;

    private final SimpleContainer fuel = new SimpleContainer(1) {
        @Override public void setChanged() {
            super.setChanged();
            CoalGeneratorBlockEntity.this.setChanged();
        }
    };
    private int burnLeft, burnTotal;

    public CoalGeneratorBlockEntity(BlockPos pos, BlockState state) { super(LogisticsMod.COAL_ENTITY, pos, state); }

    public static int burnTicks(ItemStack stack) {
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) return COAL_TICKS;
        if (stack.is(Items.COAL_BLOCK)) return COAL_TICKS * 9;
        return 0;
    }

    @Override public SimpleContainer slot() { return fuel; }
    @Override public int extra(int index) { return index == 0 ? burnLeft : burnTotal; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BlockEntity entity) {
        CoalGeneratorBlockEntity self = (CoalGeneratorBlockEntity) entity;
        Network network = Network.scan(level, pos);
        if (!network.usable() || network.channels().isEmpty()) return;
        if (self.burnLeft <= 0) {
            ItemStack stack = self.fuel.getItem(0);
            int ticks = burnTicks(stack);
            if (ticks <= 0) return;
            stack.shrink(1);
            self.fuel.setChanged();
            self.burnLeft = self.burnTotal = ticks;
        }
        self.burnLeft--;
        if (self.burnLeft % TICKS_PER_EMC == 0) Energy.add(network.channels(), 1);
        if (self.burnLeft % 20 == 0) self.setChanged();
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) net.minecraft.world.Containers.dropContents(level, pos, fuel);
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("fuel", ItemStack.OPTIONAL_CODEC, fuel.getItem(0));
        output.putInt("burn_left", burnLeft);
        output.putInt("burn_total", burnTotal);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        fuel.setItem(0, input.read("fuel", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        burnLeft = Math.max(0, input.getIntOr("burn_left", 0));
        burnTotal = Math.max(burnLeft, input.getIntOr("burn_total", 0));
    }
}
