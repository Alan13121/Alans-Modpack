package dev.alan.mineworld.city;

import static dev.alan.mineworld.city.CityLayout.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** The procedural part of a city: dome cavern, floor and streets, the wall with its gate and passage, lights, guards. */
public final class CavernPiece extends StructurePiece {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState BRICKS = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState STREET = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState PARAPET = Blocks.STONE_BRICK_WALL.defaultBlockState();
    private static final BlockState LIGHT = Blocks.GLOWSTONE.defaultBlockState();
    private static final BlockState LANTERN = Blocks.LANTERN.defaultBlockState();

    private final BlockPos centre;

    public CavernPiece(BlockPos centre) {
        super(City.CAVERN_PIECE, 0, new BoundingBox(centre.getX() - HALF, centre.getY() - 4, centre.getZ() - HALF - PASSAGE_LENGTH,
            centre.getX() + HALF, centre.getY() + DOME_HEIGHT + 1, centre.getZ() + HALF));
        this.centre = centre;
    }

    public CavernPiece(CompoundTag tag) {
        super(City.CAVERN_PIECE, tag);
        this.centre = tag.read("Centre", BlockPos.CODEC).orElseThrow();
    }

    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.store("Centre", BlockPos.CODEC, this.centre);
    }

    @Override public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                                      BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int floor = this.centre.getY();
        for (int x = chunkBB.minX(); x <= chunkBB.maxX(); x++) {
            for (int z = chunkBB.minZ(); z <= chunkBB.maxZ(); z++) {
                int dx = x - this.centre.getX(), dz = z - this.centre.getZ();
                if (inside(dx, dz)) {
                    column(level, pos, x, z, dx, dz, floor);
                } else if (dz < -WALL_OUTER && dz >= -WALL_OUTER - PASSAGE_LENGTH && Math.abs(dx) <= GATE_HALF + 1) {
                    passage(level, pos, x, z, dx, floor);
                }
            }
        }
        for (CityLayout.Guard guard : guards()) {
            BlockPos at = this.centre.offset(guard.x(), guard.y(), guard.z());
            if (chunkBB.isInside(at)) City.spawnGuard(level, guard, at, random);
        }
    }

    private void column(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int dx, int dz, int floor) {
        int top = ceiling(dx, dz);
        // Seal the ground under the floor so natural caves do not open into the city.
        for (int y = floor - 4; y < floor; y++) solidify(level, pos.set(x, y, z));
        boolean wall = isWall(dx, dz);
        level.setBlock(pos.set(x, floor, z), wall ? BRICKS : isStreet(dx, dz) ? STREET : BRICKS, 2);
        for (int y = 1; y <= top; y++) {
            BlockState state = AIR;
            if (wall && y <= WALL_HEIGHT && !isGate(dx, y, dz)) state = BRICKS;
            else if (wall && y == WALL_HEIGHT + 1 && Math.max(Math.abs(dx), Math.abs(dz)) == WALL_OUTER && (dx + dz) % 2 == 0) state = PARAPET;
            else if (!wall && y == 1 && isLamp(dx, dz)) state = LANTERN;
            level.setBlock(pos.set(x, floor + y, z), state, 2);
        }
        // Ladders up the inside of the wall on both sides of the gate.
        if (Math.abs(dx) == GATE_HALF + 2 && dz == -WALL_INNER + 1) {
            for (int y = 1; y <= WALL_HEIGHT; y++) {
                level.setBlock(pos.set(x, floor + y, z), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH), 2);
            }
        }
        // Ceiling: keep a closed dome, with a grid of glowstone.
        pos.set(x, floor + top + 1, z);
        if (Math.floorMod(dx, 8) == 4 && Math.floorMod(dz, 8) == 4) level.setBlock(pos, LIGHT, 2);
        else solidify(level, pos);
    }

    /** The short tunnel from the gate out into the rock; the spawn room tunnel of the first city ends here. */
    private void passage(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int dx, int floor) {
        boolean side = Math.abs(dx) == GATE_HALF + 1;
        for (int y = 0; y <= GATE_HEIGHT + 1; y++) {
            pos.set(x, floor + y, z);
            if (side || y == GATE_HEIGHT + 1) solidify(level, pos);
            else if (y == 0) level.setBlock(pos, STREET, 2);
            else level.setBlock(pos, AIR, 2);
        }
        if (!side && dx == 0 && Math.floorMod(z, 4) == 0) level.setBlock(pos.set(x, floor + GATE_HEIGHT + 1, z), LIGHT, 2);
    }

    private static void solidify(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.canBeReplaced() || !state.getFluidState().isEmpty()) level.setBlock(pos, STONE, 2);
    }

    /** Lanterns along the main streets and the lanes between the plots. */
    private static boolean isLamp(int dx, int dz) {
        if (Math.max(Math.abs(dx), Math.abs(dz)) <= 10) return false; // plaza
        boolean alongStreet = (Math.abs(dx) == GATE_HALF + 1 && Math.floorMod(dz, 8) == 0) || (Math.abs(dz) == GATE_HALF + 1 && Math.floorMod(dx, 8) == 0);
        boolean alongLane = (Math.abs(dx) == 28 && Math.floorMod(dz, 8) == 0) || (Math.abs(dz) == 28 && Math.floorMod(dx, 8) == 0);
        return alongStreet || alongLane;
    }
}
