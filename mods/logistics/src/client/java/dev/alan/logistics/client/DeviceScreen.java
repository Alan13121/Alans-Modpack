package dev.alan.logistics.client;

import dev.alan.logistics.DeviceMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Screen of the channel block, antenna and coal generator. */
public final class DeviceScreen extends AbstractContainerScreen<DeviceMenu> {
    private final int accent;

    public DeviceScreen(DeviceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 180);
        this.accent = switch (menu.kind) {
            case CHANNEL -> 0xFF7BD66A;
            case ANTENNA -> 0xFFB8C4CC;
            case COAL -> 0xFFC9B07A;
        };
    }

    @Override protected void init() {
        super.init();
        if (menu.kind != DeviceMenu.Kind.CHANNEL) return;
        addRenderableWidget(Button.builder(Component.literal("«"), b -> press(DeviceMenu.CHANNEL_DOWN_MANY)).bounds(leftPos + 8, topPos + 20, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> press(DeviceMenu.CHANNEL_DOWN)).bounds(leftPos + 30, topPos + 20, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> press(DeviceMenu.CHANNEL_UP)).bounds(leftPos + 126, topPos + 20, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("»"), b -> press(DeviceMenu.CHANNEL_UP_MANY)).bounds(leftPos + 148, topPos + 20, 20, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("logistics.channel.write"), b -> press(DeviceMenu.WRITE_CARD)).bounds(leftPos + 8, topPos + 50, 66, 20).build());
    }

    private void press(int id) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF172328);
        g.outline(x, y, imageWidth, imageHeight, accent);
        for (var slot : menu.slots) {
            boolean device = slot.index == 0;
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF0E191D);
            g.outline(x + slot.x - 1, y + slot.y - 1, 18, 18, device ? accent : 0xFF61746C);
        }
    }

    @Override protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        g.text(font, title, 8, 6, accent, false);
        switch (menu.kind) {
            case CHANNEL -> {
                int channel = menu.extra(0);
                Component text = channel == 0 ? Component.translatable("logistics.channel.closed") : Component.translatable("logistics.channel.number", channel);
                g.centeredText(font, text, 88, 25, 0xFFFFFFFF);
                g.textWithWordWrap(font, Component.translatable("logistics.channel.card_hint"), 102, 48, 66, 0xFFA5BBB5);
            }
            case ANTENNA -> {
                g.text(font, Component.translatable("logistics.antenna.range", menu.extra(0)), 8, 22, 0xFFCFE6EA, false);
                g.textWithWordWrap(font, Component.translatable("logistics.antenna.hint"), 8, 33, 160, 0xFFA5BBB5);
            }
            case COAL -> {
                int left = menu.extra(0), total = Math.max(1, menu.extra(1));
                g.text(font, Component.translatable("logistics.coal.burn", left / 20), 8, 22, 0xFFCFE6EA, false);
                g.textWithWordWrap(font, Component.translatable("logistics.coal.hint"), 8, 33, 160, 0xFFA5BBB5);
                g.fill(8, 73, 8 + 160 * left / total, 76, 0xFFE0A040);
            }
        }
        g.text(font, EnergyText.of(menu.data), 8, 78, 0xFFE8D27A, false);
        g.text(font, playerInventoryTitle, 8, 88, 0xFFD4E4D8, false);
    }
}
