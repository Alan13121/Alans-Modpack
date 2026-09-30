package dev.alan.logistics.test;

import dev.alan.logistics.Network;
import dev.alan.logistics.TerminalAction;
import dev.alan.logistics.TerminalMenu;
import dev.alan.logistics.client.TerminalScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Builds a small warehouse, opens the terminal and exercises store / take / cell / controller rules. */
public final class LogisticsClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        warehouse(context);
        interfaces(context);
    }

    /** Hopper → input interface → cell, and cell → output interface → furnace, plus a furnace result pulled back in. */
    private void interfaces(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            for (String command : new String[] {
                "fill -8 119 -8 8 119 8 minecraft:stone", "fill -8 120 -8 8 126 8 minecraft:air",
                "setblock 0 120 0 logistics:controller", "setblock 0 121 0 logistics:cell",
                "setblock 1 120 0 logistics:input_interface",
                "setblock -1 120 0 logistics:cable", "setblock -1 121 0 logistics:cable", "setblock -1 120 1 logistics:cable",
                "setblock -2 121 0 logistics:output_interface", "setblock -2 120 1 logistics:input_interface",
                "setblock -2 120 0 minecraft:furnace",
                "setblock 1 121 0 minecraft:hopper[facing=down]",
                "item replace block 1 121 0 container.0 with minecraft:iron_ingot 20",
                "item replace block -2 120 0 container.2 with minecraft:gold_ingot 5",
                "tp @p -1.5 120 3.5 180 5",
            }) world.getServer().runCommand(command);
            world.getServer().runOnServer(server -> {
                var cell = (dev.alan.logistics.CellBlockEntity) server.overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, 121, 0));
                cell.insert(new ItemStack(Items.IRON_ORE, 10), true);
                cell.insert(new ItemStack(Items.COAL, 10), true);
            });
            context.waitTicks(220); // a hopper moves one item per 8 ticks
            check(cellCount(world, Items.IRON_INGOT) == 20, "hopper items reached the cell, got " + cellCount(world, Items.IRON_INGOT));
            check(cellCount(world, Items.GOLD_INGOT) == 5, "the furnace result was pulled in, got " + cellCount(world, Items.GOLD_INGOT));
            check(furnaceSlot(world, 0) == 0, "an empty filter moves nothing");

            // Set the filter through the real menu: pick up iron ore, click a ghost slot.
            world.getServer().runCommand("give @p minecraft:iron_ore 1");
            context.waitTicks(5);
            context.getInput().pressKey(options -> options.keyUse);
            context.waitForScreen(dev.alan.logistics.client.OutputScreen.class);
            context.waitTicks(5);
            int ore = context.computeOnClient(mc -> {
                for (var s : mc.player.containerMenu.slots) if (s.index >= 9 && s.getItem().is(Items.IRON_ORE)) return s.index;
                return -1;
            });
            check(ore >= 9, "found iron ore in the menu");
            context.runOnClient(mc -> {
                int id = mc.player.containerMenu.containerId;
                mc.gameMode.handleContainerInput(id, ore, 0, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(id, 0, 0, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(id, ore, 0, ContainerInput.PICKUP, mc.player);
            });
            context.waitTicks(10);
            context.takeScreenshot("06-output-filter");
            context.runOnClient(mc -> mc.gui.setScreen(null));
            context.waitTicks(40);
            check(world.getServer().computeOnServer(server -> {
                var be = (dev.alan.logistics.OutputInterfaceBlockEntity) server.overworld().getBlockEntity(new net.minecraft.core.BlockPos(-2, 121, 0));
                return be.filter().getItem(0).is(Items.IRON_ORE);
            }), "the ghost slot holds the filter");
            check(furnaceSlot(world, 0) > 0, "iron ore reached the furnace input");
            check(furnaceSlot(world, 1) == 0, "coal is not on the filter, so the fuel slot stays empty");
        }
    }

    private static int cellCount(TestSingleplayerContext world, Item item) {
        return world.getServer().computeOnServer(server -> ((dev.alan.logistics.CellBlockEntity)
            server.overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, 121, 0))).count(new ItemStack(item)));
    }

    private static int furnaceSlot(TestSingleplayerContext world, int slot) {
        return world.getServer().computeOnServer(server -> ((net.minecraft.world.Container)
            server.overworld().getBlockEntity(new net.minecraft.core.BlockPos(-2, 120, 0))).getItem(slot).getCount());
    }

    private void warehouse(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            for (String command : new String[] {
                "fill -8 119 -8 8 119 8 minecraft:stone", "fill -8 120 -8 8 126 8 minecraft:air",
                "setblock 0 120 0 logistics:controller", "setblock 1 120 0 logistics:cable", "setblock 2 120 0 logistics:terminal[facing=south]",
                "setblock 1 120 1 minecraft:chest", "setblock 1 120 -1 logistics:cell", "setblock 1 121 0 logistics:cable",
                "setblock 1 122 0 minecraft:barrel",
                "item replace block 1 120 1 container.0 with minecraft:diamond 10",
                "item replace block 1 122 0 container.0 with minecraft:iron_ingot 30",
                "tp @p 2.5 120 3.5 180 20",
                "give @p minecraft:cobblestone 32",
            }) world.getServer().runCommand(command);
            context.waitTicks(20);
            context.takeScreenshot("01-warehouse-world");

            context.getInput().pressKey(options -> options.keyUse);
            context.waitForScreen(TerminalScreen.class);
            context.waitTicks(15);
            check(entry(context, Items.DIAMOND) == 10, "chest diamonds show up, got " + entry(context, Items.DIAMOND));
            check(entry(context, Items.IRON_INGOT) == 30, "barrel iron shows up through the vertical cable, got " + entry(context, Items.IRON_INGOT));
            context.takeScreenshot("02-terminal-open");

            // Shift-click cobblestone in the player inventory: it should move into the warehouse.
            int slot = context.computeOnClient(mc -> {
                for (var s : mc.player.containerMenu.slots) if (s.getItem().is(Items.COBBLESTONE)) return s.index;
                return -1;
            });
            check(slot >= 0, "found cobblestone in inventory");
            context.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player));
            context.waitTicks(15);
            check(entry(context, Items.COBBLESTONE) == 32, "cobblestone was stored, got " + entry(context, Items.COBBLESTONE));
            check(context.computeOnClient(mc -> mc.player.getInventory().countItem(Items.COBBLESTONE)) == 0, "inventory emptied");

            // Take the diamonds onto the cursor, then put them back.
            act(context, TerminalAction.Kind.TAKE_STACK, Items.DIAMOND);
            context.waitTicks(10);
            check(context.computeOnClient(mc -> mc.player.containerMenu.getCarried().is(Items.DIAMOND)
                && mc.player.containerMenu.getCarried().getCount() == 10), "diamonds are on the cursor");
            check(entry(context, Items.DIAMOND) == 0, "diamonds left the warehouse");
            act(context, TerminalAction.Kind.INSERT_ONE, null);
            context.waitTicks(10);
            check(entry(context, Items.DIAMOND) == 1, "one diamond went back");
            act(context, TerminalAction.Kind.INSERT_ALL, null);
            context.waitTicks(10);
            check(entry(context, Items.DIAMOND) == 10, "all diamonds went back");
            context.takeScreenshot("03-after-take-and-return");

            // Shift-take: iron goes straight into the inventory.
            act(context, TerminalAction.Kind.SHIFT_TAKE, Items.IRON_INGOT);
            context.waitTicks(10);
            check(context.computeOnClient(mc -> mc.player.getInventory().countItem(Items.IRON_INGOT)) == 30, "shift-take moved iron to the inventory");
            check(entry(context, Items.IRON_INGOT) == 0, "iron left the warehouse");
            context.runOnClient(mc -> mc.gui.setScreen(null));

            // Remove both chests: new items can only go to the cell.
            for (String command : new String[] {"setblock 1 120 1 minecraft:air", "setblock 1 122 0 minecraft:air", "give @p minecraft:dirt 64"})
                world.getServer().runCommand(command);
            context.waitTicks(10);
            context.getInput().pressKey(options -> options.keyUse);
            context.waitForScreen(TerminalScreen.class);
            context.waitTicks(15);
            check(entry(context, Items.DIAMOND) == 0, "removed chest is no longer part of the warehouse");
            int dirt = context.computeOnClient(mc -> {
                for (var s : mc.player.containerMenu.slots) if (s.getItem().is(Items.DIRT)) return s.index;
                return -1;
            });
            context.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, dirt, 0, ContainerInput.QUICK_MOVE, mc.player));
            context.waitTicks(15);
            check(entry(context, Items.DIRT) == 64, "dirt went into the cell, got " + entry(context, Items.DIRT));
            context.takeScreenshot("04-cell-storage");
            context.runOnClient(mc -> mc.gui.setScreen(null));

            // A second controller breaks the network.
            world.getServer().runCommand("setblock 3 120 0 logistics:controller");
            world.getServer().runCommand("setblock 2 121 0 logistics:cable");
            world.getServer().runCommand("setblock 3 121 0 logistics:cable");
            context.waitTicks(10);
            context.getInput().pressKey(options -> options.keyUse);
            context.waitForScreen(TerminalScreen.class);
            context.waitTicks(15);
            check(context.computeOnClient(mc -> ((TerminalMenu) mc.player.containerMenu).status()) == Network.Status.MULTIPLE_CONTROLLERS,
                "two controllers are reported");
            context.takeScreenshot("05-two-controllers");
            context.runOnClient(mc -> mc.gui.setScreen(null));

            // Breaking the cell keeps its contents in the dropped item.
            world.getServer().runCommand("setblock 3 120 0 minecraft:air");
            world.getServer().runCommand("setblock 1 120 -1 minecraft:air destroy");
            context.waitTicks(20);
            world.getServer().runCommand("tp @p 1.5 120 -1.5");
            context.waitTicks(40);
            check(context.computeOnClient(mc -> mc.player.getInventory().countItem(Items.DIRT)) == 0, "dirt is not spilled loose");
            check(context.computeOnClient(mc -> {
                for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
                    var s = mc.player.getInventory().getItem(i);
                    var data = s.get(dev.alan.logistics.LogisticsMod.CELL_DATA);
                    if (data != null && data.total() == 64) return true;
                }
                return false;
            }), "the picked-up cell still holds 64 dirt");

            // Placing the item again restores the contents.
            world.getServer().runCommand("tp @p 5.5 120 -3.5 0 40");
            context.runOnClient(mc -> {
                for (int i = 0; i < 9; i++)
                    if (mc.player.getInventory().getItem(i).is(dev.alan.logistics.LogisticsMod.CELL.asItem())) mc.player.getInventory().setSelectedSlot(i);
            });
            context.waitTicks(5);
            context.getInput().pressKey(options -> options.keyUse);
            context.waitTicks(10);
            int restored = world.getServer().computeOnServer(server -> {
                var level = server.overworld();
                for (int dz = -3; dz <= -1; dz++)
                    if (level.getBlockEntity(new net.minecraft.core.BlockPos(5, 120, dz)) instanceof dev.alan.logistics.CellBlockEntity cell)
                        return cell.count(new ItemStack(Items.DIRT));
                return -1;
            });
            check(restored == 64, "a placed cell restores its 64 dirt, got " + restored);
        }
    }

    private static long entry(ClientGameTestContext context, Item item) {
        return context.computeOnClient(mc -> {
            for (var e : ((TerminalMenu) mc.player.containerMenu).entries()) if (e.stack().is(item)) return e.count();
            return 0L;
        });
    }

    private static void act(ClientGameTestContext context, TerminalAction.Kind kind, Item item) {
        context.runOnClient(mc -> ClientPlayNetworking.send(new TerminalAction(mc.player.containerMenu.containerId, kind,
            item == null ? ItemStack.EMPTY : new ItemStack(item))));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
