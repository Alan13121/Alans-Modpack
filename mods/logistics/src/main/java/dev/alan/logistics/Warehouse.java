package dev.alan.logistics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * What a terminal or machine actually works on: the network it touches, plus every other usable network whose channel
 * block carries the same channel. Items are read and moved across all of them as if they were one warehouse. A network
 * without any channel block is simply its own warehouse.
 */
public final class Warehouse {
    private final List<Network> members;
    private final Network home;
    public final Network.Status status;

    private Warehouse(List<Network> members, Network home, Network.Status status) {
        this.members = members;
        this.home = home;
        this.status = status;
    }

    /** The warehouse of the network holding the node at {@code pos}. */
    public static Warehouse at(Level level, BlockPos pos) {
        Network home = Network.scan(level, pos);
        if (!home.usable() || home.channels().isEmpty()) return new Warehouse(List.of(home), home, home.status);
        List<Network> members = new ArrayList<>();
        members.add(home);
        for (ChannelBlockEntity be : Grid.channelBlocks()) {
            if (be.isRemoved() || be.getLevel() == null || !home.channels().contains(be.channel())) continue;
            addMember(members, Network.scan(be.getLevel(), be.getBlockPos()));
        }
        return new Warehouse(members, home, home.status);
    }

    /** The merged warehouse of a channel, for wireless access; unusable when no network carries it. */
    public static Warehouse ofChannel(MinecraftServer server, int channel) {
        List<Network> members = new ArrayList<>();
        for (ChannelBlockEntity be : Grid.channelBlocks()) {
            if (be.isRemoved() || be.getLevel() == null || be.channel() != channel) continue;
            addMember(members, Network.scan(be.getLevel(), be.getBlockPos()));
        }
        if (members.isEmpty()) return new Warehouse(List.of(), null, Network.Status.NO_CONTROLLER);
        return new Warehouse(members, members.get(0), Network.Status.OK);
    }

    private static void addMember(List<Network> members, Network network) {
        if (!network.usable()) return;
        for (Network m : members) if (m == network) return;
        members.add(network);
    }

    public boolean usable() { return status == Network.Status.OK && !members.isEmpty() && home.usable(); }

    /** True when {@code other} is made of exactly the same networks (so a client mirror stays valid). */
    public boolean sameAs(Warehouse other) {
        if (other == null || other.members.size() != members.size()) return false;
        for (int i = 0; i < members.size(); i++) if (members.get(i) != other.members.get(i)) return false;
        return true;
    }

    /** Changes whenever any total or the set of networks changes. */
    public long revision() {
        long r = members.size();
        for (Network n : members) r = r * 31 + n.revision();
        return r;
    }

    public long count(ItemStack template) {
        long sum = 0;
        for (Network n : members) sum += n.count(template);
        return sum;
    }

    public int typeCount() { return contents().size(); }

    public void forEach(BiConsumer<Network.Key, Long> action) {
        if (members.size() == 1) { members.get(0).forEach(action); return; }
        contents().forEach(action);
    }

    public Map<Network.Key, Long> contents() {
        Map<Network.Key, Long> map = new HashMap<>();
        for (Network n : members) n.forEach((k, c) -> map.merge(k, c, Long::sum));
        return map;
    }

    public ItemStack extract(ItemStack template, int amount) {
        ItemStack out = template.copyWithCount(0);
        for (Network n : members) {
            if (out.getCount() >= amount) break;
            out.grow(n.extract(template, amount - out.getCount()).getCount());
        }
        return out;
    }

    /** Stores the stack and returns what did not fit; networks already holding the item are filled first. */
    public ItemStack insert(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (Network n : members) {
            if (rest.isEmpty()) break;
            if (n.count(rest) > 0) rest = n.insert(rest);
        }
        for (Network n : members) {
            if (rest.isEmpty()) break;
            rest = n.insert(rest);
        }
        return rest;
    }
}
