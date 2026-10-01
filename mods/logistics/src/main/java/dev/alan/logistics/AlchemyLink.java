package dev.alan.logistics;

import dev.alchemy.AlchemyMod;
import dev.alchemy.BagData;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Reads and drains the energy of alchemy backpacks that carry a channel card. Only loaded when the alchemy backpack
 * mod is installed (see {@link LogisticsMod#ALCHEMY}); a backpack counts while its owner is online and carries it.
 */
final class AlchemyLink {
    private AlchemyLink() {}

    /** The channel picked directly, otherwise the one on the card; 0 when none or the channel no longer exists. */
    static int channelOf(ItemStack bag) {
        int picked = bag.getOrDefault(LogisticsMod.BAG_CHANNEL, 0);
        ItemStack card = bag.get(LogisticsMod.BAG_CARD);
        int id = picked > 0 ? picked : card == null ? 0 : ChannelCardItem.channelOf(card);
        return ChannelRegistry.isLive(id) ? id : 0;
    }

    /** Lets {@link ChannelActions} edit the channel of the backpack whose menu is open. */
    static ChannelActions.Target bagTarget(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        if (!(menu instanceof dev.alchemy.BagMenu bagMenu)) return null;
        ItemStack bag = bagMenu.bag();
        if (bag.isEmpty()) return null;
        return new ChannelActions.Target(() -> bag.getOrDefault(LogisticsMod.BAG_CHANNEL, 0), id -> {
            if (id <= 0) bag.remove(LogisticsMod.BAG_CHANNEL); else bag.set(LogisticsMod.BAG_CHANNEL, id);
        }, false);
    }

    static long available(MinecraftServer server, Set<Integer> channels) {
        long sum = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.is(AlchemyMod.BACKPACK) && channels.contains(channelOf(stack)))
                    sum += stack.getOrDefault(AlchemyMod.DATA, BagData.EMPTY).energy();
            }
        return sum;
    }

    static long take(MinecraftServer server, Set<Integer> channels, long amount) {
        long left = amount;
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.is(AlchemyMod.BACKPACK) || !channels.contains(channelOf(stack))) continue;
                BagData data = stack.getOrDefault(AlchemyMod.DATA, BagData.EMPTY);
                long moved = Math.min(left, data.energy());
                if (moved <= 0) continue;
                stack.set(AlchemyMod.DATA, data.withdraw(moved, 1));
                left -= moved;
                player.getInventory().setChanged();
            }
        return amount - left;
    }
}
