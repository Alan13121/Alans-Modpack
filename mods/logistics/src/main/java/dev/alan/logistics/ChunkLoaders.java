package dev.alan.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Forces the 3×3 chunks around every chunk loader block, and gives them back when the block is gone. */
public final class ChunkLoaders {
    public static final int RADIUS = 1;
    private static final int CHECK_TICKS = 200;

    private ChunkLoaders() {}

    private static boolean covers(BlockPos loader, int chunkX, int chunkZ) {
        return Math.abs((loader.getX() >> 4) - chunkX) <= RADIUS && Math.abs((loader.getZ() >> 4) - chunkZ) <= RADIUS;
    }

    /** Forces the chunks around a loader (cheap to repeat). */
    public static void apply(ServerLevel level, BlockPos pos) {
        TransmissionData.get(level.getServer()).addLoader(GlobalPos.of(level.dimension(), pos));
        ChunkPos center = ChunkPos.containing(pos);
        for (int dx = -RADIUS; dx <= RADIUS; dx++)
            for (int dz = -RADIUS; dz <= RADIUS; dz++)
                if (!level.getForceLoadedChunks().contains(ChunkPos.pack(center.x() + dx, center.z() + dz))) level.setChunkForced(center.x() + dx, center.z() + dz, true);
    }

    /** Gives the loader's chunks back, except those another loader still covers. */
    public static void release(ServerLevel level, BlockPos pos) {
        TransmissionData data = TransmissionData.get(level.getServer());
        GlobalPos self = GlobalPos.of(level.dimension(), pos);
        data.removeLoader(self);
        ChunkPos center = ChunkPos.containing(pos);
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int cx = center.x() + dx, cz = center.z() + dz;
                boolean shared = false;
                for (GlobalPos other : data.loaders())
                    if (other.dimension().equals(level.dimension()) && covers(other.pos(), cx, cz)) { shared = true; break; }
                if (!shared) level.setChunkForced(cx, cz, false);
            }
        }
    }

    /** Releases loaders whose block vanished without a removal callback (commands, world editors). */
    public static void check(MinecraftServer server) {
        if (server.getTickCount() % CHECK_TICKS != 0) return;
        for (GlobalPos loader : TransmissionData.get(server).loaders()) {
            ServerLevel level = server.getLevel(loader.dimension());
            if (level != null && !(level.getBlockState(loader.pos()).getBlock() instanceof ChunkLoaderBlock)) release(level, loader.pos());
        }
    }
}
