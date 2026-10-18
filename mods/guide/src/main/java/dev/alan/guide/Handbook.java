package dev.alan.guide;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** The handbook item: handed out once on first join, again after a respawn without it and by /guidebook, never more than one on a player. */
public final class Handbook {
    /** Entity tag that remembers the player has been given their first book, so it is never re-issued on later joins. */
    static final String GIVEN_TAG = "guide_handbook_given";

    private Handbook() {}

    public static ItemStack create() {
        return new ItemStack(GuideMod.HANDBOOK);
    }

    public static boolean isHandbook(ItemStack stack) {
        return stack.is(GuideMod.HANDBOOK);
    }

    public static boolean has(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) if (isHandbook(inventory.getItem(i))) return true;
        return false;
    }

    /** Gives one book unless the player already carries one. Returns whether a book was given. */
    public static boolean give(ServerPlayer player) {
        if (has(player)) return false;
        player.getInventory().placeItemBackInInventory(create(), Prediction.PREDICTED);
        return true;
    }

    /** After a respawn: a player who lost the book with their inventory gets a new one; a player who kept it gets nothing. */
    public static void onRespawn(ServerPlayer player) {
        give(player);
    }

    /** On join: the first time only, hand out the book. */
    public static void onJoin(ServerPlayer player) {
        if (player.entityTags().contains(GIVEN_TAG)) return;
        player.addTag(GIVEN_TAG);
        give(player);
    }
}
