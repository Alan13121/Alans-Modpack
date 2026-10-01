package dev.alan.combat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CombatMod implements ModInitializer {
    public static final String MOD_ID = "combat";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
    public static Identifier id(String name) { return Identifier.fromNamespaceAndPath(MOD_ID, name); }

    /** What is in the player's trinket slots. Kept through death; only the owner needs it. */
    public static final AttachmentType<TrinketSlots> SLOTS = AttachmentRegistry.create(id("trinkets"), b -> b
        .persistent(TrinketSlots.CODEC)
        .copyOnDeath()
        .syncWith(TrinketSlots.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    // The first three trinkets. Registration order is the slot order of the trinket screen.
    public static final Item FEATHER_CHARM = Trinkets.register("feather_charm", null, true);
    public static final Item NIGHT_VISION_CHARM = Trinkets.register("night_vision_charm", MobEffects.NIGHT_VISION, false);
    public static final Item GILLS_CHARM = Trinkets.register("gills_charm", MobEffects.WATER_BREATHING, false);
    public static final Item TRINKET_BAG = Registry.register(BuiltInRegistries.ITEM, id("trinket_bag"),
        new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("trinket_bag"))).stacksTo(16)));

    public static final MenuType<TrinketMenu> MENU = Registry.register(BuiltInRegistries.MENU, id("trinkets"),
        new MenuType<>(TrinketMenu::new, FeatureFlagSet.of()));

    @Override public void onInitialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(entries -> {
            for (Trinket trinket : Trinkets.all()) entries.accept(trinket.item());
            entries.accept(TRINKET_BAG);
        });

        PayloadTypeRegistry.serverboundPlay().register(OpenTrinkets.TYPE, OpenTrinkets.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(OpenTrinkets.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> new TrinketMenu(containerId, inventory),
                Component.translatable("container.combat.trinkets")));
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) Trinkets.tick(player, server.getTickCount());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Trinkets.forget(handler.getPlayer()));

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
            !(entity instanceof Player player && source.is(DamageTypeTags.IS_FALL) && Trinkets.wornFallImmune(player)));
        LOG.info("Combat loaded");
    }
}
