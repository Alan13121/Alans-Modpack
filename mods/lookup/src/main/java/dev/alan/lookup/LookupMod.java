package dev.alan.lookup;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LookupMod implements ModInitializer {
    public static final String MOD_ID = "lookup";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
    /** Keeps each packet far below the 1 MiB custom payload limit, even with large modded recipe sets. */
    private static final int BATCH = 64;
    public static Identifier id(String name) { return Identifier.fromNamespaceAndPath(MOD_ID, name); }

    @Override public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(RecipeSync.TYPE, RecipeSync.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BrewingSync.TYPE, BrewingSync.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(LootSync.TYPE, LootSync.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(GiveItem.TYPE, GiveItem.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(GiveItem.TYPE, (payload, context) -> give(context.player(), payload.stack()));
        // Fires for each player on join and after /reload.
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> {
            sendRecipes(player);
            sendLoot(player);
        });
    }

    /** Creative players, the owner of a singleplayer world, and operators may cheat; nobody else. */
    static boolean mayCheat(ServerPlayer player) {
        var server = player.level().getServer();
        return player.isCreative() || server.isSingleplayerOwner(player.nameAndId())
            || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private static void give(ServerPlayer player, ItemStack requested) {
        if (requested.isEmpty() || !mayCheat(player)) return;
        ItemStack stack = requested.copyWithCount(Math.min(requested.getCount(), requested.getMaxStackSize()));
        if (!player.getInventory().add(stack)) player.drop(stack, false, Prediction.PREDICTED);
    }

    private static void sendLoot(ServerPlayer player) {
        List<LootSync.Source> all = LootExtractor.extract(player.level().getServer());
        if (all.isEmpty()) {
            ServerPlayNetworking.send(player, new LootSync(true, List.of()));
            return;
        }
        for (int i = 0; i < all.size(); i += BATCH)
            ServerPlayNetworking.send(player, new LootSync(i == 0, all.subList(i, Math.min(all.size(), i + BATCH))));
    }

    /** Every recipe from every mod that can describe itself as a display. Vanilla only sends unlocked ones. */
    private static void sendRecipes(ServerPlayer player) {
        var manager = player.level().getServer().getRecipeManager();
        List<RecipeDisplayEntry> all = new ArrayList<>();
        List<BrewingSync.Entry> brewing = new ArrayList<>();
        for (var holder : manager.getRecipes()) {
            manager.listDisplaysForRecipe(holder.id(), all::add);
            if (holder.value() instanceof BrewingRecipe recipe)
                brewing.add(new BrewingSync.Entry(recipe.getInput(), recipe.getReagent(), recipe.getOutput()));
        }
        ServerPlayNetworking.send(player, new BrewingSync(brewing));
        if (all.isEmpty()) {
            ServerPlayNetworking.send(player, new RecipeSync(true, List.of()));
            return;
        }
        for (int i = 0; i < all.size(); i += BATCH)
            ServerPlayNetworking.send(player, new RecipeSync(i == 0, all.subList(i, Math.min(all.size(), i + BATCH))));
        LOG.debug("Sent {} recipe displays to {}", all.size(), player.getPlainTextName());
    }
}
