package dev.alan.logistics.client;

import dev.alan.logistics.AutoCrafterMenu;
import dev.alan.logistics.UpgradeSlots;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Pattern grid, result preview, keep-in-stock number (scroll over the result to change it) and upgrades. */
public final class AutoCrafterScreen extends AbstractContainerScreen<AutoCrafterMenu> {
    private static final int ACCENT = 0xFFC99A5B;

    public AutoCrafterScreen(AutoCrafterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 210);
    }

    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (hoveredSlot != null && (hoveredSlot.index == AutoCrafterMenu.RESULT || hoveredSlot.index < AutoCrafterMenu.RESULT) && scrollY != 0) {
            boolean many = minecraft.hasShiftDown();
            int action = scrollY > 0 ? (many ? AutoCrafterMenu.UP_MANY : AutoCrafterMenu.UP_ONE) : (many ? AutoCrafterMenu.DOWN_MANY : AutoCrafterMenu.DOWN_ONE);
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, AutoCrafterMenu.KEEP_BUTTON + action);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF172328);
        g.outline(x, y, imageWidth, imageHeight, ACCENT);
        for (var slot : menu.slots) {
            boolean special = slot.index < AutoCrafterMenu.INV_START;
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, slot.index < AutoCrafterMenu.RESULT ? 0xFF2A2116 : 0xFF0E191D);
            g.outline(x + slot.x - 1, y + slot.y - 1, 18, 18, special ? ACCENT : 0xFF61746C);
        }
    }

    @Override protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        g.text(font, title, 8, 6, 0xFFE3BE86, false);
        g.centeredText(font, Component.literal("→"), 76, 45, 0xFFE3BE86);
        g.text(font, Component.translatable("logistics.autocrafter.keep", menu.keep()), 112, 30, 0xFF55FF55, false);
        g.text(font, Component.translatable("logistics.autocrafter.scroll"), 112, 42, 0xFFA5BBB5, false);
        g.text(font, Component.translatable("logistics.upgrade.hint"), 8, 81, 0xFFA5BBB5, false);
        int textX = 8 + UpgradeSlots.SLOTS * 18 + 6;
        g.text(font, Component.translatable("logistics.upgrade.interval", menu.upgrades.interval()), textX, 90, 0xFFE8B0A8, false);
        g.text(font, Component.translatable("logistics.autocrafter.crafts", Math.max(1, menu.upgrades.amount() / UpgradeSlots.BASE_AMOUNT)), textX, 100, 0xFFCFE6EA, false);
        g.text(font, playerInventoryTitle, 8, 117, 0xFFD4E4D8, false);
    }
}
