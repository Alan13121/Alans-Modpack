package dev.alan.logistics.client;

import dev.alan.logistics.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {
    private static final int COLS = 9, ROWS = 5, CELL = 18, GRID_X = 8, GRID_Y = 34;
    private EditBox search;
    private Button sort;
    private boolean sortByCount = true;
    private int scroll;
    private List<TerminalSnapshot.Entry> shownFrom;
    private final List<TerminalSnapshot.Entry> filtered = new ArrayList<>();
    private String shownQuery = "";
    private boolean dirty = true;

    public TerminalScreen(TerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 180, 234);
    }

    @Override protected void init() {
        super.init();
        search = addRenderableWidget(new EditBox(font, leftPos + GRID_X, topPos + 17, 112, 14, Component.translatable("logistics.search")));
        search.setHint(Component.translatable("logistics.search"));
        search.setMaxLength(60);
        search.setResponder(text -> { dirty = true; scroll = 0; });
        sort = addRenderableWidget(Button.builder(sortLabel(), b -> {
            sortByCount = !sortByCount;
            b.setMessage(sortLabel());
            dirty = true;
        }).bounds(leftPos + GRID_X + 116, topPos + 16, 50, 16).build());
    }

    private Component sortLabel() { return Component.translatable(sortByCount ? "logistics.sort.count" : "logistics.sort.name"); }

    private void refilter() {
        if (!dirty && shownFrom == menu.entries()) return;
        shownFrom = menu.entries();
        dirty = false;
        filtered.clear();
        String query = search.getValue().toLowerCase(Locale.ROOT).strip();
        for (var e : shownFrom) if (matches(e.stack(), query)) filtered.add(e);
        Comparator<TerminalSnapshot.Entry> byName = Comparator.comparing(e -> e.stack().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER);
        filtered.sort(sortByCount ? Comparator.<TerminalSnapshot.Entry>comparingLong(e -> -e.count()).thenComparing(byName) : byName);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    /** Plain text matches the name; {@code @mod} matches the namespace; {@code #tag} matches an item tag. */
    private static boolean matches(ItemStack stack, String query) {
        if (query.isEmpty()) return true;
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (query.startsWith("@")) return id.getNamespace().contains(query.substring(1));
        if (query.startsWith("#")) {
            String t = query.substring(1);
            return stack.tags().anyMatch(tag -> tag.location().toString().contains(t));
        }
        return stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query) || id.getPath().contains(query);
    }

    private int maxScroll() { return Math.max(0, (filtered.size() + COLS - 1) / COLS - ROWS); }

    private int indexAt(double mx, double my) {
        int cx = (int) (mx - leftPos - GRID_X), cy = (int) (my - topPos - GRID_Y);
        if (cx < 0 || cy < 0 || cx >= COLS * CELL || cy >= ROWS * CELL) return -1;
        int i = (scroll + cy / CELL) * COLS + cx / CELL;
        return i < filtered.size() ? i : -1;
    }

    private boolean inGrid(double mx, double my) {
        return mx >= leftPos + GRID_X && mx < leftPos + GRID_X + COLS * CELL && my >= topPos + GRID_Y && my < topPos + GRID_Y + ROWS * CELL;
    }

    private void send(TerminalAction.Kind kind, ItemStack stack) {
        ClientPlayNetworking.send(new TerminalAction(menu.containerId, kind, stack));
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (inGrid(event.x(), event.y()) && menu.status() == Network.Status.OK && (event.button() == 0 || event.button() == 1)) {
            boolean right = event.button() == 1;
            if (!menu.getCarried().isEmpty()) {
                send(right ? TerminalAction.Kind.INSERT_ONE : TerminalAction.Kind.INSERT_ALL, ItemStack.EMPTY);
            } else {
                int i = indexAt(event.x(), event.y());
                if (i >= 0) {
                    TerminalAction.Kind kind = event.hasShiftDown() ? TerminalAction.Kind.SHIFT_TAKE
                        : right ? TerminalAction.Kind.TAKE_HALF : TerminalAction.Kind.TAKE_STACK;
                    send(kind, filtered.get(i).stack());
                }
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (inGrid(x, y)) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (search.isFocused() && !event.isEscape()) return search.keyPressed(event) || search.canConsumeInput();
        return super.keyPressed(event);
    }

    @Override protected void containerTick() { refilter(); }

    private static String compact(long n) {
        if (n < 1000) return Long.toString(n);
        if (n < 1_000_000) return n % 1000 < 100 || n >= 100_000 ? (n / 1000) + "k" : (n / 1000) + "." + (n % 1000) / 100 + "k";
        return (n / 1_000_000) + "." + (n % 1_000_000) / 100_000 + "M";
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);
        refilter();
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF172328);
        g.outline(x, y, imageWidth, imageHeight, 0xFF6FA3C4);
        g.fill(x + GRID_X - 1, y + GRID_Y - 1, x + GRID_X + COLS * CELL + 1, y + GRID_Y + ROWS * CELL + 1, 0xFF0E191D);
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++)
            g.outline(x + GRID_X + c * CELL, y + GRID_Y + r * CELL, CELL, CELL, 0xFF2A3D42);
        for (var slot : menu.slots) {
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF0E191D);
            g.outline(x + slot.x - 1, y + slot.y - 1, 18, 18, 0xFF61746C);
        }
        int rows = Math.max(1, (filtered.size() + COLS - 1) / COLS);
        int trackX = x + GRID_X + COLS * CELL + 2, trackH = ROWS * CELL;
        g.fill(trackX, y + GRID_Y, trackX + 4, y + GRID_Y + trackH, 0xFF0E191D);
        if (rows > ROWS) {
            int thumb = Math.max(8, trackH * ROWS / rows);
            int top = y + GRID_Y + (trackH - thumb) * scroll / Math.max(1, maxScroll());
            g.fill(trackX, top, trackX + 4, top + thumb, 0xFF6FA3C4);
        }
        Component hover = null;
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) {
            int i = (scroll + r) * COLS + c;
            if (i >= filtered.size()) continue;
            var e = filtered.get(i);
            int ix = x + GRID_X + c * CELL + 1, iy = y + GRID_Y + r * CELL + 1;
            g.item(e.stack(), ix, iy);
            g.itemDecorations(font, e.stack(), ix, iy, compact(e.count()));
        }
    }

    @Override protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        g.text(font, title, 8, 6, 0xFF9FD0EC, false);
        g.text(font, playerInventoryTitle, 8, 140, 0xFFD4E4D8, false);
        if (menu.status() != Network.Status.OK) {
            Component msg = Component.translatable(menu.status() == Network.Status.MULTIPLE_CONTROLLERS ? "logistics.status.multiple" : "logistics.status.none");
            var lines = font.split(msg, COLS * CELL - 8);
            for (int i = 0; i < lines.size(); i++)
                g.centeredText(font, lines.get(i), GRID_X + COLS * CELL / 2, GRID_Y + ROWS * CELL / 2 - 4 + i * 10 - (lines.size() - 1) * 5, 0xFFE58A8A);
        } else {
            long total = 0;
            for (var e : menu.entries()) total += e.count();
            g.text(font, Component.translatable("logistics.summary", menu.entries().size(), total), 8, 127, 0xFFA5BBB5, false);
            if (menu.entries().isEmpty()) g.centeredText(font, Component.translatable("logistics.empty"), GRID_X + COLS * CELL / 2, GRID_Y + ROWS * CELL / 2 - 4, 0xFFA5BBB5);
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        int i = menu.getCarried().isEmpty() ? indexAt(mx, my) : -1;
        if (i >= 0) {
            var e = filtered.get(i);
            List<Component> lines = new ArrayList<>(e.stack().getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL));
            lines.add(Component.translatable("logistics.tooltip.count", e.count()).withStyle(net.minecraft.ChatFormatting.GRAY));
            g.setTooltipForNextFrame(font, lines, Optional.empty(), mx, my);
        }
    }
}
