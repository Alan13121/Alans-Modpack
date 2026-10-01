package dev.alchemy.client;

import dev.alchemy.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class BagScreen extends AbstractContainerScreen<BagMenu> {
    /** Extra labels other mods draw into the backpack screen (relative to its top-left corner). */
    public static final List<java.util.function.BiConsumer<GuiGraphicsExtractor, net.minecraft.client.gui.Font>> LABELS = new ArrayList<>();
    private static final int ROWS = 6;
    private final Button[] one = new Button[ROWS], stack = new Button[ROWS];
    private final List<Integer> filtered = new ArrayList<>();
    private EditBox search;
    private Button previous, next;
    private int page;
    public BagScreen(BagMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 360, 238);
    }
    @Override protected void init() {
        super.init();
        search = addRenderableWidget(new EditBox(font, leftPos + 190, topPos + 36, 158, 18, Component.translatable("alchemy.search")));
        search.setHint(Component.translatable("alchemy.search"));
        search.setMaxLength(80);
        search.setResponder(text -> { page = 0; refresh(); });
        for (int i = 0; i < ROWS; i++) {
            final int row = i;
            one[i] = addRenderableWidget(Button.builder(Component.literal("1"), b -> redeem(row, false))
                .bounds(leftPos + 299, topPos + 60 + i * 24, 22, 20).build());
            stack[i] = addRenderableWidget(Button.builder(Component.literal("+"), b -> redeem(row, true))
                .bounds(leftPos + 324, topPos + 60 + i * 24, 24, 20).build());
            one[i].setTooltip(Tooltip.create(Component.translatable("alchemy.redeem_one")));
            stack[i].setTooltip(Tooltip.create(Component.translatable("alchemy.redeem_stack")));
        }
        previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; refresh(); })
            .bounds(leftPos + 190, topPos + 211, 24, 18).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; refresh(); })
            .bounds(leftPos + 324, topPos + 211, 24, 18).build());
        refresh();
    }
    private ItemStack item(int learnedIndex) {
        var ids = menu.data().learned();
        if (learnedIndex < 0 || learnedIndex >= ids.size()) return ItemStack.EMPTY;
        var id = Identifier.tryParse(ids.get(learnedIndex));
        return id == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(id).map(i -> i.getDefaultInstance()).orElse(ItemStack.EMPTY);
    }
    private void refresh() {
        filtered.clear();
        String query = search.getValue().toLowerCase(Locale.ROOT).strip();
        var data = menu.data();
        for (int i = 0; i < data.learned().size(); i++) {
            var item = item(i);
            if (!item.isEmpty() && (data.learned().get(i).contains(query) || item.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query))) filtered.add(i);
        }
        page = Math.max(0, Math.min(page, Math.max(0, (filtered.size() - 1) / ROWS)));
        for (int r = 0; r < ROWS; r++) {
            if (one[r] == null) continue;
            int pos = page * ROWS + r;
            boolean visible = pos < filtered.size();
            one[r].visible = stack[r].visible = visible;
            long cost = visible ? EnergyValues.value(item(filtered.get(pos)).getItem(), true) : 0;
            one[r].active = stack[r].active = cost > 0 && data.energy() >= cost;
        }
        if (previous != null) previous.active = page > 0;
        if (next != null) next.active = (page + 1) * ROWS < filtered.size();
    }
    private void redeem(int row, boolean bulk) {
        int pos = page * ROWS + row;
        if (pos < filtered.size() && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 100 + filtered.get(pos) * 2 + (bulk ? 1 : 0));
    }
    @Override protected void containerTick() { refresh(); }
    @Override public boolean keyPressed(KeyEvent event) {
        if (search.isFocused() && !event.isEscape()) {
            return search.keyPressed(event) || search.canConsumeInput();
        }
        return super.keyPressed(event);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF172328);
        g.outline(x, y, imageWidth, imageHeight, 0xFFAE945D);
        g.fill(x + 182, y + 8, x + 183, y + 230, 0xFF405150);
        g.fill(x + 12, y + 35, x + 174, y + 130, 0xFF223437);
        for (var slot : menu.slots) {
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF0E191D);
            g.outline(x + slot.x - 1, y + slot.y - 1, 18, 18, 0xFF61746C);
        }
        for (int r = 0; r < ROWS; r++)
            g.fill(x + 190, y + 59 + r * 24, x + 348, y + 81 + r * 24, 0xFF223437);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        g.text(font, title, 12, 10, 0xFFF0DC9A, false);
        g.text(font, Component.translatable("alchemy.balance", menu.data().energy()), 12, 23, 0xFFE6C66B, false);
        g.text(font, Component.translatable("alchemy.deposit"), 24, 45, 0xFFD4E4D8, false);
        g.text(font, Component.literal("→ EMC"), 58, 71, 0xFF87D9BE, false);
        g.text(font, Component.translatable("alchemy.hint1"), 22, 98, 0xFFA5BBB5, false);
        g.text(font, Component.translatable("alchemy.hint2"), 22, 112, 0xFFA5BBB5, false);
        g.text(font, playerInventoryTitle, 12, 139, 0xFFD4E4D8, false);
        g.text(font, Component.translatable("alchemy.learned", menu.data().learned().size()), 190, 15, 0xFFD4E4D8, false);
        if (filtered.isEmpty()) g.text(font, Component.translatable("alchemy.empty"), 195, 89, 0xFFA5BBB5, false);
        for (int r = 0; r < ROWS; r++) {
            int pos = page * ROWS + r;
            if (pos >= filtered.size()) break;
            var item = item(filtered.get(pos));
            int y = 62 + r * 24;
            g.item(item, 193, y);
            g.text(font, font.plainSubstrByWidth(item.getHoverName().getString(), 83), 212, y, 0xFFE5EEE9, false);
            g.text(font, EnergyValues.value(item.getItem(), true) + " EMC", 212, y + 10, 0xFFE6C66B, false);
            if (mx >= leftPos + 190 && mx < leftPos + 298 && my >= topPos + y && my < topPos + y + 22)
                g.setTooltipForNextFrame(font, item, mx, my);
        }
        for (var label : LABELS) label.accept(g, font);
        g.centeredText(font, (page + 1) + " / " + Math.max(1, (filtered.size() + ROWS - 1) / ROWS), 270, 216, 0xFFD4E4D8);
    }
}
