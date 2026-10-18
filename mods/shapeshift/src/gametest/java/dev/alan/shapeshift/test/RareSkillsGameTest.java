package dev.alan.shapeshift.test;

import dev.alan.shapeshift.Reactions;
import dev.alan.shapeshift.RequestForm;
import dev.alan.shapeshift.ShapeshiftMod;
import dev.alan.shapeshift.UseAbility;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** The R-key skills of the rare forms: phantom, allay, frog, camel, polar bear, turtle, wither skeleton and happy ghast. */
public final class RareSkillsGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            for (String command : new String[] {
                "gamerule spawn_mobs false", "gamerule advance_time false", "gamerule natural_health_regeneration false",
                "time set noon", "weather clear", "gamemode survival @p",
                "fill -24 119 -24 24 119 24 minecraft:grass_block", "fill -24 120 -24 24 132 24 minecraft:air",
            }) server.runCommand(command);
            server.runCommand("execute as @p run shapeshift unlockall");
            context.waitTicks(10);

            // Phantom: the dive carries the user forward and hurts what is in the way.
            reset(server, context);
            server.runCommand("summon minecraft:pig 0 120 -4 {NoAI:1b}");
            become(context, world, "minecraft:phantom");
            place(server, context);
            use(context, world);
            context.waitTicks(10);
            double diveZ = server.computeOnServer(s -> player(s).getZ());
            double clientZ = context.computeOnClient(mc -> mc.player.getZ());
            System.out.println("[rare-skills] dive: server z " + diveZ + ", client z " + clientZ + ", pig hp "
                + server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.PIG, e -> e.isAlive()).get(0).getHealth()));
            check(diveZ < -1.5, "the phantom dives forward (z " + diveZ + ")");
            check(server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.PIG, e -> e.isAlive()).stream().allMatch(p -> p.getHealth() < 10)),
                "the dive hurts what it passes");

            // Allay: heals the user and nearby animals.
            reset(server, context);
            server.runCommand("summon minecraft:cow 3 120 0 {NoAI:1b,Health:4f}");
            become(context, world, "minecraft:allay");
            server.runOnServer(s -> player(s).setHealth(5));
            use(context, world);
            check(server.computeOnServer(s -> player(s).getHealth()) >= 8.9f, "the allay heals its user");
            check(server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.COW, e -> e.isAlive()).stream().allMatch(c -> c.getHealth() >= 7.9f)),
                "the allay heals a cow nearby");

            // Frog: pulls in an item in the crosshair; with nothing there it does nothing and costs no cooldown.
            reset(server, context);
            become(context, world, "minecraft:frog");
            place(server, context);
            use(context, world);
            server.runCommand("summon minecraft:item 0 121 -9 {Item:{id:\"minecraft:stone\",count:1}}");
            context.waitTicks(2);
            double before = stoneZ(server);
            use(context, world);
            context.waitTicks(8);
            double after = stoneZ(server);
            check(before < -8 && after > -3 && after < 0.5, "the frog snatches the item to the front of itself (" + before + " -> " + after + ")");

            // Camel: dashes forward and shoves, without hurting.
            reset(server, context);
            server.runCommand("summon minecraft:pig 0 120 -3 {NoAI:1b}");
            become(context, world, "minecraft:camel");
            place(server, context);
            use(context, world);
            context.waitTicks(10);
            check(server.computeOnServer(s -> player(s).getZ()) < -2, "the camel dashes forward");
            check(server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.PIG, e -> e.isAlive()).stream().allMatch(p -> p.getHealth() >= 10)),
                "the dash does not hurt");

            // Polar bear: the slam hurts and slows everything around it, but not other players.
            reset(server, context);
            server.runCommand("summon minecraft:pig 2 120 0 {NoAI:1b}");
            become(context, world, "minecraft:polar_bear");
            use(context, world);
            System.out.println("[rare-skills] slam: " + server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.PIG, e -> e.isAlive()).stream()
                .map(p -> p.getHealth() + " slow=" + p.hasEffect(MobEffects.SLOWNESS) + " at " + p.position() + " player " + player(s).position()).toList()));
            check(server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.PIG, e -> e.isAlive()).stream()
                .allMatch(p -> p.getHealth() < 10 && p.hasEffect(MobEffects.SLOWNESS))), "the slam hurts and slows the pig next to the bear");

            // Turtle: the shell gives resistance and takes away the legs.
            reset(server, context);
            become(context, world, "minecraft:turtle");
            use(context, world);
            check(server.computeOnServer(s -> player(s).hasEffect(MobEffects.RESISTANCE) && player(s).hasEffect(MobEffects.SLOWNESS)),
                "the turtle withdraws into its shell");

            // Wither skeleton: a wither skull flies.
            reset(server, context);
            become(context, world, "minecraft:wither_skeleton");
            place(server, context);
            use(context, world);
            context.waitTicks(2);
            check(server.computeOnServer(s -> !s.overworld().getEntities(EntityTypes.WITHER_SKULL, e -> true).isEmpty()), "the wither skeleton shoots a skull");

            // Ocelot: a long low leap.
            reset(server, context);
            become(context, world, "minecraft:ocelot");
            place(server, context);
            use(context, world);
            context.waitTicks(10);
            check(server.computeOnServer(s -> player(s).getZ()) < -2, "the ocelot pounces forward");

            // Parrot: speed and haste, for the user.
            reset(server, context);
            become(context, world, "minecraft:parrot");
            use(context, world);
            check(server.computeOnServer(s -> player(s).hasEffect(MobEffects.SPEED) && player(s).hasEffect(MobEffects.HASTE)), "the parrot sings speed and haste");

            // Skeleton horse: speed and jump boost.
            reset(server, context);
            become(context, world, "minecraft:skeleton_horse");
            use(context, world);
            check(server.computeOnServer(s -> player(s).hasEffect(MobEffects.SPEED) && player(s).hasEffect(MobEffects.JUMP_BOOST)), "the skeleton horse gallops");

            // Zombie horse: the cone in front is hurt, what is behind is not.
            reset(server, context);
            server.runCommand("summon minecraft:pig 0 120 -3 {NoAI:1b,Tags:[\"front\"]}");
            server.runCommand("summon minecraft:pig 0 120 3 {NoAI:1b,Tags:[\"back\"]}");
            become(context, world, "minecraft:zombie_horse");
            place(server, context);
            use(context, world);
            check(server.computeOnServer(s -> hp(s, "front") < 10 && hp(s, "back") >= 10), "the zombie horse tramples what is in front, not behind");

            // Glow squid: the ink blinds and lights up the pig next to it.
            reset(server, context);
            server.runCommand("summon minecraft:pig 3 120 0 {NoAI:1b}");
            become(context, world, "minecraft:glow_squid");
            use(context, world);
            check(server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.PIG, e -> e.isAlive()).stream()
                .allMatch(p -> p.hasEffect(MobEffects.BLINDNESS) && p.hasEffect(MobEffects.GLOWING))), "the ink cloud blinds and lights up the pig");

            // Tadpole: the splash puts out the user and a fire block, and gives water breathing.
            reset(server, context);
            server.runCommand("setblock 2 120 0 minecraft:fire");
            become(context, world, "minecraft:tadpole");
            server.runOnServer(s -> player(s).setRemainingFireTicks(100));
            use(context, world);
            check(server.computeOnServer(s -> !player(s).isOnFire() && player(s).hasEffect(MobEffects.WATER_BREATHING)), "the splash puts the user out and gives water breathing");
            check(server.computeOnServer(s -> s.overworld().getBlockState(new net.minecraft.core.BlockPos(2, 120, 0)).isAir()), "the splash puts a fire out");

            // Tropical fish: an armor stand in the line is pushed away, along the line.
            reset(server, context);
            server.runCommand("summon minecraft:armor_stand 0 120 -4");
            become(context, world, "minecraft:tropical_fish");
            place(server, context);
            use(context, world);
            context.waitTicks(10);
            check(server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.ARMOR_STAND, e -> true).get(0).getZ()) < -5, "the bubble stream pushes the armor stand away");
            // Zombie forms: a villager they kill rises as a zombie villager with its profession; other forms just kill it.
            reset(server, context);
            server.runCommand("summon minecraft:villager 0 120 -2 {NoAI:1b,VillagerData:{type:\"minecraft:plains\",profession:\"minecraft:farmer\",level:2}}");
            become(context, world, "minecraft:zombie");
            server.runCommand("damage @e[type=minecraft:villager,limit=1] 100 minecraft:player_attack by @p");
            context.waitTicks(5);
            check(server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.VILLAGER, e -> e.isAlive()).isEmpty()
                && s.overworld().getEntities(EntityTypes.ZOMBIE_VILLAGER, e -> e.isAlive()).size() == 1
                && ((net.minecraft.world.entity.monster.zombie.ZombieVillager) s.overworld().getEntities(EntityTypes.ZOMBIE_VILLAGER, e -> e.isAlive()).get(0))
                    .getVillagerData().toString().contains("farmer")), "a zombie kills a villager and it rises as a zombie villager farmer");

            reset(server, context);
            server.runCommand("summon minecraft:villager 0 120 -2 {NoAI:1b}");
            become(context, world, "minecraft:pig");
            server.runCommand("damage @e[type=minecraft:villager,limit=1] 100 minecraft:player_attack by @p");
            context.waitTicks(5);
            check(server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.ZOMBIE_VILLAGER, e -> e.isAlive()).isEmpty()),
                "a pig does not infect a villager");

            reset(server, context);
            server.runCommand("difficulty peaceful");
            server.runCommand("summon minecraft:villager 0 120 -2 {NoAI:1b}");
            become(context, world, "minecraft:husk");
            server.runCommand("damage @e[type=minecraft:villager,limit=1] 100 minecraft:player_attack by @p");
            context.waitTicks(5);
            check(server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.ZOMBIE_VILLAGER, e -> e.isAlive()).isEmpty()),
                "in peaceful a villager is not turned into a zombie that would vanish");
            server.runCommand("difficulty normal");
            // Happy ghast: hostile mobs leave the user alone for a while, unless the user hits them.
            reset(server, context);
            server.runCommand("summon minecraft:zombie 4 120 0 {NoAI:1b}");
            become(context, world, "minecraft:happy_ghast");
            check(!server.computeOnServer(s -> Reactions.friendly(zombie(s), player(s))), "a zombie is not soothed before the skill is used");
            use(context, world);
            check(server.computeOnServer(s -> Reactions.friendly(zombie(s), player(s))), "a soothed player is left alone by a zombie");
            server.runOnServer(s -> zombie(s).setLastHurtByMob(player(s)));
            check(!server.computeOnServer(s -> Reactions.friendly(zombie(s), player(s))), "hitting the zombie makes it fight back");
            server.runOnServer(s -> {
                zombie(s).setLastHurtByMob(null);
                player(s).setAttached(ShapeshiftMod.CALM_UNTIL, s.overworld().getGameTime() - 1);
            });
            check(!server.computeOnServer(s -> Reactions.friendly(zombie(s), player(s))), "the calm wears off");
        }
    }

    private static float hp(net.minecraft.server.MinecraftServer s, String tag) {
        return s.overworld().getEntities(EntityTypes.PIG, e -> e.isAlive() && e.entityTags().contains(tag)).get(0).getHealth();
    }

    private static ServerPlayer player(net.minecraft.server.MinecraftServer s) { return s.getPlayerList().getPlayers().get(0); }

    /** Z of the stone item (other drops, such as meat from earlier steps, are ignored). */
    private static double stoneZ(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        return server.computeOnServer(s -> s.overworld().getEntities(EntityTypes.ITEM, e -> true).stream()
            .filter(i -> ((net.minecraft.world.entity.item.ItemEntity) i).getItem().is(net.minecraft.world.item.Items.STONE))
            .findFirst().orElseThrow().getZ());
    }

    private static Mob zombie(net.minecraft.server.MinecraftServer s) {
        return (Mob) s.overworld().getEntities(EntityTypes.ZOMBIE, e -> true).get(0);
    }

    private static void reset(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, ClientGameTestContext context) {
        server.runCommand("kill @e[type=!player]");
        server.runCommand("effect clear @p");
        server.runOnServer(s -> {
            player(s).setHealth(player(s).getMaxHealth());
            player(s).removeAttached(ShapeshiftMod.CALM_UNTIL);
        });
        place(server, context);
    }

    private static void place(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, ClientGameTestContext context) {
        server.runCommand("tp @p 0 120 0 180 0");
        context.waitTicks(2);
    }

    private static void become(ClientGameTestContext context, TestSingleplayerContext world, String id) {
        context.runOnClient(mc -> ClientPlayNetworking.send(new RequestForm(id)));
        world.getConnection().waitForServerboundPackets();
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(3);
        world.getServer().runCommand("tp @p 0 120 0 180 0");
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
        System.out.println("[rare-skills] ok: " + what);
    }
}
