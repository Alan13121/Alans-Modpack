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
        vanillaMachines(context);
        farming(context);
        autocrafting(context);
        transmission(context);
        twoChannelBlocks(context);
        crossDimension(context);
    }

    /** Two warehouses on one channel merge; energy, generators, antennas and teleporters work across them. */
    private void transmission(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            for (String command : new String[] {
                "fill -8 119 -8 48 119 8 minecraft:stone", "fill -8 120 -8 48 126 8 minecraft:air",
                "setblock 0 120 0 logistics:controller", "setblock 0 121 0 logistics:cell",
                "setblock 1 120 0 logistics:cable", "setblock 2 120 0 logistics:channel", "setblock 3 120 0 logistics:teleporter",
                "setblock 1 121 0 logistics:antenna", "setblock 1 120 1 logistics:coal_generator", "setblock 1 120 -1 logistics:terminal",
                "setblock 30 120 0 logistics:controller", "setblock 30 121 0 logistics:cell",
                "setblock 31 120 0 logistics:cable", "setblock 32 120 0 logistics:channel", "setblock 33 120 0 logistics:teleporter",
            }) world.getServer().runCommand(command);
            context.waitTicks(5);
            var a = new net.minecraft.core.BlockPos(0, 120, 0);
            var b = new net.minecraft.core.BlockPos(30, 120, 0);
            check(world.getServer().computeOnServer(server -> {
                var level = server.overworld();
                ((dev.alan.logistics.ChannelBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(2, 120, 0))).setChannel(5);
                ((dev.alan.logistics.ChannelBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(32, 120, 0))).setChannel(5);
                ((dev.alan.logistics.CellBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(0, 121, 0))).insert(new ItemStack(Items.DIAMOND, 10), true);
                ((dev.alan.logistics.CellBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(30, 121, 0))).insert(new ItemStack(Items.IRON_INGOT, 7), true);
                var wa = dev.alan.logistics.Warehouse.at(level, a);
                var wb = dev.alan.logistics.Warehouse.at(level, b);
                return wa.usable() && wa.count(new ItemStack(Items.IRON_INGOT)) == 7 && wa.count(new ItemStack(Items.DIAMOND)) == 10
                    && wb.count(new ItemStack(Items.DIAMOND)) == 10 && wa.typeCount() == 2;
            }), "two networks on channel 5 form one warehouse");
            check(world.getServer().computeOnServer(server -> {
                var level = server.overworld();
                var wa = dev.alan.logistics.Warehouse.at(level, a);
                var taken = wa.extract(new ItemStack(Items.IRON_INGOT), 5);
                var rest = wa.insert(new ItemStack(Items.DIAMOND, 4));
                return taken.getCount() == 5 && rest.isEmpty() && wa.count(new ItemStack(Items.IRON_INGOT)) == 2 && wa.count(new ItemStack(Items.DIAMOND)) == 14;
            }), "extract and insert work across the merged networks");
            check(world.getServer().computeOnServer(server -> {
                var level = server.overworld();
                ((dev.alan.logistics.ChannelBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(32, 120, 0))).setChannel(6);
                boolean split = dev.alan.logistics.Warehouse.at(level, a).count(new ItemStack(Items.IRON_INGOT)) == 0;
                ((dev.alan.logistics.ChannelBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(32, 120, 0))).setChannel(5);
                return split && dev.alan.logistics.Warehouse.at(level, a).count(new ItemStack(Items.IRON_INGOT)) == 2;
            }), "a different channel is a separate warehouse");

            // Energy: one pool per channel, filled by generators.
            check(world.getServer().computeOnServer(server -> {
                var level = server.overworld();
                dev.alan.logistics.Energy.add(java.util.Set.of(5), 100);
                return dev.alan.logistics.Energy.availableAt(level, a) == 100 && dev.alan.logistics.Energy.availableAt(level, b) == 100;
            }), "both networks see the channel's 100 energy");
            world.getServer().runOnServer(server -> ((dev.alan.logistics.CoalGeneratorBlockEntity) server.overworld()
                .getBlockEntity(new net.minecraft.core.BlockPos(1, 120, 1))).slot().setItem(0, new ItemStack(Items.COAL, 2)));
            context.waitTicks(100);
            long afterCoal = world.getServer().computeOnServer(server -> dev.alan.logistics.Energy.availableAt(server.overworld(), a));
            check(afterCoal >= 115 && afterCoal <= 125, "the coal generator adds 4 EMC a second, energy is " + afterCoal);

            // Screens: channel block, antenna, terminal energy line, teleporter list.
            world.getServer().runCommand("tp @p 2.5 120 3.5 180 20");
            context.waitTicks(10);
            context.getInput().pressKey(options -> options.keyUse);
            context.waitForScreen(dev.alan.logistics.client.DeviceScreen.class);
            context.waitTicks(5);
            context.takeScreenshot("12-channel-block");
            // Named channels: create a private one, check who can use it, rename, publish, delete, then go back to channel 5.
            java.util.function.BiConsumer<dev.alan.logistics.ChannelAction.Kind, Object[]> act = (kind, args) -> context.runOnClient(mc ->
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.alan.logistics.ChannelAction(mc.player.containerMenu.containerId,
                    kind, (Integer) args[0], (String) args[1], (Boolean) args[2])));
            act.accept(dev.alan.logistics.ChannelAction.Kind.CREATE, new Object[] {0, "Base", false});
            context.waitTicks(10);
            int created = context.computeOnClient(mc -> ((dev.alan.logistics.DeviceMenu) mc.player.containerMenu).extra(0));
            check(created >= dev.alan.logistics.ChannelRegistry.FIRST_ID, "a new channel gets an id from 10000 up, got " + created);
            check(context.computeOnClient(mc -> dev.alan.logistics.client.ClientChannels.label(created).startsWith("Base")), "the client knows the new channel's name");
            check(world.getServer().computeOnServer(server -> {
                var registry = dev.alan.logistics.ChannelRegistry.get(server);
                var info = registry.info(created);
                var stranger = java.util.UUID.randomUUID();
                var owner = server.getPlayerList().getPlayers().get(0).getUUID();
                return info != null && info.name().equals("Base") && !info.isPublic() && info.owner().equals(owner)
                    && !registry.canUse(created, stranger, false) && registry.canUse(created, stranger, true) && registry.canUse(created, owner, false)
                    && registry.visibleTo(stranger, false, java.util.List.of()).stream().noneMatch(i -> i.id() == created)
                    && !registry.canManage(created, stranger, false) && registry.canManage(created, owner, false);
            }), "a private channel is usable only by its owner (and operators)");
            act.accept(dev.alan.logistics.ChannelAction.Kind.CREATE, new Object[] {0, "base", true});
            context.waitTicks(10);
            check(context.computeOnClient(mc -> ((dev.alan.logistics.DeviceMenu) mc.player.containerMenu).extra(0)) == created, "a duplicate name is refused");
            act.accept(dev.alan.logistics.ChannelAction.Kind.RENAME, new Object[] {0, "Home", false});
            act.accept(dev.alan.logistics.ChannelAction.Kind.SET_PUBLIC, new Object[] {0, "", true});
            context.waitTicks(10);
            check(world.getServer().computeOnServer(server -> {
                var registry = dev.alan.logistics.ChannelRegistry.get(server);
                var info = registry.info(created);
                return info.name().equals("Home") && info.isPublic() && registry.canUse(created, java.util.UUID.randomUUID(), false);
            }), "rename and publish worked");
            act.accept(dev.alan.logistics.ChannelAction.Kind.DELETE, new Object[] {0, "", false});
            context.waitTicks(10);
            check(world.getServer().computeOnServer(server -> dev.alan.logistics.ChannelRegistry.get(server).info(created) == null
                && !dev.alan.logistics.ChannelRegistry.isLive(created)), "a deleted channel is gone");
            check(context.computeOnClient(mc -> ((dev.alan.logistics.DeviceMenu) mc.player.containerMenu).extra(0)) == 0, "the block lost its deleted channel");
            act.accept(dev.alan.logistics.ChannelAction.Kind.SELECT, new Object[] {5, "", false});
            context.waitTicks(10);
            check(context.computeOnClient(mc -> ((dev.alan.logistics.DeviceMenu) mc.player.containerMenu).extra(0)) == 5, "an old numbered channel can still be selected");
            context.takeScreenshot("12a-channel-selected");
            context.runOnClient(mc -> mc.gui.setScreen(null));

            world.getServer().runCommand("tp @p 1.5 120 -3.5 0 20");
            context.waitTicks(10);
            context.getInput().pressKey(options -> options.keyUse);
            context.waitForScreen(dev.alan.logistics.client.TerminalScreen.class);
            context.waitTicks(10);
            context.takeScreenshot("13-terminal-energy");
            check(context.computeOnClient(mc -> dev.alan.logistics.EnergyData.read(((dev.alan.logistics.TerminalMenu) mc.player.containerMenu).energy) >= 115),
                "the terminal menu carries the channel energy");
            context.runOnClient(mc -> mc.gui.setScreen(null));

            world.getServer().runCommand("tp @p 3.5 120 3.5 180 20");
            context.waitTicks(10);
            context.getInput().pressKey(options -> options.keyUse);
            context.waitForScreen(dev.alan.logistics.client.TeleporterScreen.class);
            context.waitTicks(10);
            check(context.computeOnClient(mc -> ((dev.alan.logistics.TeleporterMenu) mc.player.containerMenu).dests().size()) == 1, "the teleporter list shows the other pad");
            context.runOnClient(mc -> mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, dev.alan.logistics.TeleporterMenu.SELECT));
            context.waitTicks(5);
            context.runOnClient(mc -> mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, dev.alan.logistics.TeleporterMenu.OPEN_ALWAYS));
            context.waitTicks(25);
            context.takeScreenshot("14-teleporter-menu");
            context.runOnClient(mc -> mc.gui.setScreen(null));
            check(world.getServer().computeOnServer(server -> ((dev.alan.logistics.TeleporterBlockEntity) server.overworld()
                .getBlockEntity(new net.minecraft.core.BlockPos(3, 120, 0))).mode() == dev.alan.logistics.TeleporterBlockEntity.ALWAYS), "the pad is open");

            // Wireless range: an antenna reaches 10 blocks, redstone blocks add 5 each.
            check(world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().get(0);
                return dev.alan.logistics.WirelessTerminalItem.inRange(player, 5) && !dev.alan.logistics.WirelessTerminalItem.inRange(player, 6);
            }), "next to the antenna channel 5 is in range, channel 6 is not");
            world.getServer().runCommand("tp @p 22.5 120 0.5");
            check(world.getServer().computeOnServer(server -> !dev.alan.logistics.WirelessTerminalItem.inRange(server.getPlayerList().getPlayers().get(0), 5)),
                "21 blocks away is out of range");
            world.getServer().runOnServer(server -> ((dev.alan.logistics.AntennaBlockEntity) server.overworld()
                .getBlockEntity(new net.minecraft.core.BlockPos(1, 121, 0))).slot().setItem(0, new ItemStack(Items.REDSTONE_BLOCK, 3)));
            check(world.getServer().computeOnServer(server -> dev.alan.logistics.WirelessTerminalItem.inRange(server.getPlayerList().getPlayers().get(0), 5)),
                "three redstone blocks stretch the range to 25");


            // Writing a card, binding a wireless terminal by crafting, and opening it from range.
            check(world.getServer().computeOnServer(server -> {
                var level = server.overworld();
                var channel = (dev.alan.logistics.ChannelBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(2, 120, 0));
                channel.card().setItem(0, new ItemStack(dev.alan.logistics.LogisticsMod.CHANNEL_CARD, 3));
                return channel.writeCard() && dev.alan.logistics.ChannelCardItem.channelOf(channel.card().getItem(0)) == 5;
            }), "the channel block writes channel 5 onto the card");
            check(world.getServer().computeOnServer(server -> {
                var level = server.overworld();
                var channel = (dev.alan.logistics.ChannelBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(2, 120, 0));
                var card = channel.card().getItem(0).copyWithCount(1);
                var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 1, java.util.List.of(card, new ItemStack(dev.alan.logistics.LogisticsMod.WIRELESS_TERMINAL)));
                var recipe = server.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level,
                    (net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>) null);
                if (recipe.isEmpty()) return false;
                var result = recipe.get().value().assemble(input);
                return result.is(dev.alan.logistics.LogisticsMod.WIRELESS_TERMINAL) && dev.alan.logistics.ChannelCardItem.channelOf(result) == 5;
            }), "crafting a card with a wireless terminal binds its channel");
            check(world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().get(0);
                var terminal = new ItemStack(dev.alan.logistics.LogisticsMod.WIRELESS_TERMINAL);
                terminal.set(dev.alan.logistics.LogisticsMod.CHANNEL, 5);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, terminal);
                terminal.getItem().use(player.level(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
                return player.containerMenu instanceof dev.alan.logistics.TerminalMenu menu && menu.stillValid(player);
            }), "the bound wireless terminal opens the warehouse from range");
            context.waitTicks(10);
            check(context.computeOnClient(mc -> mc.gui.screen() instanceof dev.alan.logistics.client.TerminalScreen
                && ((dev.alan.logistics.TerminalMenu) mc.player.containerMenu).warehouse().entries().size() == 2), "the wireless terminal lists the merged warehouse");
            context.takeScreenshot("16-wireless-terminal");
            context.runOnClient(mc -> mc.gui.setScreen(null));

            // Teleporting: stepping onto the open pad costs 10 and lands on the other pad. The generator is removed so the count is exact.
            world.getServer().runCommand("setblock 1 120 1 minecraft:air");
            context.waitTicks(5);
            long before = world.getServer().computeOnServer(server -> dev.alan.logistics.Energy.availableAt(server.overworld(), a));
            world.getServer().runCommand("tp @p 3.5 121 0.5");
            context.waitTicks(30);
            double x = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().get(0).getX());
            check(x > 32 && x < 35, "the player arrived on the other pad, x=" + x);
            long after = world.getServer().computeOnServer(server -> dev.alan.logistics.Energy.availableAt(server.overworld(), a));
            check(before - after == 10, "one teleport cost exactly 10 energy, before " + before + " after " + after);
            context.waitTicks(40);
            x = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().get(0).getX());
            check(x > 32 && x < 35, "the player is not bounced back, x=" + x);
            context.takeScreenshot("15-teleporter-beam");

            // Open once: the pad closes after one use.
            world.getServer().runOnServer(server -> ((dev.alan.logistics.TeleporterBlockEntity) server.overworld()
                .getBlockEntity(new net.minecraft.core.BlockPos(3, 120, 0))).setMode(dev.alan.logistics.TeleporterBlockEntity.ONCE));
            world.getServer().runCommand("tp @p 20.5 121 0.5");
            context.waitTicks(10);
            world.getServer().runCommand("tp @p 3.5 121 0.5");
            context.waitTicks(30);
            x = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().get(0).getX());
            check(x > 32 && x < 35, "the once-pad also sent the player, x=" + x);
            check(world.getServer().computeOnServer(server -> ((dev.alan.logistics.TeleporterBlockEntity) server.overworld()
                .getBlockEntity(new net.minecraft.core.BlockPos(3, 120, 0))).mode() == dev.alan.logistics.TeleporterBlockEntity.CLOSED), "the once-pad closed itself");

            // Without energy the pad refuses.
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                dev.alan.logistics.Energy.spend(level, java.util.Set.of(5), dev.alan.logistics.Energy.availableAt(level, a));
                ((dev.alan.logistics.TeleporterBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(3, 120, 0))).setMode(dev.alan.logistics.TeleporterBlockEntity.ALWAYS);
            });
            world.getServer().runCommand("tp @p 20.5 121 0.5");
            context.waitTicks(10);
            world.getServer().runCommand("tp @p 3.5 121 0.5");
            context.waitTicks(30);
            x = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().get(0).getX());
            check(x < 5, "no energy, no teleport, x=" + x);
        }
    }

    /** Two channel blocks on one network: same channel, then different channels. */
    private void twoChannelBlocks(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            for (String command : new String[] {
                "fill -8 119 -8 80 119 8 minecraft:stone", "fill -8 120 -8 80 126 8 minecraft:air",
                "setblock 0 120 0 logistics:controller", "setblock 1 120 0 logistics:cable", "setblock 2 120 0 logistics:channel", "setblock 1 121 0 logistics:channel",
                "setblock 0 121 0 logistics:cell",
                "setblock 30 120 0 logistics:controller", "setblock 31 120 0 logistics:cable", "setblock 32 120 0 logistics:channel", "setblock 30 121 0 logistics:cell",
                "setblock 60 120 0 logistics:controller", "setblock 61 120 0 logistics:cable", "setblock 62 120 0 logistics:channel", "setblock 60 121 0 logistics:cell",
            }) world.getServer().runCommand(command);
            context.waitTicks(5);
            check(world.getServer().computeOnServer(server -> {
                var level = server.overworld();
                var a = new net.minecraft.core.BlockPos(0, 120, 0);
                var c = new net.minecraft.core.BlockPos(60, 120, 0);
                ((dev.alan.logistics.ChannelBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(2, 120, 0))).setChannel(5);
                ((dev.alan.logistics.ChannelBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(1, 121, 0))).setChannel(6);
                ((dev.alan.logistics.ChannelBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(62, 120, 0))).setChannel(6);
                ((dev.alan.logistics.CellBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(60, 121, 0))).insert(new ItemStack(Items.GOLD_INGOT, 1), true);
                var network = dev.alan.logistics.Network.scan(level, a);
                var warehouse = dev.alan.logistics.Warehouse.at(level, a);
                return network.status == dev.alan.logistics.Network.Status.MULTIPLE_CHANNELS && !network.usable() && !warehouse.usable()
                    && dev.alan.logistics.Warehouse.at(level, c).count(new ItemStack(Items.GOLD_INGOT)) == 1
                    && dev.alan.logistics.Warehouse.at(level, c).typeCount() == 1;
            }), "two channel blocks in one network make it unusable and do not bridge other networks");
            world.getServer().runCommand("setblock 1 121 0 minecraft:air");
            context.waitTicks(5);
            check(world.getServer().computeOnServer(server -> {
                var level = server.overworld();
                var network = dev.alan.logistics.Network.scan(level, new net.minecraft.core.BlockPos(0, 120, 0));
                return network.usable() && network.channels().equals(java.util.Set.of(5));
            }), "removing the second channel block makes the network work again");
        }
    }

    /**
     * A pad in the overworld and one in the nether on the same channel. The nether chunks are then unloaded: the pad is
     * still listed (saved pad list) and stepping on the overworld pad arrives there. Chunk loaders force their chunks.
     */
    private void crossDimension(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            for (String command : new String[] {
                "fill -8 119 -8 8 119 8 minecraft:stone", "fill -8 120 -8 8 126 8 minecraft:air",
                "setblock 0 120 0 logistics:controller", "setblock 1 120 0 logistics:cable", "setblock 2 120 0 logistics:channel", "setblock 3 120 0 logistics:teleporter",
                "execute in minecraft:the_nether run forceload add -16 -16 16 16",
            }) world.getServer().runCommand(command);
            context.waitTicks(60);
            for (String command : new String[] {
                "execute in minecraft:the_nether run fill -8 119 -8 8 119 8 minecraft:stone",
                "execute in minecraft:the_nether run fill -8 120 -8 8 126 8 minecraft:air",
                "execute in minecraft:the_nether run setblock 0 120 0 logistics:controller",
                "execute in minecraft:the_nether run setblock 1 120 0 logistics:cable",
                "execute in minecraft:the_nether run setblock 2 120 0 logistics:channel",
                "execute in minecraft:the_nether run setblock 3 120 0 logistics:teleporter",
            }) world.getServer().runCommand(command);
            context.waitTicks(5);
            world.getServer().runOnServer(server -> {
                var nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
                var overworld = server.overworld();
                ((dev.alan.logistics.ChannelBlockEntity) overworld.getBlockEntity(new net.minecraft.core.BlockPos(2, 120, 0))).setChannel(5);
                ((dev.alan.logistics.ChannelBlockEntity) nether.getBlockEntity(new net.minecraft.core.BlockPos(2, 120, 0))).setChannel(5);
                dev.alan.logistics.Energy.add(java.util.Set.of(5), 50);
            });
            context.waitTicks(60); // the pads register themselves in the saved pad list
            world.getServer().runCommand("execute in minecraft:the_nether run forceload remove all");
            context.waitTicks(200);
            check(world.getServer().computeOnServer(server -> !server.getLevel(net.minecraft.world.level.Level.NETHER).getChunkSource().hasChunk(0, 0)),
                "the nether chunks are unloaded now");
            check(world.getServer().computeOnServer(server -> {
                var overworld = server.overworld();
                var pad = (dev.alan.logistics.TeleporterBlockEntity) overworld.getBlockEntity(new net.minecraft.core.BlockPos(3, 120, 0));
                var dests = pad.destinations();
                if (dests.size() != 1 || !dests.get(0).dimension().equals("minecraft:the_nether")) return false;
                pad.setTarget(dests.get(0));
                return pad.setMode(dev.alan.logistics.TeleporterBlockEntity.ALWAYS);
            }), "the unloaded nether pad is still listed as a destination");
            world.getServer().runCommand("tp @p 3.5 121 0.5");
            context.waitTicks(60);
            check(world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().get(0);
                return player.level().dimension().equals(net.minecraft.world.level.Level.NETHER) && Math.abs(player.getX() - 3.5) < 1;
            }), "the player arrived in the nether");
            check(world.getServer().computeOnServer(server -> dev.alan.logistics.Energy.availableAt(server.overworld(), new net.minecraft.core.BlockPos(0, 120, 0)) == 40),
                "the trip cost 10 energy");

            // Chunk loader: the 3x3 chunks around it are forced, released when it is broken, and shared chunks stay.
            world.getServer().runCommand("execute in minecraft:the_nether run setblock 1 121 0 logistics:chunk_loader");
            context.waitTicks(40);
            check(world.getServer().computeOnServer(server -> {
                var forced = server.getLevel(net.minecraft.world.level.Level.NETHER).getForceLoadedChunks();
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (!forced.contains(net.minecraft.world.level.ChunkPos.pack(x, z))) return false;
                return forced.size() == 9;
            }), "a chunk loader forces the 3x3 chunks around it");
            world.getServer().runCommand("execute in minecraft:the_nether run setblock 4 120 0 logistics:cable");
            world.getServer().runCommand("execute in minecraft:the_nether run setblock 4 121 0 logistics:chunk_loader");
            context.waitTicks(10);
            check(world.getServer().computeOnServer(server -> {
                var nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
                var network = dev.alan.logistics.Network.scan(nether, new net.minecraft.core.BlockPos(0, 120, 0));
                return network.status == dev.alan.logistics.Network.Status.MULTIPLE_LOADERS && !network.usable();
            }), "two chunk loaders in one network stop it");
            world.getServer().runOnServer(server -> {
                var nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
                nether.destroyBlock(new net.minecraft.core.BlockPos(4, 121, 0), false);
                nether.destroyBlock(new net.minecraft.core.BlockPos(1, 121, 0), false);
            });
            context.waitTicks(10);
            check(world.getServer().computeOnServer(server -> server.getLevel(net.minecraft.world.level.Level.NETHER).getForceLoadedChunks().isEmpty()),
                "breaking the loaders releases their chunks");
            // A loader removed behind our back (setblock) is caught by the periodic check.
            world.getServer().runCommand("execute in minecraft:the_nether run setblock 1 121 0 logistics:chunk_loader");
            context.waitTicks(40);
            world.getServer().runCommand("execute in minecraft:the_nether run setblock 1 121 0 minecraft:air");
            context.waitTicks(250);
            check(world.getServer().computeOnServer(server -> server.getLevel(net.minecraft.world.level.Level.NETHER).getForceLoadedChunks().isEmpty()),
                "a loader that vanished is released by the periodic check");
        }
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

            // Keep-in-stock level: the interface tops the dropper up to 100 cobblestone and no further.
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                var dropper = (net.minecraft.world.Container) level.getBlockEntity(new net.minecraft.core.BlockPos(-3, 121, 0));
                dropper.clearContent();
                ((dev.alan.logistics.CellBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(0, 121, 0))).insert(new ItemStack(Items.COBBLESTONE, 500), true);
                var out = (dev.alan.logistics.OutputInterfaceBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(-2, 121, 0));
                out.levels().set(0, 100);
            });
            context.waitTicks(30);
            check(dropperItems(world) == 100, "a level of 100 stops at 100 cobblestone, got " + dropperItems(world));
            world.getServer().runOnServer(server -> {
                var dropper = (net.minecraft.world.Container) server.overworld().getBlockEntity(new net.minecraft.core.BlockPos(-3, 121, 0));
                int toRemove = 30;
                for (int i = 0; i < dropper.getContainerSize() && toRemove > 0; i++) {
                    int n = Math.min(toRemove, dropper.getItem(i).getCount());
                    dropper.getItem(i).shrink(n);
                    toRemove -= n;
                }
                dropper.setChanged();
            });
            check(dropperItems(world) == 70, "30 cobblestone were taken out");
            context.waitTicks(30);
            check(dropperItems(world) == 100, "the interface refilled the dropper back to 100, got " + dropperItems(world));

            // Changing the level through the menu, like the scroll wheel does.
            world.getServer().runCommand("tp @p -1.5 120 3.5 180 5");
            context.waitTicks(10);
            context.getInput().pressKey(options -> options.keyUse);
            context.waitForScreen(dev.alan.logistics.client.InterfaceScreen.class);
            context.waitTicks(5);
            context.runOnClient(mc -> {
                int id = mc.player.containerMenu.containerId;
                mc.gameMode.handleInventoryButtonClick(id, dev.alan.logistics.InterfaceMenu.levelButton(0, dev.alan.logistics.InterfaceMenu.UP_MANY));
                mc.gameMode.handleInventoryButtonClick(id, dev.alan.logistics.InterfaceMenu.levelButton(0, dev.alan.logistics.InterfaceMenu.DOWN_ONE));
            });
            context.waitTicks(10);
            check(world.getServer().computeOnServer(server -> ((dev.alan.logistics.OutputInterfaceBlockEntity) server.overworld()
                .getBlockEntity(new net.minecraft.core.BlockPos(-2, 121, 0))).levels().get(0)) == 115, "level 100 +16 -1 = 115");
            check(context.computeOnClient(mc -> ((dev.alan.logistics.InterfaceMenu) mc.player.containerMenu).level(0)) == 115, "the client menu shows the level");
            context.takeScreenshot("06a-output-level");
            context.runOnClient(mc -> mc.gui.setScreen(null));

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

    /** Auto crafter: pattern from the API and from a recipe placement, crafts until the keep level is reached. */
    private void autocrafting(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            for (String command : new String[] {
                "fill -8 119 -8 12 119 12 minecraft:stone", "fill -8 120 -8 12 126 12 minecraft:air",
                "setblock 0 120 0 logistics:controller", "setblock 0 121 0 logistics:cell", "setblock 1 120 0 logistics:autocrafter",
                "tp @p 1.5 120 3.5 180 20",
            }) world.getServer().runCommand(command);
            var pos = new net.minecraft.core.BlockPos(1, 120, 0);
            world.getServer().runOnServer(server -> {
                var cell = (dev.alan.logistics.CellBlockEntity) server.overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, 121, 0));
                cell.insert(new ItemStack(Items.OAK_PLANKS, 100), true);
                var crafter = (dev.alan.logistics.AutoCrafterBlockEntity) server.overworld().getBlockEntity(pos);
                crafter.pattern().setItem(1, new ItemStack(Items.OAK_PLANKS));
                crafter.pattern().setItem(4, new ItemStack(Items.OAK_PLANKS));
                crafter.keepData().set(0, 20);
            });
            context.waitTicks(150);
            var count = (java.util.function.Function<net.minecraft.world.item.Item, Integer>) item -> world.getServer().computeOnServer(server -> ((dev.alan.logistics.CellBlockEntity)
                server.overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, 121, 0))).count(new ItemStack(item)));
            check(count.apply(Items.STICK) == 20, "crafted sticks up to the keep level of 20, got " + count.apply(Items.STICK));
            check(count.apply(Items.OAK_PLANKS) == 90, "five crafts used ten planks, " + count.apply(Items.OAK_PLANKS) + " left");
            // A higher level makes it continue; a level at or below the stock makes it stop.
            world.getServer().runOnServer(server -> ((dev.alan.logistics.AutoCrafterBlockEntity) server.overworld().getBlockEntity(pos)).keepData().set(0, 32));
            context.waitTicks(150);
            check(count.apply(Items.STICK) == 32, "raising the level to 32 crafted three more times, got " + count.apply(Items.STICK));

            // The menu: Lookup's "+" (handlePlacement) rewrites the pattern for a recipe, using what the warehouse holds most of.
            context.getInput().pressKey(options -> options.keyUse);
            context.waitForScreen(dev.alan.logistics.client.AutoCrafterScreen.class);
            context.waitTicks(5);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().get(0);
                var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, net.minecraft.resources.Identifier.parse("minecraft:chest"));
                var holder = server.getRecipeManager().byKey(key).orElseThrow();
                ((net.minecraft.world.inventory.RecipeBookMenu) player.containerMenu).handlePlacement(false, true, holder, player.level(), player.getInventory());
            });
            context.waitTicks(10);
            int planks = world.getServer().computeOnServer(server -> {
                var crafter = (dev.alan.logistics.AutoCrafterBlockEntity) server.overworld().getBlockEntity(pos);
                int n = 0;
                for (int i = 0; i < 9; i++) if (crafter.pattern().getItem(i).is(Items.OAK_PLANKS)) n++;
                return n;
            });
            check(planks == 8, "a chest recipe puts eight planks in the pattern, got " + planks);
            check(context.computeOnClient(mc -> mc.player.containerMenu.getSlot(dev.alan.logistics.AutoCrafterMenu.RESULT).getItem().is(Items.CHEST)), "the preview shows the chest");
            context.runOnClient(mc -> mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, dev.alan.logistics.AutoCrafterMenu.KEEP_BUTTON + dev.alan.logistics.AutoCrafterMenu.UP_MANY));
            context.waitTicks(10);
            check(context.computeOnClient(mc -> ((dev.alan.logistics.AutoCrafterMenu) mc.player.containerMenu).keep()) == 48, "the keep number changed through the menu");
            context.takeScreenshot("11-autocrafter");
            context.runOnClient(mc -> mc.gui.setScreen(null));
            context.waitTicks(150);
            check(count.apply(Items.CHEST) == 10, "the crafter made chests until the planks ran out (84 planks = 10 chests), got " + count.apply(Items.CHEST));
            check(count.apply(Items.OAK_PLANKS) == 4, "four planks are left over, got " + count.apply(Items.OAK_PLANKS));
        }
    }

    /** Farm interface: harvests and replants wheat, cuts cane, and spends bone meal on request. */
    private void farming(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            for (String command : new String[] {
                "fill -8 118 -8 14 128 12 minecraft:air", "fill -8 119 -8 14 119 12 minecraft:stone",
                // a 9x9 field around x=3,z=0 with mature wheat, and a two-high sugar cane on sand
                "fill -1 120 -4 7 120 4 minecraft:farmland", "fill -1 121 -4 7 121 4 minecraft:wheat[age=7]",
                "setblock 6 121 -4 minecraft:air", "setblock 6 120 -4 minecraft:water",
                "setblock 6 120 -3 minecraft:sand", "setblock 6 121 -3 minecraft:sugar_cane", "setblock 6 122 -3 minecraft:sugar_cane",
                // network on the layer of the interface
                "setblock 0 123 0 logistics:controller", "setblock 0 124 0 logistics:cell",
                "setblock 1 123 0 logistics:cable", "setblock 2 123 0 logistics:cable", "setblock 3 123 0 logistics:farm_interface",
                "setblock 3 122 3 minecraft:stone", "tp @p 3.5 123 3.5 180 25",
            }) world.getServer().runCommand(command);
            world.getServer().runOnServer(server -> ((dev.alan.logistics.CellBlockEntity) server.overworld()
                .getBlockEntity(new net.minecraft.core.BlockPos(0, 124, 0))).insert(new ItemStack(Items.WHEAT_SEEDS, 100), true));
            context.waitTicks(400);
            var cell = (java.util.function.Function<net.minecraft.world.item.Item, Integer>) item -> world.getServer().computeOnServer(server -> ((dev.alan.logistics.CellBlockEntity)
                server.overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, 124, 0))).count(new ItemStack(item)));
            int wheat = cell.apply(Items.WHEAT);
            check(wheat >= 70, "most of the 81 mature wheat plants were harvested into the warehouse, got " + wheat);
            int young = world.getServer().computeOnServer(server -> {
                int n = 0;
                for (int x = -1; x <= 7; x++) for (int z = -4; z <= 4; z++) {
                    var st = server.overworld().getBlockState(new net.minecraft.core.BlockPos(x, 121, z));
                    if (st.is(net.minecraft.world.level.block.Blocks.WHEAT)) n++;
                }
                return n;
            });
            check(young >= 70, "the harvested plants were replanted, " + young + " wheat blocks stand in the field");
            check(cell.apply(Items.SUGAR_CANE) >= 1, "the upper sugar cane was cut; column now: " + world.getServer().computeOnServer(server -> {
                var lv = server.overworld();
                return lv.getBlockState(new net.minecraft.core.BlockPos(6, 120, -3)) + " / " + lv.getBlockState(new net.minecraft.core.BlockPos(6, 121, -3))
                    + " / " + lv.getBlockState(new net.minecraft.core.BlockPos(6, 122, -3)) + " / water " + lv.getBlockState(new net.minecraft.core.BlockPos(6, 120, -4));
            }));
            check(world.getServer().computeOnServer(server -> server.overworld().getBlockState(new net.minecraft.core.BlockPos(6, 121, -3))
                .is(net.minecraft.world.level.block.Blocks.SUGAR_CANE) && server.overworld().getBlockState(new net.minecraft.core.BlockPos(6, 122, -3)).isAir()),
                "the cane base stays, the top block is gone");

            // Bone meal: off by default, on with the toggle.
            world.getServer().runOnServer(server -> ((dev.alan.logistics.CellBlockEntity) server.overworld()
                .getBlockEntity(new net.minecraft.core.BlockPos(0, 124, 0))).insert(new ItemStack(Items.BONE_MEAL, 40), true));
            context.waitTicks(100);
            check(cell.apply(Items.BONE_MEAL) == 40, "bone meal is untouched while the option is off");
            context.getInput().pressKey(options -> options.keyUse);
            context.waitForScreen(dev.alan.logistics.client.InterfaceScreen.class);
            context.waitTicks(5);
            context.runOnClient(mc -> mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, dev.alan.logistics.InterfaceMenu.TOGGLE_BUTTON));
            context.waitTicks(10);
            context.takeScreenshot("10-farm-interface");
            check(context.computeOnClient(mc -> ((dev.alan.logistics.InterfaceMenu) mc.player.containerMenu).toggleOn()), "the toggle reached the client");
            context.runOnClient(mc -> mc.gui.setScreen(null));
            context.waitTicks(200);
            check(cell.apply(Items.BONE_MEAL) < 40, "with the option on, bone meal is spent on the young crops, " + cell.apply(Items.BONE_MEAL) + " left");
        }
    }

    /** Composter (seeds in, bone meal out) and brewing stand (bottles, wart and fuel in, awkward potions out). */
    private void vanillaMachines(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            for (String command : new String[] {
                "fill -8 119 -8 12 119 12 minecraft:stone", "fill -8 120 -8 12 126 12 minecraft:air",
                // composter line
                "setblock 0 120 0 logistics:controller", "setblock 0 121 0 logistics:cell",
                "setblock 1 120 0 logistics:cable", "setblock 2 120 0 logistics:cable", "setblock 2 121 0 logistics:cable",
                "setblock 3 120 0 minecraft:composter", "setblock 3 121 0 logistics:output_interface",
                "setblock 4 121 0 logistics:cable", "setblock 4 120 0 logistics:input_interface",
                // brewing line
                "setblock 0 120 8 logistics:controller", "setblock 0 121 8 logistics:cell",
                "setblock 1 120 8 logistics:cable", "setblock 2 120 8 logistics:cable", "setblock 2 121 8 logistics:cable",
                "setblock 2 120 7 logistics:cable",
                "setblock 3 120 8 minecraft:brewing_stand", "setblock 3 121 8 logistics:output_interface",
                "setblock 3 120 7 logistics:output_interface",
                "setblock 4 121 8 logistics:cable", "setblock 4 120 8 logistics:input_interface",
            }) world.getServer().runCommand(command);
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                var seedsCell = (dev.alan.logistics.CellBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(0, 121, 0));
                seedsCell.insert(new ItemStack(Items.WHEAT_SEEDS, 200), true);
                var compostOut = (dev.alan.logistics.OutputInterfaceBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(3, 121, 0));
                compostOut.filter().setItem(0, new ItemStack(Items.WHEAT_SEEDS));
                var brewCell = (dev.alan.logistics.CellBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(0, 121, 8));
                brewCell.insert(net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.WATER).copyWithCount(1), true);
                brewCell.insert(net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.WATER).copyWithCount(1), true);
                brewCell.insert(net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.WATER).copyWithCount(1), true);
                brewCell.insert(new ItemStack(Items.NETHER_WART, 5), true);
                brewCell.insert(new ItemStack(Items.BLAZE_POWDER, 4), true);
                var top = (dev.alan.logistics.OutputInterfaceBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(3, 121, 8));
                top.filter().setItem(0, new ItemStack(Items.NETHER_WART));
                top.levels().set(0, 1);
                var side = (dev.alan.logistics.OutputInterfaceBlockEntity) level.getBlockEntity(new net.minecraft.core.BlockPos(3, 120, 7));
                side.filter().setItem(0, net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.WATER));
                side.filter().setItem(1, new ItemStack(Items.BLAZE_POWDER));
                side.levels().set(1, 2);
            });
            context.waitTicks(200);
            long bone = world.getServer().computeOnServer(server -> (long) ((dev.alan.logistics.CellBlockEntity)
                server.overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, 121, 0))).count(new ItemStack(Items.BONE_MEAL)));
            long seeds = world.getServer().computeOnServer(server -> (long) ((dev.alan.logistics.CellBlockEntity)
                server.overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, 121, 0))).count(new ItemStack(Items.WHEAT_SEEDS)));
            check(bone >= 1, "the composter made bone meal and the input interface collected it, got " + bone + " (seeds left " + seeds + ")");
            check(seeds < 200, "seeds went into the composter");

            // Brewing: 400 ticks of brewing plus feeding time.
            context.waitTicks(500);
            var awkward = net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.AWKWARD);
            var water = net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.WATER);
            int made = world.getServer().computeOnServer(server -> ((dev.alan.logistics.CellBlockEntity)
                server.overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, 121, 8))).count(awkward));
            int rawLeft = world.getServer().computeOnServer(server -> ((dev.alan.logistics.CellBlockEntity)
                server.overworld().getBlockEntity(new net.minecraft.core.BlockPos(0, 121, 8))).count(water));
            check(made == 3, "three awkward potions came back into the warehouse, got " + made + " (water bottles left " + rawLeft + ")");
        }
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

            // Decompression through the real menu: one tier-2 cell in, one cell to the cursor and three empty ones in the grid.
            context.runOnClient(mc -> mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, dev.alan.logistics.CraftingTerminalMenu.STORE_GRID));
            world.getServer().runCommand("give @p logistics:cell_2");
            context.waitTicks(10);
            int cell2 = context.computeOnClient(mc -> {
                for (var s2 : mc.player.containerMenu.slots) if (s2.index >= 10 && s2.getItem().is(dev.alan.logistics.LogisticsMod.CELLS.get(1).asItem())) return s2.index;
                return -1;
            });
            check(cell2 >= 10, "found the tier-2 cell in the inventory");
            context.runOnClient(mc -> {
                int id = mc.player.containerMenu.containerId;
                mc.gameMode.handleContainerInput(id, cell2, 0, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(id, 5, 0, ContainerInput.PICKUP, mc.player);
            });
            context.waitTicks(5);
            check(context.computeOnClient(mc -> mc.player.containerMenu.getSlot(0).getItem().is(dev.alan.logistics.LogisticsMod.CELLS.get(0).asItem())),
                "the result slot offers a tier-1 cell");
            context.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 0, 0, ContainerInput.PICKUP, mc.player));
            context.waitTicks(5);
            check(context.computeOnClient(mc -> mc.player.containerMenu.getCarried().is(dev.alan.logistics.LogisticsMod.CELLS.get(0).asItem())
                && mc.player.containerMenu.getCarried().getCount() == 1), "one tier-1 cell is on the cursor");
            check(context.computeOnClient(mc -> {
                var grid = mc.player.containerMenu.getSlot(5).getItem();
                return grid.is(dev.alan.logistics.LogisticsMod.CELLS.get(0).asItem()) && grid.getCount() == 3;
            }), "three empty tier-1 cells came back into the grid");
            context.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 5, 0, ContainerInput.PICKUP, mc.player));
            context.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, planks, 0, ContainerInput.PICKUP, mc.player));

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
            // ---- decompression: contents must fit one lower cell; the other three come back empty ----
            var type = net.minecraft.world.item.crafting.RecipeType.CRAFTING;
            var one = net.minecraft.world.item.crafting.CraftingInput.of(1, 1, java.util.List.of(cell.apply(3, dirt)));
            var back = manager.getRecipeFor(type, one, level);
            if (back.isEmpty()) return "a tier-3 cell with 6000 dirt did not decompress";
            ItemStack lower = back.get().value().assemble(one);
            if (lower.getItem() != dev.alan.logistics.LogisticsMod.CELLS.get(1).asItem()) return "decompressed to the wrong tier: " + lower;
            var lowerData = lower.get(dev.alan.logistics.LogisticsMod.CELL_DATA);
            if (lowerData == null || lowerData.total() != 6000) return "decompression lost contents: " + lowerData;
            var extra = back.get().value().getRemainingItems(one);
            if (extra.size() != 1 || extra.get(0).getCount() != 3 || extra.get(0).getItem() != lower.getItem() || extra.get(0).has(dev.alan.logistics.LogisticsMod.CELL_DATA))
                return "the other three cells should come back empty: " + extra;
            var emptyBack = manager.getRecipeFor(type, net.minecraft.world.item.crafting.CraftingInput.of(1, 1, java.util.List.of(cell.apply(2, null))), level);
            if (emptyBack.isEmpty()) return "an empty tier-2 cell did not decompress";
            // Tier 1 cannot go lower; contents that do not fit one lower cell block the recipe.
            if (manager.getRecipeFor(type, net.minecraft.world.item.crafting.CraftingInput.of(1, 1, java.util.List.of(cell.apply(1, null))), level).isPresent())
                return "tier 1 decompressed";
            var tooMuch = new dev.alan.logistics.CellData(java.util.List.of(new dev.alan.logistics.CellData.Entry(new ItemStack(Items.DIRT), 12000)));
            if (manager.getRecipeFor(type, net.minecraft.world.item.crafting.CraftingInput.of(1, 1, java.util.List.of(cell.apply(2, tooMuch))), level).isPresent())
                return "12000 dirt does not fit a tier-1 cell but decompressed";
            var manyTypes = new java.util.ArrayList<dev.alan.logistics.CellData.Entry>();
            for (int i = 0; i < 65; i++) manyTypes.add(new dev.alan.logistics.CellData.Entry(new ItemStack(items.get(i)), 1));
            if (manager.getRecipeFor(type, net.minecraft.world.item.crafting.CraftingInput.of(1, 1, java.util.List.of(cell.apply(2, new dev.alan.logistics.CellData(manyTypes)))), level).isPresent())
                return "65 types do not fit a tier-1 cell but decompressed";
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
