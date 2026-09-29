package dev.alan.shapeshift.client;

import dev.alan.shapeshift.Ability;
import dev.alan.shapeshift.FormDefinitions;
import dev.alan.shapeshift.Forms;
import dev.alan.shapeshift.RequestForm;
import dev.alan.shapeshift.ShapeshiftMod;
import dev.alan.shapeshift.Unlocks;
import dev.alan.shapeshift.Weakness;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/** Grid of unlocked forms with live 3D previews. Opened with the menu key (V by default). */
public final class FormScreen extends Screen {
    private static final int COLS = 6, ROWS = 3, PER_PAGE = COLS * ROWS;
    private static final int CELL_W = 52, CELL_H = 60, GAP = 4;
    private static final int GRID_W = COLS * CELL_W + (COLS - 1) * GAP, GRID_H = ROWS * CELL_H + (ROWS - 1) * GAP;
    private final List<EntityType<?>> unlocked = new ArrayList<>();
    private final List<EntityType<?>> filtered = new ArrayList<>();
    private final Map<EntityType<?>, LivingEntity> previews = new HashMap<>();
    private final Button[] cells = new Button[PER_PAGE];
    private EditBox search;
    private Button human, previous, next;
    private int page, left, top;

    public FormScreen() {
        super(Component.translatable("shapeshift.menu.title"));
    }

    @Override protected void init() {
        unlocked.clear();
        var player = minecraft.player;
        Unlocks unlocks = player.getAttachedOrElse(ShapeshiftMod.UNLOCKS, Unlocks.EMPTY);
        for (String id : unlocks.ids()) Forms.type(id).filter(Forms::isLivingForm).ifPresent(unlocked::add);

        left = (width - GRID_W) / 2;
        top = Math.max(4, (height - (GRID_H + 72)) / 2);
        search = addRenderableWidget(new EditBox(font, left, top + 16, GRID_W - 128, 18, Component.translatable("shapeshift.menu.search")));
        search.setHint(Component.translatable("shapeshift.menu.search"));
        search.setMaxLength(80);
        search.setResponder(text -> { page = 0; refresh(); });
        human = addRenderableWidget(Button.builder(Component.translatable("shapeshift.menu.human"), b -> choose(null))
            .bounds(left + GRID_W - 120, top + 15, 120, 20).build());
        for (int i = 0; i < PER_PAGE; i++) {
            final int slot = i;
            int x = left + (i % COLS) * (CELL_W + GAP), y = top + 42 + (i / COLS) * (CELL_H + GAP);
            cells[i] = addRenderableWidget(Button.builder(Component.empty(), b -> {
                int pos = page * PER_PAGE + slot;
                if (pos < filtered.size()) choose(filtered.get(pos));
            }).bounds(x, y, CELL_W, CELL_H).build());
        }
        int pagerY = top + 42 + GRID_H + 6;
        previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; refresh(); })
            .bounds(left, pagerY, 24, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; refresh(); })
            .bounds(left + GRID_W - 24, pagerY, 24, 20).build());
        setInitialFocus(search);
        refresh();
    }

    private void refresh() {
        String query = search.getValue().toLowerCase(Locale.ROOT).strip();
        filtered.clear();
        for (var type : unlocked)
            if (Forms.id(type).contains(query) || type.getDescription().getString().toLowerCase(Locale.ROOT).contains(query))
                filtered.add(type);
        page = Math.max(0, Math.min(page, (filtered.size() - 1) / PER_PAGE));
        var current = Forms.current(minecraft.player).orElse(null);
        for (int i = 0; i < PER_PAGE; i++) {
            int pos = page * PER_PAGE + i;
            boolean visible = pos < filtered.size();
            cells[i].visible = visible;
            if (!visible) continue;
            var type = filtered.get(pos);
            cells[i].active = type != current;
            cells[i].setTooltip(Tooltip.create(tooltip(type)));
        }
        human.active = current != null;
        previous.active = page > 0;
        next.active = (page + 1) * PER_PAGE < filtered.size();
    }

    private static Component tooltip(EntityType<?> type) {
        var form = FormDefinitions.resolve(type, true);
        var text = Component.empty()
            .append(type.getDescription())
            .append(Component.literal("\n" + Forms.id(type)).withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal("\n"))
            .append(Component.translatable("shapeshift.menu.health", formatHealth(form.maxHealth())).withStyle(ChatFormatting.RED));
        for (Ability ability : Ability.values())
            if (form.has(ability))
                text.append(Component.literal("\n• ").withStyle(ChatFormatting.AQUA))
                    .append(Component.translatable(ability.translationKey()).withStyle(ChatFormatting.AQUA));
        form.active().ifPresent(active -> text
            .append(Component.literal("\n[R] ").withStyle(ChatFormatting.GOLD))
            .append(Component.translatable(active.type().translationKey()).withStyle(ChatFormatting.GOLD)));
        for (Weakness weakness : Weakness.values())
            if (form.has(weakness))
                text.append(Component.literal("\n✖ ").withStyle(ChatFormatting.RED))
                    .append(Component.translatable(weakness.translationKey()).withStyle(ChatFormatting.RED));
        return text;
    }

    private static String formatHealth(double health) {
        return health == Math.floor(health) ? String.valueOf((long) health) : String.format(Locale.ROOT, "%.1f", health);
    }

    private void choose(@Nullable EntityType<?> type) {
        ClientPlayNetworking.send(new RequestForm(type == null ? "" : Forms.id(type)));
        onClose();
    }

    private @Nullable LivingEntity preview(EntityType<?> type) {
        return previews.computeIfAbsent(type, t -> FormBodies.detached(t, minecraft.level) instanceof LivingEntity living ? living : null);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        g.text(font, title, left, top + 2, 0xFFFFFFFF, true);
        var count = Component.translatable("shapeshift.menu.count", unlocked.size());
        g.text(font, count, left + GRID_W - font.width(count), top + 2, 0xFFA0A0A0, false);
        if (unlocked.isEmpty())
            g.centeredText(font, Component.translatable("shapeshift.menu.empty"), width / 2, top + 42 + GRID_H / 2, 0xFFD0D0D0);
        var current = Forms.current(minecraft.player).orElse(null);
        for (int i = 0; i < PER_PAGE; i++) {
            if (!cells[i].visible) continue;
            var type = filtered.get(page * PER_PAGE + i);
            int x = cells[i].getX(), y = cells[i].getY();
            if (type == current) g.outline(x - 1, y - 1, CELL_W + 2, CELL_H + 2, 0xFFE6C66B);
            var entity = preview(type);
            if (entity != null) {
                var dims = Forms.dimensions(type);
                int size = (int) Math.clamp(36 / Math.max(dims.height(), dims.width() * 0.9f), 3, 30);
                try {
                    InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + 2, y + 2, x + CELL_W - 2, y + CELL_H - 13,
                        size, 0.0625f, mouseX, mouseY, entity);
                } catch (RuntimeException e) {
                    previews.put(type, null);
                }
            }
            String name = font.plainSubstrByWidth(type.getDescription().getString(), CELL_W - 4);
            g.centeredText(font, name, x + CELL_W / 2, y + CELL_H - 11, 0xFFFFFFFF);
        }
        g.centeredText(font, (page + 1) + " / " + Math.max(1, (filtered.size() + PER_PAGE - 1) / PER_PAGE),
            width / 2, top + 42 + GRID_H + 12, 0xFFD0D0D0);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (!search.isFocused() && ShapeshiftClient.OPEN_MENU.matches(event)) {
            onClose();
            return true;
        }
        if (search.isFocused() && !event.isEscape()) return search.keyPressed(event) || search.canConsumeInput();
        return super.keyPressed(event);
    }

    @Override public boolean isPauseScreen() { return false; }
}
