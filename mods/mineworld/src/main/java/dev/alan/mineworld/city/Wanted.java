package dev.alan.mineworld.city;

import com.mojang.serialization.Codec;
import dev.alan.mineworld.DynamicLevels;
import dev.alan.mineworld.MineWorldMod;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Wanted mobs: strengthened hostile mobs spawned now and then near players in mine worlds and the overworld. They
 * glow when a player is within 32 blocks, the A tier shows a boss bar, and they drop bounty proofs for the captain.
 */
public final class Wanted {
    private Wanted() {}

    public enum Tier {
        C(2.0, 1), B(3.0, 3), A(5.0, 8);

        public final double health;
        public final int proofs;

        Tier(double health, int proofs) { this.health = health; this.proofs = proofs; }

        public static final Codec<Tier> CODEC = Codec.STRING.xmap(Tier::valueOf, Tier::name);

        /** C 70%, B 25%, A 5%. */
        public static Tier roll(RandomSource random) {
            int r = random.nextInt(100);
            return r < 70 ? C : r < 95 ? B : A;
        }
    }

    /** Marks a wanted mob and its tier (minions carry nothing). */
    public static final AttachmentType<Tier> TIER = AttachmentRegistry.create(MineWorldMod.id("wanted"), b -> b.persistent(Tier.CODEC));

    /** One roll per player every ROLL_TICKS: 1 in MINE_CHANCE in a mine world, 1 in OVERWORLD_CHANCE in the overworld. */
    public static final int ROLL_TICKS = 600, MINE_CHANCE = 6, OVERWORLD_CHANCE = 20;
    public static final int MIN_DISTANCE = 24, MAX_DISTANCE = 64, ONE_PER_RADIUS = 128, GLOW_RADIUS = 32, CITY_CLEARANCE = 80;
    public static final int NAMES = 20, MINIONS = 3;

    private static final Set<Mob> TRACKED = new HashSet<>();
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Wanted::tick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Mob mob && mob.hasAttached(TIER)) TRACKED.add(mob);
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> forget(entity));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            Tier tier = entity.getAttached(TIER);
            if (tier != null && entity.level() instanceof ServerLevel level) {
                entity.spawnAtLocation(level, new ItemStack(City.BOUNTY_PROOF, tier.proofs));
            }
            forget(entity);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            TRACKED.clear();
            BARS.values().forEach(ServerBossEvent::removeAllPlayers);
            BARS.clear();
        });
    }

    private static void forget(Entity entity) {
        if (entity instanceof Mob mob) TRACKED.remove(mob);
        ServerBossEvent bar = BARS.remove(entity.getUUID());
        if (bar != null) bar.removeAllPlayers();
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % ROLL_TICKS == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) roll(player);
        }
        if (server.getTickCount() % 20 == 0) updateMarks();
    }

    /** The spawn roll for one player: right dimension, monsters on, none nearby, chance, then a spot. */
    private static void roll(ServerPlayer player) {
        if (player.isSpectator() || !(player.level() instanceof ServerLevel level) || !level.isSpawningMonsters()) return;
        boolean mine = DynamicLevels.worldId(level) != null;
        if (!mine && level.dimension() != Level.OVERWORLD) return;
        if (level.getRandom().nextInt(mine ? MINE_CHANCE : OVERWORLD_CHANCE) != 0) return;
        if (nearbyWanted(level, player.blockPosition()) > 0) return;
        trySpawn(level, player.blockPosition(), Tier.roll(level.getRandom()));
    }

    public static int nearbyWanted(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(pos).inflate(ONE_PER_RADIUS), m -> m.hasAttached(TIER)).size();
    }

    /** Spawns a wanted mob of the tier at a dark spot 24..64 blocks from around; null if no spot was found. */
    public static @Nullable Mob trySpawn(ServerLevel level, BlockPos around, Tier tier) {
        BlockPos spot = findSpot(level, around, level.getRandom(), DynamicLevels.worldId(level) != null);
        return spot == null ? null : spawn(level, spot, tier);
    }

    static @Nullable BlockPos findSpot(ServerLevel level, BlockPos around, RandomSource random, boolean mine) {
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1);
            int x = around.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = around.getZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!level.isLoaded(new BlockPos(x, around.getY(), z))) continue;
            int y = !mine && random.nextBoolean()
                ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
                : around.getY() + random.nextInt(33) - 16;
            for (int dy = 0; dy < 12; dy++) {
                BlockPos pos = new BlockPos(x, y - dy, z);
                if (!fits(level, pos)) continue;
                if (Monster.isDarkEnoughToSpawn(level, pos, random) && !(mine && nearCity(level, pos))) return pos;
                break;
            }
        }
        return null;
    }

    private static boolean fits(ServerLevel level, BlockPos pos) {
        return empty(level, pos) && empty(level, pos.above())
            && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    private static boolean empty(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    /** Inside a city or within CITY_CLEARANCE of one (checked at the spot and eight points around it). */
    private static boolean nearCity(ServerLevel level, BlockPos pos) {
        if (City.contains(level, pos)) return true;
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            if (City.contains(level, pos.offset((int) (Math.cos(a) * CITY_CLEARANCE), 0, (int) (Math.sin(a) * CITY_CLEARANCE)))) return true;
        }
        return false;
    }

    /** Creates the wanted mob (and the A tier's minions) at pos. */
    public static Mob spawn(ServerLevel level, BlockPos pos, Tier tier) {
        RandomSource random = level.getRandom();
        EntityType<? extends Mob> type = switch (tier) {
            case C -> random.nextBoolean() ? EntityTypes.ZOMBIE : EntityTypes.SKELETON;
            case B -> random.nextBoolean() ? EntityTypes.VINDICATOR : EntityTypes.PIGLIN_BRUTE;
            case A -> EntityTypes.EVOKER;
        };
        Mob mob = create(level, type, pos);
        switch (tier) {
            case C -> armor(mob, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
            case B -> {
                armor(mob, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
                mob.getAttribute(Attributes.MOVEMENT_SPEED).addPermanentModifier(
                    new AttributeModifier(MineWorldMod.id("wanted_speed"), 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
            case A -> {}
        }
        mob.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(
            new AttributeModifier(MineWorldMod.id("wanted_health"), tier.health - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        mob.setHealth(mob.getMaxHealth());
        mob.setCustomName(Component.translatable("mineworld.wanted.name",
            Component.translatable("mineworld.wanted.alias." + random.nextInt(NAMES)), tier.name()));
        mob.setCustomNameVisible(true);
        mob.setAttached(TIER, tier);
        level.addFreshEntityWithPassengers(mob);
        if (tier == Tier.A) {
            for (int i = 0; i < MINIONS; i++) level.addFreshEntityWithPassengers(create(level, EntityTypes.VINDICATOR, pos));
        }
        return mob;
    }

    private static Mob create(ServerLevel level, EntityType<? extends Mob> type, BlockPos pos) {
        Mob mob = type.create(level, EntitySpawnReason.EVENT);
        if (mob == null) throw new IllegalStateException("cannot create " + type);
        mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
        if (mob instanceof AbstractPiglin piglin) piglin.setImmuneToZombification(true);
        return mob;
    }

    private static void armor(Mob mob, Item head, Item chest, Item legs, Item feet) {
        mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(head));
        mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(chest));
        mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(legs));
        mob.setItemSlot(EquipmentSlot.FEET, new ItemStack(feet));
    }

    /** Players currently shown the boss bar of a wanted mob (tests and debugging). */
    public static List<ServerPlayer> bossBarPlayers(Mob mob) {
        ServerBossEvent bar = BARS.get(mob.getUUID());
        return bar == null ? List.of() : List.copyOf(bar.getPlayers());
    }

    /** Glow while any player is within GLOW_RADIUS; the A tier's boss bar shows to exactly those players. */
    private static void updateMarks() {
        for (Mob mob : List.copyOf(TRACKED)) {
            if (mob.isRemoved() || !(mob.level() instanceof ServerLevel level)) continue;
            List<ServerPlayer> near = level.getPlayers(p -> !p.isSpectator() && p.distanceToSqr(mob) <= GLOW_RADIUS * GLOW_RADIUS);
            mob.setGlowingTag(!near.isEmpty());
            if (mob.getAttached(TIER) != Tier.A) continue;
            ServerBossEvent bar = BARS.computeIfAbsent(mob.getUUID(), id ->
                new ServerBossEvent(id, mob.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS));
            bar.setProgress(mob.getHealth() / mob.getMaxHealth());
            for (ServerPlayer p : List.copyOf(bar.getPlayers())) if (!near.contains(p)) bar.removePlayer(p);
            for (ServerPlayer p : near) bar.addPlayer(p);
        }
    }
}
