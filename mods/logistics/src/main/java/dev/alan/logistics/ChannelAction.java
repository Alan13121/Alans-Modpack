package dev.alan.logistics;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: change which channel the open channel block or alchemy backpack uses, or manage channels.
 * {@code value} is the channel id for SELECT, {@code text} the name for CREATE and RENAME, {@code flag} the
 * visibility for CREATE and SET_PUBLIC.
 */
public record ChannelAction(int containerId, Kind kind, int value, String text, boolean flag) implements CustomPacketPayload {
    public static final Type<ChannelAction> TYPE = new Type<>(LogisticsMod.id("channel_action"));

    public enum Kind { SELECT, CREATE, RENAME, SET_PUBLIC, DELETE }

    public static final StreamCodec<RegistryFriendlyByteBuf, ChannelAction> CODEC = StreamCodec.composite(
        ByteBufCodecs.CONTAINER_ID, ChannelAction::containerId,
        ByteBufCodecs.idMapper(i -> Kind.values()[Math.floorMod(i, Kind.values().length)], Kind::ordinal), ChannelAction::kind,
        ByteBufCodecs.VAR_INT, ChannelAction::value,
        ByteBufCodecs.stringUtf8(ChannelInfo.MAX_NAME * 4), ChannelAction::text,
        ByteBufCodecs.BOOL, ChannelAction::flag,
        ChannelAction::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
