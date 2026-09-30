package dev.alan.logistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * The terminal has no real container slots: the warehouse grid is drawn and clicked by the screen and driven through
 * {@link TerminalAction}. The menu only holds the player's inventory and keeps the client's snapshot fresh.
 */
public final class TerminalMenu extends AbstractContainerMenu {
    public static final int MAX_ENTRIES = 8192;
    private static final int REFRESH_TICKS = 10;

    private final ContainerLevelAccess access;
    private final Inventory inventory;
    private int tick;
    private int lastHash = Integer.MIN_VALUE;
    private boolean forceSend = true;

    // Client-side mirror, filled by TerminalSnapshot.
    private List<TerminalSnapshot.Entry> entries = List.of();
    private Network.Status status = Network.Status.OK;

    public TerminalMenu(int id, Inventory inventory) { this(id, inventory, ContainerLevelAccess.NULL); }

    public TerminalMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(LogisticsMod.TERMINAL_MENU, id);
        this.access = access;
        this.inventory = inventory;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 152 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 210));
    }

    public List<TerminalSnapshot.Entry> entries() { return entries; }
    public Network.Status status() { return status; }

    public void receive(TerminalSnapshot snapshot) {
        entries = snapshot.entries();
        status = Network.Status.values()[Math.floorMod(snapshot.status(), Network.Status.values().length)];
    }

    @Override public boolean stillValid(Player player) { return stillValid(access, player, LogisticsMod.TERMINAL); }

    private Network network() {
        return access.evaluate((level, pos) -> Network.scan(level, pos), null);
    }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (!(inventory.player instanceof ServerPlayer player)) return;
        if (!forceSend && ++tick % REFRESH_TICKS != 0) return;
        sendSnapshot(player);
    }

    private void sendSnapshot(ServerPlayer player) {
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

    /** Handles a grid click. Runs on the server thread. */
    public void handle(ServerPlayer player, TerminalAction.Kind kind, ItemStack requested) {
        Network network = network();
        if (network == null || !network.usable()) return;
        ItemStack carried = getCarried();
        switch (kind) {
            case INSERT_ALL, INSERT_ONE -> {
                if (carried.isEmpty()) break;
                ItemStack offer = kind == TerminalAction.Kind.INSERT_ALL ? carried.copy() : carried.copyWithCount(1);
                int before = offer.getCount();
                ItemStack rest = network.insert(offer);
                carried.shrink(before - rest.getCount());
                setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
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
                    inventory.add(taken);
                    if (!taken.isEmpty()) {
                        ItemStack rest = network.insert(taken);
                        if (!rest.isEmpty()) player.drop(rest, false, net.minecraft.util.Prediction.PREDICTED);
                    }
                } else if (carried.isEmpty()) {
                    setCarried(taken);
                } else {
                    carried.grow(taken.getCount());
                }
            }
        }
        forceSend = true;
        broadcastChanges();
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || player.level().isClientSide()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
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
