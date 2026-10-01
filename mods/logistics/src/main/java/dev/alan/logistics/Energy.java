package dev.alan.logistics;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/**
 * Energy (EMC) per channel. It lives in the channel blocks bound to that channel, plus alchemy backpacks that carry a
 * card of the channel. Generators add to a channel block; teleporters take from all of them.
 */
public final class Energy {
    public static final long TELEPORT_COST = 10;

    private Energy() {}

    /** The energy of the network holding {@code pos}, or -1 when it has no channel or is not usable. */
    public static long availableAt(Level level, BlockPos pos) {
        Network network = Network.scan(level, pos);
        if (!network.usable()) return -1;
        Set<Integer> channels = network.channels();
        return channels.isEmpty() ? -1 : available(level, channels);
    }

    public static long available(Level level, Set<Integer> channels) {
        long sum = 0;
        for (ChannelBlockEntity be : Grid.channelBlocks())
            if (!be.isRemoved() && channels.contains(be.channel())) sum = saturatedAdd(sum, be.energy());
        MinecraftServer server = level.getServer();
        if (LogisticsMod.ALCHEMY && server != null) sum = saturatedAdd(sum, AlchemyLink.available(server, channels));
        return sum;
    }

    /** Takes {@code amount} from the channels' energy; false (and nothing taken) when there is not enough. */
    public static boolean spend(Level level, Set<Integer> channels, long amount) {
        if (amount <= 0) return true;
        if (available(level, channels) < amount) return false;
        long left = amount;
        for (ChannelBlockEntity be : Grid.channelBlocks()) {
            if (left <= 0) break;
            if (!be.isRemoved() && channels.contains(be.channel())) left -= be.take(left);
        }
        MinecraftServer server = level.getServer();
        if (left > 0 && LogisticsMod.ALCHEMY && server != null) left -= AlchemyLink.take(server, channels, left);
        return left <= 0;
    }

    /** Adds to the first channel block (lowest position) bound to one of the channels. */
    public static void add(Set<Integer> channels, long amount) {
        ChannelBlockEntity best = null;
        for (ChannelBlockEntity be : Grid.channelBlocks()) {
            if (be.isRemoved() || !channels.contains(be.channel())) continue;
            if (best == null || be.getBlockPos().asLong() < best.getBlockPos().asLong()) best = be;
        }
        if (best != null) best.give(amount);
    }

    private static long saturatedAdd(long a, long b) {
        long r = a + b;
        return r < 0 ? Long.MAX_VALUE : r;
    }
}
