package dev.alan.logistics.client;

import dev.alan.logistics.WarehouseMenu;
import dev.alan.lookup.api.LookupPlugin;
import dev.alan.lookup.api.LookupRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Lets Lookup's recipe pages show what the warehouse holds. Loaded only when Lookup is installed. The counts come
 * from the terminal that is open behind the recipe page; with no terminal open there is nothing to show.
 */
public final class LogisticsLookupPlugin implements LookupPlugin {
    @Override public void register(LookupRegistry registry) {
        registry.stock(LogisticsLookupPlugin::stockOf);
    }

    private static long stockOf(ItemStack stack) {
        var player = Minecraft.getInstance().player;
        if (player == null || !(player.containerMenu instanceof WarehouseMenu menu)) return -1;
        if (menu.warehouse().status() != dev.alan.logistics.Network.Status.OK) return -1;
        long total = 0;
        for (var entry : menu.warehouse().entries()) if (entry.stack().is(stack.getItem())) total += entry.count();
        return total;
    }
}
