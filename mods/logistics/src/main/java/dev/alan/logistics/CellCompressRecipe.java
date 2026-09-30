package dev.alan.logistics;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Four storage cells of one tier in a 2x2 make one cell of the next tier, with all their contents merged. If the
 * merged contents would not fit, the recipe simply does not match, so nothing is ever lost.
 */
public final class CellCompressRecipe extends CustomRecipe {
    public static final RecipeSerializer<CellCompressRecipe> SERIALIZER =
        new RecipeSerializer<>(MapCodec.unit(CellCompressRecipe::new), StreamCodec.unit(new CellCompressRecipe()));

    static int tierOf(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CellBlock cell ? cell.tier() : 0;
    }

    /** The four cells' contents merged for the next tier, or null if this is not a valid compression. */
    private static CellData merged(CraftingInput input, int[] tierOut) {
        if (input.ingredientCount() != 4 || input.width() != 2 || input.height() != 2) return null;
        int tier = tierOf(input.getItem(0));
        if (tier == 0 || tier >= CellBlock.TIERS) return null;
        List<CellData> parts = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ItemStack stack = input.getItem(i);
            if (tierOf(stack) != tier) return null;
            parts.add(stack.getOrDefault(LogisticsMod.CELL_DATA, CellData.EMPTY));
        }
        tierOut[0] = tier + 1;
        return CellData.merge(parts, CellBlock.maxTypes(tier + 1), CellBlock.maxPerType(tier + 1));
    }

    @Override public boolean matches(CraftingInput input, Level level) {
        return merged(input, new int[1]) != null;
    }

    @Override public ItemStack assemble(CraftingInput input) {
        int[] tier = new int[1];
        CellData data = merged(input, tier);
        if (data == null) return ItemStack.EMPTY;
        ItemStack result = new ItemStack(LogisticsMod.CELLS.get(tier[0] - 1));
        if (!data.entries().isEmpty()) result.set(LogisticsMod.CELL_DATA, data);
        return result;
    }

    @Override public RecipeSerializer<CellCompressRecipe> getSerializer() { return SERIALIZER; }
}
