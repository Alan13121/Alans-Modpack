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
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
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

    /** The z of the first slice of the tunnel (the room ends at z 2). */
    public static final int TUNNEL_START = 3;
    /** Slices of the tunnel dug per tick; a slice is 5x5 blocks. */
    private static final int SLICES_PER_TICK = 1;
    /** Sends the change to clients but does not notify neighbours: stone, air and a few lights have nothing to react. */
    private static final int TUNNEL_FLAGS = Block.UPDATE_CLIENTS;

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

    /**
     * Gets the world ready before the player arrives: creates its level and asks for the chunks around the spawn room, so they generate on
     * the worker threads during the wait instead of in one go on the server thread when the room is built.
     */
    public static void prewarm(MinecraftServer server, String worldId) {
        ServerLevel level = create(server, worldId);
        if (level != null) level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, new ChunkPos(0, 0), 2);
    }

    /** The level of the world with its spawn room built, created on demand; null if the world id is unknown. */
    public static @Nullable ServerLevel ensure(MinecraftServer server, String worldId) {
        ServerLevel level = create(server, worldId);
        MineWorldData data = MineWorldData.get(server);
        MineWorldData.WorldInfo info = data.world(worldId);
        if (level != null && info != null && !info.roomBuilt()) {
            data.setTunnelNext(worldId, TUNNEL_START);   // a world saved before the tunnel was dug in steps, never entered, gets one too
            buildSpawnRoom(level);
            data.setRoomBuilt(worldId);
        }
        return level;
    }

    /** The level of the world, created on demand but without its room; null if the world id is unknown. */
    private static @Nullable ServerLevel create(MinecraftServer server, String worldId) {
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
        return level;
    }

    /** Carves the 5x5x3 start room inside a sealed stone shell, with the return block and glowstone in the ceiling corners. */
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
    }

    /** The z of the last slice of the tunnel, which ends at the gate passage of the first city. */
    public static int tunnelEnd() {
        return City.FIRST_CENTRE.getZ() - CityLayout.WALL_OUTER - CityLayout.PASSAGE_LENGTH - 1;
    }

    /**
     * Digs the lit 5x5 tunnel from the room's south wall to the first city a few slices per tick, and only where the chunks are already
     * loaded (the player's own view of the world loads them), so entering a world never waits for twelve chunks to generate. The progress
     * is saved with the world, so a tunnel that is not finished when the server stops carries on afterwards.
     */
    public static void tickTunnels(MinecraftServer server) {
        long started = System.nanoTime();
        MineWorldData data = MineWorldData.get(server);
        for (String id : data.worldIds()) {
            MineWorldData.WorldInfo info = data.world(id);
            ServerLevel level = server.getLevel(key(id));
            if (info == null || level == null || !info.roomBuilt() || info.tunnelNext() > tunnelEnd()) continue;
            int z = info.tunnelNext();
            int dug = 0;
            while (dug < SLICES_PER_TICK && z <= tunnelEnd() && sliceLoaded(level, z)) {
                digTunnelSlice(level, z++);
                dug++;
            }
            if (dug > 0) data.setTunnelNext(id, z > tunnelEnd() ? MineWorldData.WorldInfo.TUNNEL_FINISHED : z);
        }
        busiestTunnelStep = Math.max(busiestTunnelStep, System.nanoTime() - started);
    }

    private static volatile long busiestTunnelStep;

    /** The longest a single tick of tunnel digging has taken since the server started, in nanoseconds; read by the timing test. */
    public static long busiestTunnelStepNanos() { return busiestTunnelStep; }

    /** Digs the whole tunnel at once, loading what it needs; for tests and commands. */
    public static void finishTunnelNow(MinecraftServer server, String worldId) {
        MineWorldData data = MineWorldData.get(server);
        MineWorldData.WorldInfo info = data.world(worldId);
        ServerLevel level = server.getLevel(key(worldId));
        if (info == null || level == null) return;
        for (int z = Math.min(info.tunnelNext(), tunnelEnd() + 1); z <= tunnelEnd(); z++) digTunnelSlice(level, z);
        data.setTunnelNext(worldId, MineWorldData.WorldInfo.TUNNEL_FINISHED);
    }

    /** Whether both chunk columns the tunnel crosses (x -2..2 touches chunk -1 and 0) are loaded at this z. */
    private static boolean sliceLoaded(ServerLevel level, int z) {
        int cz = Math.floorDiv(z, 16);
        return level.getChunkSource().getChunkNow(-1, cz) != null && level.getChunkSource().getChunkNow(0, cz) != null;
    }

    private static void digTunnelSlice(ServerLevel level, int z) {
        for (int x = -2; x <= 2; x++) {
            for (int y = FLOOR; y <= FLOOR + 4; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                boolean shell = Math.abs(x) == 2 || y == FLOOR || y == FLOOR + 4;
                if (!shell) level.setBlock(pos, Blocks.AIR.defaultBlockState(), TUNNEL_FLAGS);
                else if (y == FLOOR + 4 && x == 0 && z % 6 == 0) level.setBlock(pos, Blocks.GLOWSTONE.defaultBlockState(), TUNNEL_FLAGS);
                else if (y == FLOOR) level.setBlock(pos, Blocks.POLISHED_ANDESITE.defaultBlockState(), TUNNEL_FLAGS);
                else if (level.getBlockState(pos).canBeReplaced()) level.setBlock(pos, Blocks.STONE.defaultBlockState(), TUNNEL_FLAGS);
            }
        }
    }
}
