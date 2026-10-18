package dev.alan.combat.boss;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * Walks and flies the Form King's body. The bodies have no AI (a mob with AI off does not even fall), so each tick the fight
 * chooses a velocity for the form and moves the body with {@link Mob#move}, which handles collisions, plus a gravity of its own
 * for the walking forms.
 */
final class BossMovement {
    static final double WALK = 0.18, STRAFE = 0.16, CHARGE = 0.26, FLY = 0.2, FLY_VERTICAL = 0.1;
    static final double GRAVITY = 0.08, DRAG = 0.98, JUMP = 0.42;
    /** Walking forms that keep their distance stay between these ranges from the target; flyers circle at {@link #ORBIT}. */
    static final double NEAR = 9, FAR = 15, ORBIT = 10, HOVER_ABOVE = 5;
    /** Blocks per tick along the circle. */
    private static final double ORBIT_SPEED = 0.14;
    private static final int STRAFE_TICKS = 60;

    private BossMovement() {}

    /** Moves the body one tick. {@code target} may be null (nobody to chase); the body then just stands, and falls if it must. */
    static void tick(FormKingFight fight, Mob boss, ServerPlayer target, long now) {
        if (fight.form.hovers) {
            fly(fight, boss, target);
            return;
        }
        Vec3 wish = target == null ? Vec3.ZERO : walkWish(fight, boss, target, now);
        walk(fight, boss, wish);
    }

    /** Horizontal velocity the walking form wants this tick. */
    private static Vec3 walkWish(FormKingFight fight, Mob boss, ServerPlayer target, long now) {
        Vec3 toward = flat(target.position().subtract(boss.position()));
        double distance = toward.length();
        if (distance < 1.0E-4) return Vec3.ZERO;
        Vec3 dir = toward.scale(1 / distance);
        return switch (fight.form) {
            case SKELETON, EVOKER -> {
                if (distance < NEAR) yield dir.scale(-WALK);
                if (distance > FAR) yield dir.scale(WALK);
                int side = ((now - fight.formStart) / STRAFE_TICKS) % 2 == 0 ? 1 : -1;
                yield new Vec3(-dir.z, 0, dir.x).scale(STRAFE * side);
            }
            case SPIDER -> now < fight.leapUntil ? fight.leapVelocity : distance > 3 ? dir.scale(WALK) : Vec3.ZERO;
            case CREEPER -> distance > 2 ? dir.scale(CHARGE) : Vec3.ZERO;
            default -> Vec3.ZERO;
        };
    }

    /** Applies horizontal {@code wish} with gravity, jumping when the last step was held back by something in the way. */
    private static void walk(FormKingFight fight, Mob boss, Vec3 wish) {
        wish = insideArena(fight, boss, wish);
        double vy = fight.fallSpeed;
        if (fight.leapPending) {
            vy = JUMP + 0.03;
            fight.leapPending = false;
        } else if (boss.onGround() && vy <= 0) {
            // Pressed gently onto the floor so the body keeps counting as standing on it.
            vy = fight.blocked ? JUMP : -GRAVITY;
        }
        Vec3 before = boss.position();
        boss.move(MoverType.SELF, new Vec3(wish.x, vy, wish.z));
        double moved = flat(boss.position().subtract(before)).length();
        fight.blocked = wish.length() > 0.05 && moved < wish.length() * 0.5;
        fight.fallSpeed = vy <= 0 && (boss.verticalCollisionBelow || boss.onGround()) ? 0 : (vy - GRAVITY) * DRAG;
    }

    /**
     * Flyers hold a height above the target and circle it: the velocity is a push along the circle plus a push toward the circle's
     * radius, so a flyer that starts far away curves in instead of cutting across over the player's head.
     */
    private static void fly(FormKingFight fight, Mob boss, ServerPlayer target) {
        if (target == null) return;
        Vec3 center = target.position();
        Vec3 out = flat(boss.position().subtract(center));
        double radius = out.length();
        Vec3 radial = radius < 1.0E-4 ? new Vec3(1, 0, 0) : out.scale(1 / radius);
        Vec3 tangent = new Vec3(-radial.z, 0, radial.x).scale(ORBIT_SPEED * fight.orbitDirection);
        double correction = Math.max(-FLY, Math.min(FLY, (ORBIT - radius) * 0.1));
        Vec3 wish = tangent.add(radial.scale(correction));
        if (wish.length() > FLY) wish = wish.scale(FLY / wish.length());
        wish = insideArena(fight, boss, wish);
        double rise = Math.max(-FLY_VERTICAL, Math.min(FLY_VERTICAL, center.y + HOVER_ABOVE - boss.getY()));
        boss.move(MoverType.SELF, new Vec3(wish.x, rise, wish.z));
    }
    /** Drops the part of {@code wish} that would carry the body out of the arena. */
    private static Vec3 insideArena(FormKingFight fight, Mob boss, Vec3 wish) {
        double nx = boss.getX() + wish.x - (fight.altar.getX() + 0.5), nz = boss.getZ() + wish.z - (fight.altar.getZ() + 0.5);
        double limit = FormKingFight.RADIUS - 3;
        return nx * nx + nz * nz > limit * limit ? Vec3.ZERO : wish;
    }

    private static Vec3 flat(Vec3 v) { return new Vec3(v.x, 0, v.z); }
}
