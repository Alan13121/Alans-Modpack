package dev.alan.shapeshift.client;

import dev.alan.shapeshift.FormDefinitions;
import dev.alan.shapeshift.Forms;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Bottom-left reminder of the current form and what the ability key does. */
final class FormHud {
    private FormHud() {}

    static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var type = Forms.current(mc.player).orElse(null);
        if (type == null) return;
        var font = mc.font;
        int x = 4, y = g.guiHeight() - 12;
        var active = FormDefinitions.resolve(type, true).active();
        if (active.isPresent()) {
            var key = ShapeshiftClient.USE_ABILITY.getTranslatedKeyMessage();
            g.text(font, Component.literal("[").append(key).append("] ").append(Component.translatable(active.get().type().translationKey())),
                x, y, 0xFFE6C66B, true);
            y -= 10;
        }
        g.text(font, Component.translatable("shapeshift.hud.form", type.getDescription()), x, y, 0xFFFFFFFF, true);
    }
}
