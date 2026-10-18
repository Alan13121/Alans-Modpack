package dev.alan.combat.test;

import dev.alan.combat.CombatMod;
import dev.alan.combat.boss.BossForm;
import dev.alan.combat.boss.FormKingFight;
import dev.alan.combat.boss.FormKingFights;
import java.util.EnumSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.phys.AABB;

/** The borrowed skills of the later forms: each one shows up next to the form's own skill, and phase one has none. */
public final class BossSecondaryGameTest implements FabricClientGameTest {
    private static final BlockPos ALTAR = new BlockPos(0, 120, 0);
    private enum Seen { ARROW, FIREBALL, WIND, FANGS }

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
            server.runOnServer(s -> player(s).teleportTo(0.5, 120, 0.5));
            context.waitTicks(10);

            // Phase one and the two forms that are not shooting forms borrow nothing.
            for (BossForm form : new BossForm[] {BossForm.SKELETON, BossForm.BLAZE, BossForm.BREEZE, BossForm.CREEPER, BossForm.WEAK})
                check(form.secondary == null, form + " borrows no skill");
            check(BossForm.EVOKER.secondary == BossForm.Secondary.ARROW && BossForm.ENDERMAN.secondary == BossForm.Secondary.WIND_CHARGE
                && BossForm.SPIDER.secondary == BossForm.Secondary.SMALL_FIREBALL && BossForm.GHAST.secondary == BossForm.Secondary.FANGS
                && BossForm.BLAZE_FAST.secondary == BossForm.Secondary.ARROW, "the later forms borrow the planned skills");
            check(BossForm.Secondary.ARROW.borrowedInterval() == 60 && BossForm.Secondary.FANGS.borrowedInterval() == 120,
                "a borrowed skill is used at half its own rate");

            check(server.computeOnServer(s -> FormKingFights.forceSummon(s.overworld(), ALTAR, false)), "the fight starts");
            context.waitTicks(5);

            // Each later form fires its borrowed projectile, and keeps using its own skill.
            check(seenDuring(server, context, BossForm.EVOKER, 260).containsAll(Set.of(Seen.ARROW, Seen.FANGS)),
                "the evoker casts fangs and also shoots arrows");
            check(seenDuring(server, context, BossForm.ENDERMAN, 260).contains(Seen.WIND), "the enderman also throws wind charges");
            check(seenDuring(server, context, BossForm.SPIDER, 260).contains(Seen.FIREBALL), "the spider also spits small fireballs");
            check(seenDuring(server, context, BossForm.GHAST, 260).contains(Seen.FANGS), "the ghast also raises fangs");
            check(seenDuring(server, context, BossForm.BLAZE_FAST, 260).containsAll(Set.of(Seen.ARROW, Seen.FIREBALL)),
                "the fast blaze shoots fireballs and also arrows");
        }
    }

    /** Switches to {@code form}, keeps the player in place, and reports which projectiles showed up over {@code ticks}. */
    private static Set<Seen> seenDuring(TestServerContext server, ClientGameTestContext context, BossForm form, int ticks) {
        // Projectiles of the previous form must not count (arrows stay in the ground for a long time).
        server.runCommand("kill @e[type=minecraft:arrow]");
        server.runCommand("kill @e[type=minecraft:small_fireball]");
        server.runCommand("kill @e[type=minecraft:wind_charge]");
        server.runCommand("kill @e[type=minecraft:evoker_fangs]");
        server.runOnServer(s -> {
            fight(s).forceForm(form);
            player(s).teleportTo(0.5, 120, 0.5);
        });
        Set<Seen> seen = EnumSet.noneOf(Seen.class);
        for (int t = 0; t < ticks; t += 2) {
            context.waitTicks(2);
            server.runOnServer(s -> {
                var box = new AABB(-100, 90, -100, 100, 200, 100);
                if (!s.overworld().getEntitiesOfClass(Arrow.class, box).isEmpty()) seen.add(Seen.ARROW);
                if (!s.overworld().getEntitiesOfClass(SmallFireball.class, box).isEmpty()) seen.add(Seen.FIREBALL);
                if (!s.overworld().getEntitiesOfClass(WindCharge.class, box).isEmpty()) seen.add(Seen.WIND);
                if (!s.overworld().getEntitiesOfClass(EvokerFangs.class, box).isEmpty()) seen.add(Seen.FANGS);
            });
        }
        System.out.println("[boss-secondary] " + form + " showed " + seen);
        return seen;
    }

    private static FormKingFight fight(MinecraftServer s) { return FormKingFights.at(s.overworld(), ALTAR); }

    private static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().get(0); }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError("FAILED: " + what);
        System.out.println("[boss-secondary] ok: " + what);
    }
}
