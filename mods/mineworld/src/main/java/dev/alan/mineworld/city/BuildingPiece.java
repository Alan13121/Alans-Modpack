package dev.alan.mineworld.city;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
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

    @Override protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox chunkBB) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        City.spawnMarker(level, marker, pos, this.centre, random);
    }
}
