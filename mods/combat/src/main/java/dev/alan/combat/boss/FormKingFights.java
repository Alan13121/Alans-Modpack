package dev.alan.combat.boss;

import dev.alan.combat.CombatMod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** All Form King fights that are running, who may start one, and the hooks that keep the boss's body in line. */
public final class FormKingFights {
    /** How the fight asks the shapeshift mod about the form collection; unset when that mod is not installed. */
    public interface Collection {
        int total();
        int unlocked(ServerPlayer player);
        /** Every collectable form, rare ones included. */
        int allTotal();
        int allUnlocked(ServerPlayer player);
    }

    /** Altar cooldown after a win, in ticks (30 minutes). */
    public static final long COOLDOWN_TICKS = 30L * 60 * 20;

    private static Collection collection;
    private static final Map<String, FormKingFight> FIGHTS = new HashMap<>();
    private static final Map<String, Long> COOLDOWN_UNTIL = new HashMap<>();

    private FormKingFights() {}

    public static void setCollection(Collection source) { collection = source; }

    static String key(ServerLevel level, BlockPos pos) { return FormKingFight.dimensionOf(level) + "@" + pos.asLong(); }

    public static FormKingFight at(ServerLevel level, BlockPos pos) { return FIGHTS.get(key(level, pos)); }
    public static List<FormKingFight> running() { return List.copyOf(FIGHTS.values()); }
    public static long cooldownUntil(ServerLevel level, BlockPos pos) { return COOLDOWN_UNTIL.getOrDefault(key(level, pos), 0L); }
    /** For tests and commands. */
    public static void clearCooldown(ServerLevel level, BlockPos pos) { COOLDOWN_UNTIL.remove(key(level, pos)); }

    /** Altar used: starts a fight when every condition holds, otherwise tells the player what is missing. */
    public static boolean trySummon(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (collection == null) {
            player.sendOverlayMessage(Component.translatable("combat.boss.dormant"));
            return false;
        }
        String key = key(level, pos);
        long now = level.getGameTime();
        if (FIGHTS.containsKey(key) || nearAnyFight(level, pos)) {
            player.sendOverlayMessage(Component.translatable("combat.boss.busy"));
            return false;
        }
        long cooldown = COOLDOWN_UNTIL.getOrDefault(key, 0L);
        if (now < cooldown) {
            player.sendOverlayMessage(Component.translatable("combat.boss.cooldown", (cooldown - now + 1199) / 1200));
            return false;
        }
        var inventory = player.getInventory();
        // A seed in the bag and every form unlocked calls the True Form King; otherwise the usual rules apply.
        boolean ascended = inventory.countItem(CombatMod.FORM_SEED) >= 1 && collection.allUnlocked(player) >= collection.allTotal();
        List<ItemStack> offering = new ArrayList<>();
        if (ascended) {
            offering.add(take(player, CombatMod.FORM_SEED));
        } else {
            int total = collection.total(), unlocked = collection.unlocked(player);
            if (unlocked < total) {
                player.sendOverlayMessage(Component.translatable("combat.boss.missing_forms", unlocked, total));
                return false;
            }
            if (inventory.countItem(Items.NETHER_STAR) < 1 || inventory.countItem(Items.DRAGON_BREATH) < 1) {
                player.sendOverlayMessage(Component.translatable("combat.boss.need_items"));
                return false;
            }
            offering.add(take(player, Items.NETHER_STAR));
            offering.add(take(player, Items.DRAGON_BREATH));
        }
        int fighters = level.getPlayers(p -> p.isAlive() && !p.isSpectator()
            && p.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= FormKingFight.RADIUS * FormKingFight.RADIUS).size();
        FormKingFight fight = new FormKingFight(level, pos, key, offering, fighters, ascended);
        FIGHTS.put(key, fight);
        fight.begin(now);
        for (ServerPlayer nearby : level.getPlayers(p -> p.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= FormKingFight.BAR_RADIUS * FormKingFight.BAR_RADIUS))
            nearby.sendSystemMessage(Component.translatable(ascended ? "combat.boss.summoned_true" : "combat.boss.summoned"));
        return true;
    }

    /** Operator command: starts a fight at pos with no altar, offering, cooldown or form requirement. */
    public static boolean forceSummon(ServerLevel level, BlockPos pos) { return forceSummon(level, pos, false); }

    public static boolean forceSummon(ServerLevel level, BlockPos pos, boolean ascended) {
        String key = key(level, pos);
        if (FIGHTS.containsKey(key) || nearAnyFight(level, pos)) return false;
        double r2 = FormKingFight.RADIUS * FormKingFight.RADIUS;
        int fighters = Math.max(1, level.getPlayers(p -> p.isAlive() && !p.isSpectator()
            && p.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= r2).size());
        FormKingFight fight = new FormKingFight(level, pos, key, new ArrayList<>(), fighters, ascended);
        FIGHTS.put(key, fight);
        fight.begin(level.getGameTime());
        double bar = FormKingFight.BAR_RADIUS * FormKingFight.BAR_RADIUS;
        for (ServerPlayer nearby : level.getPlayers(p -> p.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= bar))
            nearby.sendSystemMessage(Component.translatable(ascended ? "combat.boss.summoned_true" : "combat.boss.summoned"));
        return true;
    }

    /** Operator command: ends every running fight without a payout. */
    public static int abortAll() {
        int n = 0;
        for (FormKingFight fight : running()) { fight.abandon(); n++; }
        return n;
    }

    private static boolean nearAnyFight(ServerLevel level, BlockPos pos) {
        double limit = 2.0 * FormKingFight.RADIUS;
        for (FormKingFight fight : FIGHTS.values())
            if (fight.level == level && fight.altar.distSqr(pos) < limit * limit) return true;
        return false;
    }

    private static ItemStack take(ServerPlayer player, net.minecraft.world.item.Item item) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                ItemStack taken = stack.copyWithCount(1);
                stack.shrink(1);
                return taken;
            }
        }
        return new ItemStack(item);
    }

    /** The boss body that belongs to a running fight, or null. */
    public static FormKingFight of(Entity entity) {
        String key = entity.getAttached(CombatMod.BOSS_MARK);
        if (key == null) return null;
        FormKingFight fight = FIGHTS.get(key);
        return fight != null && entity.getUUID().equals(fight.bossId) ? fight : null;
    }

    public static void tick(MinecraftServer server) {
        for (FormKingFight fight : List.copyOf(FIGHTS.values())) {
            if (server.getTickCount() % 1 == 0) fight.tick(fight.level.getGameTime());
        }
    }

    static void finished(FormKingFight fight, boolean victory, long now) {
        FIGHTS.remove(fight.key);
        if (victory) COOLDOWN_UNTIL.put(fight.key, now + COOLDOWN_TICKS);
    }

    /** A boss body that is loaded without a fight behind it (server restart, copied entity) is removed. */
    public static void onEntityLoad(Entity entity) {
        if (entity.hasAttached(CombatMod.BOSS_MARK) && of(entity) == null) entity.discard();
    }

    /** Only players can hurt the body; fire, falls, lava and the boss's own blasts do nothing to it. */
    public static boolean allowDamage(Entity entity, net.minecraft.world.damagesource.DamageSource source) {
        if (!entity.hasAttached(CombatMod.BOSS_MARK)) return true;
        return source.getEntity() instanceof ServerPlayer;
    }

    /** After a hit: books the damage on the fight's own pool and tops the body's health back up. */
    public static void afterDamage(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.damagesource.DamageSource source, float damageTaken) {
        FormKingFight fight = of(entity);
        if (fight == null) return;
        entity.setHealth(entity.getMaxHealth());
        if (source.getEntity() instanceof ServerPlayer attacker) fight.onHit(attacker, damageTaken, fight.level.getGameTime());
    }

    /** Called when a player earns the quest book entry for beating the boss. */
    static void award(ServerPlayer player) { dev.alan.combat.QuestBook.grant(player, "ch6/form_king"); }

    public static void shutdown() {
        for (FormKingFight fight : List.copyOf(FIGHTS.values())) fight.abandon();
        FIGHTS.clear();
        COOLDOWN_UNTIL.clear();
    }
}
