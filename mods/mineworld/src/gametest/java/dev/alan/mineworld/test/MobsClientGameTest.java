package dev.alan.mineworld.test;

import dev.alan.mineworld.DynamicLevels;
import dev.alan.mineworld.MineWorldData;
import dev.alan.mineworld.mob.GemSlime;
import dev.alan.mineworld.mob.Mobs;
import dev.alan.mineworld.mob.OreWorm;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/** Phase 3 creatures: spawn lists and rules per cave biome, drops, the ore worm trigger, and a look at each one. */
public final class MobsClientGameTest implements FabricClientGameTest {
    /** A room dug north of the spawn room (outside its solid shield) for drops and screenshots. */
    private static final BlockPos ROOM = new BlockPos(0, 130, -32);

    @Override public void runTest(ClientGameTestContext context) {
        TestSingleplayerContext world = context.worldBuilder().create();
        TestServerContext server = world.getServer();
        var connection = world.getConnection();
        connection.waitForChunksRender();
        server.runCommand("gamerule spawn_mobs false");
        server.runCommand("gamerule natural_health_regeneration false");
        String worldId = server.computeOnServer(s -> {
            String id = MineWorldData.get(s).createWorld();
            MineWorldData.get(s).setOrigin(id, GlobalPos.of(Level.OVERWORLD, new BlockPos(0, 120, 3)));
            DynamicLevels.ensure(s, id);
            DynamicLevels.finishTunnelNow(s, id);   // these tests walk the tunnel at once; the real one is dug in steps
            return id;
        });
        String in = "execute in mineworld:" + worldId + " run ";
        server.runCommand("gamemode creative @p");
        server.runCommand("effect give @p minecraft:night_vision infinite 0 true");
        // Arrive in the spawn room first so the room's chunks are loaded before digging it.
        server.runCommand(in + "tp @p 0.5 128 0.5");
        connection.waitForChunksRender();
        context.waitTicks(20);
        // A fresh mine world generates slowly: force-load the room's chunks and wait for them, or the fills below are skipped.
        server.runCommand(in + "forceload add -16 -48 16 -16");
        for (int i = 0; i < 200 && !server.computeOnServer(s -> roomLoaded(level(s, worldId))); i++) {
            context.waitTicks(5);
        }
        server.runCommand(in + "fill -9 129 -41 9 138 -23 minecraft:stone");
        server.runCommand(in + "fill -8 130 -40 8 137 -24 minecraft:air");
        server.runCommand(in + "tp @p 0.5 130 -26.5 180 15");
        connection.waitForChunksRender();
        context.waitTicks(20);
        check(server.computeOnServer(s -> level(s, worldId).getBlockState(ROOM.above()).isAir()), "test room is dug");

        // 1. Each cave biome lists its creature, and the creature's spawn rule passes in a dark chamber of that biome.
        String spawns = server.computeOnServer(s -> {
            ServerLevel level = level(s, worldId);
            for (int cx = 8; cx < 16; cx++) for (int cz = 8; cz < 16; cz++) level.getChunk(cx, cz);
            Map<String, String> found = new TreeMap<>();
            found.put("glow_bat", spawnCheck(level, "crystal_cave", Mobs.GLOW_BAT, MobCategory.AMBIENT));
            found.put("gem_slime", spawnCheck(level, "lush_cave", Mobs.GEM_SLIME, MobCategory.MONSTER));
            found.put("ore_golem", spawnCheck(level, "ore_nest", Mobs.ORE_GOLEM, MobCategory.MONSTER));
            // Ore worms only come out of ore blocks, never from a spawn list.
            found.put("ore_worm", spawnCheck(level, "ore_nest", Mobs.ORE_WORM, MobCategory.MONSTER));
            return found.toString();
        });
        System.out.println("MINEWORLD MOBS SPAWNS " + spawns);
        check(spawns.contains("glow_bat=listed/ok") && spawns.contains("gem_slime=listed/ok") && spawns.contains("ore_golem=listed/ok"),
            "cave creatures are listed and can spawn: " + spawns);
        check(spawns.contains("ore_worm=unlisted"), "ore worms are not in spawn lists: " + spawns);

        // 2. Drops: glow bats glowstone, the smallest gem slimes a gem each, an ore golem 8..16 ores.
        String drops = server.computeOnServer(s -> {
            ServerLevel level = level(s, worldId);
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 10; i++) spawn(level, Mobs.GLOW_BAT).kill(level);
            b.append("glowstone=").append(collect(level, Items.GLOWSTONE_DUST)).append(" amethyst=").append(collect(level, Items.AMETHYST_SHARD));
            for (int i = 0; i < 6; i++) {
                GemSlime slime = spawn(level, Mobs.GEM_SLIME);
                slime.setSize(1, true);
                slime.kill(level);
            }
            int gems = collect(level, Items.LAPIS_LAZULI) + collect(level, Items.EMERALD) + collect(level, Items.DIAMOND);
            b.append(" gems=").append(gems);
            spawn(level, Mobs.ORE_GOLEM).kill(level);
            int ores = 0;
            for (var item : new net.minecraft.world.item.Item[]{Items.COAL, Items.RAW_COPPER, Items.RAW_IRON, Items.RAW_GOLD, Items.REDSTONE,
                Items.LAPIS_LAZULI, Items.DIAMOND, Items.EMERALD}) ores += collect(level, item);
            b.append(" golemOres=").append(ores);
            return b.toString();
        });
        // A big gem slime splits into gem slimes when its body is removed (about a second after death) and drops no gem itself.
        server.runOnServer(s -> {
            GemSlime big = spawn(level(s, worldId), Mobs.GEM_SLIME);
            big.setSize(4, true);
            big.kill(level(s, worldId));
        });
        context.waitTicks(40);
        drops += server.computeOnServer(s -> {
            ServerLevel level = level(s, worldId);
            int bigGems = collect(level, Items.LAPIS_LAZULI) + collect(level, Items.EMERALD) + collect(level, Items.DIAMOND);
            var children = level.getEntitiesOfClass(GemSlime.class, room(), GemSlime::isAlive);
            boolean halves = children.stream().allMatch(c -> c.getSize() == 2);
            children.forEach(Mob::discard);
            return " bigGems=" + bigGems + " children=" + children.size() + " halves=" + halves;
        });
        System.out.println("MINEWORLD MOBS DROPS " + drops);
        int glowstone = number(drops, "glowstone"), gems = number(drops, "gems"), golemOres = number(drops, "golemOres");
        check(glowstone >= 10 && glowstone <= 20 && number(drops, "amethyst") <= 10, "glow bat drops: " + drops);
        check(gems == 6 && number(drops, "bigGems") == 0 && number(drops, "children") >= 2 && drops.contains("halves=true"),
            "gem slime drops and splits: " + drops);
        check(golemOres >= 8 && golemOres <= 16, "ore golem drops: " + drops);

        // 3. An ore golem is neutral: it leaves a survival player alone until hurt, then fights back.
        server.runCommand("gamemode survival @p");
        server.runCommand("effect give @p minecraft:resistance infinite 255 true");
        server.runOnServer(s -> spawn(level(s, worldId), Mobs.ORE_GOLEM).setPos(ROOM.getX() + 3.5, ROOM.getY(), ROOM.getZ() + 0.5));
        server.runCommand(in + "tp @p 0.5 130 -28.5 180 0");
        context.waitTicks(60);
        String before = server.computeOnServer(s -> String.valueOf(golem(level(s, worldId)).getTarget()));
        server.runOnServer(s -> {
            var g = golem(level(s, worldId));
            g.hurtServer(level(s, worldId), level(s, worldId).damageSources().playerAttack(s.getPlayerList().getPlayers().get(0)), 1.0F);
        });
        context.waitTicks(10);
        String after = server.computeOnServer(s -> golem(level(s, worldId)).getTarget() instanceof net.minecraft.world.entity.player.Player ? "player" : "other");
        server.runOnServer(s -> golem(level(s, worldId)).discard());
        server.runCommand("gamemode creative @p");
        String golem = "before=" + before + " after=" + after;
        System.out.println("MINEWORLD MOBS GOLEM " + golem);
        check(golem.equals("before=null after=player"), "ore golem is neutral: " + golem);

        // 4. Ore worms: survival mining lets them out (about 1 in 30) carrying the mined ore; creative never does.
        String worms = server.computeOnServer(s -> {
            ServerLevel level = level(s, worldId);
            var player = s.getPlayerList().getPlayers().get(0);
            BlockPos ore = ROOM.offset(4, 0, 0);
            int creative = mine(level, player, ore, 120);
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            int survival = mine(level, player, ore, 300);
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            var all = level.getEntitiesOfClass(OreWorm.class, room());
            boolean carryDiamond = !all.isEmpty() && all.stream().allMatch(w -> w.ore() == Items.DIAMOND);
            all.forEach(w -> w.kill(level));
            int diamonds = collect(level, Items.DIAMOND);
            level.getEntitiesOfClass(ItemEntity.class, room()).forEach(ItemEntity::discard);
            return "creative=" + creative + " survival=" + survival + " diamond=" + carryDiamond + " drops=" + diamonds;
        });
        System.out.println("MINEWORLD MOBS WORMS " + worms);
        int survivalWorms = number(worms, "survival");
        check(number(worms, "creative") == 0, "no worms in creative: " + worms);
        check(survivalWorms >= 2 && survivalWorms <= 30, "about 1 in 30 ores lets a worm out: " + worms);
        check(worms.contains("diamond=true") && number(worms, "drops") == survivalWorms * OreWorm.DROP_COUNT, "worms carry the mined ore: " + worms);
        check(OreWorm.oreOf(Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState()).orElseThrow() == Items.RAW_COPPER
            && OreWorm.oreOf(Blocks.STONE.defaultBlockState()).isEmpty(), "ore mapping");

        // 5. One look at each creature (frozen so they stay in frame).
        server.runOnServer(s -> {
            ServerLevel level = level(s, worldId);
            int x = -6;
            for (EntityType<? extends Mob> type : java.util.List.<EntityType<? extends Mob>>of(Mobs.GLOW_BAT, Mobs.GEM_SLIME, Mobs.ORE_GOLEM, Mobs.ORE_WORM)) {
                Mob mob = type.create(level, EntitySpawnReason.COMMAND);
                mob.snapTo(x + 0.5, ROOM.getY() + (type == Mobs.GLOW_BAT ? 2 : 0), ROOM.getZ() + 0.5, 0, 0);
                mob.setNoAi(true);
                if (mob instanceof GemSlime slime) slime.setSize(2, true);
                level.addFreshEntity(mob);
                x += 4;
            }
        });
        server.runCommand(in + "tp @p 0.5 131 -24.5 180 20");
        context.waitTicks(20);
        context.takeScreenshot("mobs-01-lineup");
        server.runCommand("effect clear @p minecraft:night_vision");
        context.waitTicks(10);
        context.takeScreenshot("mobs-02-dark-glow");
        world.close();
    }

    /** "listed/ok" when the biome's spawn list has the type and its spawn rule passes somewhere dark in a chamber of that biome. */
    private static String spawnCheck(ServerLevel level, String biome, EntityType<? extends Mob> type, MobCategory category) {
        java.util.List<BlockPos> chambers = findChambers(level, biome, 6);
        if (chambers.isEmpty()) return "no chamber";
        // A chamber can be lit by glow berries or other light sources, so try several before calling the rule failed.
        String result = "";
        for (BlockPos chamber : chambers) {
            result = spawnCheckAt(level, chamber, type, category);
            if (result.startsWith("listed/ok") || result.equals("unlisted")) return result;
        }
        return result;
    }

    private static String spawnCheckAt(ServerLevel level, BlockPos chamber, EntityType<? extends Mob> type, MobCategory category) {
        var list = level.getChunkSource().getGenerator().getMobsAt(level, level.structureManager(), category, chamber);
        boolean listed = list.unwrap().stream().anyMatch(w -> w.value().type() == type);
        if (!listed) return "unlisted";
        // Floors around the chamber centre: a single spot may be lit (glow berries) or under water.
        String last = "";
        for (int dx = -8; dx <= 8; dx += 2) for (int dz = -8; dz <= 8; dz += 2) {
            BlockPos floor = chamber.offset(dx, 0, dz);
            if (!level.getBlockState(floor).isAir()) continue;
            while (level.getBlockState(floor.below()).isAir() && floor.getY() > chamber.getY() - 30) floor = floor.below();
            for (int i = 0; i < 20; i++) {
                if (SpawnPlacements.isSpawnPositionOk(type, level, floor)
                    && SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, floor, level.getRandom())) return "listed/ok";
            }
            last = floor + " light=" + level.getMaxLocalRawBrightness(floor) + " below=" + level.getBlockState(floor.below()).getBlock();
        }
        return "listed/rule fails, last " + last;
    }

    /** Sets and breaks a diamond ore at pos {@code times} times as the player; returns how many ore worms came out. */
    private static int mine(ServerLevel level, net.minecraft.server.level.ServerPlayer player, BlockPos pos, int times) {
        int before = level.getEntitiesOfClass(OreWorm.class, room()).size();
        for (int i = 0; i < times; i++) {
            level.setBlockAndUpdate(pos, Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState());
            player.gameMode.destroyBlock(pos);
        }
        level.getEntitiesOfClass(ItemEntity.class, room()).forEach(ItemEntity::discard);
        return level.getEntitiesOfClass(OreWorm.class, room()).size() - before;
    }

    private static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type) {
        T mob = type.create(level, EntitySpawnReason.COMMAND);
        mob.snapTo(ROOM.getX() + 0.5, ROOM.getY(), ROOM.getZ() + 0.5, 0, 0);
        level.addFreshEntity(mob);
        return mob;
    }

    private static dev.alan.mineworld.mob.OreGolem golem(ServerLevel level) {
        return level.getEntitiesOfClass(dev.alan.mineworld.mob.OreGolem.class, room()).getFirst();
    }

    private static AABB room() {
        return new AABB(ROOM).inflate(12);
    }

    /** Counts and removes dropped stacks of an item in the room. */
    private static int collect(ServerLevel level, net.minecraft.world.item.Item item) {
        int count = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, room(), e -> e.getItem().is(item))) {
            count += e.getItem().getCount();
            e.discard();
        }
        return count;
    }

    private static int number(String report, String key) {
        var m = java.util.regex.Pattern.compile("\\b" + key + "=(\\d+)").matcher(report);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** Up to {@code max} such spots, at least 12 blocks apart. */
    private static java.util.List<BlockPos> findChambers(ServerLevel level, String biome, int max) {
        java.util.List<BlockPos> found = new java.util.ArrayList<>();
        for (int y = 300; y >= -40 && found.size() < max; y -= 4) for (int x = 132; x < 252 && found.size() < max; x += 4) for (int z = 132; z < 252 && found.size() < max; z += 4) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBiome(pos).unwrapKey().orElseThrow().identifier().getPath().equals(biome)) continue;
            if (found.stream().anyMatch(p -> p.distManhattan(pos) < 12)) continue;
            boolean open = true;
            for (int dx = -2; dx <= 2 && open; dx++) for (int dy = -2; dy <= 2 && open; dy++) for (int dz = -2; dz <= 2 && open; dz++) {
                if (!level.getBlockState(pos.offset(dx, dy, dz)).isAir()) open = false;
            }
            if (open) found.add(pos);
        }
        return found;
    }

    /** A spot with a 5x5x5 box of air around it in an already generated chunk (x/z 128..255) of the given biome, or null. */
    private static @Nullable BlockPos findChamber(ServerLevel level, String biome) {
        for (int y = 300; y >= -40; y -= 4) for (int x = 132; x < 252; x += 4) for (int z = 132; z < 252; z += 4) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBiome(pos).unwrapKey().orElseThrow().identifier().getPath().equals(biome)) continue;
            boolean open = true;
            for (int dx = -2; dx <= 2 && open; dx++) for (int dy = -2; dy <= 2 && open; dy++) for (int dz = -2; dz <= 2 && open; dz++) {
                if (!level.getBlockState(pos.offset(dx, dy, dz)).isAir()) open = false;
            }
            if (open) return pos;
        }
        return null;
    }

    private static boolean roomLoaded(ServerLevel level) {
        for (int cx = -1; cx <= 0; cx++) for (int cz = -3; cz <= -2; cz++) if (!level.hasChunk(cx, cz)) return false;
        return true;
    }

    private static ServerLevel level(MinecraftServer s, String worldId) {
        return s.getLevel(DynamicLevels.key(worldId));
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        System.out.println("[mineworld-mobs] ok: " + message);
    }
}
