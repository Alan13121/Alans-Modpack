package dev.alan.shapeshift;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client asks to switch form. An empty id means "revert to human". */
public record RequestForm(String entityId) implements CustomPacketPayload {
    public static final Type<RequestForm> TYPE = new Type<>(ShapeshiftMod.id("request_form"));
    public static final StreamCodec<ByteBuf, RequestForm> CODEC =
        ByteBufCodecs.stringUtf8(256).map(RequestForm::new, RequestForm::entityId);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
