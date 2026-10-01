package dev.alan.logistics;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: the teleporters an open teleporter menu can pick from, and which one is selected (-1 = none). */
public record TeleporterList(int containerId, int selected, List<TeleporterDest> dests) implements CustomPacketPayload {
    public static final Type<TeleporterList> TYPE = new Type<>(LogisticsMod.id("teleporter_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TeleporterList> CODEC = StreamCodec.composite(
        ByteBufCodecs.CONTAINER_ID, TeleporterList::containerId,
        ByteBufCodecs.VAR_INT, TeleporterList::selected,
        TeleporterDest.CODEC.apply(ByteBufCodecs.list(256)), TeleporterList::dests,
        TeleporterList::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
