package dev.alan.mineworld.city;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** The city mascot: wanders around and never explodes (no swell goal, cannot be lit). */
public class CityCreeper extends Creeper implements CityResident {
    public CityCreeper(EntityType<? extends Creeper> type, Level level) {
        super(type, level);
    }

    @Override protected void registerGoals() {
        CityResident.addCivilianGoals(this, this.goalSelector);
    }

    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }
}
