package dev.alan.shapeshift;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** What pressing the ability key (R) does. Defaults can be changed per form with "cooldown" and "power". */
public enum ActiveType implements StringRepresentable {
    /** Creeper: fuse, then an explosion that never hurts the user, who turns back into a human. power = radius. */
    EXPLODE(0, 3),
    /** Blaze: small fireball. */
    SMALL_FIREBALL(6, 0),
    /** Ghast: exploding fireball. power = explosion size. */
    FIREBALL(20, 1),
    /** Enderman: teleport to the block you are looking at. power = range in blocks. */
    TELEPORT(20, 32),
    /** Llama: spit. */
    SPIT(10, 0),
    /** Skeleton: free arrow (cannot be picked up). power = speed, 3 = fully drawn bow. */
    ARROW(10, 3),
    /** Breeze: wind charge. */
    WIND_CHARGE(10, 0),
    /** Snow golem: snowball. */
    SNOWBALL(4, 0),
    /** Evoker: a line of fangs. power = number of fangs. */
    FANGS(40, 12);

    public static final Codec<ActiveType> CODEC = StringRepresentable.fromEnum(ActiveType::values);
    public final int defaultCooldown;
    public final float defaultPower;
    ActiveType(int defaultCooldown, float defaultPower) {
        this.defaultCooldown = defaultCooldown;
        this.defaultPower = defaultPower;
    }
    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    public String translationKey() { return "shapeshift.active." + getSerializedName(); }
}
