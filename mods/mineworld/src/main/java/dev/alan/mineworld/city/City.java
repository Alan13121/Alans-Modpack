package dev.alan.mineworld.city;

import com.mojang.serialization.MapCodec;
import dev.alan.mineworld.MineWorldMod;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/** Registration and shared helpers of the monster cities: residents, structure, placement, pieces. */
public final class City {
    private City() {}

    /** Floor height of the first city, next to the spawn room; its centre is the minimum corner of chunk (0, FIRST_CHUNK_Z). */
    public static final int FIRST_CHUNK_Z = 9;
    public static final BlockPos FIRST_CENTRE = new BlockPos(0, dev.alan.mineworld.DynamicLevels.FLOOR, FIRST_CHUNK_Z * 16);
    /** Random cities keep this many chunks away from the world origin (the first city and the spawn room). */
    public static final int RANDOM_MIN_CHUNKS = 25;
    /** Random cities put their floor between these heights. */
    public static final int MIN_FLOOR = 60, MAX_FLOOR = 200;

    public static final TagKey<Structure> CITIES = TagKey.create(Registries.STRUCTURE, MineWorldMod.id("monster_city"));

    private static <T extends Mob> EntityType<T> entity(String name, EntityType.EntityFactory<T> factory, EntityType<?> look) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MineWorldMod.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.of(factory, MobCategory.MISC)
            .sized(look.getWidth(), look.getHeight()).eyeHeight(look.getDimensions().eyeHeight()).clientTrackingRange(10).build(key));
    }

    public static final EntityType<CityZombie> ZOMBIE = entity("city_zombie", CityZombie::new, EntityTypes.ZOMBIE);
    public static final EntityType<CitySkeleton> SKELETON = entity("city_skeleton", CitySkeleton::new, EntityTypes.SKELETON);
    public static final EntityType<CityPiglin> PIGLIN = entity("city_piglin", CityPiglin::new, EntityTypes.PIGLIN);
    public static final EntityType<CityPiglin> CAPTAIN = entity("city_captain", CityPiglin::new, EntityTypes.PIGLIN_BRUTE);
    public static final EntityType<CityCreeper> CREEPER = entity("city_creeper", CityCreeper::new, EntityTypes.CREEPER);
    public static final EntityType<CityEnderman> ENDERMAN = entity("city_enderman", CityEnderman::new, EntityTypes.ENDERMAN);

    public static final StructureType<CityStructure> STRUCTURE_TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
        MineWorldMod.id("monster_city"), () -> CityStructure.CODEC);
    public static final MapCodec<FixedChunkPlacement> FIXED_CHUNK = Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT,
        MineWorldMod.id("fixed_chunk"), FixedChunkPlacement.CODEC);
    public static final StructurePieceType CAVERN_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE,
        MineWorldMod.id("city_cavern"), (StructurePieceType.ContextlessType) CavernPiece::new);
    public static final StructurePieceType BUILDING_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE,
        MineWorldMod.id("city_building"), (StructurePieceType.StructureTemplateType) BuildingPiece::new);

    public static void init() {
        java.util.Objects.requireNonNull(CityMerchant.LOCKS); // registers the player attachment
        FabricDefaultAttributeRegistry.register(ZOMBIE, Zombie.createAttributes());
        FabricDefaultAttributeRegistry.register(SKELETON, AbstractSkeleton.createAttributes());
        FabricDefaultAttributeRegistry.register(PIGLIN, Piglin.createAttributes());
        FabricDefaultAttributeRegistry.register(CAPTAIN, Piglin.createAttributes());
        FabricDefaultAttributeRegistry.register(CREEPER, Creeper.createAttributes());
        FabricDefaultAttributeRegistry.register(ENDERMAN, Enderman.createAttributes());
    }

    /** Whether a position is inside a monster city (its cavern, wall or gate passage). */
    public static boolean contains(ServerLevel level, BlockPos pos) {
        return level.structureManager().getStructureWithPieceAt(pos, CITIES).isValid();
    }

    /** Spawns a persistent, invulnerable resident that stays within radius of home. */
    public static <T extends Mob> T spawn(ServerLevelAccessor level, EntityType<T> type, BlockPos pos, BlockPos home, int radius,
                                          RandomSource random, Consumer<T> setup) {
        T mob = type.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (mob == null) throw new IllegalStateException("cannot create " + type);
        mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
        mob.setYHeadRot(mob.getYRot());
        mob.setHomeTo(home, radius);
        mob.setPersistenceRequired();
        mob.setPermanentlyInvulnerable(true);
        setup.accept(mob);
        level.addFreshEntityWithPassengers(mob);
        return mob;
    }

    /** Spawns the resident a template's data marker asks for ("bartender", "merchant", ...); unknown markers spawn nothing. */
    public static void spawnMarker(ServerLevelAccessor level, String marker, BlockPos pos, BlockPos centre, RandomSource random) {
        switch (marker) {
            case "civilian" -> spawn(level, PIGLIN, pos, centre, 44, random, p -> p.setup(CityPiglin.Role.CIVILIAN,
                CityPiglin.Profession.RANDOM[random.nextInt(CityPiglin.Profession.RANDOM.length)]));
            case "bartender" -> spawn(level, PIGLIN, pos, pos, 5, random, p -> p.setup(CityPiglin.Role.CIVILIAN, CityPiglin.Profession.BARTENDER));
            case "captain" -> spawn(level, CAPTAIN, pos, pos, 5, random, p -> p.setup(CityPiglin.Role.CAPTAIN, CityPiglin.Profession.IDLER));
            case "merchant" -> spawn(level, ZOMBIE, pos, pos, 6, random, z -> {});
            case "enderman" -> spawn(level, ENDERMAN, pos, pos, 6, random, e -> {});
            case "creeper" -> spawn(level, CREEPER, pos, centre, 30, random, c -> {});
            default -> MineWorldMod.LOG.warn("Unknown city marker '{}' at {}", marker, pos);
        }
    }

    public static void spawnGuard(ServerLevelAccessor level, CityLayout.Guard guard, BlockPos pos, RandomSource random) {
        switch (guard.kind()) {
            case SKELETON -> spawn(level, SKELETON, pos, pos, guard.radius(), random,
                s -> s.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW)));
            case PIGLIN_MELEE -> spawn(level, PIGLIN, pos, pos, guard.radius(), random, p -> p.setup(CityPiglin.Role.MELEE, CityPiglin.Profession.IDLER));
            case PIGLIN_RANGED -> spawn(level, PIGLIN, pos, pos, guard.radius(), random, p -> p.setup(CityPiglin.Role.RANGED, CityPiglin.Profession.IDLER));
        }
    }
}
