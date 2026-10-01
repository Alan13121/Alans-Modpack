package dev.alan.combat.boss;

import dev.alan.combat.CombatMod;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * One battle with the Form King at one altar. The boss is a plain vanilla mob in a scripted form; its real health is
 * kept out of reach and the fight tracks its own pool. Everything runs on the server thread.
 */
public final class FormKingFight {
    public static final int RADIUS = 32, LEAVE_RADIUS = 40, BAR_RADIUS = 48;
    public static final int ABSENT_LIMIT = 200, DEAD_LIMIT = 60;
    public static final double BASE_HP = 600, EXTRA_PLAYER = 0.5, HIT_CAP = 0.05;
    public static final int MAX_PLAYERS = 4;
    public static final float STAGGER_BONUS = 0.25f, WEAK_BONUS = 0.5f;
    public static final int STAGGER_TICKS = 60, WEAK_TICKS = 100;
    /** The body's own health, high enough that nothing can kill it; it is topped up after every hit. */
    private static final double BODY_HEALTH = 1000;

    final ServerLevel level;
    final BlockPos altar;
    final String key;
    final List<ItemStack> offering;
    final double maxHp;
    final ServerBossEvent bar;
    final Set<UUID> damagers = new HashSet<>();
    double hp;
    UUID bossId;
    BossForm form = BossForm.SKELETON;
    int stage = 1;
    int formIndex = -1;
    long formStart, nextSkill, staggerUntil, weakUntil;
    // Skill state (see BossSkills).
    int strikeState;
    long strikeAt, leapUntil;
    Vec3 strikeDestination = Vec3.ZERO;
    boolean leapHit;
    private int absent, deadFor;
    private boolean over;

    FormKingFight(ServerLevel level, BlockPos altar, String key, List<ItemStack> offering, int players) {
        this.level = level;
        this.altar = altar;
        this.key = key;
        this.offering = offering;
        this.maxHp = BASE_HP * (1 + EXTRA_PLAYER * (Math.min(Math.max(players, 1), MAX_PLAYERS) - 1));
        this.hp = maxHp;
        this.bar = new ServerBossEvent(UUID.randomUUID(), title(BossForm.SKELETON), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    }

    public double hp() { return hp; }
    public double maxHp() { return maxHp; }
    public int stage() { return stage; }
    public BossForm form() { return form; }
    public boolean isOver() { return over; }
    public UUID bossId() { return bossId; }
    public BlockPos altar() { return altar; }
    /** For tests: sets the remaining health. */
    public void setHp(double hp) { this.hp = hp; }
    /** For tests and commands: switches to {@code next} right away, as if its turn had come. */
    public void forceForm(BossForm next) { changeForm(next, level.getGameTime(), false); }

    static Component title(BossForm form) {
        return Component.translatable("combat.boss.name").append(" — ").append(form.type.getDescription());
    }

    /** Starts the fight: the first body appears at a point on the arena's edge. */
    void begin(long now) {
        changeForm(BossForm.STAGES.get(0).forms().get(0), now, false);
        formIndex = 0;
    }

    Mob boss() {
        if (bossId == null) return null;
        return level.getEntity(bossId) instanceof Mob mob && mob.isAlive() ? mob : null;
    }

    private List<ServerPlayer> within(double radius) {
        double squared = radius * radius;
        return level.getPlayers(p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(altar.getX() + 0.5, altar.getY(), altar.getZ() + 0.5) <= squared);
    }

    void tick(long now) {
        if (over) return;
        // Nobody is near enough for the arena to tick: the fight waits, body and all, until somebody comes back.
        if (!level.isPositionEntityTicking(altar)) return;
        Mob boss = boss();
        if (boss == null) {
            end(false, now);
            return;
        }
        List<ServerPlayer> fighters = within(RADIUS);
        syncBar(within(BAR_RADIUS));
        absent = within(LEAVE_RADIUS).isEmpty() ? absent + 1 : 0;
        deadFor = fighters.isEmpty() ? deadFor + 1 : 0;
        if (absent >= ABSENT_LIMIT || deadFor >= DEAD_LIMIT) {
            end(false, now);
            return;
        }
        if (hp <= 0) {
            end(true, now);
            return;
        }
        int wanted = BossForm.stageFor(hp / maxHp);
        if (wanted != stage) {
            stage = wanted;
            formIndex = -1;
            changeForm(nextForm(), now, false);
            boss = boss();
            if (boss == null) return;
        } else if (form == BossForm.WEAK ? now >= weakUntil : form != BossForm.CREEPER && now - formStart >= BossForm.STAGES.get(stage - 1).formTicks()) {
            changeForm(nextForm(), now, false);
            boss = boss();
            if (boss == null) return;
        }
        BossSkills.tick(this, boss, fighters, now);
        keepInArena(boss);
        if (boss.isOnFire() && form != BossForm.BLAZE && form != BossForm.BLAZE_FAST) boss.clearFire();
        bar.setProgress((float) Math.max(0, Math.min(1, hp / maxHp)));
        bar.setName(title(form));
    }

    private BossForm nextForm() {
        var forms = BossForm.STAGES.get(stage - 1).forms();
        formIndex = (formIndex + 1) % forms.size();
        return forms.get(formIndex);
    }

    private void syncBar(List<ServerPlayer> inRange) {
        for (ServerPlayer player : inRange) if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
        for (ServerPlayer player : List.copyOf(bar.getPlayers())) if (!inRange.contains(player)) bar.removePlayer(player);
    }

    private void keepInArena(Mob boss) {
        double dx = boss.getX() - (altar.getX() + 0.5), dz = boss.getZ() - (altar.getZ() + 0.5);
        if (dx * dx + dz * dz > (RADIUS - 2.0) * (RADIUS - 2.0)) {
            Vec3 point = ringPoint(form.hovers);
            boss.teleportTo(point.x, point.y, point.z);
        }
    }

    /** Replaces the body by one in {@code next}, keeping the fight's own health. {@code inPlace} keeps the position. */
    void changeForm(BossForm next, long now, boolean inPlace) {
        Mob old = boss();
        Vec3 where = old != null && inPlace ? old.position() : ringPoint(next.hovers);
        float yaw = old != null ? old.getYRot() : 0f;
        Mob body = spawnBody(next, where, yaw);
        if (old != null) {
            level.sendParticles(ParticleTypes.POOF, old.getX(), old.getY(0.5), old.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
            old.discard();
        }
        level.sendParticles(ParticleTypes.POOF, where.x, where.y + 1, where.z, 30, 0.5, 0.8, 0.5, 0.05);
        level.playSound(null, where.x, where.y, where.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 2f, 0.6f);
        bossId = body.getUUID();
        form = next;
        formStart = now;
        nextSkill = now + 20;
        strikeState = 0;
        leapUntil = 0;
        if (old != null) staggerUntil = now + STAGGER_TICKS;
        if (next == BossForm.WEAK) weakUntil = now + WEAK_TICKS;
        if (next == BossForm.CREEPER) BossSkills.prime(body);
    }

    private Mob spawnBody(BossForm next, Vec3 at, float yaw) {
        Mob mob = next.type.create(level, EntitySpawnReason.EVENT);
        mob.snapTo(at.x, at.y, at.z, yaw, 0f);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), EntitySpawnReason.EVENT, null);
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        for (EquipmentSlot slot : EquipmentSlot.values()) mob.setDropChance(slot, 0f);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(BODY_HEALTH);
        mob.setHealth((float) BODY_HEALTH);
        mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        mob.setAttached(CombatMod.BOSS_MARK, key);
        // Registered before it enters the world, so the load check does not take it for a leftover.
        bossId = mob.getUUID();
        level.addFreshEntity(mob);
        return mob;
    }

    /** A point on a ring 10 to 18 blocks from the altar, on the ground there (or above it for flying forms). */
    private Vec3 ringPoint(boolean hover) {
        double angle = level.getRandom().nextDouble() * Math.PI * 2, radius = 10 + level.getRandom().nextDouble() * 8;
        double x = altar.getX() + 0.5 + Math.cos(angle) * radius, z = altar.getZ() + 0.5 + Math.sin(angle) * radius;
        double y = BossSkills.groundY(level, x, altar.getY() + 1, z);
        return new Vec3(x, hover ? y + 4 : y, z);
    }

    /**
     * The boss took a hit from {@code attacker}. {@code damage} is what the body lost; it is capped per hit and
     * multiplied while the boss is staggered or weak.
     */
    void onHit(ServerPlayer attacker, float damage, long now) {
        if (over) return;
        double scaled = Math.min(damage, maxHp * HIT_CAP);
        float bonus = 0f;
        if (now < staggerUntil) bonus = STAGGER_BONUS;
        if (form == BossForm.WEAK && now < weakUntil) bonus = Math.max(bonus, WEAK_BONUS);
        hp -= scaled * (1 + bonus);
        damagers.add(attacker.getUUID());
    }

    /** Ends the battle. A win pays out; a loss makes the boss vanish and gives back the offering. */
    void end(boolean victory, long now) {
        if (over) return;
        over = true;
        level.getChunkAt(altar);   // the refund must land in a loaded chunk or it would be lost
        Mob boss = boss();
        Vec3 at = boss != null ? boss.position() : new Vec3(altar.getX() + 0.5, altar.getY() + 1, altar.getZ() + 0.5);
        if (boss != null) boss.discard();
        bar.removeAllPlayers();
        var nearby = level.getPlayers(p -> p.distanceToSqr(altar.getX() + 0.5, altar.getY(), altar.getZ() + 0.5) <= BAR_RADIUS * BAR_RADIUS);
        if (victory) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 3, 1.0, 1.0, 1.0, 0);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 2f, 1f);
            ExperienceOrb.award(level, at, 500);
            for (UUID id : damagers) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
                if (player == null) continue;
                ItemStack core = new ItemStack(CombatMod.FORM_CORE);
                player.getInventory().add(core);
                if (!core.isEmpty()) level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(), core));
                FormKingFights.award(player);
            }
            for (ServerPlayer player : nearby) player.sendSystemMessage(Component.translatable("combat.boss.victory"));
        } else {
            for (ItemStack stack : offering) {
                ItemEntity drop = new ItemEntity(level, altar.getX() + 0.5, altar.getY() + 1.2, altar.getZ() + 0.5, stack.copy());
                level.addFreshEntity(drop);
            }
            for (ServerPlayer player : nearby) player.sendSystemMessage(Component.translatable("combat.boss.failed"));
        }
        FormKingFights.finished(this, victory, now);
    }

    /** Server stopping: leave nothing behind and keep the offering. */
    void abandon() {
        if (over) return;
        end(false, level.getGameTime());
    }

    static Identifier dimensionOf(ServerLevel level) { return level.dimension().identifier(); }
}
