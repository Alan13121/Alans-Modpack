package dev.alan.logistics;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.NonNullList;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * One storage cell of tier 2 or higher becomes four cells of the tier below: one carries all the contents, the other
 * three come back empty in the crafting slot. It only works while the contents fit into a single lower-tier cell, so
 * a full cell has to be emptied a little first; nothing is ever lost.
 */
public final class CellDecompressRecipe extends CustomRecipe {
    public static final RecipeSerializer<CellDecompressRecipe> SERIALIZER =
        new RecipeSerializer<>(MapCodec.unit(CellDecompressRecipe::new), StreamCodec.unit(new CellDecompressRecipe()));

    /** How many cells one decompression yields; one holds the contents, the rest are empty. */
    public static final int YIELD = 4;

    private static CellData contentsIfValid(CraftingInput input) {
        if (input.ingredientCount() != 1) return null;
        ItemStack cell = null;
        for (int i = 0; i < input.size(); i++) if (!input.getItem(i).isEmpty()) cell = input.getItem(i);
        if (cell == null) return null;
        int tier = CellCompressRecipe.tierOf(cell);
        if (tier < 2) return null;
        CellData data = cell.getOrDefault(LogisticsMod.CELL_DATA, CellData.EMPTY);
        return data.fitsIn(CellBlock.maxTypes(tier - 1), CellBlock.maxPerType(tier - 1)) ? data : null;
    }

    @Override public boolean matches(CraftingInput input, Level level) { return contentsIfValid(input) != null; }

    @Override public ItemStack assemble(CraftingInput input) {
        CellData data = contentsIfValid(input);
        if (data == null) return ItemStack.EMPTY;
        ItemStack result = new ItemStack(LogisticsMod.CELLS.get(tierBelow(input) - 1));
        if (!data.entries().isEmpty()) result.set(LogisticsMod.CELL_DATA, data);
        return result;
    }

    private static int tierBelow(CraftingInput input) {
        for (int i = 0; i < input.size(); i++) if (!input.getItem(i).isEmpty()) return CellCompressRecipe.tierOf(input.getItem(i)) - 1;
        return 1;
    }

    /** The other three cells, empty, take the place of the consumed one. */
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        if (contentsIfValid(input) == null) return remaining;
        for (int i = 0; i < input.size(); i++)
            if (!input.getItem(i).isEmpty()) remaining.set(i, new ItemStack(LogisticsMod.CELLS.get(tierBelow(input) - 1), YIELD - 1));
        return remaining;
    }

    @Override public RecipeSerializer<CellDecompressRecipe> getSerializer() { return SERIALIZER; }
}
