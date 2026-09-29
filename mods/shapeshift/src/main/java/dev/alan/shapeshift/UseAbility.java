package dev.alan.shapeshift;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client pressed the ability key. The server decides what, if anything, happens. */
public record UseAbility() implements CustomPacketPayload {
    public static final Type<UseAbility> TYPE = new Type<>(ShapeshiftMod.id("use_ability"));
    public static final StreamCodec<ByteBuf, UseAbility> CODEC = StreamCodec.unit(new UseAbility());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
