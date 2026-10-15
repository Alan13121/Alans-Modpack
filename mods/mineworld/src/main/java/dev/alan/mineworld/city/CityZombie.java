package dev.alan.mineworld.city;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

/** The general store keeper (trades come in a later phase). Never burns, never turns into a drowned, never attacks. */
public class CityZombie extends Zombie implements CityResident {
    public CityZombie(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    @Override protected void registerGoals() {
        CityResident.addCivilianGoals(this, this.goalSelector);
    }

    @Override protected boolean convertsInWater() { return false; }

    @Override protected boolean isSunSensitive() { return false; }
}
