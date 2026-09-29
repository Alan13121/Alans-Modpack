package dev.alan.lookup;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** Client → server: cheat mode click on the item list. Only honoured for players allowed to cheat. */
public record GiveItem(ItemStack stack) implements CustomPacketPayload {
    public static final Type<GiveItem> TYPE = new Type<>(LookupMod.id("give_item"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GiveItem> CODEC =
        ItemStack.STREAM_CODEC.map(GiveItem::new, GiveItem::stack);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
