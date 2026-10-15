package dev.alan.mineworld.city;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alan.mineworld.MineWorldMod;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/** A monster city: one cavern piece (dome, floor, wall, lights, guards) plus a template piece per building. */
public final class CityStructure extends Structure {
    public static final MapCodec<CityStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        settingsCodec(i),
        Codec.BOOL.optionalFieldOf("first", false).forGetter(s -> s.first)).apply(i, CityStructure::new));

    /** The first city sits at a fixed spot and height next to the spawn room; the others are random. */
    private final boolean first;

    public CityStructure(StructureSettings settings, boolean first) {
        super(settings);
        this.first = first;
    }

    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        BlockPos centre;
        if (this.first) {
            centre = new BlockPos(chunk.getMinBlockX(), City.FIRST_CENTRE.getY(), chunk.getMinBlockZ());
        } else {
            if (Math.abs(chunk.x()) < City.RANDOM_MIN_CHUNKS && Math.abs(chunk.z()) < City.RANDOM_MIN_CHUNKS) return Optional.empty();
            int floor = City.MIN_FLOOR + context.random().nextInt(City.MAX_FLOOR - City.MIN_FLOOR + 1);
            centre = new BlockPos(chunk.getMiddleBlockX(), floor, chunk.getMiddleBlockZ());
        }
        Random random = new Random(context.random().nextLong());
        return Optional.of(new GenerationStub(centre, builder -> {
            builder.addPiece(new CavernPiece(centre));
            for (CityLayout.Building b : CityLayout.buildings(random)) {
                builder.addPiece(new BuildingPiece(context.structureTemplateManager(), MineWorldMod.id("city/" + b.template()),
                    centre.offset(b.x(), 0, b.z()), centre));
            }
        }));
    }

    @Override public StructureType<?> type() {
        return City.STRUCTURE_TYPE;
    }
}
