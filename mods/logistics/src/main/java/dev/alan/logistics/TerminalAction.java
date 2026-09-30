package dev.alan.logistics;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** Client → server: a click on the warehouse grid. {@code stack} names the item for take actions, unused for inserts. */
public record TerminalAction(int containerId, Kind kind, ItemStack stack) implements CustomPacketPayload {
    public static final Type<TerminalAction> TYPE = new Type<>(LogisticsMod.id("terminal_action"));

    public enum Kind {
        TAKE_STACK, TAKE_HALF, TAKE_ONE, SHIFT_TAKE, INSERT_ALL, INSERT_ONE;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalAction> CODEC = StreamCodec.composite(
        ByteBufCodecs.CONTAINER_ID, TerminalAction::containerId,
        ByteBufCodecs.idMapper(i -> Kind.values()[Math.floorMod(i, Kind.values().length)], Kind::ordinal), TerminalAction::kind,
        ItemStack.OPTIONAL_STREAM_CODEC, TerminalAction::stack,
        TerminalAction::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
