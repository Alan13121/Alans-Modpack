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

    static int channelOf(ItemStack bag) {
        ItemStack card = bag.get(LogisticsMod.BAG_CARD);
        return card == null ? 0 : ChannelCardItem.channelOf(card);
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
