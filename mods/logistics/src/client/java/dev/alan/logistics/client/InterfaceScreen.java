package dev.alan.logistics.client;

import dev.alan.logistics.InterfaceMenu;
import dev.alan.logistics.UpgradeSlots;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Shared screen of the input interface (upgrades only) and the output interface (filter plus upgrades). */
public final class InterfaceScreen extends AbstractContainerScreen<InterfaceMenu> {
    private final int accent;

    public InterfaceScreen(InterfaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, menu.imageHeight);
        this.accent = menu.hasFilter ? 0xFFE39B4A : menu.hasToggle ? 0xFF8AD65A : 0xFF5AD2B8;
    }

    private Button toggleButton;

    @Override protected void init() {
        super.init();
        if (menu.hasToggle) {
            toggleButton = addRenderableWidget(Button.builder(toggleLabel(),
                    b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, InterfaceMenu.TOGGLE_BUTTON))
                .bounds(leftPos + 8, topPos + 22, 160, 16).build());
        }
    }

    private Component toggleLabel() {
        return Component.translatable(menu.toggleOn() ? "logistics.farm.bone_meal_on" : "logistics.farm.bone_meal_off");
    }

    @Override protected void containerTick() {
        if (toggleButton != null) toggleButton.setMessage(toggleLabel());
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

    /** Scrolling over a filter slot changes how many of the item are kept in the machine; Shift = 16 at a time. */
    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (menu.hasFilter && hoveredSlot != null && hoveredSlot.index < InterfaceMenu.FILTER_SLOTS && hoveredSlot.hasItem() && scrollY != 0) {
            boolean many = minecraft.hasShiftDown();
            int action = scrollY > 0 ? (many ? InterfaceMenu.UP_MANY : InterfaceMenu.UP_ONE) : (many ? InterfaceMenu.DOWN_MANY : InterfaceMenu.DOWN_ONE);
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, InterfaceMenu.levelButton(hoveredSlot.index, action));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        g.text(font, title, 8, 6, accent, false);
        if (menu.hasFilter) {
            g.text(font, Component.translatable("logistics.output.hint"), 8, 43, 0xFFA5BBB5, false);
            g.text(font, Component.translatable("logistics.output.level_hint"), 8, 53, 0xFFA5BBB5, false);
        }
        if (menu.hasToggle) g.text(font, Component.translatable("logistics.farm.area"), 8, 44, 0xFFA5BBB5, false);
        int upgradeY = menu.upgradeY;
        int textX = 8 + UpgradeSlots.SLOTS * 18 + 6;
        g.text(font, Component.translatable("logistics.upgrade.interval", menu.upgrades.interval()), textX, upgradeY, 0xFFE8B0A8, false);
        g.text(font, menu.hasToggle ? Component.translatable("logistics.farm.amount", Math.max(1, menu.upgrades.amount() / 8))
            : Component.translatable("logistics.upgrade.amount", menu.upgrades.amount()), textX, upgradeY + 10, 0xFFCFE6EA, false);
        g.text(font, Component.translatable("logistics.upgrade.hint"), 8, upgradeY - 11, 0xFFA5BBB5, false);
        g.text(font, playerInventoryTitle, 8, menu.labelY, 0xFFD4E4D8, false);
    }

    /** The keep-in-stock numbers go on top of the item icons, so they are drawn after the slots. */
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        if (!menu.hasFilter) return;
        for (int i = 0; i < InterfaceMenu.FILTER_SLOTS; i++) {
            var slot = menu.slots.get(i);
            int level = menu.level(i);
            if (!slot.hasItem() || level <= 0) continue;
            String text = Integer.toString(level);
            g.text(font, text, leftPos + slot.x + 17 - font.width(text), topPos + slot.y + 9, 0xFF55FF55, true);
        }
    }
}
