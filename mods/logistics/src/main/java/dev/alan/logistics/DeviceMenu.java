package dev.alan.logistics;

import java.util.function.IntSupplier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Menu of the channel block (card slot and channel buttons), the antenna (redstone blocks that extend its range) and
 * the coal generator (fuel). All three show the channel's energy; the extras differ per kind.
 */
public final class DeviceMenu extends AbstractContainerMenu {
    public enum Kind {
        /** extras: channel */
        CHANNEL(1),
        /** extras: range in blocks */
        ANTENNA(1),
        /** extras: burn ticks left, burn ticks of the current item */
        COAL(2);

        public final int extras;
        Kind(int extras) { this.extras = extras; }

        MenuType<DeviceMenu> type() {
            return switch (this) {
                case CHANNEL -> LogisticsMod.CHANNEL_MENU;
                case ANTENNA -> LogisticsMod.ANTENNA_MENU;
                case COAL -> LogisticsMod.COAL_MENU;
            };
        }

        public boolean accepts(ItemStack stack) {
            return switch (this) {
                case CHANNEL -> ChannelCardItem.isCard(stack);
                case ANTENNA -> stack.is(Items.REDSTONE_BLOCK);
                case COAL -> CoalGeneratorBlockEntity.burnTicks(stack) > 0;
            };
        }

        /** Largest stack the slot takes. */
        public int maxStack() { return this == ANTENNA ? AntennaBlockEntity.MAX_BLOCKS : 64; }
    }

    /** {@link #clickMenuButton} ids of the channel block. */
    public static final int WRITE_CARD = 4, READ_CARD = 5;

    public final Kind kind;
    public final EnergyData data;
    private final Container container;
    private final ContainerLevelAccess access;
    private final Block block;
    private final int slotEnd;
    public final int imageHeight;

    public static DeviceMenu client(int id, Inventory inventory, Kind kind) {
        return new DeviceMenu(id, inventory, kind, new SimpleContainer(1), new EnergyData(kind.extras), ContainerLevelAccess.NULL, null);
    }

    public static DeviceMenu server(int id, Inventory inventory, Kind kind, BlockEntity be, ContainerLevelAccess access, Block block) {
        DeviceEntity device = (DeviceEntity) be;
        IntSupplier[] extras = new IntSupplier[kind.extras];
        for (int i = 0; i < extras.length; i++) {
            final int index = i;
            extras[i] = () -> device.extra(index);
        }
        EnergyData data = new EnergyData(() -> access.evaluate((level, pos) -> Energy.availableAt(level, pos), -1L), extras);
        return new DeviceMenu(id, inventory, kind, device.slot(), data, access, block);
    }

    private DeviceMenu(int id, Inventory inventory, Kind kind, Container container, EnergyData data, ContainerLevelAccess access, Block block) {
        super(kind.type(), id);
        this.kind = kind;
        this.container = container;
        this.data = data;
        this.access = access;
        this.block = block;
        addDataSlots(data);
        boolean channel = kind == Kind.CHANNEL;
        this.imageHeight = channel ? 236 : 180;
        int invY = channel ? 154 : 98;
        addSlot(new Slot(container, 0, channel ? 8 : 80, channel ? 108 : 52) {
            @Override public boolean mayPlace(ItemStack stack) { return kind.accepts(stack); }
            @Override public int getMaxStackSize() { return kind.maxStack(); }
        });
        slotEnd = slots.size();
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, invY + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, invY + 58));
    }

    /** The channel block this menu belongs to (server side only). */
    public ChannelBlockEntity channelBlock() {
        return access.evaluate((level, pos) -> level.getBlockEntity(pos) instanceof ChannelBlockEntity be ? be : null, null);
    }

    public long energy() { return EnergyData.read(data); }
    public int extra(int index) { return data.get(2 + index); }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (kind != Kind.CHANNEL) return false;
        boolean[] done = {false};
        access.execute((level, pos) -> {
            if (!(level.getBlockEntity(pos) instanceof ChannelBlockEntity be)) return;
            switch (id) {
                case WRITE_CARD -> be.writeCard();
                case READ_CARD -> be.readCard();
                default -> { return; }
            }
            done[0] = true;
        });
        return done[0];
    }

    @Override public boolean stillValid(Player player) {
        return access == ContainerLevelAccess.NULL || stillValid(access, player, block);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack before = stack.copy();
        if (index < slotEnd) {
            if (!moveItemStackTo(stack, slotEnd, slots.size(), true)) return ItemStack.EMPTY;
        } else if (kind.accepts(stack)) {
            if (!moveItemStackTo(stack, 0, slotEnd, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return before;
    }
}
