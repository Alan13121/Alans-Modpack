package dev.alan.logistics;

import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Opens the warehouse grid of its channel from anywhere within range of one of that channel's antennas (same
 * dimension). The channel is written by crafting it together with a channel card.
 */
public final class WirelessTerminalItem extends Item {
    public WirelessTerminalItem(Properties properties) { super(properties); }

    /** True when the player stands inside the range of an antenna whose network carries {@code channel}. */
    public static boolean inRange(Player player, int channel) {
        for (AntennaBlockEntity antenna : Grid.antennas()) {
            Level level = antenna.getLevel();
            if (antenna.isRemoved() || level != player.level()) continue;
            if (antenna.getBlockPos().distSqr(player.blockPosition()) > (double) antenna.range() * antenna.range()) continue;
            if (Network.scan(level, antenna.getBlockPos()).channels().contains(channel)) return true;
        }
        return false;
    }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        int channel = ChannelCardItem.channelOf(player.getItemInHand(hand));
        if (channel == 0) {
            player.sendOverlayMessage(Component.translatable("logistics.wireless.unbound"));
            return InteractionResult.FAIL;
        }
        if (!inRange(player, channel)) {
            player.sendOverlayMessage(Component.translatable("logistics.wireless.out_of_range", channel));
            return InteractionResult.FAIL;
        }
        serverPlayer.openMenu(new SimpleMenuProvider((id, inv, p) -> new TerminalMenu(id, inv,
            () -> Warehouse.ofChannel(level.getServer(), channel),
            () -> Energy.available(level, Set.of(channel)),
            who -> who.isAlive() && inRange(who, channel)), Component.translatable("item.logistics.wireless_terminal")));
        return InteractionResult.SUCCESS;
    }

    public static boolean isTerminal(ItemStack stack) { return stack.is(LogisticsMod.WIRELESS_TERMINAL); }
}
