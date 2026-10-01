package dev.alan.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** One teleporter on the same channel: its dimension id and position. */
public record TeleporterDest(String dimension, BlockPos pos) {
    public static final StreamCodec<RegistryFriendlyByteBuf, TeleporterDest> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, TeleporterDest::dimension, BlockPos.STREAM_CODEC, TeleporterDest::pos, TeleporterDest::new);
}
