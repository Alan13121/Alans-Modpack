package dev.alan.lookup.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** The item list on its own, opened with a key while walking around. */
public final class LookupScreen extends Screen {
    private ItemListOverlay overlay;
    private int lastMouseX, lastMouseY;

    public LookupScreen() {
        super(Component.translatable("lookup.title"));
    }

    @Override protected void init() {
        overlay = new ItemListOverlay(minecraft, this, width, height, true);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        super.extractRenderState(g, mouseX, mouseY, a);
        overlay.extract(g, mouseX, mouseY);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return overlay.mouseClicked(event) || super.mouseClicked(event, doubleClick);
    }

    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        return overlay.mouseScrolled(x, y, scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        return overlay.keyPressed(event, lastMouseX, lastMouseY) || super.keyPressed(event);
    }

    @Override public boolean charTyped(CharacterEvent event) {
        return overlay.charTyped(event) || super.charTyped(event);
    }

    @Override public boolean isPauseScreen() { return false; }
}
