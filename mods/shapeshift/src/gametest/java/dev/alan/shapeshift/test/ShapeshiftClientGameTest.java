package dev.alan.shapeshift.test;

import dev.alan.shapeshift.FormDefinitions;
import dev.alan.shapeshift.FormPlan;
import dev.alan.shapeshift.RequestForm;
import dev.alan.shapeshift.UseAbility;
import dev.alan.shapeshift.ShapeshiftMod;
import dev.alan.shapeshift.Unlocks;
import dev.alan.shapeshift.client.FormBodies;
import dev.alan.shapeshift.mixin.LivingEntityInvoker;
import dev.alan.shapeshift.client.FormScreen;
import dev.alan.shapeshift.client.ShapeshiftClient;
import java.util.Objects;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/** Drives a real client through unlocking, the menu, transforming, space checks and death. */
public final class ShapeshiftClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            var server = world.getServer();
            var connection = world.getConnection();
            connection.waitForChunksRender();
            for (String command : new String[] {
                "gamerule spawn_mobs false", "gamerule advance_time false", "gamerule advance_weather false",
                "gamerule natural_health_regeneration false",
                "time set noon", "weather clear",
                "fill -24 119 -24 24 119 24 minecraft:grass_block", "fill -24 120 -24 24 132 24 minecraft:air",
                "tp @p 0 120 0 180 15", "kill @e[type=!player]",
            }) server.runCommand(command);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(20);

            // 1. Killing a pig unlocks the pig form, and only that form.
            server.runCommand("summon minecraft:pig 0 120 3");
            context.waitTicks(5);
            server.runCommand("damage @e[type=minecraft:pig,limit=1] 100 minecraft:player_attack by @p");
            connection.waitForClientboundPackets();
            context.waitTicks(5);
            Unlocks unlocks = unlocks(context);
            check(unlocks.contains("minecraft:pig"), "pig unlocked after kill, got " + unlocks.ids());
            check(unlocks.ids().size() == 1, "only the pig is unlocked, got " + unlocks.ids());

            // 2. The menu key opens the menu.
            context.getInput().pressKey(ShapeshiftClient.OPEN_MENU);
            context.waitForScreen(FormScreen.class);
            context.waitTicks(5);
            context.takeScreenshot("01-menu-pig-only");
            context.setScreen(() -> null);

            // 3. Locked forms are refused.
            request(context, world, "minecraft:bat");
            check(form(context) == null, "locked bat request must be refused");

            // 4. Transforming changes hitbox, eye height and max health on both sides.
            request(context, world, "minecraft:pig");
            checkForm(context, world, "minecraft:pig", 0.9f, 0.9f, 10f);
            context.takeScreenshot("02-pig");

            server.runCommand("execute as @p run shapeshift unlockall");
            connection.waitForClientboundPackets();
            context.getInput().pressKey(ShapeshiftClient.OPEN_MENU);
            context.waitForScreen(FormScreen.class);
            context.waitTicks(10);
            context.takeScreenshot("03-menu-all");
            context.setScreen(() -> null);

            String[][] forms = {
                {"minecraft:bat", "0.5", "0.9", "6"},
                {"minecraft:creeper", "0.6", "1.7", "20"},
                {"minecraft:iron_golem", "1.4", "2.7", "100"},
                {"minecraft:spider", "1.4", "0.9", "16"},
                {"minecraft:enderman", "0.6", "2.9", "40"},
                {"minecraft:chicken", "0.4", "0.7", "4"},
                {"minecraft:horse", "1.3964844", "1.6", "22"},
                {"minecraft:ghast", "4.0", "4.0", "10"},
            };
            int shot = 4;
            for (String[] f : forms) {
                request(context, world, f[0]);
                checkForm(context, world, f[0], Float.parseFloat(f[1]), Float.parseFloat(f[2]), Float.parseFloat(f[3]));
                context.waitTicks(10);
                context.takeScreenshot(String.format("%02d-%s", shot++, f[0].substring(10)));
            }

            passives(context, world);
            actives(context, world);
            weaknesses(context, world);
            reactions(context, world);
            polish(context, world);

            // 5. Health keeps its percentage: half-health golem -> half-health human.
            request(context, world, "minecraft:iron_golem");
            server.runOnServer(s -> connection.getServerPlayer().setHealth(50));
            request(context, world, "");
            checkForm(context, world, null, 0.6f, 1.8f, 20f);
            float health = server.computeOnServer(s -> connection.getServerPlayer().getHealth());
            check(Math.abs(health - 10f) < 0.01f, "half health kept when reverting, got " + health);
            server.runCommand("effect give @p minecraft:instant_health 1 10");

            // 6. No room: a 2-block-high space refuses the iron golem but allows the pig.
            server.runCommand("fill -2 122 -2 2 122 2 minecraft:stone");
            context.waitTicks(2);
            request(context, world, "minecraft:iron_golem");
            check(form(context) == null, "golem must be refused under a 2-block ceiling");
            request(context, world, "minecraft:pig");
            check("minecraft:pig".equals(form(context)), "pig fits under a 2-block ceiling");
            server.runCommand("fill -2 122 -2 2 122 2 minecraft:air");

            // 7. Dying reverts to human but keeps unlocks.
            request(context, world, "minecraft:bat");
            server.runCommand("kill @p");
            context.waitForScreen(DeathScreen.class);
            context.waitTicks(25);
            context.clickScreenButton("deathScreen.respawn");
            context.waitFor(mc -> mc.player != null && mc.player.isAlive() && mc.gui.screen() == null);
            connection.waitForClientboundPackets();
            context.waitTicks(5);
            checkForm(context, world, null, 0.6f, 1.8f, 20f);
            check(unlocks(context).contains("minecraft:pig"), "unlocks survive death");
            ShapeshiftMod.LOG.info("Shapeshift client game test passed");
        }
    }

    /** Phase 2: data-driven passives reach both sides and are removed again. */
    private static void passives(ClientGameTestContext context, TestSingleplayerContext world) {
        var server = world.getServer();
        // Every attribute named in a form file must exist, or that modifier would be skipped at runtime.
        server.runOnServer(s -> FormDefinitions.serverDefinitions().forEach((entity, def) -> def.attributes().keySet().forEach(key ->
            check(BuiltInRegistries.ATTRIBUTE.containsKey(Identifier.parse(FormPlan.attributeId(key))), entity + " uses unknown attribute " + key))));
        check(context.computeOnClient(mc -> FormDefinitions.resolve(EntityTypes.HORSE, true).maxHealth()) == 22.0,
            "definitions are synced to the client");

        request(context, world, "minecraft:bat");
        check(context.computeOnClient(mc -> mc.player.getAbilities().mayfly), "bat can fly");
        check(clientEffect(context, MobEffects.NIGHT_VISION), "bat has night vision");
        check(clientAttr(context, Attributes.FALL_DAMAGE_MULTIPLIER) == 0, "bat takes no fall damage");
        server.runCommand("effect clear @p");
        context.waitTicks(25);
        check(clientEffect(context, MobEffects.NIGHT_VISION), "night vision comes back after milk / effect clear");

        request(context, world, "minecraft:horse");
        check(!context.computeOnClient(mc -> mc.player.getAbilities().mayfly), "flight is removed when leaving the bat");
        check(!clientEffect(context, MobEffects.NIGHT_VISION), "night vision is removed when leaving the bat");
        check(Math.abs(clientAttr(context, Attributes.MOVEMENT_SPEED) - 0.2) < 1e-4, "horse runs twice as fast");
        check(Math.abs(clientAttr(context, Attributes.JUMP_STRENGTH) - 0.72) < 1e-4, "horse jumps higher");
        check(Math.abs(clientAttr(context, Attributes.STEP_HEIGHT) - 1.0) < 1e-4, "horse steps up full blocks");

        request(context, world, "minecraft:iron_golem");
        check(server.computeOnServer(s -> world.getConnection().getServerPlayer().getAttributeValue(Attributes.ATTACK_DAMAGE)) == 15.0,
            "golem hits like a golem");
        check(clientAttr(context, Attributes.CAMERA_DISTANCE) > 5.5, "camera backs off for big forms");

        request(context, world, "minecraft:blaze");
        check(clientEffect(context, MobEffects.FIRE_RESISTANCE), "fire-immune entities give fire resistance");
        request(context, world, "minecraft:chicken");
        check(clientEffect(context, MobEffects.SLOW_FALLING), "chicken falls slowly");
        request(context, world, "minecraft:squid");
        check(clientEffect(context, MobEffects.WATER_BREATHING), "aquatic entities breathe underwater");
        check(clientAttr(context, Attributes.WATER_MOVEMENT_EFFICIENCY) == 1.0, "aquatic entities swim fast");

        // Climbing: walk into a wall as a pig (stays on the ground), then as a spider (climbs).
        server.runCommand("fill -3 120 -3 3 127 -3 minecraft:stone");
        request(context, world, "minecraft:pig");
        server.runCommand("tp @p 0 120 0 180 0");
        context.waitTicks(3);
        context.getInput().holdKeyFor(options -> options.keyUp, 30);
        double pigY = context.computeOnClient(mc -> mc.player.getY());
        check(pigY < 120.6, "a pig cannot climb walls, y=" + pigY);
        server.runCommand("tp @p 0 120 0 180 0");
        context.waitTicks(3);
        request(context, world, "minecraft:spider");
        check("minecraft:spider".equals(form(context)), "spider fits away from the wall");
        context.getInput().holdKeyFor(options -> options.keyUp, 30);
        double spiderY = context.computeOnClient(mc -> mc.player.getY());
        context.takeScreenshot("20-spider-climbing");
        check(spiderY > 121.5, "a spider climbs walls, y=" + spiderY);
        server.runCommand("fill -3 120 -3 3 127 -3 minecraft:air");
        server.runCommand("tp @p 0 120 0 180 15");

        request(context, world, "");
        check(Math.abs(clientAttr(context, Attributes.MOVEMENT_SPEED) - 0.1) < 1e-6, "speed is back to normal");
        check(clientAttr(context, Attributes.ATTACK_DAMAGE) == 1.0, "fists are back to normal");
        check(clientAttr(context, Attributes.CAMERA_DISTANCE) == 4.0, "camera is back to normal");
        check(!clientEffect(context, MobEffects.WATER_BREATHING), "form effects are removed");
    }

    /** Phase 3: R-key abilities. */
    private static void actives(ClientGameTestContext context, TestSingleplayerContext world) {
        var server = world.getServer();
        var player = world.getConnection();
        server.runCommand("kill @e[type=!player]");
        server.runCommand("tp @p 0 120 0 180 15");

        // Creeper: fuse, blast that spares the user, back to human.
        server.runCommand("setblock 2 120 0 minecraft:dirt");
        server.runCommand("summon minecraft:pig 0 120 -2 {NoAI:1b}");
        request(context, world, "minecraft:creeper");
        server.runOnServer(s -> player.getServerPlayer().setHealth(player.getServerPlayer().getMaxHealth()));
        use(context, world);
        check(context.computeOnClient(mc -> mc.player.hasAttached(ShapeshiftMod.FUSE)), "fuse is lit and synced");
        context.waitTicks(15);
        context.takeScreenshot("21-creeper-swelling");
        context.waitTicks(25);
        world.getConnection().waitForClientboundPackets();
        check(form(context) == null, "creeper turns back into a human after exploding");
        float hp = server.computeOnServer(s -> player.getServerPlayer().getHealth());
        check(hp >= 19.9f, "the explosion does not hurt the user, health=" + hp);
        check(server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(2, 120, 0)).isAir()), "the explosion breaks blocks");
        check(server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.PIG, e -> true).stream().allMatch(p -> p.getHealth() < 10)),
            "the explosion hurts nearby mobs");
        context.takeScreenshot("22-after-explosion");
        server.runCommand("fill -6 115 -6 6 119 6 minecraft:grass_block");
        server.runCommand("kill @e[type=!player]");

        // Pressing R again during the fuse cancels it.
        request(context, world, "minecraft:creeper");
        use(context, world);
        context.waitTicks(5);
        use(context, world);
        context.waitTicks(40);
        check("minecraft:creeper".equals(form(context)), "defused creeper is still a creeper");
        check(!context.computeOnClient(mc -> mc.player.hasAttached(ShapeshiftMod.FUSE)), "fuse is cleared");

        // Projectiles.
        Object[][] shots = {
            {"minecraft:blaze", EntityTypes.SMALL_FIREBALL}, {"minecraft:ghast", EntityTypes.FIREBALL},
            {"minecraft:skeleton", EntityTypes.ARROW}, {"minecraft:snow_golem", EntityTypes.SNOWBALL},
            {"minecraft:breeze", EntityTypes.WIND_CHARGE}, {"minecraft:llama", EntityTypes.LLAMA_SPIT},
            {"minecraft:evoker", EntityTypes.EVOKER_FANGS},
        };
        for (Object[] shot : shots) {
            server.runCommand("kill @e[type=!player]");
            server.runCommand("tp @p 0 120 0 180 -10");
            request(context, world, (String) shot[0]);
            use(context, world);
            context.waitTicks(2);
            var projectile = (net.minecraft.world.entity.EntityType<?>) shot[1];
            int count = server.computeOnServer(s -> s.overworld().getEntities(projectile, e -> true).size());
            check(count > 0, shot[0] + " launches " + projectile);
        }
        context.takeScreenshot("23-evoker-fangs");
        server.runCommand("kill @e[type=!player]");

        // Enderman: teleport to the block under the crosshair.
        server.runCommand("tp @p 0 120 0 180 30");
        request(context, world, "minecraft:enderman");
        server.runCommand("tp @p 0 120 0 180 30");
        context.waitTicks(3);
        use(context, world);
        context.waitTicks(3);
        double z = context.computeOnClient(mc -> mc.player.getZ());
        check(z < -2, "enderman teleports forward, z=" + z);

        // Forms without an ability do nothing.
        request(context, world, "minecraft:pig");
        use(context, world);
        check("minecraft:pig".equals(form(context)), "pig has no ability and stays a pig");
        request(context, world, "");
        server.runCommand("tp @p 0 120 0 180 15");
    }

    /** Phase 3: weaknesses. */
    private static void weaknesses(ClientGameTestContext context, TestSingleplayerContext world) {
        var server = world.getServer();
        var player = world.getConnection();
        server.runCommand("time set noon");
        request(context, world, "minecraft:zombie");
        server.waitFor(s -> player.getServerPlayer().isOnFire(), 300);
        context.takeScreenshot("24-zombie-burning");
        context.waitTicks(10);
        check(context.computeOnClient(mc -> FormBodies.extract(mc.player, 1f).displayFireAnimation), "the zombie body is drawn on fire");
        context.takeScreenshot("24b-zombie-burning-later");
        request(context, world, "");
        server.runOnServer(s -> {
            player.getServerPlayer().clearFire();
            player.getServerPlayer().setHealth(20);
        });

        request(context, world, "minecraft:enderman");
        server.runOnServer(s -> player.getServerPlayer().setHealth(player.getServerPlayer().getMaxHealth()));
        server.runCommand("fill -1 120 -1 1 122 1 minecraft:water");
        context.waitTicks(20);
        float hp = server.computeOnServer(s -> player.getServerPlayer().getHealth());
        check(hp < 40, "water hurts an enderman, health=" + hp);
        // Water spreads beyond where it was placed; clear it all and let flowing water drain.
        server.runCommand("fill -8 120 -8 8 123 8 minecraft:air");
        context.waitTicks(10);
        check(!server.computeOnServer(s -> player.getServerPlayer().isInWaterOrRain()), "test area is dry again");

        request(context, world, "minecraft:cod");
        context.waitTicks(40);
        int air = server.computeOnServer(s -> player.getServerPlayer().getAirSupply());
        String state = server.computeOnServer(s -> {
            var p = player.getServerPlayer();
            return "water=" + p.isInWater() + " wetOrRain=" + p.isInWaterOrRain() + " pos=" + p.position() + " creative=" + p.isCreative()
                + " needsWater=" + FormDefinitions.resolve(EntityTypes.COD, false).has(dev.alan.shapeshift.Weakness.NEEDS_WATER);
        });
        check(air < 290, "a fish dries out on land, air=" + air + " " + state);
        request(context, world, "");
        server.runOnServer(s -> player.getServerPlayer().setHealth(20));
    }

    /** Phase 4: mobs ignore, hunt or flee shapeshifted players. */
    private static void reactions(ClientGameTestContext context, TestSingleplayerContext world) {
        var server = world.getServer();
        var conn = world.getConnection();
        server.runCommand("difficulty normal");
        server.runCommand("time set midnight");
        server.runCommand("kill @e[type=!player]");
        server.runCommand("tp @p 0 120 0 180 15");

        // Baseline: a zombie hunts a human.
        server.runCommand("summon minecraft:zombie 0 120 -8");
        server.waitFor(s -> targetOf(s, EntityTypes.ZOMBIE) == playerOf(s), 200);
        // As a zombie, it loses interest.
        request(context, world, "minecraft:zombie");
        context.waitTicks(40);
        check(server.computeOnServer(s -> targetOf(s, EntityTypes.ZOMBIE) != playerOf(s)), "zombies ignore zombie-form players");
        // Hitting it makes it fight back.
        server.runCommand("damage @e[type=minecraft:zombie,limit=1] 1 minecraft:player_attack by @p");
        server.waitFor(s -> targetOf(s, EntityTypes.ZOMBIE) == playerOf(s), 60);
        server.runCommand("kill @e[type=!player]");

        // Iron golems hunt monster forms, and give up once the player is human again.
        server.runCommand("summon minecraft:iron_golem 0 120 -8");
        awaitTarget(context, world, EntityTypes.IRON_GOLEM, 200);
        request(context, world, "");
        check(server.computeOnServer(s -> targetOf(s, EntityTypes.IRON_GOLEM)) == null, "golem stops hunting a human");
        server.runCommand("kill @e[type=!player]");

        // Wolves hunt skeleton forms.
        request(context, world, "minecraft:skeleton");
        server.runCommand("summon minecraft:wolf 0 120 -8");
        awaitTarget(context, world, EntityTypes.WOLF, 200);
        server.runCommand("kill @e[type=!player]");
        server.runCommand("tp @p 0 120 0 180 15");

        // Creepers flee cats; villagers flee zombies.
        fleeTest(context, world, "minecraft:cat", "minecraft:creeper", EntityTypes.CREEPER);
        fleeTest(context, world, "minecraft:zombie", "minecraft:villager", EntityTypes.VILLAGER);
        request(context, world, "");
        server.runCommand("kill @e[type=!player]");
        server.runCommand("difficulty peaceful");
        server.runCommand("time set noon");
    }

    private static void fleeTest(ClientGameTestContext context, TestSingleplayerContext world, String form, String mobId,
                                 net.minecraft.world.entity.EntityType<?> mobType) {
        var server = world.getServer();
        server.runCommand("kill @e[type=!player]");
        server.runCommand("tp @p 0 120 0 180 15");
        request(context, world, form);
        server.runCommand("summon " + mobId + " 0 120 -3");
        // Fleeing mobs run, stop, look around and run again, so check the farthest they got.
        var samples = new java.util.ArrayList<Double>();
        for (int i = 0; i < 12; i++) {
            context.waitTicks(10);
            samples.add(server.computeOnServer(s -> (double) Math.round(s.overworld().getEntities(mobType, e -> true).getFirst()
                .distanceTo(playerOf(s)) * 10) / 10));
        }
        double farthest = samples.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        check(farthest > 6, mobId + " flees from " + form + ", distances=" + samples);
    }

    /** Waits for a mob to target the player; on failure reports what the mob was doing. */
    private static void awaitTarget(ClientGameTestContext context, TestSingleplayerContext world,
                                    net.minecraft.world.entity.EntityType<?> type, int ticks) {
        var server = world.getServer();
        var mob = server.computeOnServer(s -> (net.minecraft.world.entity.Mob) s.overworld().getEntities(type, e -> true).getFirst());
        for (int waited = 0; waited < ticks; waited += 5) {
            if (server.computeOnServer(s -> mob.getTarget() == playerOf(s))) return;
            context.waitTicks(5);
        }
        String state = server.computeOnServer(s -> {
            var p = playerOf(s);
            if (mob.isRemoved()) return "removed=" + mob.getRemovalReason() + " at " + mob.position() + " health=" + mob.getHealth()
                + " lastDamage=" + mob.getLastDamageSource() + " lastHurtBy=" + mob.getLastHurtByMob();
            return "target=" + mob.getTarget() + " distance=" + mob.distanceTo(p) + " hunts=" + dev.alan.shapeshift.Reactions.hunts(mob, p)
                + " canAttack=" + mob.canAttack(p) + " sees=" + mob.getSensing().hasLineOfSight(p) + " form=" + p.getAttached(ShapeshiftMod.FORM);
        });
        throw new AssertionError(type + " never targeted the player: " + state);
    }

    private static net.minecraft.server.level.ServerPlayer playerOf(net.minecraft.server.MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static net.minecraft.world.entity.LivingEntity targetOf(net.minecraft.server.MinecraftServer server,
                                                                    net.minecraft.world.entity.EntityType<?> type) {
        var mobs = server.overworld().getEntities(type, e -> true);
        return mobs.isEmpty() ? null : ((net.minecraft.world.entity.Mob) mobs.getFirst()).getTarget();
    }

    /** Phase 4 polish: form sounds, HUD and first-person view. */
    private static void polish(ClientGameTestContext context, TestSingleplayerContext world) {
        var server = world.getServer();
        request(context, world, "minecraft:pig");
        var serverSound = server.computeOnServer(s -> {
            var p = world.getConnection().getServerPlayer();
            return ((LivingEntityInvoker) p).shapeshift$getHurtSound(p.damageSources().generic());
        });
        check(serverSound != null && serverSound.location().getPath().matches(".*pig.*hurt.*"), "a pig-form player squeals when hurt, got " + serverSound);
        var clientSound = context.computeOnClient(mc -> ((LivingEntityInvoker) mc.player).shapeshift$getDeathSound());
        check(clientSound != null && clientSound.location().getPath().matches(".*pig.*death.*"), "death sound matches the form on the client, got " + clientSound);

        server.runCommand("clear @p");   // an empty hand is when the bare arm would be drawn
        request(context, world, "minecraft:blaze");
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        context.waitTicks(10);
        context.takeScreenshot("30-first-person-blaze-hud");
        String keyLabel = context.computeOnClient(mc -> ShapeshiftClient.USE_ABILITY.getTranslatedKeyMessage().getString());
        // Minecraft names keys after the OS keyboard layout (R shows as "ㄐ" under Zhuyin), so only require a label.
        check(!keyLabel.isBlank(), "HUD shows the ability key, got '" + keyLabel + "'");
        request(context, world, "");
        context.waitTicks(10);
        context.takeScreenshot("31-first-person-human");
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
    }

    private static void use(ClientGameTestContext context, TestSingleplayerContext world) {
        context.runOnClient(mc -> ClientPlayNetworking.send(new UseAbility()));
        world.getConnection().waitForServerboundPackets();
        world.getConnection().waitForClientboundPackets();
    }

    private static double clientAttr(ClientGameTestContext context, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        return context.computeOnClient(mc -> mc.player.getAttributeValue(attribute));
    }

    private static boolean clientEffect(ClientGameTestContext context, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        return context.computeOnClient(mc -> mc.player.hasEffect(effect));
    }

    private static void request(ClientGameTestContext context, TestSingleplayerContext world, String id) {
        context.runOnClient(mc -> ClientPlayNetworking.send(new RequestForm(id)));
        world.getConnection().waitForServerboundPackets();
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(3);
    }

    private static void checkForm(ClientGameTestContext context, TestSingleplayerContext world, String id, float w, float h, float maxHealth) {
        check(Objects.equals(id, form(context)), "client form " + id + ", got " + form(context));
        var server = world.getServer();
        float[] s = server.computeOnServer(x -> {
            Player p = world.getConnection().getServerPlayer();
            return new float[] {p.getBbWidth(), p.getBbHeight(), p.getMaxHealth()};
        });
        float[] c = context.computeOnClient(mc -> new float[] {mc.player.getBbWidth(), mc.player.getBbHeight(), mc.player.getMaxHealth()});
        for (float[] v : new float[][] {s, c}) {
            check(Math.abs(v[0] - w) < 0.01f && Math.abs(v[1] - h) < 0.01f,
                id + " hitbox " + w + "x" + h + ", got " + v[0] + "x" + v[1]);
            check(Math.abs(v[2] - maxHealth) < 0.01f, id + " max health " + maxHealth + ", got " + v[2]);
        }
    }

    private static String form(ClientGameTestContext context) {
        return context.computeOnClient(mc -> mc.player.getAttached(ShapeshiftMod.FORM));
    }

    private static Unlocks unlocks(ClientGameTestContext context) {
        return context.computeOnClient(mc -> mc.player.getAttachedOrElse(ShapeshiftMod.UNLOCKS, Unlocks.EMPTY));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
