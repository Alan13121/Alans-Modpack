package dev.alan.mineworld.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.Level;

/** A lush cave slime made of gems. Splits like a slime (into gem slimes); the tiniest ones drop a gem (loot table). */
public class GemSlime extends Slime {
    public GemSlime(EntityType<? extends Slime> type, Level level) {
        super(type, level);
    }
}
