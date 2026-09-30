package dev.alan.mineworld.client;

import dev.alan.lookup.api.LookupPlugin;
import dev.alan.lookup.api.LookupRegistry;
import dev.alan.lookup.api.RecipeView;
import dev.alan.mineworld.MineWorldMod;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Adds the "create a mine world" card to Lookup. Loaded only when Lookup is installed. */
public final class MineWorldLookupPlugin implements LookupPlugin {
    @Override public void register(LookupRegistry registry) {
        registry.views(() -> List.of(createView()));
    }

    private static RecipeView createView() {
        var worldCauldron = new ItemStack(MineWorldMod.WORLD_CAULDRON);
        var cauldron = new RecipeView.Slot(18, 18, List.of(new ItemStack(Items.CAULDRON)),
            List.of(Component.translatable("mineworld.lookup.water").withStyle(ChatFormatting.AQUA),
                Component.translatable("mineworld.lookup.note").withStyle(ChatFormatting.GRAY)));
        List<Component> both = List.of(Component.translatable("mineworld.lookup.either").withStyle(ChatFormatting.GRAY));
        // Clockwise from the top left around the cauldron: coal, copper, iron, gold, redstone, lapis, diamond, emerald.
        int[][] cells = {{0, 0}, {18, 0}, {36, 0}, {36, 18}, {36, 36}, {18, 36}, {0, 36}, {0, 18}};
        Item[][] ores = {
            {Items.COAL}, {Items.RAW_COPPER, Items.COPPER_INGOT}, {Items.RAW_IRON, Items.IRON_INGOT}, {Items.RAW_GOLD, Items.GOLD_INGOT},
            {Items.REDSTONE}, {Items.LAPIS_LAZULI}, {Items.DIAMOND}, {Items.EMERALD}};
        var inputs = new java.util.ArrayList<RecipeView.Slot>();
        inputs.add(cauldron);
        for (int i = 0; i < ores.length; i++) {
            var stacks = java.util.Arrays.stream(ores[i]).map(ItemStack::new).toList();
            inputs.add(new RecipeView.Slot(cells[i][0], cells[i][1], stacks, stacks.size() > 1 ? both : List.<Component>of()));
        }
        return new RecipeView("mineworld:create", Component.translatable("mineworld.lookup.create"), worldCauldron,
            inputs, List.of(new RecipeView.Slot(94, 18, List.of(worldCauldron))),
            RecipeView.Decoration.ARROW, null);
    }
}
