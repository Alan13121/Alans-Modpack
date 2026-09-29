package dev.alan.lookup.client;

import dev.alan.lookup.api.RecipeView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Recipes that make an item, or recipes that use it, grouped into one tab per station. */
public final class RecipeScreen extends Screen {
    public record Query(Item item, boolean uses) {}

    private static final Deque<Query> history = new ArrayDeque<>();
    private static final int PANEL_W = 176, TAB = 24, PAD = 8, ROW = RecipeView.HEIGHT + 6;

    private final @Nullable Screen parent;
    private final Query query;
    private final Map<String, List<RecipeView>> categories = new LinkedHashMap<>();
    private final List<String> keys = new ArrayList<>();
    private Button previous, next;
    private int tab, page, left, top, panelH, perPage, lastMouseX, lastMouseY;

    private RecipeScreen(@Nullable Screen parent, Query query) {
        super(Component.translatable(query.uses() ? "lookup.uses_of" : "lookup.recipes_for", itemName(query.item())));
        this.parent = parent;
        this.query = query;
    }

    /** The item's own name; the stack name of a bare potion would read "Uncraftable". */
    private static Component itemName(Item item) {
        Component name = item.components().get(DataComponents.ITEM_NAME);
        return name != null ? name : new ItemStack(item).getHoverName();
    }

    /** Opens the recipes for an item; from another recipe page, the current page is kept for Back. */
    public static void show(Minecraft mc, @Nullable Screen from, Item item, boolean uses) {
        Screen parent = from;
        if (from instanceof RecipeScreen current) {
            history.push(current.query);
            parent = current.parent;
        } else {
            history.clear();
        }
        mc.gui.setScreen(new RecipeScreen(parent, new Query(item, uses)));
    }

    @Override protected void init() {
        categories.clear();
        keys.clear();
        var views = query.uses() ? RecipeIndex.using(query.item()) : RecipeIndex.producing(query.item());
        for (RecipeView view : views) categories.computeIfAbsent(view.categoryKey(), k -> new ArrayList<>()).add(view);
        keys.addAll(categories.keySet());
        tab = Math.min(tab, Math.max(0, keys.size() - 1));

        panelH = Math.min(height - 16, 24 + TAB + PAD + 3 * ROW + 28);
        left = (width - PANEL_W) / 2;
        top = (height - panelH) / 2;
        perPage = Math.max(1, (panelH - 24 - TAB - PAD - 28) / ROW);
        int bottom = top + panelH - 24;
        addRenderableWidget(Button.builder(Component.translatable("lookup.back"), b -> onClose())
            .bounds(left + PAD, bottom, 48, 18).build());
        previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; refresh(); })
            .bounds(left + PANEL_W - PAD - 44, bottom, 20, 18).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; refresh(); })
            .bounds(left + PANEL_W - PAD - 20, bottom, 20, 18).build());
        refresh();
    }

    private List<RecipeView> current() {
        return keys.isEmpty() ? List.of() : categories.get(keys.get(tab));
    }

    private void refresh() {
        int pages = Math.max(1, (current().size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        previous.active = page > 0;
        next.active = page + 1 < pages;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        extractTransparentBackground(g);
        g.fill(left - 1, top - 1, left + PANEL_W + 1, top + panelH + 1, 0xFF000000);
        g.fill(left, top, left + PANEL_W, top + panelH, 0xFFC6C6C6);
        g.text(font, font.plainSubstrByWidth(title.getString(), PANEL_W - 2 * PAD), left + PAD, top + 7, 0xFF404040, false);
        RecipePainter.Hover hovered = null;
        Component hoveredTab = null;
        int tabY = top + 20;
        for (int i = 0; i < keys.size(); i++) {
            int x = left + PAD + i * TAB;
            if (x + TAB > left + PANEL_W - PAD) break;
            boolean selected = i == tab;
            g.fill(x, tabY, x + TAB - 2, tabY + TAB, selected ? 0xFFFFFFFF : 0xFF8B8B8B);
            g.fill(x + 1, tabY + 1, x + TAB - 3, tabY + TAB, selected ? 0xFFC6C6C6 : 0xFF6B6B6B);
            RecipeView first = categories.get(keys.get(i)).get(0);
            g.item(first.categoryIcon(), x + 3, tabY + 4);
            if (mouseX >= x && mouseX < x + TAB - 2 && mouseY >= tabY && mouseY < tabY + TAB) hoveredTab = first.categoryName();
        }
        if (keys.isEmpty()) {
            g.centeredText(font, Component.translatable("lookup.none"), left + PANEL_W / 2, top + panelH / 2 - 8, 0xFF404040);
        }
        var views = current();
        int y0 = tabY + TAB + PAD;
        int x0 = left + (PANEL_W - RecipeView.WIDTH) / 2;
        for (int i = 0; i < perPage; i++) {
            int index = page * perPage + i;
            if (index >= views.size()) break;
            RecipePainter.Hover h = RecipePainter.draw(g, font, views.get(index), x0, y0 + i * ROW, mouseX, mouseY);
            if (h != null) hovered = h;
        }
        int pages = Math.max(1, (views.size() + perPage - 1) / perPage);
        Component pageText = Component.translatable("lookup.page", page + 1, pages);
        g.centeredText(font, pageText, left + PANEL_W / 2 + 4, top + panelH - 18, 0xFF404040);
        super.extractRenderState(g, mouseX, mouseY, a);
        if (hovered != null) tooltip(g, hovered, mouseX, mouseY);
        else if (hoveredTab != null) g.setTooltipForNextFrame(font, hoveredTab, mouseX, mouseY);
    }

    private void tooltip(GuiGraphicsExtractor g, RecipePainter.Hover hover, int mouseX, int mouseY) {
        if (hover.extra().isEmpty()) {
            g.setTooltipForNextFrame(font, hover.stack(), mouseX, mouseY);
            return;
        }
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, hover.stack()));
        lines.addAll(hover.extra());
        g.setTooltipForNextFrame(font, lines, hover.stack().getTooltipImage(), mouseX, mouseY);
    }

    private @Nullable ItemStack stackAt(int mouseX, int mouseY) {
        var views = current();
        int y0 = top + 20 + TAB + PAD, x0 = left + (PANEL_W - RecipeView.WIDTH) / 2;
        for (int i = 0; i < perPage; i++) {
            int index = page * perPage + i;
            if (index >= views.size()) break;
            RecipeView view = views.get(index);
            List<RecipeView.Slot> all = new ArrayList<>(view.inputs());
            all.addAll(view.outputs());
            for (var slot : all) {
                int x = x0 + slot.x(), y = y0 + i * ROW + slot.y();
                if (!slot.stacks().isEmpty() && mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18)
                    return RecipePainter.pick(slot.stacks());
            }
        }
        return null;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        int tabY = top + 20;
        for (int i = 0; i < keys.size(); i++) {
            int x = left + PAD + i * TAB;
            if (x + TAB > left + PANEL_W - PAD) break;
            if (event.x() >= x && event.x() < x + TAB - 2 && event.y() >= tabY && event.y() < tabY + TAB) {
                tab = i;
                page = 0;
                refresh();
                return true;
            }
        }
        ItemStack stack = stackAt((int) event.x(), (int) event.y());
        if (stack != null && (event.button() == 0 || event.button() == 1)) {
            show(minecraft, this, stack.getItem(), event.button() == 1);
            return true;
        }
        return false;
    }

    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        page -= (int) Math.signum(scrollY);
        refresh();
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        boolean recipes = LookupClient.SHOW_RECIPES.matches(event), uses = LookupClient.SHOW_USES.matches(event);
        if (recipes || uses) {
            ItemStack stack = stackAt(lastMouseX, lastMouseY);
            if (stack != null) {
                show(minecraft, this, stack.getItem(), uses);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override public void onClose() {
        Query previousQuery = history.poll();
        if (previousQuery != null) minecraft.gui.setScreen(new RecipeScreen(parent, previousQuery));
        else minecraft.gui.setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }
}
