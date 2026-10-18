package dev.alan.mineworld.city;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * One building from data/mineworld/structure/city/*.nbt, placed with its minimum corner on the floor. Data structure
 * blocks in the template ("civilian", "bartender", "captain", "merchant", "enderman", "creeper") become residents.
 */
public final class BuildingPiece extends TemplateStructurePiece {
    private final BlockPos centre;

    public BuildingPiece(StructureTemplateManager templates, Identifier template, BlockPos corner, BlockPos centre) {
        super(City.BUILDING_PIECE, 0, templates, template, template.toString(), settings(), corner);
        this.centre = centre;
    }

    public BuildingPiece(StructureTemplateManager templates, CompoundTag tag) {
        super(City.BUILDING_PIECE, tag, templates, id -> settings());
        this.centre = tag.read("Centre", BlockPos.CODEC).orElse(this.templatePosition);
    }

    private static StructurePlaceSettings settings() {
        return new StructurePlaceSettings().addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }

    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.store("Centre", BlockPos.CODEC, this.centre);
    }

    @Override public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                                      BoundingBox chunkBB, ChunkPos chunkPos, BlockPos reference) {
        super.postProcess(level, structures, generator, random, chunkBB, chunkPos, reference);
        if (this.templateName.endsWith("city/plaza")) addBlackMarket(level, random, chunkBB);
    }

    /**
     * The black market stall on the south side of the plaza: a counter of blackstone with soul lanterns, a black floor, an ender chest
     * behind it and the dealer. Built here in code rather than in the plaza template, so it also appears next to a plaza that was
     * redrawn with structure blocks.
     */
    private void addBlackMarket(WorldGenLevel level, RandomSource random, BoundingBox chunkBB) {
        for (int x = 8; x <= 14; x++) {
            for (int z = 15; z <= 16; z++) put(level, chunkBB, x, 0, z, Blocks.BLACKSTONE);
            put(level, chunkBB, x, 1, 14, x == 8 || x == 14 ? Blocks.POLISHED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE_SLAB);
        }
        put(level, chunkBB, 8, 2, 14, Blocks.SOUL_LANTERN);
        put(level, chunkBB, 14, 2, 14, Blocks.SOUL_LANTERN);
        put(level, chunkBB, 9, 1, 16, Blocks.ENDER_CHEST);
        put(level, chunkBB, 13, 1, 16, Blocks.BARREL);
        BlockPos dealer = this.templatePosition.offset(11, 1, 15);
        if (chunkBB.isInside(dealer)) City.spawnMarker(level, "dealer", dealer, this.centre, random);
    }

    private void put(WorldGenLevel level, BoundingBox chunkBB, int x, int y, int z, Block block) {
        BlockPos pos = this.templatePosition.offset(x, y, z);
        if (chunkBB.isInside(pos)) level.setBlock(pos, block.defaultBlockState(), 2);
    }

    @Override protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox chunkBB) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        City.spawnMarker(level, marker, pos, this.centre, random);
    }
}
