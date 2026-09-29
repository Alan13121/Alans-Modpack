package dev.alan.shapeshift.client;

import dev.alan.lookup.api.LookupPlugin;
import dev.alan.lookup.api.LookupRegistry;
import dev.alan.lookup.api.RecipeView;
import dev.alan.shapeshift.Ability;
import dev.alan.shapeshift.FormDefinitions;
import dev.alan.shapeshift.Forms;
import dev.alan.shapeshift.Weakness;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Shows each shapeshift form as a card on the mob's spawn egg: what you become, what it can do and what
 * hurts it. Loaded only when the Lookup mod is installed.
 */
public final class ShapeshiftLookupPlugin implements LookupPlugin {
    @Override public void register(LookupRegistry registry) {
        registry.views(ShapeshiftLookupPlugin::views);
    }

    private static List<RecipeView> views() {
        List<RecipeView> views = new ArrayList<>();
        var category = Component.translatable("shapeshift.lookup.category");
        var icon = new ItemStack(Items.ENDER_EYE);
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (!Forms.isLivingForm(type)) continue;
            var egg = SpawnEggItem.byId(type);
            if (egg.isEmpty()) continue;
            var form = FormDefinitions.resolve(type, true);
            var abilities = new ArrayList<Component>();
            for (Ability ability : Ability.values())
                if (form.has(ability)) abilities.add(Component.translatable(ability.translationKey()));
            var weaknesses = new ArrayList<Component>();
            for (Weakness weakness : Weakness.values())
                if (form.has(weakness)) weaknesses.add(Component.translatable(weakness.translationKey()));
            Component health = Component.translatable("shapeshift.menu.health", health(form.maxHealth()));
            Component active = form.active().map(a -> Component.literal("[R] ").append(Component.translatable(a.type().translationKey()))).orElse(null);

            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("shapeshift.lookup.form", type.getDescription()).withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(health.copy().withStyle(ChatFormatting.RED));
            for (Component a : abilities) tooltip.add(Component.literal("• ").append(a).withStyle(ChatFormatting.AQUA));
            if (active != null) tooltip.add(active.copy().withStyle(ChatFormatting.GOLD));
            for (Component w : weaknesses) tooltip.add(Component.literal("✖ ").append(w).withStyle(ChatFormatting.RED));

            RecipeView.Painter painter = (g, font, x, y) -> {
                int line = y + 2;
                g.text(font, font.plainSubstrByWidth(health.getString(), 84), x + 26, line, 0xFFB03030, false);
                if (!abilities.isEmpty())
                    g.text(font, Component.translatable("shapeshift.lookup.abilities", abilities.size()), x + 26, line += 10, 0xFF2A7A8A, false);
                if (active != null)
                    g.text(font, font.plainSubstrByWidth(active.getString(), 84), x + 26, line += 10, 0xFF9A6A00, false);
                if (!weaknesses.isEmpty())
                    g.text(font, Component.translatable("shapeshift.lookup.weaknesses", weaknesses.size()), x + 26, line + 10, 0xFF8A2A2A, false);
            };
            views.add(new RecipeView("shapeshift:form", category, icon,
                List.of(new RecipeView.Slot(0, 18, List.of(new ItemStack(egg.get())), tooltip)), List.of(),
                RecipeView.Decoration.NONE, Component.translatable("shapeshift.lookup.unlock"), painter));
        }
        return views;
    }

    private static String health(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
