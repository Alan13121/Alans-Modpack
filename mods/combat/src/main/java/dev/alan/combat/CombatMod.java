package dev.alan.combat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;
import dev.alan.combat.boss.FormAltarBlock;
import dev.alan.combat.boss.FormKingFights;
import java.util.function.Function;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
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
    public static final Item FEATHER_CHARM = Trinkets.register("feather_charm", Trinkets.spec().perk(Trinket.Perk.FALL_IMMUNE));
    public static final Item NIGHT_VISION_CHARM = Trinkets.register("night_vision_charm", Trinkets.spec().effect(MobEffects.NIGHT_VISION));
    public static final Item GILLS_CHARM = Trinkets.register("gills_charm", Trinkets.spec().effect(MobEffects.WATER_BREATHING));
    /** Shapeshift abilities recharge faster. */
    public static final Item SKILL_CHARM = Trinkets.register("skill_charm", Trinkets.spec().perk(Trinket.Perk.SKILL_FOCUS));
    public static final Item FIRE_RING = Trinkets.register("fire_ring", Trinkets.spec().effect(MobEffects.FIRE_RESISTANCE));
    public static final Item SPEED_BUCKLE = Trinkets.register("speed_buckle",
        Trinkets.spec().boost(Attributes.MOVEMENT_SPEED, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    public static final Item SPRING_INSOLE = Trinkets.register("spring_insole",
        Trinkets.spec().boost(Attributes.JUMP_STRENGTH, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    public static final Item REGEN_CHARM = Trinkets.register("regen_charm", Trinkets.spec().perk(Trinket.Perk.REGEN));
    public static final Item MAGNET = Trinkets.register("magnet", Trinkets.spec().perk(Trinket.Perk.MAGNET));
    public static final Item BLAST_WARD = Trinkets.register("blast_ward", Trinkets.spec().perk(Trinket.Perk.BLAST_WARD));
    public static final Item THORNS_RING = Trinkets.register("thorns_ring", Trinkets.spec().perk(Trinket.Perk.THORNS));
    public static final Item HUNTER_CHARM = Trinkets.register("hunter_charm", Trinkets.spec().perk(Trinket.Perk.HUNTER));
    /** The endgame trinket: no ability cooldown, stronger abilities, and a shapeshifted wearer keeps at least their human maximum health. */
    public static final Item MASTER_CHARM = Trinkets.register("master_charm", Trinkets.spec().perk(Trinket.Perk.FORM_MASTER));
    public static final Item TRINKET_BAG = Registry.register(BuiltInRegistries.ITEM, id("trinket_bag"),
        new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("trinket_bag"))).stacksTo(16)));

    /** Ore levels an armor piece has received at the anvil. */
    public static final DataComponentType<Upgrades> UPGRADES = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("upgrades"),
        DataComponentType.<Upgrades>builder().persistent(Upgrades.CODEC).networkSynchronized(Upgrades.STREAM_CODEC).build());

    /** Upgrades of a bow, set at the anvil. */
    public static final DataComponentType<Upgrades> BOW_UPGRADES = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("bow_upgrades"),
        DataComponentType.<Upgrades>builder().persistent(Upgrades.CODEC).networkSynchronized(Upgrades.STREAM_CODEC).build());
    /** The bow upgrades an arrow in flight carries; dropped when they have fired. Not saved. */
    public static final AttachmentType<Upgrades> ARROW_MODS = AttachmentRegistry.create(id("arrow_mods"));

    /** Drops from the True Form King, one per fighter; the material for the endgame trinket. */
    public static final Item FORM_CORE = Registry.register(BuiltInRegistries.ITEM, id("form_core"),
        new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("form_core"))).rarity(net.minecraft.world.item.Rarity.EPIC)));
    /** Drops from the Form King, 10 to 20 per fighter; made into marks and seeds. */
    public static final Item FORM_SHARD = Registry.register(BuiltInRegistries.ITEM, id("form_shard"),
        new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("form_shard"))).rarity(net.minecraft.world.item.Rarity.RARE)));
    /** Nine shards; offered at the altar to call the True Form King. */
    public static final Item FORM_SEED = Registry.register(BuiltInRegistries.ITEM, id("form_seed"),
        new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("form_seed"))).rarity(net.minecraft.world.item.Rarity.EPIC).stacksTo(16)));
    /** The rare form a mark is set to unlock. */
    public static final DataComponentType<String> MARK_TARGET = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("mark_target"),
        DataComponentType.<String>builder().persistent(com.mojang.serialization.Codec.STRING)
            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8).build());
    /** Unlocks a rare form of your choice. */
    public static final Item FORM_MARK = Registry.register(BuiltInRegistries.ITEM, id("form_mark"),
        new FormMarkItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("form_mark"))).rarity(net.minecraft.world.item.Rarity.RARE).stacksTo(16)));

    public static final FormAltarBlock FORM_ALTAR = registerBlock("form_altar", FormAltarBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(5.0F, 1200.0F).sound(SoundType.AMETHYST).lightLevel(s -> 7));

    /** Marks the body of a running Form King fight with the key of its altar. Saved, so a leftover body can be found and removed. */
    public static final AttachmentType<String> BOSS_MARK = AttachmentRegistry.create(id("boss"), b -> b.persistent(com.mojang.serialization.Codec.STRING));
    /** Marks a projectile the Form King launched; such fireballs never light or break blocks. Not saved. */
    public static final AttachmentType<Boolean> BOSS_SHOT = AttachmentRegistry.create(id("boss_shot"));

    private static <B extends Block> B registerBlock(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(name));
        B block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id(name));
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }

    public static final MenuType<TrinketMenu> MENU = Registry.register(BuiltInRegistries.MENU, id("trinkets"),
        new MenuType<>(TrinketMenu::new, FeatureFlagSet.of()));

    @Override public void onInitialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(entries -> {
            for (Trinket trinket : Trinkets.all()) entries.accept(trinket.item());
            entries.accept(TRINKET_BAG);
            entries.accept(FORM_SHARD);
            entries.accept(FORM_MARK);
            entries.accept(FORM_SEED);
            entries.accept(FORM_CORE);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(FORM_ALTAR));

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
            !(entity instanceof Player player && source.is(DamageTypeTags.IS_FALL) && Trinkets.has(player, Trinket.Perk.FALL_IMMUNE)));
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof AbstractArrow arrow) BowEffects.onArrowLoaded(arrow);
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (source.getDirectEntity() instanceof AbstractArrow arrow) BowEffects.onDamaged(entity, arrow, damageTaken);
            if (entity instanceof ServerPlayer player) Trinkets.reflect(player, source, damageTaken);
        });
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("shapeshift")) ShapeshiftLink.register();
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) ->
            dev.alan.combat.boss.FormKingCommands.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(FormKingFights::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> FormKingFights.shutdown());
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> FormKingFights.onEntityLoad(entity));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> FormKingFights.allowDamage(entity, source));
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) ->
            FormKingFights.afterDamage(entity, source, damageTaken));
        LOG.info("Combat loaded");
    }
}
