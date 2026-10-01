package dev.alan.logistics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
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
    /** Most item types one terminal lists. */
    public static final int MAX_ENTRIES = 16384;
    /** Entries per packet; keeps every packet far below the size limit even with heavy item components. */
    public static final int BATCH = 256;
    private static final int REFRESH_TICKS = 10;

    private final Supplier<Warehouse> source;
    private int tick;
    private boolean forceSend = true;

    // Server side: what this player's client has been told, so only changes are sent.
    private final Map<Network.Key, Long> sent = new HashMap<>();
    private Warehouse lastNetwork;
    private long lastRevision = -1;

    // Client side: the mirror built from the packets.
    private final Map<Network.Key, TerminalSnapshot.Entry> mirror = new HashMap<>();
    private List<TerminalSnapshot.Entry> entries = List.of();
    private Network.Status status = Network.Status.OK;

    public WarehouseLink(ContainerLevelAccess access) {
        this(() -> access.evaluate((level, pos) -> Warehouse.at(level, pos), null));
    }

    /** A link that finds its warehouse some other way (the wireless terminal). */
    public WarehouseLink(Supplier<Warehouse> source) { this.source = source; }

    public List<TerminalSnapshot.Entry> entries() { return entries; }
    public Network.Status status() { return status; }

    public void receive(TerminalSnapshot snapshot) {
        status = Network.Status.values()[Math.floorMod(snapshot.status(), Network.Status.values().length)];
        if (snapshot.reset()) mirror.clear();
        for (TerminalSnapshot.Entry e : snapshot.entries()) {
            Network.Key key = new Network.Key(e.stack());
            if (e.count() <= 0) mirror.remove(key); else mirror.put(key, e);
        }
        entries = new ArrayList<>(mirror.values());
    }

    /** The warehouse this menu works on, or null on the client. */
    public Warehouse network() { return source.get(); }

    /** Sends a fresh snapshot on the next tick regardless of the refresh timer. */
    public void markDirty() { forceSend = true; }

    /** Called from the menu's {@code broadcastChanges} on the server. */
    public void tick(ServerPlayer player, int containerId) {
        if (!forceSend && ++tick % REFRESH_TICKS != 0) return;
        Warehouse network = network();
        if (network == null) return;
        boolean reset = !network.sameAs(lastNetwork);
        long revision = network.usable() ? network.revision() : -1;
        if (!reset && !forceSend && revision == lastRevision) return;
        forceSend = false;
        lastNetwork = network;
        lastRevision = revision;
        if (reset) sent.clear();
        List<TerminalSnapshot.Entry> changes = new ArrayList<>();
        if (network.usable()) {
            java.util.Set<Network.Key> present = new java.util.HashSet<>();
            network.forEach((key, count) -> {
                Long before = sent.get(key);
                if (before == null && sent.size() >= MAX_ENTRIES) return;
                present.add(key);
                if (before == null || before != count.longValue()) {
                    changes.add(new TerminalSnapshot.Entry(key.stack(), count));
                    sent.put(key, count);
                }
            });
            var gone = sent.keySet().iterator();
            while (gone.hasNext()) {
                Network.Key key = gone.next();
                if (present.contains(key)) continue;
                changes.add(new TerminalSnapshot.Entry(key.stack(), 0));
                gone.remove();
            }
        }
        if (changes.isEmpty() && !reset) return;
        int status = network.status.ordinal();
        int from = 0;
        do {
            int to = Math.min(changes.size(), from + BATCH);
            ServerPlayNetworking.send(player, new TerminalSnapshot(containerId, status, reset && from == 0, new ArrayList<>(changes.subList(from, to))));
            from = to;
        } while (from < changes.size());
    }

    /** Handles a grid click on the server thread. */
    public void handle(AbstractContainerMenu menu, ServerPlayer player, TerminalAction.Kind kind, ItemStack requested) {
        Warehouse network = network();
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
        Warehouse network = network();
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
