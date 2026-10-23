package dev.alan.mineworld;

import java.util.Map;
import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MineWorldMod implements ModInitializer {
    public static final String MOD_ID = "mineworld";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
    public static Identifier id(String name) { return Identifier.fromNamespaceAndPath(MOD_ID, name); }

    /** Biome of chunks generated before the caves existed; old worlds still use it. */
    public static final ResourceKey<Biome> BIOME = ResourceKey.create(Registries.BIOME, id("mine"));
    /** Cave ecosystems and their (temperature, humidity) climate points; keep in sync with CAVE_BIOMES in scripts/gen_mineworld_data.py. */
    public static final Map<ResourceKey<Biome>, float[]> CAVE_BIOMES = Map.of(
        ResourceKey.create(Registries.BIOME, id("crystal_cave")), new float[]{-0.3F, -0.3F},
        ResourceKey.create(Registries.BIOME, id("lush_cave")), new float[]{-0.3F, 0.3F},
        ResourceKey.create(Registries.BIOME, id("dripstone_cave")), new float[]{0.3F, -0.3F},
        ResourceKey.create(Registries.BIOME, id("ore_nest")), new float[]{0.3F, 0.3F},
        ResourceKey.create(Registries.BIOME, id("sulfur_cave")), new float[]{0.0F, 0.0F},
        ResourceKey.create(Registries.BIOME, id("deep_dark")), new float[]{0.6F, 0.0F});
    public static final ResourceKey<NoiseGeneratorSettings> NOISE_SETTINGS = ResourceKey.create(Registries.NOISE_SETTINGS, id("mine"));
    public static final ResourceKey<DimensionType> DIMENSION_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, id("mine"));

    private static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(name));
        B block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id(name));
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }

    public static final DataComponentType<String> WORLD_ID = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("world_id"),
        DataComponentType.<String>builder().persistent(com.mojang.serialization.Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

    public static final WorldCauldronBlock WORLD_CAULDRON = block("world_cauldron", WorldCauldronBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.CAULDRON).mapColor(MapColor.COLOR_CYAN).lightLevel(s -> 6));
    public static final ReturnBlock RETURN_BLOCK = block("return_block", ReturnBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0F, 3600000.0F).sound(SoundType.DEEPSLATE)
            .lightLevel(s -> 8).noLootTable());

    public static final BlockEntityType<WorldCauldronBlockEntity> WORLD_CAULDRON_ENTITY = Registry.register(
        BuiltInRegistries.BLOCK_ENTITY_TYPE, id("world_cauldron"),
        new BlockEntityType<>(WorldCauldronBlockEntity::new, java.util.Set.of(WORLD_CAULDRON)));

    @Override public void onInitialize() {
        AncientCavern.init();
        dev.alan.mineworld.city.City.init();
        dev.alan.mineworld.mob.Mobs.init();
        ServerTickEvents.END_SERVER_TICK.register(MineTravel::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(DynamicLevels::restoreAll);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { DynamicLevels.clear(); MineTravel.forget(); });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
            entries.accept(WORLD_CAULDRON);
            entries.accept(RETURN_BLOCK);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(entries -> entries.accept(dev.alan.mineworld.city.City.BOUNTY_PROOF));
        LOG.info("Mine World loaded");
    }
}
