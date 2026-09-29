package dev.alan.lookup;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

/** Server → client: one batch of recipe displays. The first batch of a sync has {@code reset} set. */
public record RecipeSync(boolean reset, List<RecipeDisplayEntry> entries) implements CustomPacketPayload {
    public static final Type<RecipeSync> TYPE = new Type<>(LookupMod.id("recipe_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeSync> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, RecipeSync::reset,
        RecipeDisplayEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), RecipeSync::entries,
        RecipeSync::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
