package dev.alan.logistics.client;

import dev.alan.logistics.TeleporterBlockEntity;
import dev.alan.logistics.TeleporterDest;
import dev.alan.logistics.TeleporterMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Pick a target from the pads on the channel, then open once, open until closed, or close. */
public final class TeleporterScreen extends AbstractContainerScreen<TeleporterMenu> {
    private static final int ROWS = 6, ROW_H = 12, LIST_X = 8, LIST_Y = 32, LIST_W = 160;
    private int scroll;

    public TeleporterScreen(TeleporterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 160);
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("logistics.teleporter.once"), b -> press(TeleporterMenu.OPEN_ONCE)).bounds(leftPos + 8, topPos + 118, 50, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("logistics.teleporter.always"), b -> press(TeleporterMenu.OPEN_ALWAYS)).bounds(leftPos + 63, topPos + 118, 50, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("logistics.teleporter.close"), b -> press(TeleporterMenu.CLOSE)).bounds(leftPos + 118, topPos + 118, 50, 20).build());
    }

    private void press(int id) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }

    private int rowAt(double mx, double my) {
        double x = mx - leftPos - LIST_X, y = my - topPos - LIST_Y;
        if (x < 0 || x >= LIST_W || y < 0 || y >= ROWS * ROW_H) return -1;
        int index = scroll + (int) (y / ROW_H);
        return index < menu.dests().size() ? index : -1;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int row = rowAt(event.x(), event.y());
        if (row >= 0) {
            press(TeleporterMenu.SELECT + row);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int max = Math.max(0, menu.dests().size() - ROWS);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
        return true;
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF172328);
        g.outline(x, y, imageWidth, imageHeight, 0xFFB07CE0);
        g.fill(x + LIST_X - 1, y + LIST_Y - 1, x + LIST_X + LIST_W + 1, y + LIST_Y + ROWS * ROW_H + 1, 0xFF0E191D);
    }

    @Override protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        g.text(font, title, 8, 6, 0xFFD0A8F0, false);
        g.text(font, EnergyText.of(menu.data), 8, 19, 0xFFE8D27A, false);
        var dests = menu.dests();
        if (dests.isEmpty()) g.text(font, Component.translatable("logistics.teleporter.none"), LIST_X + 2, LIST_Y + 2, 0xFFA5BBB5, false);
        for (int i = 0; i < ROWS && scroll + i < dests.size(); i++) {
            int index = scroll + i;
            TeleporterDest d = dests.get(index);
            int rowY = LIST_Y + i * ROW_H;
            if (index == menu.selected()) g.fill(LIST_X, rowY, LIST_X + LIST_W, rowY + ROW_H, 0xFF3A2A55);
            String dim = d.dimension().startsWith("minecraft:") ? d.dimension().substring(10) : d.dimension();
            g.text(font, Component.literal(d.pos().getX() + ", " + d.pos().getY() + ", " + d.pos().getZ() + "  (" + dim + ")"),
                LIST_X + 2, rowY + 2, index == menu.selected() ? 0xFFFFFFFF : 0xFFCFE6EA, false);
        }
        Component state = Component.translatable(switch (menu.mode()) {
            case TeleporterBlockEntity.ONCE -> "logistics.teleporter.state_once";
            case TeleporterBlockEntity.ALWAYS -> "logistics.teleporter.state_always";
            default -> "logistics.teleporter.state_closed";
        });
        g.text(font, state, 8, 106, 0xFFA5BBB5, false);
    }
}
