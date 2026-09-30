package dev.alan.logistics;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** Server → client: everything the terminal's network currently holds. */
public record TerminalSnapshot(int containerId, int status, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<TerminalSnapshot> TYPE = new Type<>(LogisticsMod.id("terminal_snapshot"));

    /** {@code stack} is a count-1 template; {@code count} is the network-wide total. */
    public record Entry(ItemStack stack, long count) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, Entry::stack, ByteBufCodecs.VAR_LONG, Entry::count, Entry::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalSnapshot> CODEC = StreamCodec.composite(
        ByteBufCodecs.CONTAINER_ID, TerminalSnapshot::containerId,
        ByteBufCodecs.VAR_INT, TerminalSnapshot::status,
        Entry.CODEC.apply(ByteBufCodecs.list(WarehouseLink.MAX_ENTRIES)), TerminalSnapshot::entries,
        TerminalSnapshot::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
