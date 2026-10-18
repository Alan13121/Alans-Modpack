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
    SKELETON(EntityTypes.SKELETON, 30, false, null),
    BLAZE(EntityTypes.BLAZE, 25, true, null),
    BREEZE(EntityTypes.BREEZE, 40, true, null),
    EVOKER(EntityTypes.EVOKER, 60, false, Secondary.ARROW),
    ENDERMAN(EntityTypes.ENDERMAN, 70, false, Secondary.WIND_CHARGE),
    SPIDER(EntityTypes.SPIDER, 40, false, Secondary.SMALL_FIREBALL),
    GHAST(EntityTypes.GHAST, 50, true, Secondary.FANGS),
    /** The blaze again, but quicker; phase three. */
    BLAZE_FAST(EntityTypes.BLAZE, 15, true, Secondary.ARROW),
    /** Primes a fuse and blows up; see {@link BossSkills}. */
    CREEPER(EntityTypes.CREEPER, 0, false, null),
    /** What is left after the creeper blast: standing still and taking extra damage. */
    WEAK(EntityTypes.ZOMBIE, 0, false, null);

    /**
     * A skill a form borrows from another form, used on its own timer next to the form's own skill. Only the forms of phases two and
     * three have one, so phase one stays simple. {@code interval} is the borrowed skill's own pace; the borrower uses it at half rate.
     */
    public enum Secondary {
        ARROW(30), SMALL_FIREBALL(25), WIND_CHARGE(40), FANGS(60);

        public final int interval;

        Secondary(int interval) { this.interval = interval; }

        /** Ticks between two uses when borrowed. */
        public int borrowedInterval() { return interval * 2; }
    }

    public final EntityType<? extends Mob> type;
    public final int interval;
    public final boolean hovers;
    /** The borrowed skill, or null. */
    public final Secondary secondary;

    BossForm(EntityType<? extends Mob> type, int interval, boolean hovers, Secondary secondary) {
        this.type = type;
        this.interval = interval;
        this.hovers = hovers;
        this.secondary = secondary;
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
