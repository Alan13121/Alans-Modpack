package dev.alan.mineworld.test;

import dev.alan.mineworld.DynamicLevels;
import dev.alan.mineworld.MineWorldData;
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
        // Structures on, so the mine world gets its mineshafts (the consistent test settings turn them off).
        TestSingleplayerContext world = context.worldBuilder().adjustSettings(s -> s.setGenerateStructures(true)).create();
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

            // The tunnel to the first city is not dug while the player enters; it is dug a few slices per tick afterwards, once the chunks are
            // loaded, and the progress is saved.
            int waited = 0;
            while (waited < 600 && !server.computeOnServer(s -> MineWorldData.get(s).world(worldId).tunnelNext() == MineWorldData.WorldInfo.TUNNEL_FINISHED)) {
                context.waitTicks(10);
                waited += 10;
            }
            check(waited < 600, "the tunnel to the first city is dug by itself (" + waited + " ticks)");
            check(server.computeOnServer(s -> {
                var level = s.getLevel(DynamicLevels.key(worldId));
                return level.getBlockState(new BlockPos(0, DynamicLevels.FLOOR + 1, 3)).isAir()
                    && level.getBlockState(new BlockPos(0, DynamicLevels.FLOOR + 2, 40)).isAir()
                    && level.getBlockState(new BlockPos(0, DynamicLevels.FLOOR + 2, DynamicLevels.tunnelEnd())).isAir()
                    && level.getBlockState(new BlockPos(0, DynamicLevels.FLOOR, 40)).is(net.minecraft.world.level.block.Blocks.POLISHED_ANDESITE);
            }), "the dug tunnel is open from the room to the gate passage and has its floor");
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

            // 3a. Caves: sparse chambers away from spawn, a solid shield around the room, four ecosystems, mob spawns.
            checkCaves(context, server, connection, worldId);

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

    /** Samples a block of generated chunks away from spawn and the biome layout of a wide area, then checks mob spawning in a chamber. */
    private static void checkCaves(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server,
                                   net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection connection, String worldId) {
        String report = server.computeOnServer(s -> {
            var level = s.getLevel(DynamicLevels.key(worldId));
            // Sealed room: the shell around the room and the tunnel to the first city is solid, so no cave opens into it.
            for (int cx = -2; cx <= 1; cx++) for (int cz = -2; cz <= 1; cz++) level.getChunk(cx, cz);
            int shieldAir = 0;
            for (int x = -3; x <= 3; x++) for (int y = DynamicLevels.FLOOR; y <= DynamicLevels.FLOOR + 4; y++) for (int z = -3; z <= 16; z++) {
                boolean room = Math.abs(x) <= 2 && Math.abs(z) <= 2 && y >= DynamicLevels.FLOOR + 1 && y <= DynamicLevels.FLOOR + 3;
                boolean tunnel = Math.abs(x) <= 1 && z >= 3 && y >= DynamicLevels.FLOOR + 1 && y <= DynamicLevels.FLOOR + 3;
                boolean shell = z <= 3 || Math.abs(x) == 2 || y == DynamicLevels.FLOOR || y == DynamicLevels.FLOOR + 4;
                if (!room && !tunnel && shell && level.getBlockState(new BlockPos(x, y, z)).isAir()) shieldAir++;
            }
            // Wide biome layout from the noise (no chunk generation needed).
            java.util.Map<String, Integer> biomes = new java.util.TreeMap<>();
            for (int x = -1024; x <= 1024; x += 32) for (int z = -1024; z <= 1024; z += 32) for (int y = -48; y <= 300; y += 48) {
                String b = level.getUncachedNoiseBiome(x >> 2, y >> 2, z >> 2).unwrapKey().orElseThrow().identifier().getPath();
                biomes.merge(b, 1, Integer::sum);
            }
            // Dense sample of an 8x8 chunk block.
            int total = 0, air = 0, entries = 0, rows = 0;
            java.util.Map<String, Integer> marks = new java.util.TreeMap<>();
            for (int cx = 8; cx < 16; cx++) for (int cz = 8; cz < 16; cz++) level.getChunk(cx, cz);
            for (int x = 128; x < 256; x += 4) for (int y = -60; y <= 316; y += 4) {
                int run = 0;
                rows++;
                for (int z = 128; z < 256; z++) {
                    var st = level.getBlockState(new BlockPos(x, y, z));
                    total++;
                    if (st.isAir()) {
                        air++;
                        if (++run == 3) entries++; // count each opening of at least 3 blocks once
                    } else run = 0;
                    String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
                    for (String m : new String[]{"amethyst", "moss", "dripstone", "rail", "spawner", "deepslate", "clay", "azalea", "sulfur", "cinnabar"}) {
                        if (path.contains(m)) marks.merge(m, 1, Integer::sum);
                    }
                }
            }
            return "shieldAir=" + shieldAir + " air%=" + String.format("%.1f", 100.0 * air / total)
                + " chambersPer100=" + String.format("%.2f", 100.0 * entries / (rows * 128.0)) + " biomes=" + biomes + " marks=" + marks;
        });
        System.out.println("MINEWORLD CAVES " + report);
        check(report.startsWith("shieldAir=0 "), "no cave opens into the spawn room or tunnel: " + report);
        for (String b : new String[]{"crystal_cave", "lush_cave", "dripstone_cave", "ore_nest", "sulfur_cave"}) check(report.contains(b + "="), "biome " + b + " appears: " + report);
        check(!report.contains("mine="), "new worlds only use the cave biomes: " + report);
        String mineshaft = server.computeOnServer(s -> {
            var level = s.getLevel(DynamicLevels.key(worldId));
            var shaft = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .getOrThrow(net.minecraft.world.level.levelgen.structure.BuiltinStructures.MINESHAFT);
            var found = level.getChunkSource().getGenerator().findNearestMapStructure(level,
                net.minecraft.core.HolderSet.direct(shaft), BlockPos.ZERO, 100, false);
            return found == null ? "none" : found.getFirst().toString();
        });
        System.out.println("MINEWORLD MINESHAFT " + mineshaft);
        check(!mineshaft.equals("none"), "mineshafts generate in the mine world");

        // One look into a chamber of each kind, with night vision so the screenshot shows something.
        server.runCommand("gamemode creative @p");
        server.runCommand("effect give @p minecraft:night_vision infinite 0 true");
        for (String b : new String[]{"crystal_cave", "lush_cave", "dripstone_cave", "ore_nest", "sulfur_cave"}) {
            BlockPos spot = server.computeOnServer(s -> findChamber(s.getLevel(DynamicLevels.key(worldId)), b));
            System.out.println("MINEWORLD CHAMBER " + b + " " + spot);
            if (spot == null) continue;
            server.runCommand("execute in mineworld:" + worldId + " run tp @p " + spot.getX() + " " + spot.getY() + " " + spot.getZ() + " 45 10");
            connection.waitForChunksRender();
            context.waitTicks(20);
            context.takeScreenshot("04-cave-" + b);
        }

        // Monsters spawn in the dark caves.
        BlockPos dark = server.computeOnServer(s -> findChamber(s.getLevel(DynamicLevels.key(worldId)), "dripstone_cave"));
        if (dark == null) dark = server.computeOnServer(s -> findChamber(s.getLevel(DynamicLevels.key(worldId)), null));
        check(dark != null, "found a chamber for the spawn check");
        BlockPos spawnSpot = dark;
        server.runCommand("execute in mineworld:" + worldId + " run tp @p " + spawnSpot.getX() + " " + spawnSpot.getY() + " " + spawnSpot.getZ());
        // The per-level monster flag is only refreshed by difficulty or spawn_monsters changes, not by spawn_mobs.
        server.runCommand("gamerule spawn_mobs true");
        server.runCommand("gamerule spawn_monsters false");
        server.runCommand("gamerule spawn_monsters true");
        context.waitTicks(300);
        int monsters = server.computeOnServer(s -> s.getLevel(DynamicLevels.key(worldId)).getEntitiesOfClass(
            net.minecraft.world.entity.monster.Monster.class, new net.minecraft.world.phys.AABB(spawnSpot).inflate(128)).size());
        System.out.println("MINEWORLD MONSTERS " + monsters);
        server.runCommand("gamerule spawn_mobs false");
        server.runCommand("kill @e[type=!player]");
        check(monsters > 0, "monsters spawn in the caves");
        server.runCommand("execute in mineworld:" + worldId + " run tp @p 0.5 128 0.5");
        context.waitTicks(10);
    }

    /** A spot with a 5x5x5 box of air around it in an already generated chunk (x/z 128..255) of the given biome, or null. */
    private static @org.jspecify.annotations.Nullable BlockPos findChamber(net.minecraft.server.level.ServerLevel level, @org.jspecify.annotations.Nullable String biome) {
        for (int y = 300; y >= -40; y -= 4) for (int x = 132; x < 252; x += 4) for (int z = 132; z < 252; z += 4) {
            BlockPos pos = new BlockPos(x, y, z);
            if (biome != null && !level.getBiome(pos).unwrapKey().orElseThrow().identifier().getPath().equals(biome)) continue;
            boolean open = true;
            for (int dx = -2; dx <= 2 && open; dx++) for (int dy = -2; dy <= 2 && open; dy++) for (int dz = -2; dz <= 2 && open; dz++) {
                if (!level.getBlockState(pos.offset(dx, dy, dz)).isAir()) open = false;
            }
            if (open) return pos;
        }
        return null;
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
