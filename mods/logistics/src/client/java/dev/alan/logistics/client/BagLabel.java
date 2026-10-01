package dev.alan.logistics.client;

import dev.alan.logistics.BagCardSlot;
import net.minecraft.network.chat.Component;

/** Adds the "channel card" caption above the card slot in the alchemy backpack screen (only touched when that mod is present). */
final class BagLabel {
    private BagLabel() {}

    static void register() {
        dev.alchemy.client.BagScreen.LABELS.add((g, font) ->
            g.text(font, Component.translatable("logistics.bag.card"),
                BagCardSlot.X + 16 - font.width(Component.translatable("logistics.bag.card")), BagCardSlot.Y - 12, 0xFFD4E4D8, false));
    }
}
