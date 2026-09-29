package dev.alan.lookup;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.PotionIngredient;

/** Server → client: every brewing recipe. Vanilla gives them no display, so they travel as plain data. */
public record BrewingSync(List<Entry> entries) implements CustomPacketPayload {
    public static final Type<BrewingSync> TYPE = new Type<>(LookupMod.id("brewing_sync"));

    public record Entry(PotionIngredient input, PotionIngredient reagent, ItemStackTemplate output) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
            PotionIngredient.STREAM_CODEC, Entry::input,
            PotionIngredient.STREAM_CODEC, Entry::reagent,
            ItemStackTemplate.STREAM_CODEC, Entry::output,
            Entry::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, BrewingSync> CODEC =
        Entry.CODEC.apply(ByteBufCodecs.list()).map(BrewingSync::new, BrewingSync::entries);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
