package dev.alan.mineworld.city;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/** Places a structure in exactly one chunk of every level that can have it (used for the first city). */
public record FixedChunkPlacement(int chunkX, int chunkZ) implements StructurePlacement {
    public static final MapCodec<FixedChunkPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.INT.fieldOf("chunk_x").forGetter(FixedChunkPlacement::chunkX),
        Codec.INT.fieldOf("chunk_z").forGetter(FixedChunkPlacement::chunkZ)).apply(i, FixedChunkPlacement::new));

    @Override public boolean isStructureChunk(ChunkGeneratorStructureState state, int sourceX, int sourceZ) {
        return sourceX == this.chunkX && sourceZ == this.chunkZ;
    }

    @Override public MapCodec<? extends StructurePlacement> codec() {
        return CODEC;
    }
}
