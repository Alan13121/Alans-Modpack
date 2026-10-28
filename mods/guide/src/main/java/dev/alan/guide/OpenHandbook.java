package dev.alan.guide;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: open the handbook screen (sent by /guidebook). */
public record OpenHandbook() implements CustomPacketPayload {
    public static final Type<OpenHandbook> TYPE = new Type<>(GuideMod.id("open_handbook"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenHandbook> CODEC = StreamCodec.unit(new OpenHandbook());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
