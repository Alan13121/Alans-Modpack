package dev.alan.combat.boss;

import dev.alan.combat.CombatMod;
import java.util.List;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** What the Form King does in each form. Every skill is scripted: the bodies have no AI of their own. */
final class BossSkills {
    /** Ticks a creeper fuse burns before the blast; the real creeper would blow at 30, so this stays below. */
    static final int FUSE_TICKS = 27;
    static final float CREEPER_POWER = 2.5f;
    static final float MELEE_DAMAGE = 7f;
    private static final int FANGS = 12;
    private static final int STRIKE_WARNING = 10, STRIKE_DELAY = 8, LEAP_TICKS = 14;

    private BossSkills() {}

    /** Runs once per tick for the body in its current form. */
    static void tick(FormKingFight fight, Mob boss, List<ServerPlayer> targets, long now) {
        ServerPlayer target = nearest(boss, targets);
        if (target != null) boss.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        ServerLevel level = fight.level;
        switch (fight.form) {
            case SKELETON -> timed(fight, now, target, () -> arrow(fight, level, boss, target));
            case BLAZE, BLAZE_FAST -> timed(fight, now, target, () -> fireball(level, boss, target));
            case BREEZE -> timed(fight, now, target, () -> windCharge(level, boss, target));
            case EVOKER -> timed(fight, now, target, () -> fangs(level, boss, target));
            case GHAST -> timed(fight, now, target, () -> bigFireball(level, boss, target));
            case ENDERMAN -> strike(fight, boss, target, targets, now);
            case SPIDER -> leap(fight, boss, target, targets, now);
            case CREEPER -> {
                if (now - fight.formStart >= FUSE_TICKS) {
                    level.explode(boss, boss.getX(), boss.getY(0.5), boss.getZ(), CREEPER_POWER * fight.damageScale(), Level.ExplosionInteraction.NONE);
                    fight.changeForm(BossForm.WEAK, now, true);
                }
            }
            case WEAK -> { }
        }
    }

    static ServerPlayer nearest(Mob boss, List<ServerPlayer> players) {
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : players) {
            double distance = player.distanceToSqr(boss);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private static void timed(FormKingFight fight, long now, ServerPlayer target, Runnable skill) {
        if (target == null || now < fight.nextSkill) return;
        skill.run();
        fight.nextSkill = now + fight.paced(fight.form.interval);
    }

    private static void arrow(FormKingFight fight, ServerLevel level, Mob boss, ServerPlayer target) {
        Vec3 from = boss.getEyePosition(), to = target.getEyePosition();
        Arrow arrow = new Arrow(level, boss, new ItemStack(Items.ARROW), null);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.setBaseDamage(4.0 * fight.damageScale());
        arrow.setPos(from);
        Vec3 d = to.subtract(from);
        arrow.shoot(d.x, d.y + Math.sqrt(d.x * d.x + d.z * d.z) * 0.2, d.z, 1.8f, 3f);
        level.addFreshEntity(arrow);
        sound(level, boss, SoundEvents.SKELETON_SHOOT);
    }

    private static void fireball(ServerLevel level, Mob boss, ServerPlayer target) {
        Vec3 from = boss.getEyePosition();
        Vec3 d = target.getEyePosition().subtract(from).normalize();
        SmallFireball ball = new SmallFireball(level, boss, d);
        ball.setPos(from.add(d));
        ball.setAttached(CombatMod.BOSS_SHOT, true);
        level.addFreshEntity(ball);
        sound(level, boss, SoundEvents.BLAZE_SHOOT);
    }

    private static void bigFireball(ServerLevel level, Mob boss, ServerPlayer target) {
        Vec3 from = boss.getEyePosition();
        Vec3 d = target.getEyePosition().subtract(from).normalize();
        LargeFireball ball = new LargeFireball(level, boss, d, 1);
        ball.setPos(from.add(d.scale(2)));
        ball.setAttached(CombatMod.BOSS_SHOT, true);
        level.addFreshEntity(ball);
        sound(level, boss, SoundEvents.GHAST_SHOOT);
    }

    private static void windCharge(ServerLevel level, Mob boss, ServerPlayer target) {
        Vec3 from = boss.getEyePosition();
        Vec3 d = target.getEyePosition().subtract(from).normalize();
        WindCharge charge = new WindCharge(level, from.x + d.x, from.y + d.y, from.z + d.z, d);
        charge.setOwner(boss);
        charge.shoot(d.x, d.y, d.z, 1.4f, 1f);
        level.addFreshEntity(charge);
        sound(level, boss, SoundEvents.BREEZE_SHOOT);
    }

    /** A line of fangs from the body toward the target, each placed on the ground as the evoker does. */
    private static void fangs(ServerLevel level, Mob boss, ServerPlayer target) {
        double dx = target.getX() - boss.getX(), dz = target.getZ() - boss.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4) return;
        dx /= length;
        dz /= length;
        float angle = (float) Math.atan2(dz, dx);
        for (int i = 1; i <= FANGS; i++) {
            double x = boss.getX() + dx * 1.25 * i, z = boss.getZ() + dz * 1.25 * i;
            level.addFreshEntity(new EvokerFangs(level, x, groundY(level, x, boss.getY(), z), z, angle, i, boss));
        }
        sound(level, boss, SoundEvents.EVOKER_CAST_SPELL);
    }

    /** Enderman: warns with portal particles, vanishes, and hits whoever is still close after a moment. */
    private static void strike(FormKingFight fight, Mob boss, ServerPlayer target, List<ServerPlayer> targets, long now) {
        ServerLevel level = fight.level;
        switch (fight.strikeState) {
            case 0 -> {
                if (target == null || now < fight.nextSkill) return;
                Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1, 0, 1).normalize().scale(2));
                fight.strikeDestination = new Vec3(behind.x, groundY(level, behind.x, target.getY(), behind.z), behind.z);
                fight.strikeAt = now + STRIKE_WARNING;
                fight.strikeState = 1;
                level.sendParticles(ParticleTypes.PORTAL, fight.strikeDestination.x, fight.strikeDestination.y + 1,
                    fight.strikeDestination.z, 60, 0.4, 0.8, 0.4, 0.3);
            }
            case 1 -> {
                if (now < fight.strikeAt) return;
                Vec3 to = fight.strikeDestination;
                level.sendParticles(ParticleTypes.PORTAL, boss.getX(), boss.getY() + 1, boss.getZ(), 40, 0.4, 0.8, 0.4, 0.2);
                boss.teleportTo(to.x, to.y, to.z);
                sound(level, boss, SoundEvents.ENDERMAN_TELEPORT);
                fight.strikeAt = now + STRIKE_DELAY;
                fight.strikeState = 2;
            }
            default -> {
                if (now < fight.strikeAt) return;
                for (ServerPlayer player : targets) if (player.distanceToSqr(boss) < 3.5 * 3.5) hit(fight, level, boss, player);
                fight.strikeState = 0;
                fight.nextSkill = now + fight.paced(fight.form.interval);
            }
        }
    }

    /** Spider: a long jump at the target; whoever is under the body while it flies is bitten once. */
    private static void leap(FormKingFight fight, Mob boss, ServerPlayer target, List<ServerPlayer> targets, long now) {
        ServerLevel level = fight.level;
        if (now < fight.leapUntil) {
            if (fight.leapHit) return;
            for (ServerPlayer player : targets) {
                if (player.distanceToSqr(boss) < 2.0 * 2.0) {
                    hit(fight, level, boss, player);
                    fight.leapHit = true;
                    break;
                }
            }
            return;
        }
        if (target == null || now < fight.nextSkill) return;
        Vec3 d = target.position().subtract(boss.position());
        double length = Math.max(0.1, Math.sqrt(d.x * d.x + d.z * d.z));
        boss.setDeltaMovement(d.x / length * 0.9, 0.45, d.z / length * 0.9);
        fight.leapUntil = now + LEAP_TICKS;
        fight.leapHit = false;
        fight.nextSkill = now + fight.paced(fight.form.interval);
        sound(level, boss, SoundEvents.SPIDER_AMBIENT);
    }

    static void hit(FormKingFight fight, ServerLevel level, Mob boss, ServerPlayer target) {
        target.hurtServer(level, level.damageSources().mobAttack(boss), MELEE_DAMAGE * fight.damageScale());
    }

    /** Primes the creeper body's fuse; the real blast is scripted, see {@link #FUSE_TICKS}. */
    static void prime(Mob boss) {
        if (boss instanceof Creeper creeper) creeper.ignite();
    }

    /** Height of the first walkable floor at or below {@code refY + 2}, or {@code refY} when there is none close by. */
    static double groundY(ServerLevel level, double x, double refY, double z) {
        BlockPos pos = BlockPos.containing(x, refY + 2, z);
        for (int i = 0; i < 10; i++, pos = pos.below()) {
            BlockPos below = pos.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty())
                return pos.getY();
        }
        return refY;
    }

    private static void sound(ServerLevel level, Mob boss, SoundEvent sound) {
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), sound, SoundSource.HOSTILE, 1.5f, 1f);
    }
}
