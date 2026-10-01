package dev.alan.shapeshift.api;

import dev.alan.shapeshift.Forms;
import dev.alan.shapeshift.FormDefinitions;
import dev.alan.shapeshift.ShapeshiftConfig;
import dev.alan.shapeshift.Shapeshifter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/** Read-only questions other mods may ask about the form collection. All of them run on the server thread. */
public final class FormsApi {
    private FormsApi() {}

    /** Ids of every form that can be unlocked: data-defined, not blocked in the config, and an entity that still exists. */
    public static List<String> collectable() {
        var ids = new ArrayList<String>();
        for (String id : FormDefinitions.serverDefinitions().keySet()) {
            if (ShapeshiftConfig.isBlocked(id)) continue;
            if (Forms.type(id).filter(Forms::isLivingForm).isEmpty()) continue;
            ids.add(id);
        }
        return ids;
    }

    /** The collectable forms this player has not unlocked yet. */
    public static List<String> missing(ServerPlayer player) {
        var unlocks = Shapeshifter.unlocks(player);
        return collectable().stream().filter(id -> !unlocks.contains(id)).toList();
    }

    public static int unlockedCount(ServerPlayer player) {
        return collectable().size() - missing(player).size();
    }

    /** Re-applies the player's current form, e.g. after something changed what the form should grant. */
    public static void refresh(ServerPlayer player) { Shapeshifter.refresh(player); }
}
