package dev.alan.lookup.client;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** The item tooltip of the lists and recipe pages: the vanilla lines plus the name of the mod that owns the item. */
final class ItemTooltip {
    private ItemTooltip() {}

    static List<Component> lines(Minecraft mc, ItemStack stack, List<Component> extra) {
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(mc, stack));
        lines.addAll(extra);
        lines.add(Component.literal(modName(stack)).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
        return lines;
    }

    static String modName(ItemStack stack) {
        String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        return FabricLoader.getInstance().getModContainer(namespace).map(mod -> mod.getMetadata().getName()).orElse(namespace);
    }

    static void drawText(GuiGraphicsExtractor g, Font font, Component text, int mouseX, int mouseY) {
        g.nextStratum();
        g.tooltip(font, List.of(ClientTooltipComponent.create(text.getVisualOrderText())), mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null, false);
    }

    /**
     * Draws right away instead of deferring: a screen's after-render hook runs once the deferred
     * tooltip has already been flushed, so a deferred one would be lost.
     */
    static void draw(GuiGraphicsExtractor g, Minecraft mc, Font font, ItemStack stack, List<Component> extra, int mouseX, int mouseY) {
        List<ClientTooltipComponent> parts = new ArrayList<>();
        for (Component line : lines(mc, stack, extra)) parts.add(ClientTooltipComponent.create(line.getVisualOrderText()));
        stack.getTooltipImage().ifPresent(image -> parts.add(parts.isEmpty() ? 0 : 1, ClientTooltipComponent.create(image)));
        g.nextStratum();
        g.tooltip(font, parts, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, stack.get(DataComponents.TOOLTIP_STYLE), false);
    }
}
