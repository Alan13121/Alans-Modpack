package dev.alan.combat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client pressed the trinket key; the server opens the trinket screen. */
public record OpenTrinkets() implements CustomPacketPayload {
    public static final Type<OpenTrinkets> TYPE = new Type<>(CombatMod.id("open_trinkets"));
    public static final StreamCodec<ByteBuf, OpenTrinkets> CODEC = StreamCodec.unit(new OpenTrinkets());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
