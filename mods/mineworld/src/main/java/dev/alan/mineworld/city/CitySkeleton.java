package dev.alan.mineworld.city;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.level.Level;

/** Wall guard with a bow; shoots hostile monsters that come near (the bow goal is added by AbstractSkeleton once it holds a bow). */
public class CitySkeleton extends Skeleton implements CityResident {
    public CitySkeleton(EntityType<? extends Skeleton> type, Level level) {
        super(type, level);
    }

    @Override protected void registerGoals() {
        CityResident.addCivilianGoals(this, this.goalSelector);
        CityResident.addGuardTargets(this, this.targetSelector);
    }
}
