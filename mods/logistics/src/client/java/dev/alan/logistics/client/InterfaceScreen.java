package dev.alan.logistics.client;

import dev.alan.logistics.InterfaceMenu;
import dev.alan.logistics.UpgradeSlots;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Shared screen of the input interface (upgrades only) and the output interface (filter plus upgrades). */
public final class InterfaceScreen extends AbstractContainerScreen<InterfaceMenu> {
    private final int accent;

    public InterfaceScreen(InterfaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, menu.imageHeight);
        this.accent = menu.hasFilter ? 0xFFE39B4A : 0xFF5AD2B8;
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF172328);
        g.outline(x, y, imageWidth, imageHeight, accent);
        for (var slot : menu.slots) {
            boolean ghost = menu.hasFilter && slot.index < InterfaceMenu.FILTER_SLOTS;
            boolean upgrade = slot.container == menu.upgrades;
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, ghost ? 0xFF2A2116 : 0xFF0E191D);
            g.outline(x + slot.x - 1, y + slot.y - 1, 18, 18, ghost || upgrade ? accent : 0xFF61746C);
        }
    }

    @Override protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        g.text(font, title, 8, 6, accent, false);
        if (menu.hasFilter) g.text(font, Component.translatable("logistics.output.hint"), 8, 43, 0xFFA5BBB5, false);
        int upgradeY = menu.upgradeY;
        int textX = 8 + UpgradeSlots.SLOTS * 18 + 6;
        g.text(font, Component.translatable("logistics.upgrade.interval", menu.upgrades.interval()), textX, upgradeY, 0xFFE8B0A8, false);
        g.text(font, Component.translatable("logistics.upgrade.amount", menu.upgrades.amount()), textX, upgradeY + 10, 0xFFCFE6EA, false);
        g.text(font, Component.translatable("logistics.upgrade.hint"), 8, upgradeY - 11, 0xFFA5BBB5, false);
        g.text(font, playerInventoryTitle, 8, menu.labelY, 0xFFD4E4D8, false);
    }
}
