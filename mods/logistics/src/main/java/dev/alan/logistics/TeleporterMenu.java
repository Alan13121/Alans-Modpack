package dev.alan.logistics;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/**
 * Menu of a teleporter: the pads on the same channel to pick from, open once / open / close buttons and the channel's
 * energy. It has no item slots; the list travels as a {@link TeleporterList} packet.
 */
public final class TeleporterMenu extends AbstractContainerMenu {
    public static final int CLOSE = 0, OPEN_ONCE = 1, OPEN_ALWAYS = 2, SELECT = 100;
    private static final int REFRESH_TICKS = 20;

    public final EnergyData data;
    private final ContainerLevelAccess access;
    private final Inventory inventory;
    private final TeleporterBlockEntity pad;
    private List<TeleporterDest> dests = List.of();
    private int selected = -1;
    private int tick;
    private List<TeleporterDest> sent;
    private int sentSelected = -2;

    public static TeleporterMenu client(int id, Inventory inventory) {
        return new TeleporterMenu(id, inventory, new EnergyData(1), ContainerLevelAccess.NULL, null);
    }

    public static TeleporterMenu server(int id, Inventory inventory, TeleporterBlockEntity pad, ContainerLevelAccess access) {
        EnergyData data = new EnergyData(() -> access.evaluate((level, pos) -> Energy.availableAt(level, pos), -1L), pad::mode);
        return new TeleporterMenu(id, inventory, data, access, pad);
    }

    private TeleporterMenu(int id, Inventory inventory, EnergyData data, ContainerLevelAccess access, TeleporterBlockEntity pad) {
        super(LogisticsMod.TELEPORTER_MENU, id);
        this.inventory = inventory;
        this.data = data;
        this.access = access;
        this.pad = pad;
        addDataSlots(data);
    }

    public long energy() { return EnergyData.read(data); }
    public int mode() { return data.get(2); }
    public List<TeleporterDest> dests() { return dests; }
    public int selected() { return selected; }

    public void receive(TeleporterList list) {
        dests = list.dests();
        selected = list.selected();
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (pad == null || pad.isRemoved()) return false;
        if (id >= SELECT) {
            int index = id - SELECT;
            if (index < 0 || index >= dests.size()) return false;
            pad.setTarget(dests.get(index));
            return true;
        }
        return switch (id) {
            case CLOSE -> pad.setMode(TeleporterBlockEntity.CLOSED);
            case OPEN_ONCE -> {
                boolean ok = pad.setMode(TeleporterBlockEntity.ONCE);
                if (!ok) player.sendOverlayMessage(Component.translatable("logistics.teleporter.pick"));
                yield ok;
            }
            case OPEN_ALWAYS -> {
                boolean ok = pad.setMode(TeleporterBlockEntity.ALWAYS);
                if (!ok) player.sendOverlayMessage(Component.translatable("logistics.teleporter.pick"));
                yield ok;
            }
            default -> false;
        };
    }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (pad == null || !(inventory.player instanceof ServerPlayer player)) return;
        if (sent != null && ++tick % REFRESH_TICKS != 0 && pad.selectedIndex(dests) == sentSelected) return;
        dests = new ArrayList<>(pad.destinations());
        selected = pad.selectedIndex(dests);
        if (dests.equals(sent) && selected == sentSelected) return;
        sent = dests;
        sentSelected = selected;
        ServerPlayNetworking.send(player, new TeleporterList(containerId, selected, dests));
    }

    @Override public boolean stillValid(Player player) {
        return access == ContainerLevelAccess.NULL || stillValid(access, player, LogisticsMod.TELEPORTER);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
