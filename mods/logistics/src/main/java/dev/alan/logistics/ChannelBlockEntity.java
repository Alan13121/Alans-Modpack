package dev.alan.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Binds its network to a channel (0 = closed). The channel's energy is kept here, and the card slot is where blank
 * cards are written with the channel.
 */
public final class ChannelBlockEntity extends BlockEntity implements DeviceEntity {
    private int channel;
    private long energy;
    private final SimpleContainer card = new SimpleContainer(1) {
        @Override public void setChanged() {
            super.setChanged();
            ChannelBlockEntity.this.setChanged();
        }
    };

    public ChannelBlockEntity(BlockPos pos, BlockState state) { super(LogisticsMod.CHANNEL_ENTITY, pos, state); }

    public int channel() { return channel; }
    public long energy() { return energy; }
    public SimpleContainer card() { return card; }
    @Override public SimpleContainer slot() { return card; }
    @Override public int extra(int index) { return channel; }

    public void setChannel(int value) {
        int next = Math.max(0, Math.min(ChannelCardItem.MAX_CHANNEL, value));
        if (next == channel) return;
        channel = next;
        setChanged();
        if (level != null) Network.invalidate(level, worldPosition);
    }

    /** Writes the channel onto every card in the slot (a closed channel blanks them). */
    public boolean writeCard() {
        ItemStack stack = card.getItem(0);
        if (!ChannelCardItem.isCard(stack)) return false;
        if (channel == 0) stack.remove(LogisticsMod.CHANNEL); else stack.set(LogisticsMod.CHANNEL, channel);
        card.setChanged();
        return true;
    }

    /** Takes the channel written on the card in the slot (how a private channel is shared). */
    public boolean readCard() {
        int fromCard = ChannelCardItem.channelOf(card.getItem(0));
        if (fromCard <= 0 || !ChannelRegistry.isLive(fromCard)) return false;
        setChannel(fromCard);
        return true;
    }

    public void give(long amount) {
        if (amount <= 0) return;
        long sum = energy + amount;
        energy = sum < 0 ? Long.MAX_VALUE : sum;
        setChanged();
    }

    /** Takes up to {@code amount}; returns what was taken. */
    public long take(long amount) {
        long taken = Math.max(0, Math.min(amount, energy));
        if (taken > 0) {
            energy -= taken;
            setChanged();
        }
        return taken;
    }

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
        if (level != null) net.minecraft.world.Containers.dropContents(level, pos, card);
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("channel", channel);
        output.putLong("energy", energy);
        output.store("card", ItemStack.OPTIONAL_CODEC, card.getItem(0));
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        channel = Math.max(0, Math.min(ChannelCardItem.MAX_CHANNEL, input.getIntOr("channel", 0)));
        energy = Math.max(0, input.getLongOr("energy", 0));
        card.setItem(0, input.read("card", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
    }
}
