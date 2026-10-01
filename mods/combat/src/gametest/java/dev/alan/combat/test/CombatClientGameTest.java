package dev.alan.combat.test;

import dev.alan.combat.CombatMod;
import dev.alan.combat.TrinketMenu;
import dev.alan.combat.Trinkets;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

/** Wears the three trinkets through the real menu and checks what each one does, plus the bag rule and death. */
public final class CombatClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
        TestServerContext server = world.getServer();
        world.getConnection().waitForChunksRender();
        for (String command : new String[] {
            "gamerule spawn_mobs false", "gamerule advance_time false", "time set noon", "weather clear",
            "gamemode survival", "fill -8 119 -8 8 119 8 minecraft:stone", "fill -8 120 -8 8 200 8 minecraft:air",
            "tp @p 0 120 0 0 15",
        }) server.runCommand(command);
        context.waitTicks(10);

        // 1. Menu: trinkets go in, the duplicate and non-trinkets are refused, and the attachment follows.
        check(server.computeOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new TrinketMenu(id, inv), Component.literal("Trinkets")));
            var menu = player.containerMenu;
            var feather = new ItemStack(CombatMod.FEATHER_CHARM);
            boolean ok = menu.getSlot(0).mayPlace(feather) && !menu.getSlot(0).mayPlace(new ItemStack(net.minecraft.world.item.Items.STICK));
            menu.getSlot(0).set(feather);
            ok &= !menu.getSlot(1).mayPlace(new ItemStack(CombatMod.FEATHER_CHARM));
            ok &= CombatMod.id("feather_charm").toString().equals(player.getAttached(CombatMod.SLOTS).at(0));
            return ok;
        }), "feather charm accepted, duplicate and stick refused, attachment saved");
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(5);
        context.takeScreenshot("01-trinket-screen");
        server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).closeContainer());

        // 2. Feather charm: a 50 block fall is harmless with it and hurts without it.
        server.runCommand("tp @p 0 170 0");
        context.waitTicks(80);
        check(server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getHealth() == 20.0f), "feather charm cancels fall damage");
        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            player.setAttached(CombatMod.SLOTS, dev.alan.combat.TrinketSlots.EMPTY);
        });
        server.runCommand("tp @p 0 140 0");
        context.waitTicks(60);
        check(server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getHealth() < 20.0f), "without it the fall hurts");
        server.runCommand("effect give @p minecraft:instant_health 1 5");
        context.waitTicks(5);

        // 3. Night vision and gills charms grant their effects while worn and take them away when removed.
        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new TrinketMenu(id, inv), Component.literal("Trinkets")));
            player.containerMenu.getSlot(1).set(new ItemStack(CombatMod.NIGHT_VISION_CHARM));
            player.containerMenu.getSlot(2).set(new ItemStack(CombatMod.GILLS_CHARM));
            player.closeContainer();
        });
        context.waitTicks(40);
        check(server.computeOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            return player.hasEffect(MobEffects.NIGHT_VISION) && player.hasEffect(MobEffects.WATER_BREATHING);
        }), "night vision and water breathing granted");
        context.takeScreenshot("02-effects");
        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new TrinketMenu(id, inv), Component.literal("Trinkets")));
            player.containerMenu.getSlot(1).set(ItemStack.EMPTY);
            player.closeContainer();
        });
        context.waitTicks(40);
        check(server.computeOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            return !player.hasEffect(MobEffects.NIGHT_VISION) && player.hasEffect(MobEffects.WATER_BREATHING);
        }), "removing the night vision charm removes only its effect");

        // 4. Bags are counted wherever they are in the inventory.
        check(server.computeOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            player.getInventory().add(new ItemStack(CombatMod.TRINKET_BAG, 2));
            return Trinkets.bags(player) == 2 && Trinkets.slotCount(player) == Math.min(5, Trinkets.total());
        }), "two bags counted");

        // 4b. Anvil: ore into armor costs only ore, adds the stat, and respects the caps.
        check(server.computeOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            var pos = new net.minecraft.core.BlockPos(0, 120, 0).east(3);
            s.overworld().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.ANVIL.defaultBlockState());
            player.setExperienceLevels(0);
            var menu = new net.minecraft.world.inventory.AnvilMenu(77, player.getInventory(),
                net.minecraft.world.inventory.ContainerLevelAccess.create(s.overworld(), pos));
            menu.getSlot(0).set(new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
            menu.getSlot(1).set(new ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
            ItemStack result = menu.getSlot(2).getItem();
            boolean ok = !result.isEmpty() && result.getOrDefault(CombatMod.UPGRADES, dev.alan.combat.Upgrades.EMPTY).level("diamond") == 4;
            double health = 0;
            for (var entry : result.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers())
                if (entry.attribute().equals(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)) health += entry.modifier().amount();
            ok &= health == 4.0;
            // The cap of 4 per ore means only 4 of the 7 diamonds are used, and no experience is needed to take it.
            menu.clicked(2, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
            ok &= menu.getCarried().is(net.minecraft.world.item.Items.IRON_CHESTPLATE);
            ok &= menu.getSlot(1).getItem().getCount() == 3;
            ok &= menu.getSlot(0).getItem().isEmpty();
            // Worn (checked after a few ticks below), the extra health is real.
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, menu.getCarried().copy());
            // A second round with the same ore adds nothing (cap reached): the vanilla path leaves no result.
            menu.getSlot(0).set(menu.getCarried().copy());
            menu.setCarried(ItemStack.EMPTY);
            ok &= menu.getSlot(2).getItem().isEmpty();
            player.getInventory().add(menu.getSlot(0).getItem().copy());
            menu.getSlot(0).set(ItemStack.EMPTY);
            return ok;
        }), "anvil upgrade: 4 levels from 7 diamonds, 3 left, no xp, +4 health, capped afterwards");
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(5);
        check(server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0)
            .getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH) == 24.0), "upgraded chestplate gives +4 max health when worn");
        server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, ItemStack.EMPTY));
        context.waitTicks(5);
        check(server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0)
            .getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH) == 20.0), "and it is gone again when taken off");

        // 4c. Bow upgrades at the anvil: one item per level, capped per upgrade.
        check(server.computeOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            var pos = new net.minecraft.core.BlockPos(0, 120, 0).east(3);
            var menu = new net.minecraft.world.inventory.AnvilMenu(78, player.getInventory(),
                net.minecraft.world.inventory.ContainerLevelAccess.create(s.overworld(), pos));
            menu.getSlot(0).set(new ItemStack(net.minecraft.world.item.Items.BOW));
            menu.getSlot(1).set(new ItemStack(net.minecraft.world.item.Items.BLAZE_POWDER, 9));
            var result = menu.getSlot(2).getItem();
            boolean ok = result.getOrDefault(CombatMod.BOW_UPGRADES, dev.alan.combat.Upgrades.EMPTY).level("burn") == 3;
            menu.clicked(2, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
            ok &= menu.getSlot(1).getItem().getCount() == 6;
            // Armor and bow do not mix: ender pearl on armor is nothing, redstone on a bow is draw speed.
            menu.setCarried(ItemStack.EMPTY);
            menu.getSlot(1).set(ItemStack.EMPTY);
            menu.getSlot(0).set(new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
            menu.getSlot(1).set(new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, 4));
            ok &= menu.getSlot(2).getItem().isEmpty();
            return ok;
        }), "bow anvil upgrade: 3 blaze powder used out of 9, ender pearl does nothing to armor");

        // 4d. An upgraded bow: draws faster, arrows fly straight and on fire, and the hit slows, burns and explodes.
        server.runCommand("tp @p 0 120 0 0 10");
        server.runCommand("summon iron_golem 0 120 8 {NoAI:1b}");
        server.runCommand("summon cow 1 120 8 {NoAI:1b}");
        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            ItemStack bow = new ItemStack(net.minecraft.world.item.Items.BOW);
            bow.set(CombatMod.BOW_UPGRADES, new dev.alan.combat.Upgrades(java.util.Map.of(
                "draw", 3, "no_drop", 1, "burn", 1, "slow", 2, "explode", 1)));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bow);
            player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.ARROW, 16));
        });
        context.waitTicks(5);
        context.getInput().holdKey(options -> options.keyUse);
        context.waitTicks(20);
        check(server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getTicksUsingItem() >= 30),
            "draw speed 3 gets at least 30 ticks of charge in 20 ticks");
        context.getInput().releaseKey(options -> options.keyUse);
        context.waitTicks(2);
        check(server.computeOnServer(s -> {
            for (var arrow : s.overworld().getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class,
                new net.minecraft.world.phys.AABB(-3, 118, -3, 3, 124, 14)))
                if (arrow.isNoGravity() && arrow.isOnFire() && arrow.hasAttached(CombatMod.ARROW_MODS)) return true;
            return false;
        }), "fired arrow is gravity-free, burning and carries the upgrades");
        context.waitTicks(40);
        check(server.computeOnServer(s -> {
            var golem = s.overworld().getEntitiesOfClass(net.minecraft.world.entity.animal.golem.IronGolem.class,
                new net.minecraft.world.phys.AABB(-3, 118, 5, 3, 124, 11));
            var cows = s.overworld().getEntitiesOfClass(net.minecraft.world.entity.animal.cow.Cow.class,
                new net.minecraft.world.phys.AABB(-1, 118, 5, 5, 124, 11));
            boolean slowed = !golem.isEmpty() && golem.get(0).hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
            boolean burning = !golem.isEmpty() && golem.get(0).isOnFire();
            boolean blast = cows.isEmpty() || cows.get(0).getHealth() < cows.get(0).getMaxHealth();
            System.out.println("[combat-test] hit: slowed=" + slowed + " burning=" + burning + " blast=" + blast);
            return slowed && burning && blast;
        }), "hit slows, burns and the explosion hurts the cow beside the target");
        server.runCommand("kill @e[type=!player]");

        // 4e. Shapeshift skills: gear scales the skeleton's R-key arrow, the skill charm shortens its cooldown,
        // and the arrow carries the upgrades of the bow in hand.
        server.runCommand("execute as @p run shapeshift unlockall");
        server.runCommand("execute as @p run shapeshift into minecraft:skeleton");
        server.runCommand("tp @p 0 120 0 0 -60");
        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setAttached(CombatMod.SLOTS, dev.alan.combat.TrinketSlots.EMPTY);
        });
        context.waitTicks(10);
        double plain = fireSkillArrow(context, server);
        check(plain > 2.4 && plain < 3.6, "unscaled skeleton arrow speed is about 3, got " + plain);

        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            for (var slot : new net.minecraft.world.entity.EquipmentSlot[] {
                net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST}) {
                ItemStack piece = new ItemStack(slot == net.minecraft.world.entity.EquipmentSlot.HEAD
                    ? net.minecraft.world.item.Items.IRON_HELMET : net.minecraft.world.item.Items.IRON_CHESTPLATE);
                piece.set(CombatMod.UPGRADES, new dev.alan.combat.Upgrades(java.util.Map.of("emerald", 4)));
                player.setItemSlot(slot, piece);
            }
        });
        context.waitTicks(30);
        double scaled = fireSkillArrow(context, server);
        check(scaled > 3.9 && scaled < 4.6, "8 emerald levels make the arrow about 1.4x as fast, got " + scaled);

        // Cooldown: the arrow ability has 10 ticks. Two presses 8 ticks apart fire twice with the skill charm only.
        server.runCommand("kill @e[type=minecraft:arrow]");
        server.runCommand("kill @e[type=minecraft:arrow]");
        context.waitTicks(30);
        int without = pressTwice(context, server, 8);
        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            player.setAttached(CombatMod.SLOTS, dev.alan.combat.TrinketSlots.EMPTY.with(0, "combat:skill_charm"));
        });
        context.waitTicks(30);
        server.runCommand("kill @e[type=minecraft:arrow]");
        int with = pressTwice(context, server, 8);
        check(without == 1 && with == 2, "skill charm shortens the cooldown: arrows without=" + without + " with=" + with);

        // The arrow takes on the upgrades of the bow in hand.
        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            ItemStack bow = new ItemStack(net.minecraft.world.item.Items.BOW);
            bow.set(CombatMod.BOW_UPGRADES, new dev.alan.combat.Upgrades(java.util.Map.of("no_drop", 1)));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bow);
        });
        context.waitTicks(30);
        server.runCommand("kill @e[type=minecraft:arrow]");
        pressSkill(context);
        context.waitTicks(2);
        check(server.computeOnServer(s -> {
            for (var arrow : s.overworld().getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class,
                new net.minecraft.world.phys.AABB(-200, 100, -200, 200, 300, 200)))
                if (arrow.isNoGravity() && arrow.hasAttached(CombatMod.ARROW_MODS)) return true;
            return false;
        }), "skill arrow carries the upgrades of the bow in hand");

        // A form's own infinite night vision is not removed by taking a night vision charm off.
        server.runCommand("execute as @p run shapeshift into minecraft:cat");
        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setAttached(CombatMod.SLOTS, dev.alan.combat.TrinketSlots.EMPTY.with(0, "combat:night_vision_charm"));
        });
        context.waitTicks(40);
        server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).setAttached(CombatMod.SLOTS, dev.alan.combat.TrinketSlots.EMPTY));
        context.waitTicks(40);
        check(server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).hasEffect(MobEffects.NIGHT_VISION)),
            "cat form keeps its night vision after the charm is removed");
        server.runCommand("execute as @p run shapeshift human");
        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
            player.setAttached(CombatMod.SLOTS, dev.alan.combat.TrinketSlots.EMPTY.with(2, "combat:gills_charm"));
        });
        context.waitTicks(5);

        // 4f. The other trinkets. Enough bags for every slot, then each trinket on its own.
        server.runCommand("tp @p 0 120 0 0 0");
        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            player.getInventory().add(new ItemStack(CombatMod.TRINKET_BAG, 12));
            player.setHealth(player.getMaxHealth());
        });
        check(server.computeOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            return Trinkets.total() == 12 && Trinkets.slotCount(player) == 12;
        }), "twelve trinkets exist and fourteen bags open every slot");

        // The screen with every trinket worn.
        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            var all = dev.alan.combat.TrinketSlots.EMPTY;
            for (int i = 0; i < Trinkets.total(); i++) all = all.with(i, Trinkets.idOf(new ItemStack(Trinkets.all().get(i).item())));
            player.setAttached(CombatMod.SLOTS, all);
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new TrinketMenu(id, inv), Component.literal("Trinkets")));
        });
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(5);
        context.takeScreenshot("03-all-trinkets");
        server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).closeContainer());
        wear(server);

        // Fire ring: fire resistance. Speed buckle and spring insole: attribute boosts that come off again.
        wear(server, "fire_ring", "speed_buckle", "spring_insole");
        context.waitTicks(5);
        check(server.computeOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            return player.hasEffect(MobEffects.FIRE_RESISTANCE)
                && Math.abs(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) - 0.1 * 1.15) < 1e-6
                && Math.abs(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH) - 0.42 * 1.25) < 1e-6;
        }), "fire resistance, +15% speed and +25% jump while worn");
        wear(server);
        context.waitTicks(5);
        check(server.computeOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            return !player.hasEffect(MobEffects.FIRE_RESISTANCE)
                && Math.abs(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) - 0.1) < 1e-6
                && Math.abs(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH) - 0.42) < 1e-6;
        }), "all three are gone again when taken off");

        // Regeneration charm: 1 health per 3 seconds.
        server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).setHealth(10f));
        wear(server, "regen_charm");
        context.waitTicks(130);
        check(server.computeOnServer(s -> {
            float health = s.getPlayerList().getPlayers().get(0).getHealth();
            return health >= 12f && health <= 14f;
        }), "regeneration charm heals about 1 every 3 seconds");
        wear(server);

        // Magnet: a loose item four blocks away (clear of the anvil used earlier) flies into the inventory.
        wear(server, "magnet");
        server.runCommand("summon item 0 120 -4 {Item:{id:\"minecraft:stick\",count:1},PickupDelay:0s}");
        context.waitTicks(40);
        check(server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getInventory().countItem(net.minecraft.world.item.Items.STICK) == 1),
            "magnet pulls a dropped stick into the inventory");
        wear(server);
        server.runCommand("summon item 0 120 -4 {Item:{id:\"minecraft:stick\",count:1},PickupDelay:0s}");
        context.waitTicks(40);
        check(server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getInventory().countItem(net.minecraft.world.item.Items.STICK) == 1),
            "without the magnet the stick stays where it is");
        server.runCommand("kill @e[type=minecraft:item]");

        // Blast ward: the same explosion hurts half as much.
        float plainBlast = blast(context, server, false);
        float wardedBlast = blast(context, server, true);
        check(Math.abs(plainBlast - 10f) < 0.01f && Math.abs(wardedBlast - 5f) < 0.01f,
            "blast ward halves explosion damage: " + plainBlast + " vs " + wardedBlast);

        // Thorns ring: a melee hit on the wearer hurts the attacker.
        server.runCommand("summon iron_golem 0 120 6 {NoAI:1b}");
        context.waitTicks(5);
        wear(server);
        float golemWithout = server.computeOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            var golem = s.overworld().getEntitiesOfClass(net.minecraft.world.entity.animal.golem.IronGolem.class,
                new net.minecraft.world.phys.AABB(-5, 118, 0, 5, 124, 12)).get(0);
            player.hurtServer(s.overworld(), s.overworld().damageSources().mobAttack(golem), 4f);
            player.setHealth(player.getMaxHealth());
            return golem.getHealth();
        });
        wear(server, "thorns_ring");
        context.waitTicks(25);
        float golemWith = server.computeOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            var golem = s.overworld().getEntitiesOfClass(net.minecraft.world.entity.animal.golem.IronGolem.class,
                new net.minecraft.world.phys.AABB(-5, 118, 0, 5, 124, 12)).get(0);
            player.hurtServer(s.overworld(), s.overworld().damageSources().mobAttack(golem), 4f);
            player.setHealth(player.getMaxHealth());
            return golem.getHealth();
        });
        check(golemWithout == 100f && golemWith < 100f, "thorns ring: golem health without=" + golemWithout + " with=" + golemWith);
        wear(server);
        server.runCommand("kill @e[type=!player]");

        // Hunter charm: killing 40 cows drops clearly more beef with it than without.
        int plainBeef = cowKills(context, server, false);
        int hunterBeef = cowKills(context, server, true);
        check(hunterBeef > plainBeef * 1.2, "hunter charm drops more beef: " + plainBeef + " vs " + hunterBeef);
        wear(server);
        server.runCommand("kill @e[type=!player]");
        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            player.getInventory().clearContent();
            player.getInventory().add(new ItemStack(CombatMod.TRINKET_BAG, 2));
            player.setAttached(CombatMod.SLOTS, dev.alan.combat.TrinketSlots.EMPTY.with(2, "combat:gills_charm"));
        });
        context.waitTicks(5);

        // 5. Trinkets survive death.
        server.runCommand("kill @p");
        context.waitTicks(10);
        context.clickScreenButton("deathScreen.respawn");
        context.waitTicks(20);
        check(server.computeOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            return player.isAlive() && CombatMod.id("gills_charm").toString().equals(player.getAttached(CombatMod.SLOTS).at(2));
        }), "trinkets kept after death");
        }
    }

    private static net.minecraft.client.KeyMapping skillKey(ClientGameTestContext context) {
        return context.computeOnClient(mc -> net.minecraft.client.KeyMapping.get("key.shapeshift.ability"));
    }

    private static void pressSkill(ClientGameTestContext context) {
        context.getInput().pressKey(skillKey(context));
    }

    /** Presses R once and returns the speed of the arrow it launched. */
    private static double fireSkillArrow(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("kill @e[type=minecraft:arrow]");
        pressSkill(context);
        context.waitTicks(1);
        return server.computeOnServer(s -> {
            double best = 0;
            for (var arrow : s.overworld().getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class,
                new net.minecraft.world.phys.AABB(-200, 100, -200, 200, 300, 200)))
                best = Math.max(best, arrow.getDeltaMovement().length());
            return best;
        });
    }

    /** Presses R, waits 8 ticks, presses R again, and counts the arrows in the air. */
    private static int pressTwice(ClientGameTestContext context, TestServerContext server, int gap) {
        pressSkill(context);
        context.waitTicks(gap);
        pressSkill(context);
        context.waitTicks(2);
        return server.computeOnServer(s -> s.overworld().getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.AbstractArrow.class,
            new net.minecraft.world.phys.AABB(-200, 100, -200, 200, 300, 200)).size());
    }

    /** Puts exactly these trinkets (by short name) in the first slots and refreshes the player. */
    private static void wear(TestServerContext server, String... names) {
        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            var slots = dev.alan.combat.TrinketSlots.EMPTY;
            for (int i = 0; i < names.length; i++) slots = slots.with(i, "combat:" + names[i]);
            player.setAttached(CombatMod.SLOTS, slots);
            Trinkets.refresh(player);
        });
    }

    /** Damage dealt to the player by a 10 point explosion, with or without the blast ward. */
    private static float blast(ClientGameTestContext context, TestServerContext server, boolean ward) {
        if (ward) wear(server, "blast_ward"); else wear(server);
        context.waitTicks(25);   // the previous hit's invulnerability has to run out
        return server.computeOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            player.setHealth(player.getMaxHealth());
            float before = player.getHealth();
            player.hurtServer(s.overworld(), s.overworld().damageSources().explosion(null, null), 10f);
            float lost = before - player.getHealth();
            player.setHealth(player.getMaxHealth());
            return lost;
        });
    }

    /** Beef dropped by killing 40 cows as the player. */
    private static int cowKills(ClientGameTestContext context, TestServerContext server, boolean hunter) {
        if (hunter) wear(server, "hunter_charm"); else wear(server);
        server.runCommand("kill @e[type=minecraft:item]");
        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            for (int i = 0; i < 40; i++) {
                var cow = net.minecraft.world.entity.EntityTypes.COW.create(s.overworld(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                cow.setPos(30 + (i % 8) * 2, 120, (i / 8) * 2);
                cow.setNoAi(true);
                s.overworld().addFreshEntity(cow);
                cow.hurtServer(s.overworld(), s.overworld().damageSources().playerAttack(player), 1000f);
            }
        });
        context.waitTicks(5);
        return server.computeOnServer(s -> {
            int beef = 0;
            for (var item : s.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(20, 100, -10, 60, 140, 30)))
                if (item.getItem().is(net.minecraft.world.item.Items.BEEF)) beef += item.getItem().getCount();
            return beef;
        });
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError("FAILED: " + what);
        System.out.println("[combat-test] ok: " + what);
    }
}
