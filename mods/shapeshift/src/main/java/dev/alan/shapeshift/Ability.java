package dev.alan.shapeshift;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Passive traits a form can have. Numeric tweaks (speed, jump...) go in a form's "attributes" instead. */
public enum Ability implements StringRepresentable {
    /** Creative-style flight (double-tap jump). */
    FLIGHT,
    /** Walk up walls like a spider. */
    CLIMB,
    NIGHT_VISION,
    FIRE_IMMUNE,
    WATER_BREATHING,
    /** Move through water as fast as on land. */
    SWIM_FAST,
    SLOW_FALLING,
    NO_FALL_DAMAGE;

    public static final Codec<Ability> CODEC = StringRepresentable.fromEnum(Ability::values);
    @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    public String translationKey() { return "shapeshift.ability." + getSerializedName(); }
}
