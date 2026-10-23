package dev.alan.mineworld;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * The big cavern that every ancient city sits in. A mine world is solid rock, so the vanilla city would be buried;
 * this structure shares the vanilla city's placement (same spacing, separation and salt, so the same start chunk),
 * runs one generation step earlier, and carves a flattened dome that the city then overwrites.
 */
public final class AncientCavern extends Structure {
    public static final MapCodec<AncientCavern> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(settingsCodec(i)).apply(i, AncientCavern::new));

    /** Horizontal radius of the dome; the vanilla city reaches about 122 blocks from its centre. */
    static final int RADIUS = 124;
    /** Lowest carved layer (above the bedrock floor) and the dome height at its centre. */
    static final int FLOOR = -58, HEIGHT = 56;
    /** The vanilla city is placed with its centre at this height, which is also what its biome is checked at. */
    private static final int CITY_Y = -27;

    public static final StructureType<AncientCavern> STRUCTURE_TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
        MineWorldMod.id("ancient_cavern"), () -> CODEC);
    public static final StructurePieceType PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE,
        MineWorldMod.id("ancient_cavern"), (StructurePieceType.ContextlessType) Piece::new);

    public AncientCavern(StructureSettings settings) {
        super(settings);
    }

    public static void init() {}

    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        BlockPos centre = new BlockPos(chunk.getMiddleBlockX(), CITY_Y, chunk.getMiddleBlockZ());
        return Optional.of(new GenerationStub(centre, builder -> builder.addPiece(new Piece(centre))));
    }

    @Override public StructureType<?> type() {
        return STRUCTURE_TYPE;
    }

    /** Height of the dome roof above {@link #FLOOR} at a horizontal distance from the centre, with a ragged edge; 0 outside. */
    static int roof(int dx, int dz, int x, int z) {
        double d = Math.sqrt((double) dx * dx + (double) dz * dz) / RADIUS;
        // Cheap deterministic wobble so the wall is not a perfect circle.
        d += 0.06 * (Math.sin(x * 0.13) + Math.sin(z * 0.11) + Math.sin((x + z) * 0.07)) / 3.0;
        if (d >= 1.0) return 0;
        return (int) (HEIGHT * Math.sqrt(1.0 - d * d));
    }

    public static final class Piece extends StructurePiece {
        private final BlockPos centre;

        public Piece(BlockPos centre) {
            super(PIECE, 0, new BoundingBox(centre.getX() - RADIUS - 8, FLOOR, centre.getZ() - RADIUS - 8,
                centre.getX() + RADIUS + 8, FLOOR + HEIGHT, centre.getZ() + RADIUS + 8));
            this.centre = centre;
        }

        public Piece(CompoundTag tag) {
            super(PIECE, tag);
            this.centre = tag.read("Centre", BlockPos.CODEC).orElseThrow();
        }

        @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.store("Centre", BlockPos.CODEC, this.centre);
        }

        @Override public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                          BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = chunkBB.minX(); x <= chunkBB.maxX(); x++) {
                for (int z = chunkBB.minZ(); z <= chunkBB.maxZ(); z++) {
                    int top = roof(x - this.centre.getX(), z - this.centre.getZ(), x, z);
                    for (int y = 1; y <= top; y++) level.setBlock(pos.set(x, FLOOR + y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }
}
