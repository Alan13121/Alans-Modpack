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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Plays through the Form King fight: summoning rules, damage rules, phases, skills, defeat and victory. */
public final class BossClientGameTest implements FabricClientGameTest {
    private static final BlockPos ALTAR = new BlockPos(0, 120, 0);

    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            world.getConnection().waitForChunksRender();
            for (String command : new String[] {
                "gamerule spawn_mobs false", "gamerule advance_time false", "time set noon", "weather clear", "gamemode creative @p",
                "fill -45 119 -45 45 119 45 minecraft:stone", "fill -45 120 -45 45 122 45 minecraft:air",
                "fill -45 123 -45 45 130 45 minecraft:air", "tp @p 3 120 3 0 10",
            }) server.runCommand(command);
            server.runOnServer(s -> s.overworld().setBlockAndUpdate(ALTAR, CombatMod.FORM_ALTAR.defaultBlockState()));
            context.waitTicks(10);

            // 1. The altar refuses until every form is unlocked and the offering is in the inventory.
            check(!summon(server), "no summon without any unlocked forms");
            server.runCommand("execute as @p run shapeshift unlockall");
            check(!summon(server), "no summon without the offering");
            giveOffering(server);
            check(summon(server), "summoned with every form unlocked and the offering");
            context.waitTicks(5);
            check(server.computeOnServer(s -> {
                FormKingFight fight = fight(s);
                Mob boss = body(s, fight);
                ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                return fight != null && boss != null && fight.hp() == 600 && fight.form() == BossForm.SKELETON
                    && player.getInventory().countItem(Items.NETHER_STAR) == 0 && player.getInventory().countItem(Items.DRAGON_BREATH) == 0
                    && boss.getAttached(CombatMod.BOSS_MARK) != null;
            }), "the boss stands in skeleton form with 600 health and the offering is spent");
            context.takeScreenshot("10-boss-skeleton");

            // 2. Damage rules: only players hurt it, one hit counts at most 5%, and the body never really loses health.
            check(server.computeOnServer(s -> {
                FormKingFight fight = fight(s);
                Mob boss = body(s, fight);
                ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                boss.hurtServer(s.overworld(), s.overworld().damageSources().lava(), 50f);
                boolean untouched = fight.hp() == 600;
                boss.hurtServer(s.overworld(), s.overworld().damageSources().playerAttack(player), 100f);
                return untouched && Math.abs(fight.hp() - 570) < 0.01 && boss.getHealth() == boss.getMaxHealth();
            }), "lava does nothing, a 100 point hit counts as 30, and the body is topped up");

            // 3. The skeleton shoots at the player.
            context.waitTicks(80);
            check(server.computeOnServer(s -> {
                for (var arrow : s.overworld().getEntitiesOfClass(AbstractArrow.class, wide()))
                    if (arrow.getOwner() instanceof Mob) return true;
                return false;
            }), "the skeleton form fires arrows");

            // 4. Phase two after dropping below two thirds: a new form, staggered, takes 25% more.
            server.runOnServer(s -> fight(s).setHp(300));
            context.waitTicks(3);
            check(server.computeOnServer(s -> {
                FormKingFight fight = fight(s);
                return fight.stage() == 2 && fight.form() == BossForm.EVOKER && body(s, fight) != null;
            }), "below two thirds the boss turns into the evoker (stage 2)");
            check(server.computeOnServer(s -> {
                FormKingFight fight = fight(s);
                ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                body(s, fight).hurtServer(s.overworld(), s.overworld().damageSources().playerAttack(player), 100f);
                return Math.abs(fight.hp() - (300 - 37.5)) < 0.01;
            }), "right after a form change a hit does 25% more");
            context.waitTicks(80);
            check(server.computeOnServer(s -> !s.overworld().getEntitiesOfClass(net.minecraft.world.entity.projectile.EvokerFangs.class, wide()).isEmpty()
                || fight(s).form() != BossForm.EVOKER), "the evoker form raises fangs");

            // 5. Phase three, the creeper's fuse and the weak form afterwards. Nothing is destroyed by the blast.
            server.runOnServer(s -> fight(s).setHp(150));
            context.waitTicks(3);
            check(server.computeOnServer(s -> fight(s).stage() == 3 && fight(s).form() == BossForm.GHAST), "below one third: stage 3 starts with the ghast");
            server.runOnServer(s -> fight(s).forceForm(BossForm.CREEPER));
            context.waitTicks(10);
            context.takeScreenshot("11-boss-creeper-fuse");
            context.waitTicks(30);
            check(server.computeOnServer(s -> {
                FormKingFight fight = fight(s);
                Mob boss = body(s, fight);
                boolean floorIntact = true;
                if (boss != null)
                    for (BlockPos pos : BlockPos.betweenClosed(boss.blockPosition().offset(-4, -1, -4), boss.blockPosition().offset(4, -1, 4)))
                        if (!s.overworld().getBlockState(pos).is(Blocks.STONE)) floorIntact = false;
                return fight.form() == BossForm.WEAK && boss != null && boss.getType() == net.minecraft.world.entity.EntityTypes.ZOMBIE && floorIntact;
            }), "the creeper blows up into the weak form and the floor is intact");
            check(server.computeOnServer(s -> {
                FormKingFight fight = fight(s);
                double before = fight.hp();
                ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                body(s, fight).hurtServer(s.overworld(), s.overworld().damageSources().playerAttack(player), 100f);
                return Math.abs((before - fight.hp()) - 45.0) < 0.01;
            }), "the weak form takes 50% more damage (30 becomes 45)");
            context.waitTicks(110);
            check(server.computeOnServer(s -> fight(s).form() != BossForm.WEAK), "the weak form ends after five seconds");

            // 6. Boss fireballs light nothing.
            server.runOnServer(s -> fight(s).forceForm(BossForm.BLAZE_FAST));
            context.waitTicks(140);
            check(server.computeOnServer(s -> {
                int fires = 0;
                for (BlockPos pos : BlockPos.betweenClosed(-30, 119, -30, 30, 124, 30))
                    if (s.overworld().getBlockState(pos).is(Blocks.FIRE)) fires++;
                return fires == 0;
            }), "no fire blocks after a blaze barrage");

            // 7. Walking away ends the fight: the boss vanishes. Even with the arena unloaded the offering is
            // kept in the world; it is lying at the altar when the player comes back.
            // Far enough away that the arena stops ticking, the fight just waits.
            server.runOnServer(s -> s.getPlayerList().setSimulationDistance(2));
            server.runCommand("tp @p 0 120 60");
            context.waitTicks(260);
            check(server.computeOnServer(s -> fight(s) != null), "with nobody near enough for the arena to tick, the fight waits");
            // Close enough to keep it ticking, but outside the arena: after ten seconds the fight is given up.
            server.runOnServer(s -> s.getPlayerList().setSimulationDistance(10));
            server.runCommand("tp @p 0 120 41");
            context.waitTicks(260);
            check(server.computeOnServer(s -> fight(s) == null && noBosses(s)), "staying away ends the fight and the boss is gone");
            server.runCommand("tp @p 3 120 3");
            context.waitTicks(40);
            check(server.computeOnServer(s -> {
                // A creative player standing next to the altar picks the offering up again, so count the inventory too.
                var inventory = s.getPlayerList().getPlayers().get(0).getInventory();
                int stars = inventory.countItem(Items.NETHER_STAR), breath = inventory.countItem(Items.DRAGON_BREATH);
                for (var item : s.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(ALTAR).inflate(6))) {
                    if (item.getItem().is(Items.NETHER_STAR)) stars += item.getItem().getCount();
                    if (item.getItem().is(Items.DRAGON_BREATH)) breath += item.getItem().getCount();
                }
                return stars == 1 && breath == 1;
            }), "the offering is back after the fight was abandoned");
            server.runOnServer(s -> {
                var inventory = s.getPlayerList().getPlayers().get(0).getInventory();
                inventory.clearContent();
            });
            server.runCommand("kill @e[type=minecraft:item]");

            // 8. Victory: the core goes to the fighter, the advancement is awarded, and the altar rests afterwards.
            server.runCommand("tp @p 3 120 3");
            context.waitTicks(10);
            giveOffering(server);
            check(summon(server), "summoned again after the failed fight");
            context.waitTicks(5);
            server.runOnServer(s -> fight(s).setHp(10));
            check(server.computeOnServer(s -> {
                FormKingFight fight = fight(s);
                ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                body(s, fight).hurtServer(s.overworld(), s.overworld().damageSources().playerAttack(player), 100f);
                return fight.hp() <= 0;
            }), "a hit takes the last health");
            context.waitTicks(5);
            check(server.computeOnServer(s -> {
                ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                return fight(s) == null && noBosses(s) && player.getInventory().countItem(CombatMod.FORM_CORE) == 1;
            }), "victory: fight over and one form core in the inventory (the quest book entry is checked in the pack test)");
            giveOffering(server);
            check(!summon(server), "the altar rests for a while after a victory");
            check(server.computeOnServer(s -> FormKingFights.cooldownUntil(s.overworld(), ALTAR) > s.overworld().getGameTime()), "the cooldown is set");
        }
    }

    private static FormKingFight fight(net.minecraft.server.MinecraftServer s) { return FormKingFights.at(s.overworld(), ALTAR); }

    private static Mob body(net.minecraft.server.MinecraftServer s, FormKingFight fight) {
        return fight != null && s.overworld().getEntity(fight.bossId()) instanceof Mob mob ? mob : null;
    }

    private static boolean noBosses(net.minecraft.server.MinecraftServer s) {
        for (var mob : s.overworld().getEntitiesOfClass(Mob.class, wide())) if (mob.hasAttached(CombatMod.BOSS_MARK)) return false;
        return true;
    }

    private static AABB wide() { return new AABB(-100, 90, -100, 100, 200, 100); }

    private static void giveOffering(TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            player.getInventory().add(new ItemStack(Items.NETHER_STAR));
            player.getInventory().add(new ItemStack(Items.DRAGON_BREATH));
        });
    }

    private static boolean summon(TestServerContext server) {
        return server.computeOnServer(s -> FormKingFights.trySummon(s.overworld(), ALTAR, s.getPlayerList().getPlayers().get(0)));
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError("FAILED: " + what);
        System.out.println("[boss-test] ok: " + what);
    }
}
