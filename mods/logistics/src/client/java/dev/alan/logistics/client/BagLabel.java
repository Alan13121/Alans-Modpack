package dev.alan.logistics.client;

import dev.alan.logistics.BagCardSlot;
import dev.alan.logistics.ChannelAction;
import dev.alan.logistics.LogisticsMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** The channel picker and card caption in the alchemy backpack screen (only touched when that mod is present). */
final class BagLabel {
    private static final int PICKER_Y = 88;

    private BagLabel() {}

    private static int channel(dev.alchemy.client.BagScreen screen) {
        return screen.getMenu().bag().getOrDefault(LogisticsMod.BAG_CHANNEL, 0);
    }

    static void register() {
        dev.alchemy.client.BagScreen.EXTENSIONS.add((screen, add, left, top) -> {
            add.accept(Button.builder(Component.literal("‹"), b -> pick(screen, -1)).bounds(left + 16, top + PICKER_Y - 2, 14, 11).build());
            add.accept(Button.builder(Component.literal("›"), b -> pick(screen, 1)).bounds(left + 156, top + PICKER_Y - 2, 14, 11).build());
        });
        dev.alchemy.client.BagScreen.LABELS.add((g, font) -> {
            g.text(font, Component.translatable("logistics.bag.card"),
                BagCardSlot.X + 16 - font.width(Component.translatable("logistics.bag.card")), BagCardSlot.Y - 12, 0xFFD4E4D8, false);
            var screen = net.minecraft.client.Minecraft.getInstance().gui.screen();
            if (!(screen instanceof dev.alchemy.client.BagScreen bag)) return;
            int id = channel(bag);
            Component text = id == 0 ? Component.translatable("logistics.bag.no_channel") : Component.literal(font.plainSubstrByWidth(ClientChannels.label(id), 118));
            g.centeredText(font, text, 93, PICKER_Y, id == 0 ? 0xFFA5BBB5 : 0xFF7BD66A);
        });
    }

    private static void pick(dev.alchemy.client.BagScreen screen, int direction) {
        int next = ClientChannels.step(channel(screen), direction);
        ClientPlayNetworking.send(new ChannelAction(screen.getMenu().containerId, ChannelAction.Kind.SELECT, next, "", false));
    }
}
