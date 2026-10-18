package dev.alan.mineworld.test;

import dev.alan.mineworld.DynamicLevels;
import dev.alan.mineworld.MineWorldData;
import dev.alan.mineworld.city.City;
import dev.alan.mineworld.city.CityPiglin;
import dev.alan.mineworld.city.Wanted;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Wanted mobs: the three tiers, glow and boss bar by distance, bounty proof drops, the spawner, the captain's trades. */
public final class WantedClientGameTest implements FabricClientGameTest {
    /** A spot in the lit tunnel from the spawn room to the first city. */
    private static final BlockPos ARENA = new BlockPos(0, DynamicLevels.FLOOR + 1, 40);

    @Override public void runTest(ClientGameTestContext context) {
        TestSingleplayerContext world = context.worldBuilder().adjustSettings(s -> s.setGenerateStructures(true)).create();
        TestServerContext server = world.getServer();
        var connection = world.getConnection();
        connection.waitForChunksRender();
        server.runCommand("gamerule spawn_mobs false");
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
        server.runCommand(in + "tp @p 0.5 " + ARENA.getY() + " 20.5 0 0");
        connection.waitForChunksRender();
        context.waitTicks(20);

        // 1. Each tier: health multiplier, armor, name, marker; the A tier brings three vindicators.
        String tiers = server.computeOnServer(s -> {
            ServerLevel level = level(s, worldId);
            StringBuilder b = new StringBuilder();
            for (Wanted.Tier tier : Wanted.Tier.values()) {
                Mob mob = Wanted.spawn(level, ARENA, tier);
                double base = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getBaseValue();
                b.append(tier).append(": health=").append(Math.round(mob.getMaxHealth() / base * 10) / 10.0)
                    .append(" armor=").append(mob.getItemBySlot(EquipmentSlot.CHEST).getItem())
                    .append(" marked=").append(mob.getAttached(Wanted.TIER) == tier)
                    .append(" named=").append(mob.hasCustomName()).append("; ");
            }
            int minions = level.getEntitiesOfClass(Vindicator.class, new AABB(ARENA).inflate(4), v -> !v.hasAttached(Wanted.TIER)).size();
            return b + "minions=" + minions + " nearby=" + Wanted.nearbyWanted(level, ARENA);
        });
        System.out.println("MINEWORLD WANTED " + tiers);
        check(tiers.contains("C: health=2.0") && tiers.contains("B: health=3.0") && tiers.contains("A: health=5.0"), "health multipliers: " + tiers);
        check(tiers.contains("armor=minecraft:iron_chestplate") && tiers.contains("armor=minecraft:diamond_chestplate"), "tier armor: " + tiers);
        check(!tiers.contains("marked=false") && !tiers.contains("named=false") && tiers.contains("minions=3") && tiers.contains("nearby=3"), "markers and minions: " + tiers);
        server.runCommand(in + "effect give @e[type=!player] minecraft:slowness infinite 255 true");
        server.runCommand(in + "effect give @e[type=!player] minecraft:weakness infinite 255 true");

        // 2. Within 32 blocks they glow and the A tier shows its boss bar; farther away neither.
        context.waitTicks(30);
        String near = marks(server, worldId);
        context.takeScreenshot("wanted-01-glow");
        server.runCommand(in + "tp @p 0.5 " + ARENA.getY() + " 3.5 0 0");
        context.waitTicks(30);
        String far = marks(server, worldId);
        System.out.println("MINEWORLD WANTED MARKS near=" + near + " far=" + far);
        check(near.equals("glowing=3 bar=1") && far.equals("glowing=0 bar=0"), "glow and boss bar by distance: near=" + near + " far=" + far);

        // 3. Killing them drops 1 + 3 + 8 bounty proofs.
        int proofs = server.computeOnServer(s -> {
            ServerLevel level = level(s, worldId);
            for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(ARENA).inflate(6), m -> true)) mob.kill(level);
            int count = 0;
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(ARENA).inflate(6), e -> e.getItem().is(City.BOUNTY_PROOF))) {
                count += e.getItem().getCount();
                e.discard();
            }
            return count;
        });
        check(proofs == 12, "bounty proofs dropped: " + proofs);

        // 4. The spawner finds a dark spot 24..64 blocks away, not near a city.
        String spawned = server.computeOnServer(s -> {
            ServerLevel level = level(s, worldId);
            for (int cx = 8; cx < 16; cx++) for (int cz = 8; cz < 16; cz++) level.getChunk(cx, cz);
            BlockPos around = new BlockPos(192, 100, 192);
            List<String> found = new ArrayList<>();
            for (int y = 20; y <= 260 && found.size() < 3; y += 40) {
                Mob mob = Wanted.trySpawn(level, around.atY(y), Wanted.Tier.C);
                if (mob == null) continue;
                double d = Math.sqrt(mob.distanceToSqr(around.getX() + 0.5, mob.getY(), around.getZ() + 0.5));
                found.add(Math.round(d) + "/" + City.contains(level, mob.blockPosition()));
                mob.discard();
            }
            return found.toString();
        });
        System.out.println("MINEWORLD WANTED SPAWNER " + spawned);
        check(!spawned.equals("[]") && !spawned.contains("true"), "spawner found spots outside cities: " + spawned);
        for (String part : spawned.replaceAll("[\\[\\] ]", "").split(",")) {
            int d = Integer.parseInt(part.split("/")[0]);
            check(d >= Wanted.MIN_DISTANCE - 1 && d <= Wanted.MAX_DISTANCE + 1, "spawn distance " + d);
        }

        // 5. The guard captain trades a totem for six proofs.
        String captain = server.computeOnServer(s -> {
            ServerLevel level = level(s, worldId);
            var player = s.getPlayerList().getPlayers().get(0);
            player.teleportTo(level, City.FIRST_CENTRE.getX() + 18, City.FIRST_CENTRE.getY() + 1, City.FIRST_CENTRE.getZ() - 37, java.util.Set.of(), 0, 0, false);
            for (int cx = -1; cx <= 2; cx++) for (int cz = 6; cz <= 8; cz++) level.getChunk(cx, cz);
            CityPiglin cap = level.getEntitiesOfClass(CityPiglin.class, new AABB(City.FIRST_CENTRE).inflate(70, 40, 70),
                p -> p.role() == CityPiglin.Role.CAPTAIN).getFirst();
            player.teleportTo(level, cap.getX() + 1, cap.getY(), cap.getZ(), java.util.Set.of(), 0, 0, false);
            player.getInventory().add(new ItemStack(City.BOUNTY_PROOF, 6));
            cap.mobInteract(player, InteractionHand.MAIN_HAND);
            if (!(player.containerMenu instanceof MerchantMenu menu)) return "no menu";
            int totem = -1;
            for (int i = 0; i < menu.getOffers().size(); i++) if (menu.getOffers().get(i).getResult().is(Items.TOTEM_OF_UNDYING)) totem = i;
            menu.setSelectionHint(totem);
            menu.tryMoveItems(totem);
            ItemStack bought = menu.quickMoveStack(player, 2);
            player.closeContainer();
            return "offers=" + menu.getOffers().size() + " bought=" + bought.getItem();
        });
        System.out.println("MINEWORLD WANTED CAPTAIN " + captain);
        check(captain.equals("offers=4 bought=minecraft:totem_of_undying"), "captain trade: " + captain);
        world.close();
    }

    private static String marks(TestServerContext server, String worldId) {
        return server.computeOnServer(s -> {
            ServerLevel level = level(s, worldId);
            int glowing = 0, bar = 0;
            for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(ARENA).inflate(4), m -> m.hasAttached(Wanted.TIER))) {
                if (mob.hasGlowingTag()) glowing++;
                bar += Wanted.bossBarPlayers(mob).size();
            }
            return "glowing=" + glowing + " bar=" + bar;
        });
    }

    private static ServerLevel level(MinecraftServer s, String worldId) {
        return s.getLevel(DynamicLevels.key(worldId));
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
