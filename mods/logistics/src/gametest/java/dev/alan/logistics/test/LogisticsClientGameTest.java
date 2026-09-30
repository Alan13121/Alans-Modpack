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
import net.minecraft.world.level.block.Blocks;

/** Builds a small warehouse, opens the terminal and exercises store / take / cell / controller rules. */
public final class LogisticsClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        if (System.getenv("LOGISTICS_BENCH") != null) {
            benchmark(context);
            return;
        }
        warehouse(context);
        interfaces(context);
        crafting(context);
    }

    /**
     * Timing of the network operations on a large warehouse (about 4000 chests, 2000 cables, 100k stacks).
     * Only runs with LOGISTICS_BENCH set: {@code LOGISTICS_BENCH=1 ./gradlew :mods:logistics:runClientGameTest}.
     */
    private void benchmark(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("forceload add 0 -10 120 100");
            context.waitTicks(100);
            String report = world.getServer().computeOnServer(server -> {
                var level = server.overworld();
                var rnd = new java.util.Random(42);
                var items = new java.util.ArrayList<>(net.minecraft.core.registries.BuiltInRegistries.ITEM.stream().filter(i -> i != Items.AIR).toList());
                var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
                long t0 = System.nanoTime();
                int chests = 0, cables = 0;
                for (int x = -2; x <= 101; x++) for (int z = -2; z <= 82; z++) level.setBlock(pos.set(x, 119, z), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(new net.minecraft.core.BlockPos(0, 120, 0), dev.alan.logistics.LogisticsMod.CONTROLLER.defaultBlockState(), 2);
                // A spine of cable along x=-1, and every 4th row a cable line with chests on both sides.
                for (int z = 0; z <= 80; z++) { level.setBlock(pos.set(-1, 120, z), dev.alan.logistics.LogisticsMod.CABLE.defaultBlockState(), 2); cables++; }
                for (int z = 0; z <= 80; z += 4) {
                    for (int x = 0; x < 100; x++) {
                        if (x != 0 || z != 0) { level.setBlock(pos.set(x, 120, z), dev.alan.logistics.LogisticsMod.CABLE.defaultBlockState(), 2); cables++; }
                        for (int side : new int[] {-1, 1}) {
                            var chestPos = new net.minecraft.core.BlockPos(x, 120, z + side);
                            level.setBlock(chestPos, Blocks.BARREL.defaultBlockState(), 2);
                            if (level.getBlockEntity(chestPos) instanceof net.minecraft.world.Container c)
                                for (int i = 0; i < c.getContainerSize(); i++) c.setItem(i, new ItemStack(items.get(rnd.nextInt(items.size())), 1 + rnd.nextInt(16)));
                            chests++;
                        }
                    }
                }
                var sb = new StringBuilder();
                sb.append(String.format("BENCH built %d barrels, %d cables in %d ms%n", chests, cables, (System.nanoTime() - t0) / 1_000_000));
                var start = new net.minecraft.core.BlockPos(0, 120, 0);
                for (int run = 0; run < 3; run++) {
                    long a = System.nanoTime();
                    var network = dev.alan.logistics.Network.scan(level, start);
                    long b = System.nanoTime();
                    var contents = network.contents();
                    long c2 = System.nanoTime();
                    var hit = network.extract(new ItemStack(items.get(5)), 64);
                    long d = System.nanoTime();
                    network.insert(hit);
                    long e = System.nanoTime();
                    network.insert(new ItemStack(Items.BEDROCK, 64));
                    long f = System.nanoTime();
                    sb.append(String.format("BENCH run%d scan=%.1fms contents=%.1fms (%d types) extract=%.2fms insert=%.2fms insertMiss=%.2fms status=%s%n",
                        run, (b - a) / 1e6, (c2 - b) / 1e6, contents.size(), (d - c2) / 1e6, (e - d) / 1e6, (f - e) / 1e6, network.status));
                }
                // Steady state: one container changes, then everything is read again; then 200 mixed operations.
                var network = dev.alan.logistics.Network.scan(level, start);
                network.contents();
                var barrel = (net.minecraft.world.Container) level.getBlockEntity(new net.minecraft.core.BlockPos(50, 120, 1));
                long g = System.nanoTime();
                barrel.setItem(0, new ItemStack(Items.DIAMOND, 3));
                long rev = network.revision();
                long h = System.nanoTime();
                sb.append(String.format("BENCH one-container-change: revision() = %.3f ms (rev %d)%n", (h - g) / 1e6, rev));
                long k = System.nanoTime();
                for (int i = 0; i < 200; i++) {
                    var taken = network.extract(new ItemStack(items.get(i * 7 % items.size())), 16);
                    network.insert(taken);
                    network.insert(new ItemStack(items.get(i * 13 % items.size()), 8));
                }
                sb.append(String.format("BENCH 200 x (extract + insert hit + insert new) = %.2f ms total, %.3f ms per op%n",
                    (System.nanoTime() - k) / 1e6, (System.nanoTime() - k) / 1e6 / 600));
                long m = System.nanoTime();
                for (int i = 0; i < 100; i++) dev.alan.logistics.Network.scan(level, start);
                sb.append(String.format("BENCH 100 cached scans = %.3f ms%n", (System.nanoTime() - m) / 1e6));
                // Topology change: one more cable at the end of a line, then the next scan rebuilds.
                level.setBlock(new net.minecraft.core.BlockPos(100, 120, 0), dev.alan.logistics.LogisticsMod.CABLE.defaultBlockState(), 3);
                long n0 = System.nanoTime();
                var rebuilt = dev.alan.logistics.Network.scan(level, start);
                long n1 = System.nanoTime();
                rebuilt.contents();
                sb.append(String.format("BENCH after topology change: scan=%.1f ms, first contents=%.1f ms, rebuilt=%b%n", (n1 - n0) / 1e6, (System.nanoTime() - n1) / 1e6, rebuilt != network));
                return sb.toString();
            });
            System.out.println(report);
        }
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
            context.waitForScreen(dev.alan.logistics.client.InterfaceScreen.class);
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

            // Upgrades: quartz moves more per pulse, redstone pulses more often. A dropper takes 576 items.
            world.getServer().runCommand("setblock -3 121 0 minecraft:dropper");
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                var cell = (dev.alan.logistics.CellBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(0, 121, 0));
                cell.insert(new ItemStack(Items.COBBLESTONE, 500), true);
                var out = (dev.alan.logistics.OutputInterfaceBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(-2, 121, 0));
                for (int i = 0; i < 9; i++) out.filter().setItem(i, ItemStack.EMPTY);
                out.filter().setItem(0, new ItemStack(Items.COBBLESTONE));
                // The dropper is next to the output interface's west face.
            });
            context.waitTicks(25);
            int slow = dropperItems(world);
            check(slow > 0 && slow <= 96, "without upgrades only a few pulses of 32 arrive, got " + slow);
            check(world.getServer().computeOnServer(server -> {
                var out = (dev.alan.logistics.OutputInterfaceBlockEntity) server.overworld().getBlockEntity(new net.minecraft.core.BlockPos(-2, 121, 0));
                return out.upgrades().interval() == 10 && out.upgrades().amount() == 32;
            }), "no upgrades means 10 ticks and 32 items");
            world.getServer().runOnServer(server -> {
                var out = (dev.alan.logistics.OutputInterfaceBlockEntity) server.overworld().getBlockEntity(new net.minecraft.core.BlockPos(-2, 121, 0));
                out.upgrades().setItem(0, new ItemStack(Items.REDSTONE, 8));
                out.upgrades().setItem(1, new ItemStack(Items.REDSTONE, 8));
                out.upgrades().setItem(2, new ItemStack(Items.QUARTZ, 8));
                out.upgrades().setItem(3, new ItemStack(Items.QUARTZ, 8));
            });
            check(world.getServer().computeOnServer(server -> {
                var out = (dev.alan.logistics.OutputInterfaceBlockEntity) server.overworld().getBlockEntity(new net.minecraft.core.BlockPos(-2, 121, 0));
                return out.upgrades().interval() == 1 && out.upgrades().amount() == 256;
            }), "16 redstone and 16 quartz mean every tick and 256 items");
            context.waitTicks(20);
            int fast = dropperItems(world);
            check(fast >= 400, "upgraded interface moved most of the 500 cobblestone in 20 ticks, dropper has " + fast);

            // The input interface has a screen with upgrade slots; shift-click puts quartz there.
            world.getServer().runCommand("give @p minecraft:quartz 5");
            world.getServer().runCommand("tp @p 1.5 120 3.5 180 20");
            context.waitTicks(10);
            context.getInput().pressKey(options -> options.keyUse);
            context.waitForScreen(dev.alan.logistics.client.InterfaceScreen.class);
            context.waitTicks(5);
            int quartz = context.computeOnClient(mc -> {
                for (var st : mc.player.containerMenu.slots) if (st.getItem().is(Items.QUARTZ)) return st.index;
                return -1;
            });
            context.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, quartz, 0, ContainerInput.QUICK_MOVE, mc.player));
            context.waitTicks(10);
            context.takeScreenshot("06b-input-upgrades");
            check(world.getServer().computeOnServer(server -> {
                var be = (dev.alan.logistics.InputInterfaceBlockEntity) server.overworld().getBlockEntity(new net.minecraft.core.BlockPos(1, 120, 0));
                return be.upgrades().amount() == 32 + 5 * 14;
            }), "quartz landed in the input interface's upgrade slots");
        }
    }

    private static int dropperItems(TestSingleplayerContext world) {
        return world.getServer().computeOnServer(server -> {
            var dropper = (net.minecraft.world.Container) server.overworld().getBlockEntity(new net.minecraft.core.BlockPos(-3, 121, 0));
            int n = 0;
            for (int i = 0; i < dropper.getContainerSize(); i++) n += dropper.getItem(i).getCount();
            return n;
        });
    }

    /** Crafting terminal: ingredients come from the warehouse and the grid refills itself. */
    private void crafting(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            for (String command : new String[] {
                "fill -8 119 -8 8 119 8 minecraft:stone", "fill -8 120 -8 8 126 8 minecraft:air",
                "setblock 0 120 0 logistics:controller", "setblock 1 120 0 logistics:cable",
                "setblock 2 120 0 logistics:crafting_terminal[facing=south]", "setblock 1 120 1 minecraft:chest",
                "item replace block 1 120 1 container.0 with minecraft:oak_planks 12",
                "tp @p 2.5 120 3.5 180 20",
            }) world.getServer().runCommand(command);
            context.waitTicks(20);
            context.getInput().pressKey(options -> options.keyUse);
            context.waitForScreen(dev.alan.logistics.client.CraftingTerminalScreen.class);
            context.waitTicks(15);
            check(entry(context, Items.OAK_PLANKS) == 12, "planks are listed, got " + entry(context, Items.OAK_PLANKS));

            compression(context, world);

            // Two planks in a column make sticks.
            act(context, TerminalAction.Kind.TAKE_STACK, Items.OAK_PLANKS);
            context.waitTicks(10);
            context.runOnClient(mc -> {
                int id = mc.player.containerMenu.containerId;
                mc.gameMode.handleContainerInput(id, 1, 1, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(id, 4, 1, ContainerInput.PICKUP, mc.player);
            });
            context.waitTicks(10);
            check(context.computeOnClient(mc -> mc.player.containerMenu.getSlot(0).getItem().is(Items.STICK)
                && mc.player.containerMenu.getSlot(0).getItem().getCount() == 4), "the grid shows a stick recipe");
            context.takeScreenshot("07-crafting-terminal");
            act(context, TerminalAction.Kind.INSERT_ALL, null);
            context.waitTicks(10);
            check(entry(context, Items.OAK_PLANKS) == 10, "the rest of the planks went back, got " + entry(context, Items.OAK_PLANKS));

            // Shift-click the result: crafts until the warehouse is out of planks (12 planks = 6 crafts = 24 sticks).
            context.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 0, 0, ContainerInput.QUICK_MOVE, mc.player));
            context.waitTicks(20);
            int sticks = context.computeOnClient(mc -> mc.player.getInventory().countItem(Items.STICK));
            check(sticks == 24, "shift-crafting used the whole warehouse stock, got " + sticks + " sticks");
            check(entry(context, Items.OAK_PLANKS) == 0, "no planks left in the warehouse");
            context.takeScreenshot("08-after-shift-craft");

            // Store grid: items in the grid go back into the warehouse.
            world.getServer().runCommand("give @p minecraft:oak_planks 3");
            context.waitTicks(5);
            int planks = context.computeOnClient(mc -> {
                for (var s : mc.player.containerMenu.slots) if (s.index >= 10 && s.getItem().is(Items.OAK_PLANKS)) return s.index;
                return -1;
            });
            context.runOnClient(mc -> {
                int id = mc.player.containerMenu.containerId;
                mc.gameMode.handleContainerInput(id, planks, 0, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(id, 1, 0, ContainerInput.PICKUP, mc.player);
            });
            context.waitTicks(5);
            check(context.computeOnClient(mc -> mc.player.containerMenu.getSlot(1).getItem().getCount()) == 3, "planks are in the grid");
            context.runOnClient(mc -> mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, dev.alan.logistics.CraftingTerminalMenu.STORE_GRID));
            context.waitTicks(15);
            check(context.computeOnClient(mc -> mc.player.containerMenu.getSlot(1).getItem().isEmpty()), "grid emptied");
            check(entry(context, Items.OAK_PLANKS) == 3, "planks are in the warehouse, got " + entry(context, Items.OAK_PLANKS));

            // Recipe placement (what Lookup's "+" triggers) draws from the warehouse, not just the inventory.
            world.getServer().runCommand("clear @p");
            world.getServer().runCommand("item replace block 1 120 1 container.1 with minecraft:oak_planks 10");
            context.waitTicks(15);
            context.runOnClient(mc -> mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, dev.alan.logistics.CraftingTerminalMenu.STORE_GRID));
            context.waitTicks(10);
            long before = entry(context, Items.OAK_PLANKS);
            place(context, world, "minecraft:stick", false);
            check(context.computeOnClient(mc -> mc.player.containerMenu.getSlot(0).getItem().is(Items.STICK)), "placement filled a stick recipe from the warehouse");
            check(context.computeOnClient(mc -> mc.player.getInventory().countItem(Items.OAK_PLANKS)) == 0, "no planks are left loose in the inventory");
            check(entry(context, Items.OAK_PLANKS) == before - 2, "two planks moved from the warehouse to the grid, got " + entry(context, Items.OAK_PLANKS) + " of " + before);
            place(context, world, "minecraft:stick", true);
            // The recipe is centred in the grid, so add up every grid slot; each stick craft uses two planks in a column.
            int inGrid = context.computeOnClient(mc -> {
                int n = 0;
                for (int i = 1; i <= 9; i++) n += mc.player.containerMenu.getSlot(i).getItem().getCount();
                return n / 2;
            });
            check(inGrid > 1, "max placement stacks several crafts, got " + inGrid);
            check(context.computeOnClient(mc -> mc.player.getInventory().countItem(Items.OAK_PLANKS)) == 0, "unused planks went back to the warehouse");
            check(entry(context, Items.OAK_PLANKS) + inGrid * 2 == before, "nothing was lost: " + entry(context, Items.OAK_PLANKS) + " + " + inGrid * 2 + " vs " + before);
            context.takeScreenshot("09-placed-recipe");

            // Closing the screen returns whatever is left in the grid to the warehouse too.
            context.runOnClient(mc -> {
                int id = mc.player.containerMenu.containerId;
                mc.gameMode.handleContainerInput(id, planks, 0, ContainerInput.PICKUP, mc.player);
            });
            context.runOnClient(mc -> mc.gui.setScreen(null));
        }
    }

    /** Storage cell compression: four cells make the next tier and keep everything inside. */
    private static void compression(ClientGameTestContext context, TestSingleplayerContext world) {
        String result = world.getServer().computeOnServer(server -> {
            var level = server.overworld();
            var manager = server.getRecipeManager();
            java.util.function.BiFunction<Integer, dev.alan.logistics.CellData, ItemStack> cell = (tier, data) -> {
                ItemStack st = new ItemStack(dev.alan.logistics.LogisticsMod.CELLS.get(tier - 1));
                if (data != null) st.set(dev.alan.logistics.LogisticsMod.CELL_DATA, data);
                return st;
            };
            var dirt = new dev.alan.logistics.CellData(java.util.List.of(new dev.alan.logistics.CellData.Entry(new ItemStack(Items.DIRT), 6000)));
            var more = new dev.alan.logistics.CellData(java.util.List.of(new dev.alan.logistics.CellData.Entry(new ItemStack(Items.DIRT), 4000),
                new dev.alan.logistics.CellData.Entry(new ItemStack(Items.STONE), 5)));
            var four = java.util.List.of(cell.apply(1, dirt), cell.apply(1, more), cell.apply(1, null), cell.apply(1, null));
            var found = manager.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,
                net.minecraft.world.item.crafting.CraftingInput.of(2, 2, four), level);
            if (found.isEmpty()) return "four tier-1 cells did not match";
            ItemStack out = found.get().value().assemble(net.minecraft.world.item.crafting.CraftingInput.of(2, 2, four));
            if (out.getItem() != dev.alan.logistics.LogisticsMod.CELLS.get(1).asItem()) return "wrong result tier: " + out;
            var data = out.get(dev.alan.logistics.LogisticsMod.CELL_DATA);
            if (data == null || data.total() != 10005 || data.entries().size() != 2) return "contents not merged: " + data;
            // Mixed tiers, or three cells, do not match.
            var mixed = java.util.List.of(cell.apply(1, null), cell.apply(1, null), cell.apply(1, null), cell.apply(2, null));
            if (manager.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, net.minecraft.world.item.crafting.CraftingInput.of(2, 2, mixed), level).isPresent())
                return "mixed tiers matched";
            // Too many distinct item types for the next tier (4 x 64 > 128): no recipe, nothing is destroyed.
            var items = new java.util.ArrayList<>(net.minecraft.core.registries.BuiltInRegistries.ITEM.stream().filter(i -> i != Items.AIR).toList());
            var full = new java.util.ArrayList<ItemStack>();
            for (int c = 0; c < 4; c++) {
                var entries = new java.util.ArrayList<dev.alan.logistics.CellData.Entry>();
                for (int i = 0; i < 64; i++) entries.add(new dev.alan.logistics.CellData.Entry(new ItemStack(items.get(c * 64 + i)), 1));
                full.add(cell.apply(1, new dev.alan.logistics.CellData(entries)));
            }
            if (manager.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, net.minecraft.world.item.crafting.CraftingInput.of(2, 2, full), level).isPresent())
                return "overflowing merge matched";
            // Tier 4 is the top.
            var top = java.util.List.of(cell.apply(4, null), cell.apply(4, null), cell.apply(4, null), cell.apply(4, null));
            if (manager.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, net.minecraft.world.item.crafting.CraftingInput.of(2, 2, top), level).isPresent())
                return "tier 4 compressed further";
            return "ok";
        });
        check(result.equals("ok"), "cell compression: " + result);
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
            check(context.computeOnClient(mc -> ((dev.alan.logistics.WarehouseMenu) mc.player.containerMenu).warehouse().status()) == Network.Status.MULTIPLE_CONTROLLERS,
                "two controllers are reported");
            context.takeScreenshot("05-two-controllers");
            context.runOnClient(mc -> mc.gui.setScreen(null));

            // Breaking the cell keeps its contents in the dropped item.
            world.getServer().runCommand("setblock 3 120 0 minecraft:air");
            world.getServer().runCommand("setblock 1 120 -1 minecraft:air destroy");
            context.waitTicks(20);
            world.getServer().runCommand("tp @p 1.5 120 -1.5");
            // The dropped item is picked up once its pickup delay ends; poll instead of guessing a fixed wait.
            for (int i = 0; i < 20 && context.computeOnClient(mc -> mc.player.getInventory().countItem(dev.alan.logistics.LogisticsMod.CELL.asItem())) == 0; i++) {
                world.getServer().runCommand("tp @p @n[type=item]");
                context.waitTicks(10);
            }
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

    private static void place(ClientGameTestContext context, TestSingleplayerContext world, String recipeId, boolean max) {
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().get(0);
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, net.minecraft.resources.Identifier.parse(recipeId));
            var holder = server.getRecipeManager().byKey(key).orElseThrow();
            ((net.minecraft.world.inventory.RecipeBookMenu) player.containerMenu).handlePlacement(max, true, holder, player.level(), player.getInventory());
        });
        context.waitTicks(10);
    }

    private static long entry(ClientGameTestContext context, Item item) {
        return context.computeOnClient(mc -> {
            for (var e : ((dev.alan.logistics.WarehouseMenu) mc.player.containerMenu).warehouse().entries()) if (e.stack().is(item)) return e.count();
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
