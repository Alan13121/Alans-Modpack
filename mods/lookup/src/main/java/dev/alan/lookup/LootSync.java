package dev.alan.lookup;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: what loot tables can drop. Loot tables never reach the client in vanilla, so the server
 * reads them and sends this summary. The first batch of a sync has {@code reset} set.
 */
public record LootSync(boolean reset, List<Source> sources) implements CustomPacketPayload {
    public static final Type<LootSync> TYPE = new Type<>(LookupMod.id("loot_sync"));

    public enum Kind { MOB, BLOCK, TABLE }

    /** One possible drop. {@code chance} is 1 when the drop is not gated by a random chance. */
    public record Drop(Identifier item, int min, int max, float chance) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Drop> CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, Drop::item,
            ByteBufCodecs.VAR_INT, Drop::min,
            ByteBufCodecs.VAR_INT, Drop::max,
            ByteBufCodecs.FLOAT, Drop::chance,
            Drop::new);
    }

    /** {@code id} is the entity type or block for MOB and BLOCK, the loot table itself for TABLE. */
    public record Source(Kind kind, Identifier id, List<Drop> drops) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Source> CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Kind.values()[i], Kind::ordinal), Source::kind,
            Identifier.STREAM_CODEC, Source::id,
            Drop.CODEC.apply(ByteBufCodecs.list()), Source::drops,
            Source::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, LootSync> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, LootSync::reset,
        Source.CODEC.apply(ByteBufCodecs.list()), LootSync::sources,
        LootSync::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
