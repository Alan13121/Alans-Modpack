package dev.alan.lookup;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

/**
 * Client → server: move the ingredients of a recipe into the open crafting or furnace menu. Vanilla's own
 * request only works for unlocked recipes; this one works for any recipe the server knows.
 */
public record FillRecipe(int containerId, RecipeDisplayId display, boolean max) implements CustomPacketPayload {
    public static final Type<FillRecipe> TYPE = new Type<>(LookupMod.id("fill_recipe"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FillRecipe> CODEC = StreamCodec.composite(
        ByteBufCodecs.CONTAINER_ID, FillRecipe::containerId,
        RecipeDisplayId.STREAM_CODEC, FillRecipe::display,
        ByteBufCodecs.BOOL, FillRecipe::max,
        FillRecipe::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
