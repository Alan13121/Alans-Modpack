package dev.alan.shapeshift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/** A form's R-key ability, e.g. {"type": "explode", "power": 3, "fuse": 30}. Cooldown and fuse are in ticks (20 = 1 s). */
public record ActiveAbility(ActiveType type, Optional<Integer> cooldown, Optional<Float> power, Optional<Integer> fuse) {
    public static final Codec<ActiveAbility> CODEC = RecordCodecBuilder.create(i -> i.group(
        ActiveType.CODEC.fieldOf("type").forGetter(ActiveAbility::type),
        Codec.intRange(0, 72000).optionalFieldOf("cooldown").forGetter(ActiveAbility::cooldown),
        Codec.floatRange(0, 64).optionalFieldOf("power").forGetter(ActiveAbility::power),
        Codec.intRange(0, 200).optionalFieldOf("fuse").forGetter(ActiveAbility::fuse)
    ).apply(i, ActiveAbility::new));
    public static final int DEFAULT_FUSE = 30;
    public static ActiveAbility of(ActiveType type) { return new ActiveAbility(type, Optional.empty(), Optional.empty(), Optional.empty()); }
    public int cooldownTicks() { return cooldown.orElse(type.defaultCooldown); }
    public float powerValue() { return power.orElse(type.defaultPower); }
    /** Only used by {@link ActiveType#EXPLODE}; 0 explodes instantly. */
    public int fuseTicks() { return fuse.orElse(DEFAULT_FUSE); }
}
