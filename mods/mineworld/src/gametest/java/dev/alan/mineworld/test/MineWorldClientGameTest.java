package dev.alan.mineworld.test;

import dev.alan.mineworld.DynamicLevels;
import dev.alan.mineworld.MineWorldMod;
import dev.alan.mineworld.WorldCauldronBlockEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;

/** Throws the eight ores into a water cauldron, travels into the mine world and back, and checks the world itself. */
public final class MineWorldClientGameTest implements FabricClientGameTest {
    private static final BlockPos CAULDRON = new BlockPos(0, 120, 3);

    @Override public void runTest(ClientGameTestContext context) {
        TestSingleplayerContext world = context.worldBuilder().create();
        {
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server = world.getServer();
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection connection = world.getConnection();
            connection.waitForChunksRender();
            for (String command : new String[] {
                "gamerule spawn_mobs false", "gamerule advance_time false", "time set noon", "weather clear",
                "fill -8 119 -8 8 119 8 minecraft:stone", "fill -8 120 -8 8 130 8 minecraft:air",
                "tp @p 0 120 0 0 15",
            }) server.runCommand(command);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            server.runOnServer(s -> s.overworld().setBlockAndUpdate(CAULDRON,
                Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3)));
            context.waitTicks(10);

            // 1. Seven of the eight ores are not enough; the eighth completes the cauldron.
            Object[][] first7 = {{Items.COAL}, {Items.RAW_IRON}, {Items.COPPER_INGOT}, {Items.GOLD_INGOT}, {Items.REDSTONE}, {Items.LAPIS_LAZULI}, {Items.DIAMOND}};
            for (Object[] o : first7) throwIn(context, server, (net.minecraft.world.item.Item) o[0]);
            check(server.computeOnServer(s -> s.overworld().getBlockState(CAULDRON).is(Blocks.WATER_CAULDRON)), "7 ores keep it a normal cauldron");
            throwIn(context, server, Items.COAL); // duplicate kind is not consumed
            context.waitTicks(20);
            check(server.computeOnServer(s -> s.overworld().getBlockState(CAULDRON).is(Blocks.WATER_CAULDRON)), "duplicate ore does not complete it");
            throwIn(context, server, Items.EMERALD);
            context.waitTicks(10);
            check(server.computeOnServer(s -> s.overworld().getBlockState(CAULDRON).is(MineWorldMod.WORLD_CAULDRON)), "8 ores make a world cauldron");
            String worldId = server.computeOnServer(s -> ((WorldCauldronBlockEntity) s.overworld().getBlockEntity(CAULDRON)).worldId());
            check(!worldId.isEmpty(), "cauldron has a world id");
            context.waitTicks(5);
            context.takeScreenshot("01-world-cauldron");

            // 2. Standing in it and sneaking for 4 seconds enters the world.
            server.runCommand("tp @p 0.5 120.25 3.5");
            context.waitTicks(5);
            context.getInput().holdShift();
            context.waitTicks(60);
            check(!server.computeOnServer(s -> DynamicLevels.worldId(s.getPlayerList().getPlayers().get(0).level()) != null),
                "still in the overworld before the 4 second delay");
            context.waitTicks(40);
            context.getInput().releaseShift();
            connection.waitForClientboundPackets();
            context.waitTicks(20);
            check(server.computeOnServer(s -> worldId.equals(DynamicLevels.worldId(s.getPlayerList().getPlayers().get(0).level()))),
                "player is in the mine world");
            context.takeScreenshot("02-spawn-room");

            // 3. The room, the return block, and solid ore-rich stone all around.
            String report = server.computeOnServer(s -> {
                var level = s.getLevel(DynamicLevels.key(worldId));
                int oreCount = 0, stone = 0, air = 0, total = 0;
                for (int x = -40; x <= 40; x++) for (int z = -40; z <= 40; z++) for (int y = -60; y <= 300; y += 3) {
                    var st = level.getBlockState(new BlockPos(x, y, z));
                    total++;
                    if (st.isAir()) air++;
                    else if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath().endsWith("_ore")) oreCount++;
                    else stone++;
                }
                boolean room = level.getBlockState(DynamicLevels.RETURN_BLOCK).is(MineWorldMod.RETURN_BLOCK)
                    && level.getBlockState(DynamicLevels.ARRIVAL).isAir() && level.getBlockState(new BlockPos(0, 130, 0)).isAir();
                boolean bedrock = level.getBlockState(new BlockPos(0, -64, 0)).is(Blocks.BEDROCK) && level.getBlockState(new BlockPos(0, 319, 0)).is(Blocks.BEDROCK);
                return "room=" + room + " bedrock=" + bedrock + " sampled=" + total + " air=" + air + " ore=" + oreCount + " other=" + stone;
            });
            System.out.println("MINEWORLD " + report);
            check(report.contains("room=true") && report.contains("bedrock=true"), "spawn room and bedrock: " + report);

            // 3b. Save and reopen the world: the mine world, its blocks and the player inside it all come back.
            server.runOnServer(s -> s.getLevel(DynamicLevels.key(worldId)).setBlockAndUpdate(new BlockPos(0, 128, 2), Blocks.DIAMOND_BLOCK.defaultBlockState()));
            context.waitTicks(5);
            var save = world.getWorldSave();
            world.close();
            world = save.open();
            server = world.getServer();
            connection = world.getConnection();
            connection.waitForChunksRender();
            context.waitTicks(20);
            check(server.computeOnServer(s -> worldId.equals(DynamicLevels.worldId(s.getPlayerList().getPlayers().get(0).level()))),
                "player is still in the mine world after reload");
            check(server.computeOnServer(s -> s.getLevel(DynamicLevels.key(worldId)).getBlockState(new BlockPos(0, 128, 2)).is(Blocks.DIAMOND_BLOCK)),
                "built block survived the reload");
            check(server.computeOnServer(s -> s.getLevel(DynamicLevels.key(worldId)).getBlockState(DynamicLevels.RETURN_BLOCK).is(MineWorldMod.RETURN_BLOCK)),
                "return block survived the reload");

            // 4. Standing on the return block and sneaking brings you back on top of the cauldron.
            server.runCommand("execute in mineworld:" + worldId + " run tp @p 0.5 128 -1.5");
            context.waitTicks(5);
            context.getInput().holdShift();
            context.waitTicks(10);
            server.runCommand("execute in mineworld:" + worldId + " run tp @p 0.5 128 -1.5");
            context.waitTicks(100);
            context.getInput().releaseShift();
            connection.waitForClientboundPackets();
            context.waitTicks(20);
            check(server.computeOnServer(s -> DynamicLevels.worldId(s.getPlayerList().getPlayers().get(0).level()) == null),
                "back in the overworld");
            var back = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).position());
            check(Math.abs(back.x - 0.5) < 1.5 && Math.abs(back.z - 3.5) < 1.5 && back.y >= 120.0, "back near the cauldron: " + back);
            context.takeScreenshot("03-back");

            // 5. Breaking the cauldron drops an item that carries the world id; placing it again restores the link.
            String dropped = server.computeOnServer(s -> {
                var level = s.overworld();
                level.destroyBlock(CAULDRON, true);
                for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(CAULDRON).inflate(3))) {
                    if (e.getItem().is(MineWorldMod.WORLD_CAULDRON.asItem())) {
                        String id = e.getItem().get(MineWorldMod.WORLD_ID);
                        level.setBlockAndUpdate(CAULDRON, MineWorldMod.WORLD_CAULDRON.defaultBlockState());
                        level.getBlockEntity(CAULDRON).applyComponentsFromItemStack(e.getItem());
                        return id + "/" + ((WorldCauldronBlockEntity) level.getBlockEntity(CAULDRON)).worldId();
                    }
                }
                return "no drop";
            });
            check(dropped.equals(worldId + "/" + worldId), "drop keeps the world id and placing restores it: " + dropped);
        }
        world.close();
    }

    private static void throwIn(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, net.minecraft.world.item.Item item) {
        server.runOnServer(s -> {
            var level = s.overworld();
            var e = new ItemEntity(level, CAULDRON.getX() + 0.5, CAULDRON.getY() + 1.3, CAULDRON.getZ() + 0.5, new ItemStack(item, 1));
            e.setDeltaMovement(0, 0, 0);
            level.addFreshEntity(e);
        });
        context.waitTicks(15);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
