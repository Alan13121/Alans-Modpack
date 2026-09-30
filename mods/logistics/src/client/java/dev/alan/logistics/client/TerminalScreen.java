package dev.alan.logistics.client;

import dev.alan.logistics.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {
    private WarehousePanel panel;
    private net.minecraft.client.gui.components.EditBox search;

    public TerminalScreen(TerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 180, 234);
    }

    @Override protected void init() {
        super.init();
        panel = new WarehousePanel(font, menu, menu.warehouse(), 8, 34, 5);
        search = addRenderableWidget(panel.searchBox(leftPos, topPos));
        addRenderableWidget(panel.sortButton(leftPos, topPos));
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return panel.click(event, leftPos, topPos) || super.mouseClicked(event, doubleClick);
    }

    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        return panel.scroll(x, y, scrollY, leftPos, topPos) || super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (search.isFocused() && !event.isEscape()) return search.keyPressed(event) || search.canConsumeInput();
        return super.keyPressed(event);
    }

    @Override protected void containerTick() { panel.refilter(); }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF172328);
        g.outline(x, y, imageWidth, imageHeight, 0xFF6FA3C4);
        for (var slot : menu.slots) {
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF0E191D);
            g.outline(x + slot.x - 1, y + slot.y - 1, 18, 18, 0xFF61746C);
        }
        panel.drawBackground(g, x, y);
    }

    @Override protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        g.text(font, title, 8, 6, 0xFF9FD0EC, false);
        g.text(font, playerInventoryTitle, 8, 140, 0xFFD4E4D8, false);
        panel.drawLabels(g, 127);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        panel.drawTooltip(g, minecraft, mx, my, leftPos, topPos);
    }
}
