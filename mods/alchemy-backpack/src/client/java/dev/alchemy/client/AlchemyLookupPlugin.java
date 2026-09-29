package dev.alchemy.client;

import dev.alan.lookup.api.LookupPlugin;
import dev.alan.lookup.api.LookupRegistry;
import dev.alan.lookup.api.RecipeView;
import dev.alchemy.AlchemyMod;
import dev.alchemy.EnergyValues;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Lists the energy value of every convertible item under "Uses". Loaded only when the Lookup mod is
 * installed. Values are read when the list is rebuilt, so the server's synced values are what shows.
 */
public final class AlchemyLookupPlugin implements LookupPlugin {
    @Override public void register(LookupRegistry registry) {
        registry.views(AlchemyLookupPlugin::views);
    }

    private static List<RecipeView> views() {
        List<RecipeView> views = new ArrayList<>();
        var category = Component.translatable("alchemy_backpack.lookup.category");
        var icon = new ItemStack(AlchemyMod.BACKPACK);
        var format = NumberFormat.getIntegerInstance(Locale.ROOT);
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = item.getDefaultInstance();
            if (!EnergyValues.canConvert(stack, true)) continue;
            long value = EnergyValues.value(item, true);
            Component single = Component.translatable("alchemy_backpack.lookup.value", format.format(value));
            Component full = stack.getMaxStackSize() > 1
                ? Component.translatable("alchemy_backpack.lookup.stack", stack.getMaxStackSize(), format.format(value * stack.getMaxStackSize()))
                : null;
            RecipeView.Painter painter = (g, font, x, y) -> {
                g.text(font, single, x + 26, y + 14, 0xFF9A6A00, false);
                if (full != null) g.text(font, font.plainSubstrByWidth(full.getString(), 84), x + 26, y + 26, 0xFF404040, false);
            };
            views.add(new RecipeView("alchemy_backpack:energy", category, icon,
                List.of(new RecipeView.Slot(0, 18, List.of(stack))), List.of(), RecipeView.Decoration.NONE, null, painter));
        }
        return views;
    }
}
