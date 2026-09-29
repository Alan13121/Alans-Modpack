package dev.alchemy;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.Map;
import java.util.HashMap;

public record EnergySync(long fallback, Map<String, Long> values) implements CustomPacketPayload {
    public static final Type<EnergySync> TYPE = new Type<>(AlchemyMod.id("energy_values"));
    public static final StreamCodec<ByteBuf, EnergySync> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG, EnergySync::fallback,
        ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_LONG, 16384), EnergySync::values,
        EnergySync::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
