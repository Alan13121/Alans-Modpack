package dev.alan.lookup.client;

import dev.alan.lookup.api.RecipeView;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Draws item slots and recipe views in a plain vanilla-like style. */
final class RecipePainter {
    /** What the mouse is over: the stack plus any extra tooltip lines the view attached to that slot. */
    record Hover(ItemStack stack, List<Component> extra) {}

    private RecipePainter() {}

    static void slotBackground(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF373737);
        g.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    /** The stack shown for a slot with alternatives: cycles once a second. */
    static ItemStack pick(java.util.List<ItemStack> stacks) {
        return stacks.get((int) (System.currentTimeMillis() / 1000 % stacks.size()));
    }

    /** Draws the view with its top-left at (x, y) and returns the stack under the mouse, if any. */
    static @Nullable Hover draw(GuiGraphicsExtractor g, Font font, RecipeView view, int x, int y, int mouseX, int mouseY) {
        Hover hovered = null;
        switch (view.decoration()) {
            case NONE -> {}
            case SHORT_ARROW -> arrow(g, x + 21, y + 22);
            case ARROW -> arrow(g, x + 64, y + 22);
            case BREWING -> {
                arrow(g, x + 64, y + 22);
                g.fill(x + 19, y + 26, x + 25, y + 28, 0xFF6B6B6B);
                g.fill(x + 21, y + 24, x + 23, y + 30, 0xFF6B6B6B);
            }
            case FURNACE -> {
                arrow(g, x + 42, y + 22);
                g.fill(x + 4, y + 21, x + 14, y + 33, 0xFF555555);
                g.fill(x + 6, y + 27, x + 12, y + 33, 0xFFE8A030);
                g.fill(x + 7, y + 23, x + 11, y + 27, 0xFFF0D060);
            }
        }
        for (var slot : view.inputs()) {
            Hover h = slot(g, font, slot, x, y, mouseX, mouseY);
            if (h != null) hovered = h;
        }
        for (var slot : view.outputs()) {
            Hover h = slot(g, font, slot, x, y, mouseX, mouseY);
            if (h != null) hovered = h;
        }
        if (view.painter() != null) view.painter().draw(g, font, x, y);
        if (view.note() != null) g.text(font, view.note(), x + 24, y + RecipeView.HEIGHT - 9, 0xFF404040, false);
        return hovered;
    }

    private static @Nullable Hover slot(GuiGraphicsExtractor g, Font font, RecipeView.Slot slot, int ox, int oy, int mouseX, int mouseY) {
        int x = ox + slot.x(), y = oy + slot.y();
        slotBackground(g, x, y);
        if (slot.stacks().isEmpty()) return null;
        ItemStack stack = pick(slot.stacks());
        g.item(stack, x + 1, y + 1);
        g.itemDecorations(font, stack, x + 1, y + 1);
        boolean hover = mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
        if (hover) g.fill(x + 1, y + 1, x + 17, y + 17, 0x80FFFFFF);
        return hover ? new Hover(stack, slot.extra()) : null;
    }

    private static void arrow(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y + 5, x + 16, y + 9, 0xFF6B6B6B);
        for (int i = 0; i < 7; i++) g.fill(x + 16 + i, y + 7 - (6 - i), x + 17 + i, y + 7 + (6 - i) + 1, 0xFF6B6B6B);
    }
}
