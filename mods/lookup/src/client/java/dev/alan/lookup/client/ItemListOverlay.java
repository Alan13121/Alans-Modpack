package dev.alan.lookup.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alan.lookup.GiveItem;
import dev.alan.lookup.client.mixin.AbstractContainerScreenAccessor;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The item list with its search box, paging and bookmark row, on the right edge of an inventory-style screen.
 */
final class ItemListOverlay {
    private static final int CELL = 18;

    private final AbstractContainerScreen<?> screen;
    private final Minecraft mc;
    private final Font font;
    private EditBox search;
    private List<ItemStack> filtered = List.of();
    private boolean visible;
    private int left, top, cols, rows, page, width, height;

    ItemListOverlay(Minecraft mc, AbstractContainerScreen<?> screen, int width, int height) {
        this.mc = mc;
        this.screen = screen;
        this.font = mc.font;
        this.width = width;
        this.height = height;
        var accessor = (AbstractContainerScreenAccessor) screen;
        int free = width - 4 - (accessor.lookup$leftPos() + accessor.lookup$imageWidth() + 8);
        cols = Math.min(free / CELL, 12);
        left = width - 4 - cols * CELL;
        visible = cols >= 3 && mc.level != null;
        RecipeIndex.refreshPlugins();
        if (!visible) return;
        top = 22;
        rows = Math.max(2, (height - 26 - top) / CELL);
        search = new EditBox(font, left, height - 22, cols * CELL - 2 * CELL - 4, 18, Component.translatable("lookup.search"));
        search.setHint(Component.translatable("lookup.search"));
        search.setMaxLength(60);
        search.setResponder(text -> { page = 0; refilter(); });
        refilter();
    }

    private void refilter() {
        filtered = ItemList.filter(ItemList.all(mc.level), search.getValue());
        page = Math.max(0, Math.min(page, pages() - 1));
    }

    /** One row is given to bookmarks while there are any. */
    private int bookmarkRows() { return Bookmarks.items().isEmpty() ? 0 : 1; }
    private int listTop() { return top + bookmarkRows() * CELL; }
    private int listRows() { return Math.max(1, rows - bookmarkRows()); }
    private int perPage() { return cols * listRows(); }
    private int pages() { return Math.max(1, (filtered.size() + perPage() - 1) / perPage()); }
    private boolean inArea(double x, double y) { return x >= left && x < left + cols * CELL && y >= top && y < top + rows * CELL; }
    private int prevX() { return left + cols * CELL - 2 * CELL; }
    private int nextX() { return left + cols * CELL - CELL; }

    private @Nullable ItemStack itemAt(double x, double y) {
        if (!inArea(x, y)) return null;
        int col = (int) ((x - left) / CELL);
        if (y < listTop()) {
            var pinned = Bookmarks.items();
            return col < pinned.size() ? new ItemStack(pinned.get(col)) : null;
        }
        int index = page * perPage() + (int) ((y - listTop()) / CELL) * cols + col;
        return index < filtered.size() ? filtered.get(index) : null;
    }

    /** The item under the mouse: one of ours, or a slot of the container itself. */
    @Nullable ItemStack hovered(double x, double y) {
        if (!visible) return null;
        ItemStack own = itemAt(x, y);
        if (own != null) return own;
        Slot slot = ((AbstractContainerScreenAccessor) screen).lookup$hoveredSlot();
        return slot != null && slot.hasItem() ? slot.getItem() : null;
    }

    boolean searchFocused() { return visible && search.isFocused(); }

    void extract(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!visible) return;
        page = Math.max(0, Math.min(page, pages() - 1));
        Component pageText = Component.translatable("lookup.page", page + 1, pages());
        g.text(font, pageText, left + (cols * CELL - font.width(pageText)) / 2, 13, 0xFFFFFFFF, true);
        boolean cheat = LookupConfig.cheatMode();
        Component label = Component.translatable(cheat ? "lookup.cheat.label_on" : "lookup.cheat.label_off");
        boolean overLabel = mouseX >= left && mouseX < left + font.width(label) + 2 && mouseY >= 2 && mouseY < 12;
        g.text(font, label, left, 3, cheat ? 0xFFFFAA00 : overLabel ? 0xFFFFFFFF : 0xFF909090, true);
        if (overLabel) ItemTooltip.drawText(g, font, Component.translatable("lookup.cheat.tip"), mouseX, mouseY);
        ItemStack hover = itemAt(mouseX, mouseY);
        if (bookmarkRows() == 1) {
            g.fill(left, top, left + cols * CELL, top + CELL, 0x50FFCC00);
            var pinned = Bookmarks.items();
            for (int i = 0; i < Math.min(cols, pinned.size()); i++) {
                int x = left + i * CELL;
                if (hover != null && hover.getItem() == pinned.get(i) && mouseY < listTop()) g.fill(x, top, x + CELL, top + CELL, 0x80FFFFFF);
                g.item(new ItemStack(pinned.get(i)), x + 1, top + 1);
            }
        }
        for (int i = 0; i < perPage(); i++) {
            int index = page * perPage() + i;
            if (index >= filtered.size()) break;
            int x = left + (i % cols) * CELL, y = listTop() + (i / cols) * CELL;
            ItemStack stack = filtered.get(index);
            if (stack == hover) g.fill(x, y, x + CELL, y + CELL, 0x80FFFFFF);
            g.item(stack, x + 1, y + 1);
        }
        search.extractRenderState(g, mouseX, mouseY, 0);
        button(g, prevX(), "<", page > 0, mouseX, mouseY);
        button(g, nextX(), ">", page + 1 < pages(), mouseX, mouseY);
        if (hover != null) ItemTooltip.draw(g, mc, font, hover, List.of(), mouseX, mouseY);
    }

    private void button(GuiGraphicsExtractor g, int x, String label, boolean active, int mouseX, int mouseY) {
        int y = height - 22;
        boolean hover = active && mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + 18;
        g.fill(x, y, x + CELL, y + 18, 0xFF000000);
        g.fill(x + 1, y + 1, x + CELL - 1, y + 17, hover ? 0xFF7080B0 : 0xFF555555);
        g.centeredText(font, label, x + CELL / 2, y + 5, active ? 0xFFFFFFFF : 0xFF808080);
    }

    /** Returns true when the click was ours and the screen should not see it. */
    boolean mouseClicked(MouseButtonEvent event) {
        if (!visible) return false;
        double x = event.x(), y = event.y();
        // The cheat label doubles as a switch.
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && x >= left && x < left + font.width(Component.translatable(LookupConfig.cheatMode() ? "lookup.cheat.label_on" : "lookup.cheat.label_off")) + 2 && y >= 2 && y < 12) {
            LookupClient.toggleCheat(mc);
            return true;
        }
        boolean onSearch = x >= search.getX() && x < search.getX() + search.getWidth() && y >= search.getY() && y < search.getY() + 18;
        search.setFocused(onSearch);
        if (onSearch) {
            search.mouseClicked(event, false);
            return true;
        }
        if (y >= height - 22 && y < height - 4) {
            if (x >= prevX() && x < prevX() + CELL && page > 0) page--;
            else if (x >= nextX() && x < nextX() + CELL && page + 1 < pages()) page++;
            else return false;
            return true;
        }
        ItemStack stack = itemAt(x, y);
        if (stack != null && LookupConfig.cheatMode() && (event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT)) {
            // Left click takes a full stack, right click a single item; recipes stay on the R and U keys.
            ClientPlayNetworking.send(new GiveItem(stack.copyWithCount(event.button() == InputConstants.MOUSE_BUTTON_LEFT ? stack.getMaxStackSize() : 1)));
            return true;
        }
        if (stack != null && (event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT)) {
            RecipeScreen.show(mc, screen, stack.getItem(), event.button() == InputConstants.MOUSE_BUTTON_RIGHT);
            return true;
        }
        return inArea(x, y);
    }

    boolean mouseScrolled(double x, double y, double scrollY) {
        if (!visible || !inArea(x, y)) return false;
        page = Math.max(0, Math.min(page - (int) Math.signum(scrollY), pages() - 1));
        return true;
    }

    /** Returns true when the key was ours and the screen should not see it. */
    boolean keyPressed(KeyEvent event, double mouseX, double mouseY) {
        if (!visible) return false;
        // The key mapping only fires in the world, so inventory screens check it here.
        if (LookupClient.TOGGLE_CHEAT.matches(event)) {
            LookupClient.toggleCheat(mc);
            return true;
        }
        if (search.isFocused()) {
            if (event.isEscape()) search.setFocused(false);
            else search.keyPressed(event);
            return true;
        }
        boolean recipes = LookupClient.SHOW_RECIPES.matches(event), uses = LookupClient.SHOW_USES.matches(event);
        boolean bookmark = LookupClient.BOOKMARK.matches(event);
        if (!recipes && !uses && !bookmark) return false;
        ItemStack stack = hovered(mouseX, mouseY);
        if (stack == null) return false;
        if (bookmark) Bookmarks.toggle(stack.getItem());
        else RecipeScreen.show(mc, screen, stack.getItem(), uses);
        return true;
    }

    boolean charTyped(CharacterEvent event) {
        if (!visible || !search.isFocused()) return false;
        search.charTyped(event);
        return true;
    }
}
