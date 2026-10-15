package dev.alan.mineworld.client;

import dev.alan.mineworld.city.CityMerchant;
import dev.alan.mineworld.client.mixin.MerchantScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;

/**
 * Adds a lock button to the city enderman's trading screen and a star on locked offers. The server marks lockable
 * merchants (and how many offers are locked, always listed first) through the otherwise unused merchant xp: 1 + locked.
 */
final class TradeLockButton {
    private static final int WIDTH = 276, HEIGHT = 166, ROWS = 7;

    private TradeLockButton() {}

    static void register() {
        ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
            if (!(screen instanceof MerchantScreen merchant)) return;
            int left = (width - WIDTH) / 2, top = (height - HEIGHT) / 2;
            Button button = Button.builder(Component.translatable("mineworld.trade.lock"), b -> press(mc, merchant))
                .bounds(left + 5, top + HEIGHT + 2, 88, 20).build();
            button.visible = false;
            Screens.getWidgets(screen).add(button);
            ScreenEvents.afterTick(screen).register(s -> update(merchant, button));
            ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> {
                int locked = locked(merchant);
                int scroll = ((MerchantScreenAccessor) merchant).mineworld$scrollOff();
                for (int row = 0; row < ROWS; row++) {
                    if (scroll + row < locked) {
                        graphics.text(Minecraft.getInstance().font, "★", left + 5 + 58, top + 18 + row * 20 + 1, 0xFFFFC020, true);
                    }
                }
            });
        });
    }

    /** -1 for merchants without locks, else the number of locked offers. */
    private static int locked(MerchantScreen screen) {
        return screen.getMenu().getTraderXp() - 1;
    }

    private static void update(MerchantScreen screen, Button button) {
        int locked = locked(screen);
        button.visible = locked >= 0 && !screen.getMenu().getOffers().isEmpty();
        boolean selectedLocked = ((MerchantScreenAccessor) screen).mineworld$shopItem() < locked;
        button.setMessage(Component.translatable(selectedLocked ? "mineworld.trade.unlock" : "mineworld.trade.lock"));
    }

    private static void press(Minecraft mc, MerchantScreen screen) {
        int selected = ((MerchantScreenAccessor) screen).mineworld$shopItem();
        if (mc.gameMode != null) mc.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, CityMerchant.LOCK_BUTTON + selected);
    }
}
