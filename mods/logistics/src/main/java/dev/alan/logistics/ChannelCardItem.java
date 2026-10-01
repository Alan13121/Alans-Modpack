package dev.alan.logistics;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A channel card. Blank cards are crafted; a channel block writes its channel onto them. Cards (and wireless
 * terminals) carry the channel as the {@link LogisticsMod#CHANNEL} component.
 */
public final class ChannelCardItem extends Item {
    public static final int MAX_CHANNEL = Integer.MAX_VALUE;

    public ChannelCardItem(Properties properties) { super(properties); }

    /** The channel written on any stack (card, wireless terminal); 0 = none. */
    public static int channelOf(ItemStack stack) { return stack.isEmpty() ? 0 : stack.getOrDefault(LogisticsMod.CHANNEL, 0); }

    public static boolean isCard(ItemStack stack) { return stack.is(LogisticsMod.CHANNEL_CARD); }
}
