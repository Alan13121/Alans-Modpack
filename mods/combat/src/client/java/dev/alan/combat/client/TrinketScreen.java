package dev.alan.combat.client;

import dev.alan.combat.TrinketMenu;
import dev.alan.combat.Trinkets;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class TrinketScreen extends AbstractContainerScreen<TrinketMenu> {
    private final Inventory inventory;

    public TrinketScreen(TrinketMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, TrinketMenu.height());
        this.inventory = inventory;
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF172328);
        g.outline(x, y, imageWidth, imageHeight, 0xFFAE945D);
        int active = Trinkets.slotCount(inventory.player);
        int total = Trinkets.total();
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            boolean locked = i < total && i >= active;
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, locked ? 0xFF05080A : 0xFF0E191D);
            g.outline(x + slot.x - 1, y + slot.y - 1, 18, 18, locked ? 0xFF2A3331 : i < total ? 0xFFAE945D : 0xFF61746C);
        }
    }

    @Override protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        g.text(font, title, 8, 8, 0xFFF0DC9A, false);
        Component count = Component.translatable("combat.trinkets.slots", Trinkets.slotCount(inventory.player), Trinkets.total());
        g.text(font, count, imageWidth - 8 - font.width(count), 8, 0xFFE6C66B, false);
        int rowsEnd = TrinketMenu.top(0) + TrinketMenu.rows() * 18;
        g.text(font, Component.translatable("combat.trinkets.hint", Trinkets.bags(inventory.player)), 8, rowsEnd + 2, 0xFFA5BBB5, false);
        g.text(font, playerInventoryTitle, 8, rowsEnd + 2 + 12, 0xFFD4E4D8, false);
    }
}
