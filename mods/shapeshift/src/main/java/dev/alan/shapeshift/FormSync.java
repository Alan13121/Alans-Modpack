package dev.alan.shapeshift;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: all form definitions, sent on join and after /reload. */
public record FormSync(Map<String, FormDefinition> definitions) implements CustomPacketPayload {
    public static final Type<FormSync> TYPE = new Type<>(ShapeshiftMod.id("form_definitions"));
    public static final StreamCodec<ByteBuf, FormSync> CODEC = ByteBufCodecs.fromCodec(
        Codec.unboundedMap(Codec.STRING, FormDefinition.CODEC)).map(FormSync::new, FormSync::definitions);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
