package dev.alan.shapeshift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** One attribute change, e.g. {"amount": 1.0, "operation": "add_multiplied_base"} for double speed. */
public record ModifierSpec(double amount, AttributeModifier.Operation operation) {
    public static final Codec<ModifierSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.DOUBLE.fieldOf("amount").forGetter(ModifierSpec::amount),
        AttributeModifier.Operation.CODEC.optionalFieldOf("operation", AttributeModifier.Operation.ADD_VALUE).forGetter(ModifierSpec::operation)
    ).apply(i, ModifierSpec::new));
    public static ModifierSpec add(double amount) { return new ModifierSpec(amount, AttributeModifier.Operation.ADD_VALUE); }
}
