package dev.alan.lookup.api;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * One recipe (or any "card" about items) laid out on a fixed {@link #WIDTH} x {@link #HEIGHT} canvas.
 * Views with the same {@code categoryKey} share a tab. A view is found from every item in its inputs (as a
 * "use") and in its outputs (as a "recipe").
 *
 * @param categoryKey  identifies the tab; use your own namespace, e.g. {@code "mymod:smelter"}
 * @param categoryName tab tooltip
 * @param categoryIcon tab icon
 * @param inputs       slots; alternatives in one slot are cycled on screen
 * @param outputs      slots
 * @param decoration   what to draw between inputs and outputs
 * @param note         optional line drawn at the bottom of the canvas
 * @param painter      optional extra drawing, called after the slots
 * @param displayId    the server's id for this recipe, or -1; when set and a crafting or furnace menu is open,
 *                     a button moves the ingredients into it. Plugins normally leave it at -1.
 */
public record RecipeView(String categoryKey, Component categoryName, ItemStack categoryIcon,
                         List<Slot> inputs, List<Slot> outputs, Decoration decoration, @Nullable Component note,
                         @Nullable Painter painter, int displayId) {
    public static final int WIDTH = 112, HEIGHT = 54;

    public RecipeView(String categoryKey, Component categoryName, ItemStack categoryIcon,
                      List<Slot> inputs, List<Slot> outputs, Decoration decoration, @Nullable Component note) {
        this(categoryKey, categoryName, categoryIcon, inputs, outputs, decoration, note, null, -1);
    }

    public RecipeView(String categoryKey, Component categoryName, ItemStack categoryIcon,
                      List<Slot> inputs, List<Slot> outputs, Decoration decoration, @Nullable Component note,
                      @Nullable Painter painter) {
        this(categoryKey, categoryName, categoryIcon, inputs, outputs, decoration, note, painter, -1);
    }

    /** Position is relative to the canvas; {@code extra} lines are appended to the item tooltip. */
    public record Slot(int x, int y, List<ItemStack> stacks, List<Component> extra) {
        public Slot(int x, int y, List<ItemStack> stacks) { this(x, y, stacks, List.of()); }
    }

    public enum Decoration { NONE, ARROW, SHORT_ARROW, FURNACE, BREWING }

    /** Draws relative to the canvas origin (x, y). */
    @FunctionalInterface
    public interface Painter {
        void draw(GuiGraphicsExtractor graphics, Font font, int x, int y);
    }
}
