package dev.alan.combat;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Hands out quest book advancements. Does nothing when the guide mod (and so the advancement) is not installed. */
public final class QuestBook {
    private QuestBook() {}

    private static net.minecraft.advancements.AdvancementHolder holder(ServerPlayer player, String path) {
        return player.level().getServer().getAdvancements().get(Identifier.fromNamespaceAndPath("guide", path));
    }

    /** Awards {@code guide:<path>}; its only criterion is called "done". */
    public static void grant(ServerPlayer player, String path) {
        var holder = holder(player, path);
        if (holder != null) player.getAdvancements().award(holder, "done");
    }

    public static boolean has(ServerPlayer player, String path) {
        var holder = holder(player, path);
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }
}
