package dev.alan.logistics.client;

import dev.alan.logistics.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** The scrolling, searchable warehouse grid shared by the terminal screens. Coordinates are relative to the screen. */
final class WarehousePanel {
    static final int COLS = 9, CELL = 18;

    private final Font font;
    private final WarehouseLink link;
    private final AbstractContainerMenu menu;
    private final int gridX, gridY, rows;
    private EditBox search;
    private boolean sortByCount = true;
    private int scroll;
    private List<TerminalSnapshot.Entry> shownFrom;
    private final List<TerminalSnapshot.Entry> filtered = new ArrayList<>();
    private boolean dirty = true;

    WarehousePanel(Font font, AbstractContainerMenu menu, WarehouseLink link, int gridX, int gridY, int rows) {
        this.font = font;
        this.menu = menu;
        this.link = link;
        this.gridX = gridX;
        this.gridY = gridY;
        this.rows = rows;
    }

    EditBox searchBox(int left, int top) {
        search = new EditBox(font, left + gridX, top + gridY - 17, 112, 14, Component.translatable("logistics.search"));
        search.setHint(Component.translatable("logistics.search"));
        search.setMaxLength(60);
        search.setResponder(text -> { dirty = true; scroll = 0; });
        return search;
    }

    Button sortButton(int left, int top) {
        return Button.builder(sortLabel(), b -> {
            sortByCount = !sortByCount;
            b.setMessage(sortLabel());
            dirty = true;
        }).bounds(left + gridX + 116, top + gridY - 18, 50, 16).build();
    }

    EditBox search() { return search; }

    private Component sortLabel() { return Component.translatable(sortByCount ? "logistics.sort.count" : "logistics.sort.name"); }

    /** Display names are looked up once per entry; entries are replaced whenever their count changes. */
    private final java.util.Map<TerminalSnapshot.Entry, String> names = new java.util.IdentityHashMap<>();

    private String nameOf(TerminalSnapshot.Entry e) {
        return names.computeIfAbsent(e, x -> x.stack().getHoverName().getString().toLowerCase(Locale.ROOT));
    }

    void refilter() {
        if (!dirty && shownFrom == link.entries()) return;
        shownFrom = link.entries();
        dirty = false;
        if (names.size() > 2 * shownFrom.size() + 64) names.clear();
        filtered.clear();
        String query = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).strip();
        for (var e : shownFrom) if (matches(e, query)) filtered.add(e);
        Comparator<TerminalSnapshot.Entry> byName = Comparator.comparing(this::nameOf);
        filtered.sort(sortByCount ? Comparator.<TerminalSnapshot.Entry>comparingLong(e -> -e.count()).thenComparing(byName) : byName);
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    /** Plain text matches the name; {@code @mod} matches the namespace; {@code #tag} matches an item tag. */
    private boolean matches(TerminalSnapshot.Entry entry, String query) {
        if (query.isEmpty()) return true;
        ItemStack stack = entry.stack();
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (query.startsWith("@")) return id.getNamespace().contains(query.substring(1));
        if (query.startsWith("#")) {
            String t = query.substring(1);
            return stack.tags().anyMatch(tag -> tag.location().toString().contains(t));
        }
        return nameOf(entry).contains(query) || id.getPath().contains(query);
    }

    private int maxScroll() { return Math.max(0, (filtered.size() + COLS - 1) / COLS - rows); }

    private int indexAt(double mx, double my, int left, int top) {
        int cx = (int) (mx - left - gridX), cy = (int) (my - top - gridY);
        if (cx < 0 || cy < 0 || cx >= COLS * CELL || cy >= rows * CELL) return -1;
        int i = (scroll + cy / CELL) * COLS + cx / CELL;
        return i < filtered.size() ? i : -1;
    }

    boolean inGrid(double mx, double my, int left, int top) {
        return mx >= left + gridX && mx < left + gridX + COLS * CELL && my >= top + gridY && my < top + gridY + rows * CELL;
    }

    private void send(TerminalAction.Kind kind, ItemStack stack) {
        ClientPlayNetworking.send(new TerminalAction(menu.containerId, kind, stack));
    }

    boolean click(MouseButtonEvent event, int left, int top) {
        if (!inGrid(event.x(), event.y(), left, top) || link.status() != Network.Status.OK || (event.button() != 0 && event.button() != 1)) return false;
        boolean right = event.button() == 1;
        if (!menu.getCarried().isEmpty()) {
            send(right ? TerminalAction.Kind.INSERT_ONE : TerminalAction.Kind.INSERT_ALL, ItemStack.EMPTY);
        } else {
            int i = indexAt(event.x(), event.y(), left, top);
            if (i >= 0) {
                TerminalAction.Kind kind = event.hasShiftDown() ? TerminalAction.Kind.SHIFT_TAKE
                    : right ? TerminalAction.Kind.TAKE_HALF : TerminalAction.Kind.TAKE_STACK;
                send(kind, filtered.get(i).stack());
            }
        }
        return true;
    }

    boolean scroll(double x, double y, double scrollY, int left, int top) {
        if (!inGrid(x, y, left, top)) return false;
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
        return true;
    }

    private static String compact(long n) {
        if (n < 1000) return Long.toString(n);
        if (n < 1_000_000) return n % 1000 < 100 || n >= 100_000 ? (n / 1000) + "k" : (n / 1000) + "." + (n % 1000) / 100 + "k";
        return (n / 1_000_000) + "." + (n % 1_000_000) / 100_000 + "M";
    }

    void drawBackground(GuiGraphicsExtractor g, int left, int top) {
        refilter();
        int x = left + gridX, y = top + gridY;
        g.fill(x - 1, y - 1, x + COLS * CELL + 1, y + rows * CELL + 1, 0xFF0E191D);
        for (int r = 0; r < rows; r++) for (int c = 0; c < COLS; c++)
            g.outline(x + c * CELL, y + r * CELL, CELL, CELL, 0xFF2A3D42);
        int total = Math.max(1, (filtered.size() + COLS - 1) / COLS);
        int trackX = x + COLS * CELL + 2, trackH = rows * CELL;
        g.fill(trackX, y, trackX + 4, y + trackH, 0xFF0E191D);
        if (total > rows) {
            int thumb = Math.max(8, trackH * rows / total);
            int thumbTop = y + (trackH - thumb) * scroll / Math.max(1, maxScroll());
            g.fill(trackX, thumbTop, trackX + 4, thumbTop + thumb, 0xFF6FA3C4);
        }
        for (int r = 0; r < rows; r++) for (int c = 0; c < COLS; c++) {
            int i = (scroll + r) * COLS + c;
            if (i >= filtered.size()) continue;
            var e = filtered.get(i);
            int ix = x + c * CELL + 1, iy = y + r * CELL + 1;
            g.item(e.stack(), ix, iy);
            g.itemDecorations(font, e.stack(), ix, iy, compact(e.count()));
        }
    }

    /** Status text and the item summary; call from the labels pass (coordinates relative to the screen). */
    void drawLabels(GuiGraphicsExtractor g, int summaryY) {
        int centerX = gridX + COLS * CELL / 2, centerY = gridY + rows * CELL / 2 - 4;
        if (link.status() != Network.Status.OK) {
            Component msg = Component.translatable(link.status() == Network.Status.MULTIPLE_CONTROLLERS ? "logistics.status.multiple" : "logistics.status.none");
            var lines = font.split(msg, COLS * CELL - 8);
            for (int i = 0; i < lines.size(); i++)
                g.centeredText(font, lines.get(i), centerX, centerY + i * 10 - (lines.size() - 1) * 5, 0xFFE58A8A);
        } else {
            long total = 0;
            for (var e : link.entries()) total += e.count();
            g.text(font, Component.translatable("logistics.summary", link.entries().size(), total), gridX, summaryY, 0xFFA5BBB5, false);
            if (link.entries().isEmpty()) g.centeredText(font, Component.translatable("logistics.empty"), centerX, centerY, 0xFFA5BBB5);
        }
    }

    void drawTooltip(GuiGraphicsExtractor g, Minecraft mc, int mx, int my, int left, int top) {
        int i = menu.getCarried().isEmpty() ? indexAt(mx, my, left, top) : -1;
        if (i < 0) return;
        var e = filtered.get(i);
        List<Component> lines = new ArrayList<>(e.stack().getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL));
        lines.add(Component.translatable("logistics.tooltip.count", e.count()).withStyle(ChatFormatting.GRAY));
        g.setTooltipForNextFrame(font, lines, Optional.empty(), mx, my);
    }
}
