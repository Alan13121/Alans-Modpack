package dev.alchemy;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * Reads the server's recipes into {@link EmcSolver} recipes. Inputs come from the recipe's placement info and the result from its
 * display, so crafting, smelting, stonecutting, smithing and other mods' recipes all work. Recipes that cannot be described (dyeing,
 * fireworks, repairs...) are ignored.
 */
final class RecipeEmc {
    private RecipeEmc() {}

    static List<EmcSolver.Recipe> extract(RecipeManager manager) {
        List<EmcSolver.Recipe> out = new ArrayList<>();
        for (var holder : manager.getRecipes()) {
            var recipe = holder.value();
            var placement = recipe.placementInfo();
            if (placement.isImpossibleToPlace() || placement.ingredients().isEmpty()) continue;
            List<List<String>> inputs = placement.ingredients().stream()
                .map(ingredient -> ingredient.items().map(item -> EnergyValues.id(item.value())).toList())
                .toList();
            for (var display : recipe.display()) {
                if (display.result() instanceof SlotDisplay.ItemStackSlotDisplay stack) {
                    out.add(new EmcSolver.Recipe(inputs, EnergyValues.id(stack.stack().item().value()), Math.max(1, stack.stack().count())));
                } else if (display.result() instanceof SlotDisplay.ItemSlotDisplay item) {
                    out.add(new EmcSolver.Recipe(inputs, EnergyValues.id(item.item().value()), 1));
                }
            }
        }
        return out;
    }
}
