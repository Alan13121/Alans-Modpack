package dev.alan.mineworld.test;

import dev.alan.mineworld.DynamicLevels;
import dev.alan.mineworld.MineWorldData;
import dev.alan.mineworld.MineWorldMod;
import dev.alan.mineworld.city.City;
import dev.alan.mineworld.city.CityLayout;
import dev.alan.mineworld.city.CityPiglin;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;

/** The first monster city next to the spawn room: tunnel, layout, residents, guards, safety, inn respawn, random cities. */
public final class CityClientGameTest implements FabricClientGameTest {
    private static final BlockPos C = City.FIRST_CENTRE;

    @Override public void runTest(ClientGameTestContext context) {
        TestSingleplayerContext world = context.worldBuilder().adjustSettings(s -> s.setGenerateStructures(true)).create();
        TestServerContext server = world.getServer();
        var connection = world.getConnection();
        connection.waitForChunksRender();
        server.runCommand("gamerule spawn_mobs false");
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

        String worldId = server.computeOnServer(s -> {
            String id = MineWorldData.get(s).createWorld();
            MineWorldData.get(s).setOrigin(id, GlobalPos.of(Level.OVERWORLD, new BlockPos(0, 120, 3)));
            DynamicLevels.ensure(s, id);
            return id;
        });
        String in = "execute in mineworld:" + worldId + " run ";
        server.runCommand(in + "forceload add -64 80 63 207");
        server.runCommand("gamemode creative @p");
        server.runCommand("effect give @p minecraft:night_vision infinite 0 true");
        server.runCommand(in + "tp @p 0.5 128 1.5 0 0");
        connection.waitForChunksRender();
        context.waitTicks(40);

        // 1. Tunnel from the spawn room to the gate passage, and the city itself.
        String layout = server.computeOnServer(s -> {
            ServerLevel level = level(s, worldId);
            int floor = C.getY();
            int blocked = 0;
            for (int z = 3; z <= C.getZ() - CityLayout.WALL_OUTER; z++) {
                for (int y = floor + 1; y <= floor + 3; y++) if (!level.getBlockState(new BlockPos(0, y, z)).isAir()) blocked++;
            }
            boolean gate = level.getBlockState(C.offset(0, 1, -CityLayout.WALL_OUTER)).isAir();
            boolean wall = level.getBlockState(C.offset(20, 3, -CityLayout.WALL_OUTER)).is(net.minecraft.world.level.block.Blocks.STONE_BRICKS);
            boolean dome = level.getBlockState(C.offset(30, 20, 0)).isAir();
            boolean returnBlock = level.getBlockState(C.offset(0, 0, -5)).is(MineWorldMod.RETURN_BLOCK);
            boolean inside = City.contains(level, C.offset(0, 5, 0)) && City.contains(level, C.offset(0, 2, -CityLayout.WALL_OUTER - 3));
            boolean outside = !City.contains(level, new BlockPos(0, floor + 1, 0));
            var monsters = level.getChunkSource().getGenerator().getMobsAt(level, level.structureManager(), MobCategory.MONSTER, C.offset(20, 1, 20));
            return "tunnelBlocked=" + blocked + " gate=" + gate + " wall=" + wall + " dome=" + dome + " return=" + returnBlock
                + " inside=" + inside + " outside=" + outside + " monsterSpawns=" + monsters.unwrap().size();
        });
        System.out.println("MINEWORLD CITY " + layout);
        check(layout.equals("tunnelBlocked=0 gate=true wall=true dome=true return=true inside=true outside=true monsterSpawns=0"), layout);
        context.takeScreenshot("city-01-tunnel");

        // 2. Residents of every kind.
        String residents = server.computeOnServer(s -> {
            ServerLevel level = level(s, worldId);
            Map<String, Integer> counts = new TreeMap<>();
            for (Entity e : level.getEntities((Entity) null, new AABB(C).inflate(70, 40, 70), e -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals("mineworld"))) {
                String key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
                if (e instanceof CityPiglin p) key += "/" + p.role().name().toLowerCase() + (p.role() == CityPiglin.Role.CIVILIAN ? "/" + p.profession().id() : "");
                counts.merge(key, 1, Integer::sum);
                check(e.isInvulnerable(), "resident is invulnerable: " + key);
            }
            return counts.toString();
        });
        System.out.println("MINEWORLD RESIDENTS " + residents);
        for (String need : new String[]{"city_zombie=", "city_enderman=", "city_creeper=", "city_captain/captain=", "city_skeleton=4",
            "city_piglin/melee=", "city_piglin/ranged=", "city_piglin/civilian/bartender="}) {
            check(residents.contains(need), "residents include " + need + ": " + residents);
        }

        // 2b. Trading: a piglin trade goes through; the enderman's offers are enchanted, lockable, and locks persist.
        checkTrades(context, server, connection, worldId, in);

        // 3. Guards kill a zombie that walks in through the gate (the player stands in the passage so the area ticks).
        server.runCommand(in + "tp @p 0.5 " + (C.getY() + 1) + " " + (C.getZ() - CityLayout.WALL_OUTER - 4) + " 0 0");
        context.waitTicks(40);
        BlockPos intruder = C.offset(0, 1, -CityLayout.WALL_INNER + 6);
        int zombieId = server.computeOnServer(s -> {
            ServerLevel level = level(s, worldId);
            Zombie z = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            z.snapTo(intruder.getX() + 0.5, intruder.getY(), intruder.getZ() + 0.5, 0, 0);
            level.addFreshEntity(z);
            return z.getId();
        });
        context.waitTicks(400);
        boolean zombieGone = server.computeOnServer(s -> {
            Entity e = level(s, worldId).getEntity(zombieId);
            return e == null || !e.isAlive();
        });
        check(zombieGone, "guards killed the intruding zombie");

        // 4. Inn beds set the respawn point; beds elsewhere in the mine world do not.
        BlockPos bed = server.computeOnServer(s -> findBed(level(s, worldId)));
        check(bed != null, "found a bed in the inn");
        String beds = server.computeOnServer(s -> {
            ServerLevel level = level(s, worldId);
            var rule = ((AbstractBedBlock) level.getBlockState(bed).getBlock()).getBedRule(level, bed);
            var outside = ((AbstractBedBlock) level.getBlockState(bed).getBlock()).getBedRule(level, new BlockPos(0, C.getY() + 1, 0));
            return "inn=" + rule.canSetSpawn(level) + " outside=" + outside.canSetSpawn(level);
        });
        check(beds.equals("inn=true outside=false"), "bed rules: " + beds);
        server.runCommand(in + "tp @p " + bed.getX() + " " + (bed.getY() + 1) + " " + bed.getZ());
        context.waitTicks(10);
        server.runOnServer(s -> {
            var player = s.getPlayerList().getPlayers().get(0);
            var level = level(s, worldId);
            level.getBlockState(bed).useWithoutItem(level, player,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(bed), net.minecraft.core.Direction.UP, bed, false));
        });
        context.waitTicks(10);
        context.takeScreenshot("city-02-inn");
        server.runCommand("gamemode survival @p");
        server.runCommand("kill @p");
        context.waitTicks(20);
        context.runOnClient(mc -> mc.player.respawn());
        connection.waitForClientboundPackets();
        context.waitTicks(40);
        String respawn = server.computeOnServer(s -> {
            var player = s.getPlayerList().getPlayers().get(0);
            return worldId.equals(DynamicLevels.worldId(player.level())) + " " + player.blockPosition();
        });
        System.out.println("MINEWORLD RESPAWN " + respawn);
        check(respawn.startsWith("true ") && City.FIRST_CENTRE != null, "respawned in the city: " + respawn);
        server.runCommand("gamemode creative @p");

        // 5. Screenshots: gate from the tunnel, plaza, wall top.
        server.runCommand(in + "tp @p 0.5 128 " + (C.getZ() - CityLayout.WALL_OUTER - 10) + " 0 0");
        connection.waitForChunksRender();
        context.waitTicks(20);
        context.takeScreenshot("city-03-gate");
        server.runCommand(in + "tp @p 0.5 " + (C.getY() + 3) + " " + (C.getZ() - 20) + " 0 15");
        connection.waitForChunksRender();
        context.waitTicks(20);
        context.takeScreenshot("city-04-plaza");
        server.runCommand(in + "tp @p 30.5 " + (C.getY() + 12) + " " + (C.getZ() - 30) + " 45 25");
        connection.waitForChunksRender();
        context.waitTicks(20);
        context.takeScreenshot("city-05-overview");

        // 6. Random cities exist farther out.
        String far = server.computeOnServer(s -> {
            ServerLevel level = level(s, worldId);
            var city = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE, MineWorldMod.id("monster_city")));
            var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.<Structure>direct(city), new BlockPos(2000, 100, 2000), 100, false);
            return found == null ? "none" : found.getFirst().toString();
        });
        System.out.println("MINEWORLD RANDOM CITY " + far);
        check(!far.equals("none"), "random cities generate");
        world.close();
    }


    private static void checkTrades(ClientGameTestContext context, TestServerContext server,
                                    net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection connection, String worldId, String in) {
        // A piglin with a trading profession sells its list; buying the first offer pays with gold.
        String piglin = server.computeOnServer(s -> {
            ServerLevel level = level(s, worldId);
            var player = s.getPlayerList().getPlayers().get(0);
            CityPiglin seller = level.getEntitiesOfClass(CityPiglin.class, new AABB(C).inflate(70, 40, 70), p -> p.seller() != null).getFirst();
            player.teleportTo(level, seller.getX(), seller.getY(), seller.getZ(), java.util.Set.of(), 0, 0, false);
            player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 64));
            seller.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
            if (!(player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu menu)) return "no menu";
            int expected = dev.alan.mineworld.city.CityTrades.of(seller.seller()).size();
            var offer = menu.getOffers().getFirst();
            menu.setSelectionHint(0);
            menu.tryMoveItems(0);
            var bought = menu.quickMoveStack(player, 2);
            player.closeContainer();
            return "offers=" + menu.getOffers().size() + "/" + expected + " bought=" + !bought.isEmpty()
                + " result=" + net.minecraft.world.item.ItemStack.isSameItem(bought, offer.getResult()) + " unlimited=" + (offer.getMaxUses() == Integer.MAX_VALUE);
        });
        System.out.println("MINEWORLD TRADE PIGLIN " + piglin);
        check(piglin.matches("offers=(\\d+)/\\1 bought=true result=true unlimited=true"), "piglin trade: " + piglin);

        // The enderman: open its screen for real so the client draws the lock button.
        int ender = server.computeOnServer(s -> {
            ServerLevel level = level(s, worldId);
            var player = s.getPlayerList().getPlayers().get(0);
            var e = level.getEntitiesOfClass(dev.alan.mineworld.city.CityEnderman.class, new AABB(C).inflate(70, 40, 70), x -> true).getFirst();
            player.teleportTo(level, e.getX() + 1, e.getY(), e.getZ(), java.util.Set.of(), 0, 0, false);
            e.merchant().interact(player, e.seller(), e.lockable());
            return e.getId();
        });
        connection.waitForClientboundPackets();
        context.waitTicks(10);
        String offers = server.computeOnServer(s -> {
            var menu = (net.minecraft.world.inventory.MerchantMenu) s.getPlayerList().getPlayers().get(0).containerMenu;
            int enchanted = 0, cursed = 0;
            for (var o : menu.getOffers()) {
                var result = o.getResult();
                if (net.minecraft.world.item.enchantment.EnchantmentHelper.hasAnyEnchantments(result)) enchanted++;
                var all = result.getOrDefault(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS,
                    result.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY));
                for (var en : all.keySet()) if (en.is(net.minecraft.tags.EnchantmentTags.CURSE)) cursed++;
            }
            return "offers=" + menu.getOffers().size() + " enchanted=" + enchanted + " cursed=" + cursed + " xp=" + menu.getTraderXp();
        });
        System.out.println("MINEWORLD TRADE ENDER " + offers);
        check(offers.equals("offers=10 enchanted=10 cursed=0 xp=1"), "enderman offers: " + offers);

        // Lock offer 3 with the client button path (menu button click), as the lock button does.
        net.minecraft.world.item.ItemStack picked = server.computeOnServer(s ->
            ((net.minecraft.world.inventory.MerchantMenu) s.getPlayerList().getPlayers().get(0).containerMenu).getOffers().get(3).getResult().copy());
        context.runOnClient(mc -> mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, dev.alan.mineworld.city.CityMerchant.LOCK_BUTTON + 3));
        connection.waitForClientboundPackets();
        context.waitTicks(10);
        context.takeScreenshot("city-06-enderman-trades");
        String locked = server.computeOnServer(s -> {
            var player = s.getPlayerList().getPlayers().get(0);
            var locks = player.getAttachedOrCreate(dev.alan.mineworld.city.CityMerchant.LOCKS);
            var first = ((net.minecraft.world.inventory.MerchantMenu) player.containerMenu).getOffers().getFirst().getResult();
            return "locks=" + locks.size() + " first=" + net.minecraft.world.item.ItemStack.matches(first, picked);
        });
        check(locked.equals("locks=1 first=true"), "locked offer moves to the top: " + locked);
        boolean clientSees = context.computeOnClient(mc -> mc.player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu m && m.getTraderXp() == 2);
        check(clientSees, "client knows one offer is locked");

        // Close and reopen: the locked offer is still there, unchanged; then unlock it.
        String reopened = server.computeOnServer(s -> {
            ServerLevel level = level(s, worldId);
            var player = s.getPlayerList().getPlayers().get(0);
            player.closeContainer();
            var e = (dev.alan.mineworld.city.CityEnderman) level.getEntity(ender);
            e.merchant().interact(player, e.seller(), e.lockable());
            var menu = (net.minecraft.world.inventory.MerchantMenu) player.containerMenu;
            boolean same = net.minecraft.world.item.ItemStack.matches(menu.getOffers().getFirst().getResult(), picked);
            e.merchant().toggleLock(player, 0);
            int after = player.getAttachedOrCreate(dev.alan.mineworld.city.CityMerchant.LOCKS).size();
            player.closeContainer();
            return "same=" + same + " offers=" + menu.getOffers().size() + " afterUnlock=" + after;
        });
        System.out.println("MINEWORLD TRADE LOCK " + reopened);
        check(reopened.equals("same=true offers=11 afterUnlock=0"), "lock persists across visits: " + reopened);
    }

    private static ServerLevel level(net.minecraft.server.MinecraftServer s, String worldId) {
        return s.getLevel(DynamicLevels.key(worldId));
    }

    private static BlockPos findBed(ServerLevel level) {
        for (int x = -46; x <= -11; x++) for (int z = 10; z <= 26; z++) {
            BlockPos pos = C.offset(x, 1, z);
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) return pos;
        }
        return null;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
