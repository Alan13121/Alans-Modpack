package dev.alan.logistics;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/** Server → client: the channels this player may use, so screens and tooltips can show names. Sent on join and on every change. */
public record ChannelSync(List<Entry> channels) implements CustomPacketPayload {
    public static final Type<ChannelSync> TYPE = new Type<>(LogisticsMod.id("channel_sync"));

    public record Entry(int id, String name, String ownerName, boolean isPublic, boolean canManage) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Entry::id, ByteBufCodecs.STRING_UTF8, Entry::name, ByteBufCodecs.STRING_UTF8, Entry::ownerName,
            ByteBufCodecs.BOOL, Entry::isPublic, ByteBufCodecs.BOOL, Entry::canManage, Entry::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ChannelSync> CODEC =
        Entry.CODEC.apply(ByteBufCodecs.list(1024)).map(ChannelSync::new, ChannelSync::channels);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static boolean isOp(ServerPlayer player) { return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER); }

    public static void send(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        ChannelRegistry registry = ChannelRegistry.get(server);
        boolean op = isOp(player);
        var legacy = new TreeSet<Integer>();
        for (ChannelBlockEntity be : Grid.channelBlocks()) if (be.channel() > 0 && be.channel() < ChannelRegistry.FIRST_ID) legacy.add(be.channel());
        List<Entry> list = new ArrayList<>();
        for (ChannelInfo info : registry.visibleTo(player.getUUID(), op, legacy))
            list.add(new Entry(info.id(), info.name(), info.ownerName(), info.isPublic(), registry.canManage(info.id(), player.getUUID(), op)));
        if (list.size() > 1024) list = new ArrayList<>(list.subList(0, 1024));
        ServerPlayNetworking.send(player, new ChannelSync(list));
    }

    public static void sendAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) send(player);
    }
}
