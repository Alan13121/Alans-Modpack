package dev.alan.mineworld.mob;

import dev.alan.mineworld.DynamicLevels;
import dev.alan.mineworld.MineWorldMod;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** The mine world's own creatures: registration, spawn rules, spawn eggs and the ore worm trigger. */
public final class Mobs {
    private Mobs() {}

    /** One in this many ore blocks a survival player mines in a mine world lets out an ore worm. */
    public static final int ORE_WORM_CHANCE = 30;

    private static <T extends Mob> EntityType<T> entity(String name, EntityType.EntityFactory<T> factory, MobCategory category,
                                                        float width, float height, float eyeHeight, int tracking) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MineWorldMod.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.of(factory, category)
            .sized(width, height).eyeHeight(eyeHeight).clientTrackingRange(tracking).build(key));
    }

    // Sizes copy the vanilla mob each one is drawn as.
    public static final EntityType<GlowBat> GLOW_BAT = entity("glow_bat", GlowBat::new, MobCategory.AMBIENT, 0.5F, 0.9F, 0.45F, 5);
    public static final EntityType<GemSlime> GEM_SLIME = Registry.register(BuiltInRegistries.ENTITY_TYPE, MineWorldMod.id("gem_slime"),
        EntityType.Builder.of(GemSlime::new, MobCategory.MONSTER).sized(0.52F, 0.52F).eyeHeight(0.325F).spawnDimensionsScale(4.0F)
            .clientTrackingRange(10).build(ResourceKey.create(Registries.ENTITY_TYPE, MineWorldMod.id("gem_slime"))));
    public static final EntityType<OreGolem> ORE_GOLEM = entity("ore_golem", OreGolem::new, MobCategory.MONSTER, 1.4F, 2.7F, 2.3F, 10);
    public static final EntityType<OreWorm> ORE_WORM = entity("ore_worm", OreWorm::new, MobCategory.MONSTER, 0.4F, 0.3F, 0.13F, 8);

    private static Item egg(String name, EntityType<?> type) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MineWorldMod.id(name + "_spawn_egg"));
        return Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().setId(key).spawnEgg(type)));
    }

    public static final Item GLOW_BAT_EGG = egg("glow_bat", GLOW_BAT);
    public static final Item GEM_SLIME_EGG = egg("gem_slime", GEM_SLIME);
    public static final Item ORE_GOLEM_EGG = egg("ore_golem", ORE_GOLEM);
    public static final Item ORE_WORM_EGG = egg("ore_worm", ORE_WORM);
    public static final List<Item> EGGS = List.of(GLOW_BAT_EGG, GEM_SLIME_EGG, ORE_GOLEM_EGG, ORE_WORM_EGG);

    public static void init() {
        FabricDefaultAttributeRegistry.register(GLOW_BAT, Bat.createAttributes());
        FabricDefaultAttributeRegistry.register(GEM_SLIME, Monster.createMonsterAttributes());
        FabricDefaultAttributeRegistry.register(ORE_GOLEM, IronGolem.createAttributes());
        FabricDefaultAttributeRegistry.register(ORE_WORM, Silverfish.createAttributes());
        // Natural spawns come from the cave biomes' spawn lists (scripts/gen_mineworld_data.py); these are the per-position rules.
        // Ignores light, unlike vanilla bats: crystal caves are lit by glow lichen and amethyst, and glow bats live there.
        SpawnPlacements.register(GLOW_BAT, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules);
        // Any dark spot, unlike vanilla slimes that need slime chunks or swamps.
        SpawnPlacements.register(GEM_SLIME, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules);
        SpawnPlacements.register(ORE_GOLEM, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules);
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> afterBreak(level, player, pos, state));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> EGGS.forEach(entries::accept));
    }

    private static void afterBreak(Level level, Player player, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server) || DynamicLevels.worldId(server) == null) return;
        if (player.isCreative() || player.isSpectator()) return;
        var ore = OreWorm.oreOf(state);
        if (ore.isEmpty() || server.getRandom().nextInt(ORE_WORM_CHANCE) != 0) return;
        spawnWorm(server, pos, ore.get());
    }

    /** Lets an ore worm carrying {@code ore} out of the (now empty) block at {@code pos}. */
    public static OreWorm spawnWorm(ServerLevel level, BlockPos pos, Item ore) {
        OreWorm worm = ORE_WORM.create(level, EntitySpawnReason.TRIGGERED);
        if (worm == null) throw new IllegalStateException("cannot create ore worm");
        worm.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        worm.setOre(ore);
        level.addFreshEntity(worm);
        worm.spawnAnim();
        return worm;
    }
}
