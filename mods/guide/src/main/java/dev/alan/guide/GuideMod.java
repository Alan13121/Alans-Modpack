package dev.alan.guide;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuideMod implements ModInitializer {
    public static final String MOD_ID = "guide";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
    public static Identifier id(String name) { return Identifier.fromNamespaceAndPath(MOD_ID, name); }
    public static final Item HANDBOOK = Registry.register(BuiltInRegistries.ITEM, id("handbook"),
        new HandbookItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("handbook"))).stacksTo(1).rarity(Rarity.UNCOMMON)));
    /** Set by the client entrypoint to open the handbook screen; does nothing on a dedicated server. */
    public static volatile Runnable openHandbook = () -> {};

    @Override public void onInitialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(HANDBOOK));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Handbook.onJoin(handler.getPlayer()));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) ->
            dispatcher.register(Commands.literal("guidebook").executes(c -> {
                var player = c.getSource().getPlayerOrException();
                if (!Handbook.give(player)) {
                    c.getSource().sendFailure(Component.translatable("guide.handbook.have"));
                    return 0;
                }
                c.getSource().sendSuccess(() -> Component.translatable("guide.handbook.given"), false);
                return 1;
            })));
        LOG.info("Guide loaded");
    }
}
