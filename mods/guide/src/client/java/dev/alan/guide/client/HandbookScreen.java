package dev.alan.guide.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.google.gson.JsonParser;
import dev.alan.guide.GuideMod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * The handbook: chapter list on the left, the chapter text on the right, wrapped to the window and scrolled with the wheel or
 * keys, so nothing is ever cut off. The chapter list scrolls on its own when the wheel is over it. Text comes from lang keys handbook.guide.entry.N.title / .body (see scripts/gen_guide_data.py);
 * in a body a line starting with "# " is a heading, "- " a bullet, anything else plain text.
 */
public final class HandbookScreen extends Screen {
    private static final int SIDE_W = 116, ROW_H = 22, PAD = 8, LINE_H = 11;
    private static final int PAPER = 0xFFEBDDB8, SIDE = 0xFFD6C198, SELECT = 0xFFEBDDB8, HOVER = 0xFFE0CDA2, EDGE = 0xFF5A3E1B;
    private static final int INK = 0xFF3B2A14, HEADING = 0xFF8B2E16, BULLET = 0xFF8B6A2F;
    private static List<ItemStack> icons;
    private static int selected;

    private record Line(FormattedCharSequence text, int x, int y, int color, boolean bullet) {}

    private final List<Line> lines = new ArrayList<>();
    private int left, top, panelW, panelH, textX, textW, viewTop, viewH, contentH, scroll, sideScroll;

    private final Screen parent;

    public HandbookScreen() {
        this(null);
    }

    /** {@code parent} (the inventory, when opened from its button) is shown again on close. */
    public HandbookScreen(Screen parent) {
        super(Component.translatable("item.guide.handbook"));
        this.parent = parent;
    }

    @Override public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private static List<ItemStack> icons() {
        if (icons == null) {
            icons = new ArrayList<>();
            try (var in = HandbookScreen.class.getResourceAsStream("/assets/guide/handbook.json")) {
                for (var entry : JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray()) {
                    var id = Identifier.parse(entry.getAsJsonObject().get("icon").getAsString());
                    icons.add(BuiltInRegistries.ITEM.getOptional(id).map(i -> i.getDefaultInstance()).orElse(ItemStack.EMPTY));
                }
            } catch (IOException | RuntimeException e) {
                GuideMod.LOG.error("Could not read the handbook entry list", e);
            }
        }
        return icons;
    }

    @Override protected void init() {
        panelW = Math.min(width - 16, 400);
        panelH = Math.min(height - 40, 250);
        left = (width - panelW) / 2;
        top = Math.max(4, (height - panelH - 24) / 2);
        textX = left + SIDE_W + PAD;
        textW = panelW - SIDE_W - PAD * 2 - 6;
        viewTop = top + PAD;
        viewH = panelH - PAD * 2;
        selected = Math.max(0, Math.min(selected, icons().size() - 1));
        revealSelected();
        addRenderableWidget(Button.builder(Component.translatable("handbook.guide.close"), b -> onClose())
            .bounds(width / 2 - 40, top + panelH + 4, 80, 20).build());
        layout();
    }

    /** Wraps the selected entry to the current width. */
    private void layout() {
        lines.clear();
        int y = 0;
        String body = Component.translatable("handbook.guide.entry." + (selected + 1) + ".body").getString();
        for (String paragraph : body.split("\n")) {
            boolean heading = paragraph.startsWith("# "), bullet = paragraph.startsWith("- ");
            String text = heading || bullet ? paragraph.substring(2) : paragraph;
            if (heading && y > 0) y += 5;
            int indent = bullet ? 9 : 0;
            Component styled = heading ? Component.literal(text).withStyle(ChatFormatting.BOLD) : Component.literal(text);
            boolean first = true;
            for (FormattedCharSequence wrapped : font.split(styled, textW - indent)) {
                lines.add(new Line(wrapped, textX + indent, y, heading ? HEADING : INK, bullet && first));
                first = false;
                y += LINE_H;
            }
            y += heading ? 2 : 3;
        }
        contentH = y;
        scroll = 0;
    }

    private int maxScroll() {
        return Math.max(0, contentH - viewH);
    }

    private int maxSideScroll() {
        return Math.max(0, icons().size() * ROW_H - viewH);
    }

    /** Scrolls the chapter list just enough to show the selected row. */
    private void revealSelected() {
        int rowTop = selected * ROW_H;
        sideScroll = Math.max(0, Math.min(maxSideScroll(), Math.max(Math.min(sideScroll, rowTop), rowTop + ROW_H - viewH)));
    }

    private void select(int index) {
        if (index == selected || index < 0 || index >= icons().size()) return;
        selected = index;
        revealSelected();
        layout();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        g.fill(left - 2, top - 2, left + panelW + 2, top + panelH + 2, EDGE);
        g.fill(left, top, left + panelW, top + panelH, PAPER);
        g.fill(left, top, left + SIDE_W, top + panelH, SIDE);
        g.fill(left + SIDE_W - 1, top, left + SIDE_W, top + panelH, EDGE);

        var list = icons();
        g.enableScissor(left, viewTop, left + SIDE_W, viewTop + viewH);
        for (int i = 0; i < list.size(); i++) {
            int y = viewTop + i * ROW_H - sideScroll;
            if (y + ROW_H < viewTop || y > viewTop + viewH) continue;
            boolean hover = mouseX >= left && mouseX < left + SIDE_W && mouseY >= Math.max(y, viewTop) && mouseY < Math.min(y + ROW_H, viewTop + viewH);
            if (i == selected) g.fill(left + 2, y, left + SIDE_W, y + ROW_H - 2, SELECT);
            else if (hover) g.fill(left + 2, y, left + SIDE_W - 2, y + ROW_H - 2, HOVER);
            g.item(list.get(i), left + 5, y + 2);
            String title = Component.translatable("handbook.guide.entry." + (i + 1) + ".title").getString();
            g.text(font, font.plainSubstrByWidth(title, SIDE_W - 28), left + 25, y + 7, i == selected ? HEADING : INK, false);
        }
        g.disableScissor();
        if (maxSideScroll() > 0) {
            int trackX = left + SIDE_W - 4;
            int thumbH = Math.max(16, viewH * viewH / (list.size() * ROW_H));
            int thumbY = viewTop + (viewH - thumbH) * sideScroll / maxSideScroll();
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, EDGE);
        }

        g.enableScissor(textX - 2, viewTop, textX + textW + 2, viewTop + viewH);
        for (Line line : lines) {
            int y = viewTop + line.y() - scroll;
            if (y + LINE_H < viewTop || y > viewTop + viewH) continue;
            if (line.bullet()) g.text(font, "・", line.x() - 9, y, BULLET, false);
            g.text(font, line.text(), line.x(), y, line.color(), false);
        }
        g.disableScissor();

        if (maxScroll() > 0) {
            int trackX = left + panelW - 7;
            g.fill(trackX, viewTop, trackX + 3, viewTop + viewH, HOVER);
            int thumbH = Math.max(16, viewH * viewH / contentH);
            int thumbY = viewTop + (viewH - thumbH) * scroll / maxScroll();
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, EDGE);
        }
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.x() >= left && event.x() < left + SIDE_W
            && event.y() >= viewTop && event.y() < viewTop + viewH) {
            select((int) (event.y() - viewTop + sideScroll) / ROW_H);
            return true;
        }
        return false;
    }

    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (x >= left && x < left + SIDE_W && y >= top && y < top + panelH) {
            sideScroll = Math.max(0, Math.min(maxSideScroll(), sideScroll - (int) Math.signum(scrollY) * ROW_H));
            return true;
        }
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY) * LINE_H * 3));
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        int step = switch (event.key()) {
            case InputConstants.KEY_UP -> -LINE_H;                    // up
            case InputConstants.KEY_DOWN -> LINE_H;                     // down
            case InputConstants.KEY_PAGEUP -> -viewH + LINE_H;            // page up
            case InputConstants.KEY_PAGEDOWN -> viewH - LINE_H;             // page down
            case InputConstants.KEY_HOME -> -contentH;                  // home
            case InputConstants.KEY_END -> contentH;                   // end
            default -> 0;
        };
        if (step != 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll + step));
            return true;
        }
        if (event.key() == InputConstants.KEY_LEFT) { select(selected - 1); return true; }   // left
        if (event.key() == InputConstants.KEY_RIGHT) { select(selected + 1); return true; }   // right
        return super.keyPressed(event);
    }

    @Override public boolean isPauseScreen() { return false; }
}
