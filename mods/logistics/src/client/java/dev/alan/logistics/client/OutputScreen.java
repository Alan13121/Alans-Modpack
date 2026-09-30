package dev.alan.logistics.client;

import dev.alan.logistics.OutputMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class OutputScreen extends AbstractContainerScreen<OutputMenu> {
    public OutputScreen(OutputMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 156);
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF172328);
        g.outline(x, y, imageWidth, imageHeight, 0xFFE39B4A);
        for (var slot : menu.slots) {
            boolean ghost = slot.index < 9;
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, ghost ? 0xFF2A2116 : 0xFF0E191D);
            g.outline(x + slot.x - 1, y + slot.y - 1, 18, 18, ghost ? 0xFFE39B4A : 0xFF61746C);
        }
    }

    @Override protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        g.text(font, title, 8, 6, 0xFFF0C48A, false);
        g.text(font, Component.translatable("logistics.output.hint"), 8, 43, 0xFFA5BBB5, false);
        g.text(font, playerInventoryTitle, 8, 63, 0xFFD4E4D8, false);
    }
}
