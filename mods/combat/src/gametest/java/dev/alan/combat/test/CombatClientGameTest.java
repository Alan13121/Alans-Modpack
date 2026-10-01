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

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError("FAILED: " + what);
        System.out.println("[combat-test] ok: " + what);
    }
}
