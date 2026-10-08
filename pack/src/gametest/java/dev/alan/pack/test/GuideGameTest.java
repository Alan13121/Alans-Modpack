package dev.alan.pack.test;

import dev.alchemy.AlchemyMod;
import dev.alchemy.BagMenu;
import dev.alan.mineworld.CauldronRecipe;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;

/** The quest book: every advancement loads, rewards pay out, and each hook in the mods hands out its entry. */
public final class GuideGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            TestServerContext server = world.getServer();
            world.getConnection().waitForChunksRender();
            for (String command : new String[] {
                "gamerule spawn_mobs false", "gamerule advance_time false", "time set noon", "weather clear",
                "fill -10 119 -10 10 119 10 minecraft:stone", "fill -10 120 -10 10 130 10 minecraft:air", "tp @p 0 120 0",
            }) server.runCommand(command);
            context.waitTicks(10);

            // 1. Everything the book promises loads: 30 advancements, six tabs, and every reward table exists.
            check(server.computeOnServer(s -> {
                var all = s.getAdvancements().getAllAdvancements().stream().filter(h -> h.id().getNamespace().equals("guide")).toList();
                long roots = all.stream().filter(h -> h.value().parent().isEmpty()).count();
                int missingTables = 0;
                for (var holder : all)
                    for (var table : holder.value().rewards().loot()) {
                        try {
                            if (table.value() == LootTable.EMPTY) missingTables++;
                        } catch (RuntimeException unbound) {
                            missingTables++;
                        }
                    }
                System.out.println("[guide-test] advancements=" + all.size() + " roots=" + roots + " missingTables=" + missingTables);
                return all.size() == 35 && roots == 6 && missingTables == 0;
            }), "35 advancements (30 tasks + 5 chapter headers) in 6 chapters, and every reward table exists");

            // 1b. The handbook: one on first join, never a second, /guidebook only when none is carried, and no re-issue on rejoin.
            check(books(server) == 1, "the first join handed out exactly one handbook");
            server.runCommand("execute as @p run guidebook");
            check(books(server) == 1, "/guidebook with a book in the inventory adds nothing");
            server.runCommand("clear @p guide:handbook");
            server.runOnServer(s -> dev.alan.guide.Handbook.onJoin(player(s)));
            check(books(server) == 0, "a later join does not hand out another book");
            server.runCommand("execute as @p run guidebook");
            check(books(server) == 1, "/guidebook replaces a lost book");

            // 2. Chapter 1 with vanilla triggers: pickaxe, depth, the eight ores and the leather/chest reward.
            server.runCommand("give @p minecraft:iron_pickaxe");
            context.waitTicks(3);
            check(done(server, "ch1/iron_pickaxe"), "the iron pickaxe is noticed");
            // A pocket of air, or the player suffocates in the rock down there and drops everything.
            server.runCommand("fill -2 -12 -2 2 -12 2 minecraft:stone");
            server.runCommand("fill -2 -11 -2 2 -7 2 minecraft:air");
            server.runCommand("tp @p 0 -10 0");
            context.waitTicks(40);   // the location trigger only looks once a second
            check(done(server, "ch1/deep") && count(server, Items.TORCH) == 16, "digging below Y 0 is noticed and pays 16 torches");
            server.runCommand("tp @p 0 120 0");
            for (String ore : new String[] {"coal", "raw_copper", "iron_ingot", "raw_gold", "redstone", "lapis_lazuli", "diamond", "emerald"})
                server.runCommand("give @p minecraft:" + ore);
            context.waitTicks(10);
            check(count(server, Items.BREAD) == 8, "the welcome entry paid 8 bread");
            check(done(server, "ch1/eight_ores") && count(server, Items.LEATHER) == 5 && count(server, Items.CHEST) == 1,
                "the eight ores (raw copper and gold count) finish the chapter step and pay leather and a chest");

            // 3. Chapter 3's harvest pays an efficiency II pickaxe.
            server.runCommand("give @p minecraft:diamond 16");
            server.runCommand("give @p minecraft:emerald 16");
            server.runCommand("give @p minecraft:redstone 64");
            context.waitTicks(3);
            check(done(server, "ch3/harvest") && server.computeOnServer(s -> {
                var enchantment = s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);
                var player = player(s);
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.is(Items.IRON_PICKAXE) && stack.getEnchantments().getLevel(enchantment) == 2) return true;
                }
                return false;
            }), "the harvest step pays an efficiency II pickaxe");

            // 4. Obtaining and placing mod items.
            server.runCommand("give @p alchemy_backpack:backpack");
            server.runCommand("give @p logistics:cell_3");
            server.runCommand("give @p combat:magnet");
            server.runCommand("give @p combat:master_charm");
            context.waitTicks(3);
            check(done(server, "ch2/backpack") && done(server, "ch4/cell") && done(server, "ch6/trinket") && done(server, "ch6/master_charm"),
                "backpack, any storage cell, any trinket and the master charm are noticed");
            // The game fires this trigger whenever a block item is placed; do the same for blocks that need two or three groups.
            place(server, "logistics:controller");
            check(done(server, "ch4/controller"), "placing the controller is noticed");
            place(server, "logistics:storage_interface");
            check(!done(server, "ch4/interfaces"), "the storage interface alone does not finish the interfaces step");
            place(server, "logistics:conduit");
            check(done(server, "ch4/interfaces"), "storage interface and conduit together do");
            place(server, "logistics:coal_generator");
            check(!done(server, "ch4/power_and_teleport"), "a generator alone is not enough");
            place(server, "logistics:teleporter");
            check(done(server, "ch4/power_and_teleport"), "a generator plus the teleporter finish chapter four");
            place(server, "logistics:crafting_terminal");
            place(server, "logistics:autocrafter");
            place(server, "logistics:terminal");
            check(done(server, "ch4/automation") && done(server, "ch4/terminal"), "crafting terminal with auto crafter, and the terminal");

            // 5. The two vanilla bosses, one at a time.
            killed(server, true);
            check(!done(server, "ch6/vanilla_bosses"), "the wither alone does not finish the boss step");
            killed(server, false);
            check(done(server, "ch6/vanilla_bosses"), "wither and ender dragon do");

            // 6. Shapeshift: unlocking, transforming and using a skill.
            server.runCommand("execute as @p run shapeshift unlock minecraft:zombie");
            check(done(server, "ch5/first_form") && !done(server, "ch5/ten_forms"), "the first unlocked form is noticed, ten are not yet");
            server.runCommand("execute as @p run shapeshift unlockall");
            check(done(server, "ch5/ten_forms") && done(server, "ch5/thirty_forms") && done(server, "ch5/all_forms"), "10, 30 and all forms are noticed");
            server.runCommand("execute as @p run shapeshift into minecraft:skeleton");
            check(done(server, "ch5/first_transform"), "the first transformation is noticed");
            context.waitTicks(5);
            net.minecraft.client.KeyMapping skill = context.computeOnClient(mc -> net.minecraft.client.KeyMapping.get("key.shapeshift.ability"));
            context.getInput().pressKey(skill);
            context.waitTicks(3);
            check(done(server, "ch5/first_skill"), "the first skill is noticed");
            server.runCommand("execute as @p run shapeshift human");

            // 7. Anvil: one armor upgrade and one bow upgrade.
            check(server.computeOnServer(s -> {
                ServerPlayer player = player(s);
                BlockPos pos = new BlockPos(5, 120, 0);
                s.overworld().setBlockAndUpdate(pos, Blocks.ANVIL.defaultBlockState());
                var menu = new AnvilMenu(77, player.getInventory(), ContainerLevelAccess.create(s.overworld(), pos));
                menu.getSlot(0).set(new ItemStack(Items.IRON_CHESTPLATE));
                menu.getSlot(1).set(new ItemStack(Items.DIAMOND, 1));
                menu.clicked(2, 0, ContainerInput.PICKUP, player);
                menu.setCarried(ItemStack.EMPTY);
                menu.getSlot(0).set(new ItemStack(Items.BOW));
                menu.getSlot(1).set(new ItemStack(Items.BLAZE_POWDER, 1));
                menu.clicked(2, 0, ContainerInput.PICKUP, player);
                return true;
            }), "anvil upgrades ran");
            check(done(server, "ch6/armor_upgrade") && done(server, "ch6/bow_upgrade"), "armor and bow upgrades are noticed");

            // 8. Backpack: first deposit, twenty learned items, first redeem.
            check(server.computeOnServer(s -> {
                ServerPlayer player = player(s);
                player.getInventory().setItem(0, new ItemStack(AlchemyMod.BACKPACK));
                player.getInventory().setSelectedSlot(0);
                var menu = new BagMenu(78, player.getInventory(), 0);
                int deposited = 0;
                for (Item item : BuiltInRegistries.ITEM) {
                    if (menu.data().learned().size() >= 20 || deposited > 400) break;
                    deposited++;
                    menu.getSlot(0).set(new ItemStack(item));
                    menu.broadcastChanges();
                }
                boolean learned = menu.data().learned().size() >= 20;
                menu.clickMenuButton(player, 100);
                return learned;
            }), "twenty items were put into the backpack");
            check(done(server, "ch2/first_deposit") && done(server, "ch2/scholar") && done(server, "ch2/redeem"), "deposit, 20 learned and redeem are noticed");

            // 9. Mine world: the first and the second world cauldron.
            check(server.computeOnServer(s -> {
                ServerPlayer player = player(s);
                for (int index = 0; index < 2; index++) {
                    BlockPos pos = new BlockPos(-5 + index * 3, 120, 5);
                    s.overworld().setBlockAndUpdate(pos, Blocks.WATER_CAULDRON.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
                    for (Item ore : new Item[] {Items.COAL, Items.RAW_COPPER, Items.RAW_IRON, Items.RAW_GOLD, Items.REDSTONE, Items.LAPIS_LAZULI, Items.DIAMOND, Items.EMERALD}) {
                        ItemEntity entity = new ItemEntity(s.overworld(), pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, new ItemStack(ore));
                        entity.setThrower(player);
                        CauldronRecipe.tryAccept(s.overworld(), entity);
                    }
                }
                return true;
            }), "two world cauldrons were made");
            check(done(server, "ch3/cauldron") && done(server, "ch3/second_world"), "first and second world are noticed");

            // 10. Beating the Form King awards its entry and pays a form core (the unlockall above satisfied the altar).
            check(server.computeOnServer(s -> {
                ServerPlayer player = player(s);
                BlockPos altar = new BlockPos(8, 120, 8);
                s.overworld().setBlockAndUpdate(altar, dev.alan.combat.CombatMod.FORM_ALTAR.defaultBlockState());
                player.teleportTo(altar.getX() + 3.5, 120, altar.getZ() + 3.5);
                player.getInventory().add(new ItemStack(Items.NETHER_STAR));
                player.getInventory().add(new ItemStack(Items.DRAGON_BREATH));
                return dev.alan.combat.boss.FormKingFights.trySummon(s.overworld(), altar, player);
            }), "the altar accepts the offering in the full pack");
            context.waitTicks(5);
            check(server.computeOnServer(s -> {
                ServerPlayer player = player(s);
                var fight = dev.alan.combat.boss.FormKingFights.at(s.overworld(), new BlockPos(8, 120, 8));
                fight.setHp(10);
                var body = (net.minecraft.world.entity.Mob) s.overworld().getEntity(fight.bossId());
                body.hurtServer(s.overworld(), s.overworld().damageSources().playerAttack(player), 100f);
                return true;
            }), "the last hit lands");
            context.waitTicks(5);
            check(done(server, "ch6/form_king") && server.computeOnServer(s -> player(s).getInventory().countItem(dev.alan.combat.CombatMod.FORM_CORE) == 1),
                "beating the Form King awards its quest book entry");

            // 11. The Form King's entry exists and is a challenge that announces itself.
            check(server.computeOnServer(s -> {
                var holder = s.getAdvancements().get(Identifier.fromNamespaceAndPath("guide", "ch6/form_king"));
                return holder != null && holder.value().display().isPresent() && holder.value().display().get().announceToChat();
            }), "the Form King entry announces itself");
        }
    }

    private static ServerPlayer player(MinecraftServer s) { return s.getPlayerList().getPlayers().get(0); }

    private static boolean done(TestServerContext server, String path) {
        return server.computeOnServer(s -> {
            var holder = s.getAdvancements().get(Identifier.fromNamespaceAndPath("guide", path));
            return holder != null && player(s).getAdvancements().getOrStartProgress(holder).isDone();
        });
    }

    private static int books(TestServerContext server) {
        return server.computeOnServer(s -> {
            int n = 0;
            var inv = player(s).getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) if (dev.alan.guide.Handbook.isHandbook(inv.getItem(i))) n += inv.getItem(i).getCount();
            return n;
        });
    }

    private static int count(TestServerContext server, Item item) {
        return server.computeOnServer(s -> player(s).getInventory().countItem(item));
    }

    /** Puts the block in the world and fires the same trigger a placed block item fires. */
    private static void place(TestServerContext server, String id) {
        server.runOnServer(s -> {
            var block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
            BlockPos pos = new BlockPos(-8 + Math.floorMod(id.hashCode(), 16), 120, -8);
            s.overworld().setBlockAndUpdate(pos, block.defaultBlockState());
            CriteriaTriggers.PLACED_BLOCK.trigger(player(s), pos, new ItemStack(block));
        });
    }

    private static void killed(TestServerContext server, boolean wither) {
        server.runOnServer(s -> {
            var victim = (wither ? EntityTypes.WITHER : EntityTypes.ENDER_DRAGON).create(s.overworld(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            CriteriaTriggers.PLAYER_KILLED_ENTITY.trigger(player(s), victim, s.overworld().damageSources().playerAttack(player(s)));
        });
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError("FAILED: " + what);
        System.out.println("[guide-test] ok: " + what);
    }
}
