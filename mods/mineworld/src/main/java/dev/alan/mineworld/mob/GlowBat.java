package dev.alan.mineworld.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.level.Level;

/** A crystal cave bat with glowing wings; harmless, drops glowstone dust and amethyst shards (loot table). */
public class GlowBat extends Bat {
    public GlowBat(EntityType<? extends Bat> type, Level level) {
        super(type, level);
    }
}
