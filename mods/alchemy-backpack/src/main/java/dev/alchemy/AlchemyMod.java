package dev.alchemy;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;

public final class AlchemyMod implements ModInitializer {
    public static Identifier id(String name) { return Identifier.fromNamespaceAndPath("alchemy_backpack", name); }
    public static final DataComponentType<BagData> DATA = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("contents"),
        DataComponentType.<BagData>builder().persistent(BagData.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(BagData.CODEC)).build());
    public static final Item BACKPACK = Registry.register(BuiltInRegistries.ITEM, id("backpack"),
        new BackpackItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("backpack"))).stacksTo(1).component(DATA, BagData.EMPTY)));
    public static final MenuType<BagMenu> MENU = Registry.register(BuiltInRegistries.MENU, id("backpack"),
        new MenuType<>(BagMenu::new, FeatureFlagSet.of()));
    @Override public void onInitialize() {
        EnergyValues.load();
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(BACKPACK));
        PayloadTypeRegistry.clientboundPlay().register(EnergySync.TYPE, EnergySync.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            ServerPlayNetworking.send(handler.getPlayer(), new EnergySync(EnergyValues.defaultValue(), EnergyValues.values())));
    }
}
