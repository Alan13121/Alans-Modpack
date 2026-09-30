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
    public static final InputInterfaceBlock INPUT_INTERFACE = block("input_interface", InputInterfaceBlock::new, metal(MapColor.COLOR_CYAN));
    public static final OutputInterfaceBlock OUTPUT_INTERFACE = block("output_interface", OutputInterfaceBlock::new, metal(MapColor.COLOR_ORANGE));

    public static final FarmInterfaceBlock FARM_INTERFACE = block("farm_interface", FarmInterfaceBlock::new, metal(MapColor.PLANT));

    public static final BlockEntityType<FarmInterfaceBlockEntity> FARM_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
        id("farm_interface"), new BlockEntityType<>(FarmInterfaceBlockEntity::new, Set.of(FARM_INTERFACE)));
    public static final BlockEntityType<InputInterfaceBlockEntity> INPUT_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
        id("input_interface"), new BlockEntityType<>(InputInterfaceBlockEntity::new, Set.of(INPUT_INTERFACE)));
    public static final BlockEntityType<OutputInterfaceBlockEntity> OUTPUT_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
        id("output_interface"), new BlockEntityType<>(OutputInterfaceBlockEntity::new, Set.of(OUTPUT_INTERFACE)));
    public static final BlockEntityType<CellBlockEntity> CELL_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("cell"),
        new BlockEntityType<>(CellBlockEntity::new, new java.util.HashSet<net.minecraft.world.level.block.Block>(CELLS)));
    public static final DataComponentType<CellData> CELL_DATA = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("cell_contents"),
        DataComponentType.<CellData>builder().persistent(CellData.CODEC).networkSynchronized(CellData.STREAM_CODEC).build());
    public static final MenuType<TerminalMenu> TERMINAL_MENU = Registry.register(BuiltInRegistries.MENU, id("terminal"),
        new MenuType<>(TerminalMenu::new, FeatureFlagSet.of()));
    public static final MenuType<CraftingTerminalMenu> CRAFTING_TERMINAL_MENU = Registry.register(BuiltInRegistries.MENU, id("crafting_terminal"),
        new MenuType<>(CraftingTerminalMenu::new, FeatureFlagSet.of()));
    public static final MenuType<InterfaceMenu> INPUT_MENU = Registry.register(BuiltInRegistries.MENU, id("input_interface"),
        new MenuType<>(InterfaceMenu::input, FeatureFlagSet.of()));
    public static final MenuType<InterfaceMenu> FARM_MENU = Registry.register(BuiltInRegistries.MENU, id("farm_interface"),
        new MenuType<>(InterfaceMenu::farm, FeatureFlagSet.of()));
    public static final MenuType<InterfaceMenu> OUTPUT_MENU = Registry.register(BuiltInRegistries.MENU, id("output_interface"),
        new MenuType<>(InterfaceMenu::output, FeatureFlagSet.of()));

    @Override public void onInitialize() {
        // Cached networks must not outlive their world, and chunk loads can change what a network touches.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> Network.clearCaches());
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> Network.invalidateChunk(level, chunk.getPos()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> Network.invalidateChunk(level, chunk.getPos()));
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id("cell_compress"), CellCompressRecipe.SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id("cell_decompress"), CellDecompressRecipe.SERIALIZER);
        PayloadTypeRegistry.clientboundPlay().register(TerminalSnapshot.TYPE, TerminalSnapshot.CODEC);
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
            entries.accept(INPUT_INTERFACE);
            entries.accept(OUTPUT_INTERFACE);
            entries.accept(FARM_INTERFACE);
        });
        LOG.info("Logistics loaded");
    }
}
