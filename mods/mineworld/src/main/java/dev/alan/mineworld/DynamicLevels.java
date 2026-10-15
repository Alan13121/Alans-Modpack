package dev.alan.mineworld;

import dev.alan.mineworld.city.City;
import dev.alan.mineworld.city.CityLayout;
import dev.alan.mineworld.mixin.MinecraftServerAccessor;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.storage.DerivedLevelData;
import org.jspecify.annotations.Nullable;

/** Creates mine worlds (extra ServerLevels) while the server is running and rebuilds the saved ones on start. */
public final class DynamicLevels {
    /** Interior of the spawn room: x/z from -2..2, y from FLOOR+1 .. FLOOR+3. */
    public static final int FLOOR = 127;
    public static final BlockPos ARRIVAL = new BlockPos(0, FLOOR + 1, 1);
    public static final BlockPos RETURN_BLOCK = new BlockPos(0, FLOOR, -2);

    private static final Map<ResourceKey<Level>, Long> SEEDS = new HashMap<>();
    private static final Queue<String> PENDING = new ArrayDeque<>();

    private DynamicLevels() {}

    public static ResourceKey<Level> key(String worldId) {
        return ResourceKey.create(Registries.DIMENSION, MineWorldMod.id(worldId));
    }

    /** The world id of a mine world level, or null for any other level. */
    public static @Nullable String worldId(Level level) {
        var id = level.dimension().identifier();
        return id.getNamespace().equals(MineWorldMod.MOD_ID) ? id.getPath() : null;
    }

    public static @Nullable Long seedOf(ResourceKey<Level> dimension) { return SEEDS.get(dimension); }

    public static void clear() { SEEDS.clear(); PENDING.clear(); }

    /** Asks for the world to be created at the end of the current tick (safe from inside level ticking). */
    public static void queue(String worldId) { PENDING.add(worldId); }

    /** Call at a point where levels are not being iterated. */
    public static void processQueue(MinecraftServer server) {
        String id;
        while ((id = PENDING.poll()) != null) ensure(server, id);
    }

    public static void restoreAll(MinecraftServer server) {
        for (String id : MineWorldData.get(server).worldIds()) ensure(server, id);
    }

    /** The level of the world, created on demand; null if the world id is unknown. */
    public static @Nullable ServerLevel ensure(MinecraftServer server, String worldId) {
        ResourceKey<Level> key = key(worldId);
        ServerLevel existing = server.getLevel(key);
        if (existing != null) return existing;
        MineWorldData data = MineWorldData.get(server);
        MineWorldData.WorldInfo info = data.world(worldId);
        if (info == null) return null;

        SEEDS.put(key, info.seed());
        var registries = server.registryAccess();
        var biomes = registries.lookupOrThrow(Registries.BIOME);
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> caves = new ArrayList<>();
        MineWorldMod.CAVE_BIOMES.forEach((biome, point) ->
            caves.add(Pair.of(Climate.parameters(point[0], point[1], 0, 0, 0, 0, 0), biomes.getOrThrow(biome))));
        ChunkGenerator generator = new NoiseBasedChunkGenerator(
            MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(caves)),
            registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(MineWorldMod.NOISE_SETTINGS));
        Holder<net.minecraft.world.level.dimension.DimensionType> type =
            registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(MineWorldMod.DIMENSION_TYPE);
        var accessor = (MinecraftServerAccessor) server;
        ServerLevel level = new ServerLevel(server, accessor.mineworld$executor(), accessor.mineworld$storageSource(),
            new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData()), key,
            new LevelStem(type, generator), false, BiomeManager.obfuscateSeed(info.seed()), List.of(), false);
        accessor.mineworld$levels().put(key, level);
        level.setSpawnSettings(level.isSpawningMonsters());
        level.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize());
        server.getPlayerList().addWorldborderListener(level);
        MineWorldMod.LOG.info("Created mine world {}", key.identifier());

        if (!info.roomBuilt()) {
            buildSpawnRoom(level);
            data.setRoomBuilt(worldId);
        }
        return level;
    }

    /** Carves the 5x5x3 start room inside a sealed stone shell, with the return block, glowstone in the ceiling corners and the tunnel to the first city. */
    private static void buildSpawnRoom(ServerLevel level) {
        for (int cx = -1; cx <= 0; cx++) for (int cz = -1; cz <= 0; cz++) level.getChunk(cx, cz);
        // A closed stone shell first, so a geode or other feature next to the room cannot open into it.
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = FLOOR; y <= FLOOR + 4; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).canBeReplaced()) level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        for (int x : new int[]{-2, 2}) {
            for (int z : new int[]{-2, 2}) {
                level.setBlock(new BlockPos(x, FLOOR + 4, z), Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        level.setBlock(RETURN_BLOCK, MineWorldMod.RETURN_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        digTunnelToFirstCity(level);
    }

    /** A lit 3x3 tunnel from the room's south wall straight to the gate passage of the first city. */
    private static void digTunnelToFirstCity(ServerLevel level) {
        int end = City.FIRST_CENTRE.getZ() - CityLayout.WALL_OUTER - CityLayout.PASSAGE_LENGTH - 1;
        for (int z = 3; z <= end; z++) {
            for (int x = -2; x <= 2; x++) {
                for (int y = FLOOR; y <= FLOOR + 4; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    boolean shell = Math.abs(x) == 2 || y == FLOOR || y == FLOOR + 4;
                    if (!shell) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    else if (y == FLOOR + 4 && x == 0 && z % 6 == 0) level.setBlock(pos, Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_ALL);
                    else if (y == FLOOR) level.setBlock(pos, Blocks.POLISHED_ANDESITE.defaultBlockState(), Block.UPDATE_ALL);
                    else if (level.getBlockState(pos).canBeReplaced()) level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }
}
