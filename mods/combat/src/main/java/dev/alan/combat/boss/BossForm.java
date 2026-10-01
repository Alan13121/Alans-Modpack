package dev.alan.combat.boss;

import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

/**
 * The shapes the Form King takes. {@code interval} is the number of ticks between uses of the form's skill
 * (0 = the form has no repeating skill), {@code hovers} places the body above the ground.
 */
public enum BossForm {
    SKELETON(EntityTypes.SKELETON, 30, false),
    BLAZE(EntityTypes.BLAZE, 25, true),
    BREEZE(EntityTypes.BREEZE, 40, true),
    EVOKER(EntityTypes.EVOKER, 60, false),
    ENDERMAN(EntityTypes.ENDERMAN, 70, false),
    SPIDER(EntityTypes.SPIDER, 40, false),
    GHAST(EntityTypes.GHAST, 50, true),
    /** The blaze again, but quicker; phase three. */
    BLAZE_FAST(EntityTypes.BLAZE, 15, true),
    /** Primes a fuse and blows up; see {@link BossSkills}. */
    CREEPER(EntityTypes.CREEPER, 0, false),
    /** What is left after the creeper blast: standing still and taking extra damage. */
    WEAK(EntityTypes.ZOMBIE, 0, false);

    public final EntityType<? extends Mob> type;
    public final int interval;
    public final boolean hovers;

    BossForm(EntityType<? extends Mob> type, int interval, boolean hovers) {
        this.type = type;
        this.interval = interval;
        this.hovers = hovers;
    }

    /** A phase: how long each form lasts and in which order they come. */
    public record Stage(int number, int formTicks, List<BossForm> forms) {}

    public static final List<Stage> STAGES = List.of(
        new Stage(1, 400, List.of(SKELETON, BLAZE, BREEZE)),
        new Stage(2, 300, List.of(EVOKER, ENDERMAN, SPIDER)),
        new Stage(3, 200, List.of(GHAST, CREEPER, BLAZE_FAST)));

    /** Phase for the share of health that is left (1 = full). */
    public static int stageFor(double ratio) { return ratio > 2.0 / 3 ? 1 : ratio > 1.0 / 3 ? 2 : 3; }
}
