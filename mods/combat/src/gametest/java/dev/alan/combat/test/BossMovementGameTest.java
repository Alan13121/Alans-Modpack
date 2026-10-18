package dev.alan.combat.test;

import dev.alan.combat.CombatMod;
import dev.alan.combat.boss.BossForm;
import dev.alan.combat.boss.FormKingFight;
import dev.alan.combat.boss.FormKingFights;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Where the Form King's body goes in each form: the flyers circle the player above, the archers keep their distance, the
 * spider and the creeper close in, the weak form stands still, and nothing sinks through or floats off the floor.
 */
public final class BossMovementGameTest implements FabricClientGameTest {
    private static final BlockPos ALTAR = new BlockPos(0, 120, 0);
    private static final Vec3 PLAYER = new Vec3(0.5, 120, 0.5);

    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            world.getConnection().waitForChunksRender();
            for (String command : new String[] {
                "gamerule spawn_mobs false", "gamerule advance_time false", "time set noon", "weather clear", "gamemode creative @p",
                "fill -45 119 -45 45 119 45 minecraft:stone", "fill -45 120 -45 45 122 45 minecraft:air",
                "fill -45 123 -45 45 130 45 minecraft:air",
            }) server.runCommand(command);
            server.runOnServer(s -> s.overworld().setBlockAndUpdate(ALTAR, CombatMod.FORM_ALTAR.defaultBlockState()));
            server.runOnServer(s -> player(s).teleportTo(PLAYER.x, PLAYER.y, PLAYER.z));
            context.waitTicks(10);
            check(server.computeOnServer(s -> FormKingFights.forceSummon(s.overworld(), ALTAR, false)), "the fight starts");
            context.waitTicks(5);

            // Flyers hold a height above the player and circle at about ten blocks, moving all the time.
            for (BossForm form : new BossForm[] {BossForm.BLAZE, BossForm.BREEZE, BossForm.GHAST, BossForm.BLAZE_FAST}) {
                enter(server, context, form);
                Vec3 start = bossPos(server);
                context.waitTicks(100);
                Vec3 end = bossPos(server);
                double distance = flat(end.subtract(PLAYER)).length();
                System.out.println("[boss-move] " + form + " " + start + " -> " + end + " distance " + distance);
                check(end.y > 123.5 && end.y < 126.5, form + " floats about five blocks above the player (y " + end.y + ")");
                check(distance > 6 && distance < 14, form + " circles the player at a distance (" + distance + ")");
                check(flat(end.subtract(start)).length() > 2, form + " has moved");
            }

            // Archers and evokers: keep their distance, walk on the floor.
            for (BossForm form : new BossForm[] {BossForm.SKELETON, BossForm.EVOKER}) {
                enter(server, context, form);
                context.waitTicks(100);
                Vec3 end = bossPos(server);
                double distance = flat(end.subtract(PLAYER)).length();
                System.out.println("[boss-move] " + form + " at " + end + " distance " + distance);
                check(distance > 8 && distance < 16.5, form + " keeps its distance (" + distance + ")");
                check(Math.abs(end.y - 120) < 0.3 && server.computeOnServer(s -> body(s).onGround()), form + " stands on the floor");
                Vec3 mark = bossPos(server);
                context.waitTicks(60);
                check(bossPos(server).distanceTo(mark) > 1, form + " strafes while it keeps its distance");
            }

            // The spider closes in by walking and leaping, and the leap carries it through the air.
            enter(server, context, BossForm.SPIDER);
            double closest = Double.MAX_VALUE, highest = 0;
            for (int i = 0; i < 24; i++) {
                context.waitTicks(10);
                Vec3 at = bossPos(server);
                closest = Math.min(closest, flat(at.subtract(PLAYER)).length());
                highest = Math.max(highest, at.y);
            }
            System.out.println("[boss-move] SPIDER closest " + closest + " highest " + highest);
            check(closest < 5, "the spider gets close to the player (" + closest + ")");
            check(highest > 120.5, "the spider leaps off the floor (" + highest + ")");

            // A one block wall does not stop a walker.
            server.runCommand("fill 6 120 -40 6 120 40 minecraft:stone");
            enter(server, context, BossForm.SPIDER);
            server.runOnServer(s -> body(s).teleportTo(14, 120, 0));
            double nearest = Double.MAX_VALUE;
            for (int i = 0; i < 20; i++) {
                context.waitTicks(10);
                nearest = Math.min(nearest, bossPos(server).x);
            }
            check(nearest < 5, "the spider gets over a one block wall (x " + nearest + ")");
            server.runCommand("fill 6 120 -40 6 120 40 minecraft:air");

            // The creeper runs at the player while the fuse burns.
            enter(server, context, BossForm.CREEPER);
            double before = flat(bossPos(server).subtract(PLAYER)).length();
            context.waitTicks(22);
            double after = flat(bossPos(server).subtract(PLAYER)).length();
            check(before - after > 3, "the creeper charges while the fuse burns (" + before + " -> " + after + ")");

            // The weak form stands still, and the body is on the floor.
            server.runOnServer(s -> fight(s).forceForm(BossForm.WEAK));
            context.waitTicks(10);
            Vec3 rest = bossPos(server);
            context.waitTicks(40);
            check(bossPos(server).distanceTo(rest) < 0.2, "the weak form does not move");
            check(Math.abs(rest.y - 120) < 0.3, "the weak form stands on the floor");

            // Nothing leaves the arena, however long the fight runs.
            enter(server, context, BossForm.SKELETON);
            context.waitTicks(200);
            check(flat(bossPos(server).subtract(new Vec3(0.5, 120, 0.5))).length() <= FormKingFight.RADIUS, "the body stays inside the arena");
        }
    }

    private static void enter(TestServerContext server, ClientGameTestContext context, BossForm form) {
        server.runOnServer(s -> {
            fight(s).forceForm(form);
            player(s).teleportTo(PLAYER.x, PLAYER.y, PLAYER.z);
        });
        context.waitTicks(2);
    }

    private static FormKingFight fight(MinecraftServer s) { return FormKingFights.at(s.overworld(), ALTAR); }

    private static Mob body(MinecraftServer s) { return (Mob) s.overworld().getEntity(fight(s).bossId()); }

    private static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().get(0); }

    private static Vec3 bossPos(TestServerContext server) { return server.computeOnServer(s -> body(s).position()); }

    private static Vec3 flat(Vec3 v) { return new Vec3(v.x, 0, v.z); }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError("FAILED: " + what);
        System.out.println("[boss-move] ok: " + what);
    }
}
