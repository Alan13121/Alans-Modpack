package dev.alan.shapeshift;

import java.util.EnumSet;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Runs from players whose form scares this mob, like a creeper from a cat.
 * Vanilla's AvoidEntityGoal only flees from things the mob could attack, but scared mobs are made
 * unable to attack the player they fear, so this goal does its own search.
 */
final class FleeFormGoal extends Goal {
    private static final double RANGE = 8, WALK = 1.0, SPRINT = 1.3;
    private final PathfinderMob mob;
    private @Nullable Player scary;
    private @Nullable Path path;

    FleeFormGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override public boolean canUse() {
        scary = mob.level().getNearestPlayer(mob.getX(), mob.getY(), mob.getZ(), RANGE,
            e -> e instanceof Player p && !p.isSpectator() && !p.isCreative() && Reactions.scares(mob, p));
        if (scary == null) return false;
        Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, scary.position());
        if (away == null || scary.distanceToSqr(away) < scary.distanceToSqr(mob)) return false;
        path = mob.getNavigation().createPath(away.x, away.y, away.z, 0);
        return path != null;
    }

    @Override public boolean canContinueToUse() { return !mob.getNavigation().isDone(); }

    @Override public void start() { mob.getNavigation().moveTo(path, WALK); }

    @Override public void stop() { scary = null; }

    @Override public void tick() {
        if (scary != null) mob.getNavigation().setSpeedModifier(mob.distanceToSqr(scary) < 49 ? SPRINT : WALK);
    }
}
