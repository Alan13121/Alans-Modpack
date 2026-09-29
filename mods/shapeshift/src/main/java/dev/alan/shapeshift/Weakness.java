package dev.alan.shapeshift;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

public enum Weakness implements StringRepresentable {
    /** Catches fire in sunlight unless wearing a helmet (which wears down), like zombies. */
    BURNS_IN_DAYLIGHT,
    /** Hurt by water and rain, like endermen. */
    WATER_DAMAGE,
    /** Dries out and suffocates after about 15 seconds out of water, like fish. */
    NEEDS_WATER;

    public static final Codec<Weakness> CODEC = StringRepresentable.fromEnum(Weakness::values);
    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    public String translationKey() { return "shapeshift.weakness." + getSerializedName(); }
}
