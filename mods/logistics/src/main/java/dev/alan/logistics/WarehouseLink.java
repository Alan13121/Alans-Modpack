package dev.alan.logistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/**
 * The warehouse-grid state shared by the terminal menus: on the server it finds the network, sends snapshots and runs
 * grid clicks; on the client it just holds the last snapshot received.
 */
public final class WarehouseLink {
    public static final int MAX_ENTRIES = 8192;
    private static final int REFRESH_TICKS = 10;

    private final ContainerLevelAccess access;
    private int tick;
    private int lastHash = Integer.MIN_VALUE;
    private boolean forceSend = true;

    // Client-side mirror, filled by TerminalSnapshot.
    private List<TerminalSnapshot.Entry> entries = List.of();
    private Network.Status status = Network.Status.OK;

    public WarehouseLink(ContainerLevelAccess access) { this.access = access; }

    public List<TerminalSnapshot.Entry> entries() { return entries; }
    public Network.Status status() { return status; }

    public void receive(TerminalSnapshot snapshot) {
        entries = snapshot.entries();
        status = Network.Status.values()[Math.floorMod(snapshot.status(), Network.Status.values().length)];
    }

    /** The network this menu's block belongs to, or null on the client. */
    public Network network() {
        return access.evaluate((level, pos) -> Network.scan(level, pos), null);
    }

    /** Sends a fresh snapshot on the next tick regardless of the refresh timer. */
    public void markDirty() { forceSend = true; }

    /** Called from the menu's {@code broadcastChanges} on the server. */
    public void tick(ServerPlayer player, int containerId) {
        if (!forceSend && ++tick % REFRESH_TICKS != 0) return;
        Network network = network();
        if (network == null) return;
        List<TerminalSnapshot.Entry> out = new ArrayList<>();
        int hash = network.status.ordinal();
        if (network.usable()) {
            for (Map.Entry<Network.Key, Long> e : network.contents().entrySet()) {
                if (out.size() >= MAX_ENTRIES) break;
                out.add(new TerminalSnapshot.Entry(e.getKey().stack(), e.getValue()));
                hash += e.getKey().hashCode() * 31 + Long.hashCode(e.getValue());
            }
        }
        if (!forceSend && hash == lastHash) return;
        forceSend = false;
        lastHash = hash;
        ServerPlayNetworking.send(player, new TerminalSnapshot(containerId, network.status.ordinal(), out));
    }

    /** Handles a grid click on the server thread. */
    public void handle(AbstractContainerMenu menu, ServerPlayer player, TerminalAction.Kind kind, ItemStack requested) {
        Network network = network();
        if (network == null || !network.usable()) return;
        ItemStack carried = menu.getCarried();
        switch (kind) {
            case INSERT_ALL, INSERT_ONE -> {
                if (carried.isEmpty()) break;
                ItemStack offer = kind == TerminalAction.Kind.INSERT_ALL ? carried.copy() : carried.copyWithCount(1);
                int before = offer.getCount();
                ItemStack rest = network.insert(offer);
                carried.shrink(before - rest.getCount());
                menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            }
            default -> {
                if (requested.isEmpty()) break;
                boolean shift = kind == TerminalAction.Kind.SHIFT_TAKE;
                int max = requested.getMaxStackSize();
                int amount = switch (kind) {
                    case TAKE_HALF -> Math.max(1, max / 2);
                    case TAKE_ONE -> 1;
                    default -> max;
                };
                if (!shift && !carried.isEmpty()) {
                    // Only stack more of the same item onto what the cursor already holds.
                    if (!ItemStack.isSameItemSameComponents(carried, requested)) break;
                    amount = Math.min(amount, carried.getMaxStackSize() - carried.getCount());
                    if (amount <= 0) break;
                }
                ItemStack taken = network.extract(requested, amount);
                if (taken.isEmpty()) break;
                if (shift) {
                    player.getInventory().add(taken);
                    if (!taken.isEmpty()) {
                        ItemStack rest = network.insert(taken);
                        if (!rest.isEmpty()) player.drop(rest, false, Prediction.PREDICTED);
                    }
                } else if (carried.isEmpty()) {
                    menu.setCarried(taken);
                } else {
                    carried.grow(taken.getCount());
                }
            }
        }
        forceSend = true;
        menu.broadcastChanges();
    }

    /** Shift-click from a slot into the warehouse. Returns a copy of what was there, or EMPTY if nothing moved. */
    public ItemStack quickInsert(net.minecraft.world.inventory.Slot slot) {
        if (!slot.hasItem()) return ItemStack.EMPTY;
        Network network = network();
        if (network == null || !network.usable()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        ItemStack rest = network.insert(stack);
        if (rest.getCount() == before.getCount()) return ItemStack.EMPTY;
        slot.setByPlayer(rest.isEmpty() ? ItemStack.EMPTY : rest);
        forceSend = true;
        return before;
    }
}
