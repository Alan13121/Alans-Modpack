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
        super(menu, inventory, title, 176, menu.imageHeight);
        this.accent = switch (menu.kind) {
            case CHANNEL -> 0xFF7BD66A;
            case ANTENNA -> 0xFFB8C4CC;
            case COAL -> 0xFFC9B07A;
        };
    }

    private net.minecraft.client.gui.components.EditBox nameBox;
    private Button createButton, renameButton, deleteButton, visibilityButton, publicToggle;
    private boolean newPublic = true, deleteArmed;

    @Override protected void init() {
        super.init();
        if (menu.kind != DeviceMenu.Kind.CHANNEL) return;
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> select(ClientChannels.step(menu.extra(0), -1))).bounds(leftPos + 8, topPos + 16, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> select(ClientChannels.step(menu.extra(0), 1))).bounds(leftPos + 148, topPos + 16, 20, 16).build());
        nameBox = addRenderableWidget(new net.minecraft.client.gui.components.EditBox(font, leftPos + 8, topPos + 48, 100, 16, Component.translatable("logistics.channel.name")));
        nameBox.setHint(Component.translatable("logistics.channel.name"));
        nameBox.setMaxLength(dev.alan.logistics.ChannelInfo.MAX_NAME);
        nameBox.setResponder(text -> deleteArmed = false);
        publicToggle = addRenderableWidget(Button.builder(visibilityLabel(newPublic), b -> {
            newPublic = !newPublic;
            b.setMessage(visibilityLabel(newPublic));
        }).bounds(leftPos + 112, topPos + 48, 56, 16).build());
        createButton = addRenderableWidget(Button.builder(Component.translatable("logistics.channel.create"),
            b -> { send(dev.alan.logistics.ChannelAction.Kind.CREATE, 0, nameBox.getValue(), newPublic); nameBox.setValue(""); }).bounds(leftPos + 8, topPos + 68, 52, 16).build());
        renameButton = addRenderableWidget(Button.builder(Component.translatable("logistics.channel.rename"),
            b -> { send(dev.alan.logistics.ChannelAction.Kind.RENAME, 0, nameBox.getValue(), false); nameBox.setValue(""); }).bounds(leftPos + 62, topPos + 68, 52, 16).build());
        deleteButton = addRenderableWidget(Button.builder(Component.translatable("logistics.channel.delete"), b -> {
            if (!deleteArmed) { deleteArmed = true; return; }
            deleteArmed = false;
            send(dev.alan.logistics.ChannelAction.Kind.DELETE, 0, "", false);
        }).bounds(leftPos + 116, topPos + 68, 52, 16).build());
        visibilityButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            var entry = ClientChannels.get(menu.extra(0));
            if (entry != null) send(dev.alan.logistics.ChannelAction.Kind.SET_PUBLIC, 0, "", !entry.isPublic());
        }).bounds(leftPos + 8, topPos + 88, 160, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("logistics.channel.write"), b -> press(DeviceMenu.WRITE_CARD)).bounds(leftPos + 30, topPos + 107, 66, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("logistics.channel.read"), b -> press(DeviceMenu.READ_CARD)).bounds(leftPos + 100, topPos + 107, 68, 20).build());
        updateButtons();
    }

    private static Component visibilityLabel(boolean isPublic) {
        return Component.translatable(isPublic ? "logistics.channel.public" : "logistics.channel.private");
    }

    private void send(dev.alan.logistics.ChannelAction.Kind kind, int value, String text, boolean flag) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.alan.logistics.ChannelAction(menu.containerId, kind, value, text, flag));
    }

    private void select(int id) { send(dev.alan.logistics.ChannelAction.Kind.SELECT, id, "", false); }

    private void updateButtons() {
        if (nameBox == null) return;
        var entry = ClientChannels.get(menu.extra(0));
        boolean manage = entry != null && entry.canManage();
        boolean named = !nameBox.getValue().isBlank();
        createButton.active = named;
        renameButton.active = manage && named;
        deleteButton.active = manage;
        deleteButton.setMessage(Component.translatable(deleteArmed ? "logistics.channel.sure" : "logistics.channel.delete"));
        visibilityButton.active = manage;
        visibilityButton.setMessage(entry == null ? Component.translatable("logistics.channel.pick")
            : Component.translatable(entry.isPublic() ? "logistics.channel.make_private" : "logistics.channel.make_public"));
    }

    @Override protected void containerTick() { updateButtons(); }

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
                var entry = ClientChannels.get(channel);
                Component text = channel == 0 ? Component.translatable("logistics.channel.closed") : Component.literal(font.plainSubstrByWidth(ClientChannels.label(channel), 112));
                g.centeredText(font, text, 88, 21, 0xFFFFFFFF);
                if (entry != null) g.centeredText(font, visibilityLabel(entry.isPublic()), 88, 36, entry.isPublic() ? 0xFF8AD65A : 0xFFE39B4A);
                else if (channel != 0) g.centeredText(font, Component.translatable("logistics.channel.unlisted"), 88, 36, 0xFFA5BBB5);
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
        int base = menu.kind == DeviceMenu.Kind.CHANNEL ? 56 : 0;
        g.text(font, EnergyText.of(menu.data), 8, 78 + base * 0 + (base > 0 ? 52 : 0), 0xFFE8D27A, false);
        g.text(font, playerInventoryTitle, 8, 88 + (base > 0 ? 54 : 0), 0xFFD4E4D8, false);
    }
}
