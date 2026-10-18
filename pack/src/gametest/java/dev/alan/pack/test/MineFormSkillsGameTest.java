package dev.alan.pack.test;

import dev.alan.shapeshift.RequestForm;
import dev.alan.shapeshift.UseAbility;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;

/** The skills of the four mine world forms, which only the full pack can load (they need both mods). */
public final class MineFormSkillsGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            world.getConnection().waitForChunksRender();
            for (String command : new String[] {
                "gamerule spawn_mobs false", "gamerule advance_time false", "time set noon", "weather clear", "gamemode survival @p",
                "fill -24 119 -24 24 119 24 minecraft:grass_block", "fill -24 120 -24 24 132 24 minecraft:air",
            }) server.runCommand(command);
            server.runCommand("execute as @p run shapeshift unlockall");
            context.waitTicks(10);

            // Gem slime: a high bounce.
            reset(server, context);
            become(context, world, "mineworld:gem_slime");
            use(context, world);
            double highest = 0;
            for (int i = 0; i < 8; i++) {
                context.waitTicks(2);
                highest = Math.max(highest, server.computeOnServer(s -> player(s).getY()));
            }
            check(highest > 121.5, "the gem slime bounces high (" + highest + ")");

            // Glow bat: the pig five blocks away glows, the user sees in the dark.
            reset(server, context);
            server.runCommand("summon minecraft:pig 5 120 0 {NoAI:1b}");
            become(context, world, "mineworld:glow_bat");
            use(context, world);
            check(server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.PIG, e -> e.isAlive()).stream().allMatch(p -> p.hasEffect(MobEffects.GLOWING))),
                "the glow bat's flash lights up the pig");
            check(server.computeOnServer(s -> player(s).hasEffect(MobEffects.NIGHT_VISION)), "the flash gives night vision");

            // Ore golem: the slam reaches five blocks.
            reset(server, context);
            server.runCommand("summon minecraft:pig 4 120 0 {NoAI:1b}");
            become(context, world, "mineworld:ore_golem");
            use(context, world);
            check(server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.PIG, e -> e.isAlive()).stream().allMatch(p -> p.getHealth() < 10)),
                "the ore golem's slam reaches a pig four blocks away");

            // Ore worm: six blocks of tunnel through stone, a seventh stays.
            reset(server, context);
            server.runCommand("fill 0 120 -1 0 121 -8 minecraft:stone");
            become(context, world, "mineworld:ore_worm");
            server.runCommand("tp @p 0.5 120 0.5 180 0");
            context.waitTicks(2);
            use(context, world);
            check(server.computeOnServer(s -> {
                for (int z = -1; z >= -6; z--) for (int y = 120; y <= 121; y++) if (!s.overworld().getBlockState(new BlockPos(0, y, z)).isAir()) return false;
                return !s.overworld().getBlockState(new BlockPos(0, 120, -7)).isAir();
            }), "the ore worm digs six blocks and no more");
        }
    }

    private static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().get(0); }

    private static void reset(TestServerContext server, ClientGameTestContext context) {
        server.runCommand("kill @e[type=!player]");
        server.runCommand("effect clear @p");
        server.runCommand("fill -24 120 -24 24 132 24 minecraft:air");
        server.runOnServer(s -> player(s).setHealth(player(s).getMaxHealth()));
        server.runCommand("tp @p 0.5 120 0.5 180 0");
        context.waitTicks(2);
    }

    private static void become(ClientGameTestContext context, TestSingleplayerContext world, String id) {
        context.runOnClient(mc -> ClientPlayNetworking.send(new RequestForm(id)));
        world.getConnection().waitForServerboundPackets();
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(3);
        world.getServer().runCommand("tp @p 0.5 120 0.5 180 0");
        context.waitTicks(2);
    }

    private static void use(ClientGameTestContext context, TestSingleplayerContext world) {
        context.runOnClient(mc -> ClientPlayNetworking.send(new UseAbility()));
        world.getConnection().waitForServerboundPackets();
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(2);
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError("FAILED: " + what);
        System.out.println("[mine-skills] ok: " + what);
    }
}
