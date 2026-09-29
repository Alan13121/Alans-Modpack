package dev.alan.shapeshift;

import dev.alan.shapeshift.mixin.MobAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;

/** How mobs treat shapeshifted players: ignore them, flee from them, or hunt them. Server side only. */
public final class Reactions {
    private Reactions() {}

    /** Mob will not pick (or keep) this player as a target. Hitting the mob always lets it fight back. */
    public static boolean friendly(Mob mob, Player player) {
        var form = Forms.current(player).orElse(null);
        if (form == null || mob.getLastHurtByMob() == player || hunts(mob, player)) return false;
        if (mob.getType() == form || scares(mob, player)) return true;   // a mob that fears you does not also attack you
        return ShapeshiftConfig.monstersIgnoreMonsterForms() && mob instanceof Enemy && form.getCategory() == MobCategory.MONSTER;
    }

    public static boolean hunts(Mob mob, Player player) {
        var form = Forms.current(player).orElse(null);
        if (form == null || mob instanceof TamableAnimal tame && tame.isTame()) return false;
        if (ShapeshiftConfig.golemsHuntMonsterForms() && isGolem(mob.getType()) && form.getCategory() == MobCategory.MONSTER) return true;
        return FormDefinitions.matches(FormDefinitions.resolve(form, false).huntedBy(), mob.getType());
    }

    public static boolean scares(Mob mob, Player player) {
        return Forms.current(player)
            .map(form -> FormDefinitions.matches(FormDefinitions.resolve(form, false).scares(), mob.getType()))
            .orElse(false);
    }

    /**
     * After a form change, mobs that were hunting the player only because of the old form give up.
     * (Vanilla target goals never re-check why they picked a target.) Mobs the player hit keep fighting.
     */
    public static void onFormChanged(ServerPlayer player) {
        var area = player.getBoundingBox().inflate(48);
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, area, m -> m.getTarget() == player)) {
            boolean formHunter = isGolem(mob.getType()) || FormDefinitions.anyHuntedBy(mob.getType());
            if (!formHunter || hunts(mob, player) || mob.getLastHurtByMob() == player) continue;
            // Neutral mobs (golems, wolves) turn any target into a lasting grudge; clear that too.
            if (mob instanceof NeutralMob neutral) neutral.stopBeingAngry();
            else mob.setTarget(null);
        }
    }

    /** Gives newly loaded mobs the extra AI they need; mobs that can never react get nothing. */
    public static void onEntityLoad(Entity entity) {
        if (!(entity instanceof Mob mob)) return;
        EntityType<?> type = mob.getType();
        var goals = (MobAccessor) mob;
        // Villagers run on a brain instead of goals; VillagerHostilesSensorMixin makes them panic instead.
        if (mob instanceof PathfinderMob pathfinder && !(mob instanceof Villager) && FormDefinitions.anyScares(type))
            goals.shapeshift$goalSelector().addGoal(1, new FleeFormGoal(pathfinder));
        if (isGolem(type) || FormDefinitions.anyHuntedBy(type))
            goals.shapeshift$targetSelector().addGoal(2, new NearestAttackableTargetGoal<>(mob, Player.class, true,
                (target, level) -> hunts(mob, (Player) target)));
    }

    private static boolean isGolem(EntityType<?> type) {
        return type == net.minecraft.world.entity.EntityTypes.IRON_GOLEM || type == net.minecraft.world.entity.EntityTypes.SNOW_GOLEM;
    }
}
