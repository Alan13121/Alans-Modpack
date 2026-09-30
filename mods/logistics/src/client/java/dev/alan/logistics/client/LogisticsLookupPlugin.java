package dev.alan.logistics.client;

import dev.alan.logistics.CellBlock;
import dev.alan.logistics.LogisticsMod;
import dev.alan.logistics.WarehouseMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import dev.alan.lookup.api.RecipeView;
import dev.alan.lookup.api.LookupPlugin;
import dev.alan.lookup.api.LookupRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Lets Lookup's recipe pages show what the warehouse holds. Loaded only when Lookup is installed. The counts come
 * from the terminal that is open behind the recipe page; with no terminal open there is nothing to show.
 */
public final class LogisticsLookupPlugin implements LookupPlugin {
    @Override public void register(LookupRegistry registry) {
        registry.stock(LogisticsLookupPlugin::stockOf);
        registry.views(LogisticsLookupPlugin::compressionViews);
    }

    /** Vanilla can't display the storage-cell compression (a special recipe), so it gets a card per tier. */
    private static List<RecipeView> compressionViews() {
        List<RecipeView> views = new ArrayList<>();
        var category = Component.translatable("logistics.lookup.compress");
        var icon = new ItemStack(LogisticsMod.CELLS.get(CellBlock.TIERS - 1));
        for (int tier = 1; tier < CellBlock.TIERS; tier++) {
            var from = List.of(new ItemStack(LogisticsMod.CELLS.get(tier - 1)));
            views.add(new RecipeView("logistics:compress", category, icon,
                List.of(new RecipeView.Slot(0, 9, from), new RecipeView.Slot(18, 9, from),
                        new RecipeView.Slot(0, 27, from), new RecipeView.Slot(18, 27, from)),
                List.of(new RecipeView.Slot(94, 18, List.of(new ItemStack(LogisticsMod.CELLS.get(tier))))),
                RecipeView.Decoration.ARROW, Component.translatable("logistics.lookup.compress_note")));
        }
        return views;
    }

    private static long stockOf(ItemStack stack) {
        var player = Minecraft.getInstance().player;
        if (player == null || !(player.containerMenu instanceof WarehouseMenu menu)) return -1;
        if (menu.warehouse().status() != dev.alan.logistics.Network.Status.OK) return -1;
        long total = 0;
        for (var entry : menu.warehouse().entries()) if (entry.stack().is(stack.getItem())) total += entry.count();
        return total;
    }
}
