package dev.alan.mineworld.city;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

/** Marker for the friendly monsters that live in a city; they never attack players and guards never attack them. */
public interface CityResident {
    static boolean isHostile(LivingEntity target) {
        return target instanceof Monster && !(target instanceof CityResident);
    }

    /** Wander around the home (set with setHomeTo), look at players. */
    static void addCivilianGoals(PathfinderMob mob, GoalSelector goals) {
        goals.addGoal(0, new FloatGoal(mob));
        goals.addGoal(5, new MoveTowardsRestrictionGoal(mob, 0.8));
        goals.addGoal(6, new WaterAvoidingRandomStrollGoal(mob, 0.6));
        goals.addGoal(7, new LookAtPlayerGoal(mob, Player.class, 8.0F));
        goals.addGoal(8, new RandomLookAroundGoal(mob));
    }

    /** Guards go after hostile monsters (not other residents). */
    static void addGuardTargets(Mob mob, GoalSelector targets) {
        targets.addGoal(1, guardTarget(mob));
    }

    static NearestAttackableTargetGoal<Monster> guardTarget(Mob mob) {
        return new NearestAttackableTargetGoal<>(mob, Monster.class, true, (target, level) -> isHostile(target));
    }
}
