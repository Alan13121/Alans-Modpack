package dev.alan.mineworld.city;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.level.Level;

/** The enchantment merchant (trades come in a later phase). Does not care about being stared at and moves no blocks. */
public class CityEnderman extends Enderman implements CityResident {
    public CityEnderman(EntityType<? extends Enderman> type, Level level) {
        super(type, level);
    }

    @Override protected void registerGoals() {
        CityResident.addCivilianGoals(this, this.goalSelector);
    }
}
