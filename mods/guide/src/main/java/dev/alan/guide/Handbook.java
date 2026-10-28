package dev.alan.guide;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** The handbook opens from a button in the inventory screen; the item is kept only for books players already carry. */
public final class Handbook {
    /** Entity tag that remembers the player has seen the first-join hint (and, in older worlds, was given a book). */
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

    /** On join: the first time only, tell the player where the handbook is. */
    public static void onJoin(ServerPlayer player) {
        if (player.entityTags().contains(GIVEN_TAG)) return;
        player.addTag(GIVEN_TAG);
        player.sendSystemMessage(Component.translatable("guide.handbook.hint"));
    }
}
