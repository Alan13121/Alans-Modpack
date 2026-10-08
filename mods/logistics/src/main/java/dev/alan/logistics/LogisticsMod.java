package dev.alan.logistics;

import java.util.Set;
import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LogisticsMod implements ModInitializer {
    public static final String MOD_ID = "logistics";
    /** True when the alchemy backpack mod is installed: its backpacks can then carry a channel card. */
    public static final boolean ALCHEMY = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("alchemy_backpack");
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
    public static Identifier id(String name) { return Identifier.fromNamespaceAndPath(MOD_ID, name); }

    private static BlockBehaviour.Properties metal(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    private static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(name));
        B block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id(name));
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }

    public static final ControllerBlock CONTROLLER = block("controller", ControllerBlock::new, metal(MapColor.COLOR_LIGHT_BLUE));
    public static final CableBlock CABLE = block("cable", CableBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(1.0F, 3.0F).sound(SoundType.COPPER).noOcclusion());
    public static final TerminalBlock TERMINAL = block("terminal", TerminalBlock::new, metal(MapColor.COLOR_GRAY));
    /** Tier 1 is "cell", the others "cell_2" .. "cell_4". */
    public static final java.util.List<CellBlock> CELLS = cells();
    public static final CellBlock CELL = CELLS.get(0);

    private static java.util.List<CellBlock> cells() {
        MapColor[] colors = {MapColor.COLOR_PURPLE, MapColor.COLOR_BLUE, MapColor.GOLD, MapColor.SNOW};
        java.util.List<CellBlock> list = new java.util.ArrayList<>();
        for (int tier = 1; tier <= CellBlock.TIERS; tier++) {
            final int t = tier;
            list.add(block(tier == 1 ? "cell" : "cell_" + tier, p -> new CellBlock(t, p), metal(colors[tier - 1])));
        }
        return list;
    }

    public static final CraftingTerminalBlock CRAFTING_TERMINAL = block("crafting_terminal", CraftingTerminalBlock::new, metal(MapColor.COLOR_BROWN));
    public static final StorageInterfaceBlock STORAGE_INTERFACE = block("storage_interface", StorageInterfaceBlock::new, metal(MapColor.COLOR_CYAN));
    public static final ConduitBlock CONDUIT = block("conduit", ConduitBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0F, 3.0F).sound(SoundType.COPPER).noOcclusion());

    public static final FarmInterfaceBlock FARM_INTERFACE = block("farm_interface", FarmInterfaceBlock::new, metal(MapColor.PLANT));

    public static final BlockEntityType<FarmInterfaceBlockEntity> FARM_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
        id("farm_interface"), new BlockEntityType<>(FarmInterfaceBlockEntity::new, Set.of(FARM_INTERFACE)));
    public static final AutoCrafterBlock AUTOCRAFTER = block("autocrafter", AutoCrafterBlock::new, metal(MapColor.COLOR_BROWN));
    public static final BlockEntityType<AutoCrafterBlockEntity> AUTOCRAFTER_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
        id("autocrafter"), new BlockEntityType<>(AutoCrafterBlockEntity::new, Set.of(AUTOCRAFTER)));
    public static final BlockEntityType<StorageInterfaceBlockEntity> INTERFACE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
        id("storage_interface"), new BlockEntityType<>(StorageInterfaceBlockEntity::new, Set.of(STORAGE_INTERFACE)));
    public static final BlockEntityType<ConduitBlockEntity> CONDUIT_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
        id("conduit"), new BlockEntityType<>(ConduitBlockEntity::new, Set.of(CONDUIT)));
    public static final BlockEntityType<CellBlockEntity> CELL_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("cell"),
        new BlockEntityType<>(CellBlockEntity::new, new java.util.HashSet<net.minecraft.world.level.block.Block>(CELLS)));
    public static final DataComponentType<CellData> CELL_DATA = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("cell_contents"),
        DataComponentType.<CellData>builder().persistent(CellData.CODEC).networkSynchronized(CellData.STREAM_CODEC).build());
    public static final MenuType<TerminalMenu> TERMINAL_MENU = Registry.register(BuiltInRegistries.MENU, id("terminal"),
        new MenuType<>(TerminalMenu::new, FeatureFlagSet.of()));
    public static final MenuType<CraftingTerminalMenu> CRAFTING_TERMINAL_MENU = Registry.register(BuiltInRegistries.MENU, id("crafting_terminal"),
        new MenuType<>(CraftingTerminalMenu::new, FeatureFlagSet.of()));
    public static final MenuType<AutoCrafterMenu> AUTOCRAFTER_MENU = Registry.register(BuiltInRegistries.MENU, id("autocrafter"),
        new MenuType<>(AutoCrafterMenu::client, FeatureFlagSet.of()));
    public static final MenuType<InterfaceMenu> FARM_MENU = Registry.register(BuiltInRegistries.MENU, id("farm_interface"),
        new MenuType<>(InterfaceMenu::farm, FeatureFlagSet.of()));
    public static final MenuType<InterfaceMenu> INTERFACE_MENU = Registry.register(BuiltInRegistries.MENU, id("storage_interface"),
        new MenuType<>(InterfaceMenu::storage, FeatureFlagSet.of()));

    // ---- channels, energy, wireless and teleporting ------------------------------------------------------------

    private static Item plainItem(String name, java.util.function.Function<Item.Properties, Item> factory, int stack) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key).stacksTo(stack)));
    }

    /** The channel written on a card or wireless terminal. */
    public static final DataComponentType<Integer> CHANNEL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("channel"),
        DataComponentType.<Integer>builder().persistent(com.mojang.serialization.Codec.intRange(1, ChannelCardItem.MAX_CHANNEL))
            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build());
    /** The channel an alchemy backpack was set to directly (a card in its slot is the fallback). */
    public static final DataComponentType<Integer> BAG_CHANNEL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("bag_channel"),
        DataComponentType.<Integer>builder().persistent(com.mojang.serialization.Codec.intRange(1, ChannelCardItem.MAX_CHANNEL))
            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build());
    /** The channel card inside an alchemy backpack. */
    public static final DataComponentType<net.minecraft.world.item.ItemStack> BAG_CARD = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("bag_card"),
        DataComponentType.<net.minecraft.world.item.ItemStack>builder().persistent(net.minecraft.world.item.ItemStack.CODEC)
            .networkSynchronized(net.minecraft.world.item.ItemStack.STREAM_CODEC).build());

    public static final Item CHANNEL_CARD = plainItem("channel_card", ChannelCardItem::new, 64);
    public static final Item WIRELESS_TERMINAL = plainItem("wireless_terminal", WirelessTerminalItem::new, 1);

    public static final DeviceBlock CHANNEL_BLOCK = block("channel", p -> new DeviceBlock(p, DeviceMenu.Kind.CHANNEL, () -> LogisticsMod.CHANNEL_ENTITY, null), metal(MapColor.COLOR_LIGHT_GREEN));
    public static final DeviceBlock ANTENNA = block("antenna", p -> new DeviceBlock(p, DeviceMenu.Kind.ANTENNA, () -> LogisticsMod.ANTENNA_ENTITY, null), metal(MapColor.METAL));
    public static final DeviceBlock SOLAR = block("solar_generator", p -> new DeviceBlock(p, null, () -> LogisticsMod.SOLAR_ENTITY, SolarGeneratorBlockEntity::serverTick), metal(MapColor.COLOR_BLUE));
    public static final DeviceBlock COAL_GENERATOR = block("coal_generator", p -> new DeviceBlock(p, DeviceMenu.Kind.COAL, () -> LogisticsMod.COAL_ENTITY, CoalGeneratorBlockEntity::serverTick), metal(MapColor.COLOR_BLACK));
    public static final ChunkLoaderBlock CHUNK_LOADER = block("chunk_loader", ChunkLoaderBlock::new, metal(MapColor.COLOR_MAGENTA));
    public static final TeleporterBlock TELEPORTER = block("teleporter", TeleporterBlock::new,
        metal(MapColor.COLOR_PURPLE).lightLevel(state -> state.getValue(TeleporterBlock.OPEN) ? 10 : 0));

    public static final BlockEntityType<ChannelBlockEntity> CHANNEL_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
        id("channel"), new BlockEntityType<>(ChannelBlockEntity::new, Set.of(CHANNEL_BLOCK)));
    public static final BlockEntityType<AntennaBlockEntity> ANTENNA_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
        id("antenna"), new BlockEntityType<>(AntennaBlockEntity::new, Set.of(ANTENNA)));
    public static final BlockEntityType<SolarGeneratorBlockEntity> SOLAR_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
        id("solar_generator"), new BlockEntityType<>(SolarGeneratorBlockEntity::new, Set.of(SOLAR)));
    public static final BlockEntityType<CoalGeneratorBlockEntity> COAL_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
        id("coal_generator"), new BlockEntityType<>(CoalGeneratorBlockEntity::new, Set.of(COAL_GENERATOR)));
    public static final BlockEntityType<ChunkLoaderBlockEntity> LOADER_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
        id("chunk_loader"), new BlockEntityType<>(ChunkLoaderBlockEntity::new, Set.of(CHUNK_LOADER)));
    public static final BlockEntityType<TeleporterBlockEntity> TELEPORTER_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
        id("teleporter"), new BlockEntityType<>(TeleporterBlockEntity::new, Set.of(TELEPORTER)));

    public static final MenuType<DeviceMenu> CHANNEL_MENU = Registry.register(BuiltInRegistries.MENU, id("channel"),
        new MenuType<>((id, inv) -> DeviceMenu.client(id, inv, DeviceMenu.Kind.CHANNEL), FeatureFlagSet.of()));
    public static final MenuType<DeviceMenu> ANTENNA_MENU = Registry.register(BuiltInRegistries.MENU, id("antenna"),
        new MenuType<>((id, inv) -> DeviceMenu.client(id, inv, DeviceMenu.Kind.ANTENNA), FeatureFlagSet.of()));
    public static final MenuType<DeviceMenu> COAL_MENU = Registry.register(BuiltInRegistries.MENU, id("coal_generator"),
        new MenuType<>((id, inv) -> DeviceMenu.client(id, inv, DeviceMenu.Kind.COAL), FeatureFlagSet.of()));
    public static final MenuType<TeleporterMenu> TELEPORTER_MENU = Registry.register(BuiltInRegistries.MENU, id("teleporter"),
        new MenuType<>(TeleporterMenu::client, FeatureFlagSet.of()));

    @Override public void onInitialize() {
        if (ALCHEMY) dev.alchemy.BagExtras.addSlot(BagCardSlot::new);
        // Cached networks must not outlive their world, and chunk loads can change what a network touches.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            Network.clearCaches();
            Grid.clear();
            ChannelRegistry.setCurrent(null);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> Network.invalidateChunk(level, chunk.getPos()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> Network.invalidateChunk(level, chunk.getPos()));
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id("cell_compress"), CellCompressRecipe.SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id("cell_decompress"), CellDecompressRecipe.SERIALIZER);
        PayloadTypeRegistry.clientboundPlay().register(TerminalSnapshot.TYPE, TerminalSnapshot.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TeleporterList.TYPE, TeleporterList.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ChannelSync.TYPE, ChannelSync.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ChannelAction.TYPE, ChannelAction.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ChannelAction.TYPE, (payload, context) -> ChannelActions.handle(context.player(), payload));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ChannelSync.send(handler.getPlayer()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(ChunkLoaders::check);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> ChannelRegistry.setCurrent(ChannelRegistry.get(server)));
        PayloadTypeRegistry.serverboundPlay().register(TerminalAction.TYPE, TerminalAction.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TerminalAction.TYPE, (payload, context) -> {
            var menu = context.player().containerMenu;
            if (menu instanceof WarehouseMenu warehouse && menu.containerId == payload.containerId() && menu.stillValid(context.player()))
                warehouse.warehouse().handle(menu, context.player(), payload.kind(), payload.stack());
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
            entries.accept(CONTROLLER);
            entries.accept(CABLE);
            entries.accept(TERMINAL);
            entries.accept(CRAFTING_TERMINAL);
            for (CellBlock cell : CELLS) entries.accept(cell);
            entries.accept(STORAGE_INTERFACE);
            entries.accept(CONDUIT);
            entries.accept(FARM_INTERFACE);
            entries.accept(AUTOCRAFTER);
            entries.accept(CHANNEL_BLOCK);
            entries.accept(ANTENNA);
            entries.accept(SOLAR);
            entries.accept(COAL_GENERATOR);
            entries.accept(TELEPORTER);
            entries.accept(CHUNK_LOADER);
            entries.accept(CHANNEL_CARD);
            entries.accept(WIRELESS_TERMINAL);
        });
        LOG.info("Logistics loaded");
    }
}
