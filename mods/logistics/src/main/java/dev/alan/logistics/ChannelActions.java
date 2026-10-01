package dev.alan.logistics;

import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/** Server side of {@link ChannelAction}: permission checks and registry changes. */
public final class ChannelActions {
    private ChannelActions() {}

    /** The thing whose channel is being edited: a channel block or an alchemy backpack. */
    public record Target(IntSupplier get, IntConsumer set, boolean manage) {}

    private static @Nullable Target target(ServerPlayer player) {
        var menu = player.containerMenu;
        if (!menu.stillValid(player)) return null;
        if (menu instanceof DeviceMenu device && device.kind == DeviceMenu.Kind.CHANNEL) {
            ChannelBlockEntity be = device.channelBlock();
            return be == null ? null : new Target(be::channel, be::setChannel, true);
        }
        return LogisticsMod.ALCHEMY ? AlchemyLink.bagTarget(menu) : null;
    }

    public static void handle(ServerPlayer player, ChannelAction action) {
        MinecraftServer server = player.level().getServer();
        if (server == null || player.containerMenu.containerId != action.containerId()) return;
        Target target = target(player);
        if (target == null) return;
        ChannelRegistry registry = ChannelRegistry.get(server);
        UUID me = player.getUUID();
        boolean op = ChannelSync.isOp(player);
        if (action.kind() != ChannelAction.Kind.SELECT && !target.manage()) return;
        int current = target.get().getAsInt();
        switch (action.kind()) {
            case SELECT -> {
                int id = action.value();
                if (id != 0 && !registry.canUse(id, me, op)) { deny(player, "logistics.channel.error.private"); return; }
                target.set().accept(id);
            }
            case CREATE -> {
                String name = action.text().strip();
                String problem = registry.checkName(name, me, -1);
                if (!problem.equals("ok")) { deny(player, problem); return; }
                if (!op && registry.countOwned(me) >= ChannelRegistry.MAX_PER_OWNER) { deny(player, "logistics.channel.error.limit"); return; }
                ChannelInfo info = registry.create(name, me, player.getGameProfile().name(), action.flag());
                target.set().accept(info.id());
            }
            case RENAME -> {
                if (!registry.canManage(current, me, op)) { deny(player, "logistics.channel.error.manage"); return; }
                ChannelInfo info = registry.info(current);
                String name = action.text().strip();
                String problem = registry.checkName(name, info.owner(), current);
                if (!problem.equals("ok")) { deny(player, problem); return; }
                registry.rename(current, name);
            }
            case SET_PUBLIC -> {
                if (!registry.canManage(current, me, op)) { deny(player, "logistics.channel.error.manage"); return; }
                registry.setPublic(current, action.flag());
            }
            case DELETE -> {
                if (!registry.canManage(current, me, op) || !registry.delete(current)) { deny(player, "logistics.channel.error.manage"); return; }
                Network.clearCaches();
                target.set().accept(0);
            }
        }
        ChannelSync.sendAll(server);
    }

    private static void deny(ServerPlayer player, String key) { player.sendOverlayMessage(Component.translatable(key)); }
}
