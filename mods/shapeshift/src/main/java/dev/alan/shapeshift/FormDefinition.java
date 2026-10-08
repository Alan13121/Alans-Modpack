package dev.alan.shapeshift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hand-tuned data for one form, loaded from {@code data/<namespace>/shapeshift/forms/<entity>.json}.
 * Every field is optional; {@code rare} marks forms that are hard to find (a biome or structure) and are not required by the Form King altar; forms without a file still get the entity's size, health and combat stats.
 */
public record FormDefinition(Optional<Double> maxHealth, Optional<Float> flyingSpeed, List<Ability> abilities,
                             Map<String, ModifierSpec> attributes, Optional<ActiveAbility> active, List<Weakness> weaknesses,
                             List<String> scares, List<String> huntedBy, boolean rare) {
    public static final FormDefinition EMPTY = new FormDefinition(Optional.empty(), Optional.empty(), List.of(), Map.of(), Optional.empty(), List.of(), List.of(), List.of(), false);
    /** Entity ids ("minecraft:creeper") or entity type tags ("#minecraft:skeletons"). */
    public static final Codec<String> ENTITY_SELECTOR = Codec.STRING.validate(s -> {
        String id = s.startsWith("#") ? s.substring(1) : s;
        return net.minecraft.resources.Identifier.tryParse(id) != null
            ? com.mojang.serialization.DataResult.success(s)
            : com.mojang.serialization.DataResult.error(() -> "Not an entity id or #tag: " + s);
    });
    public static final Codec<FormDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.doubleRange(1, 1024).optionalFieldOf("max_health").forGetter(FormDefinition::maxHealth),
        Codec.floatRange(0, 1).optionalFieldOf("flying_speed").forGetter(FormDefinition::flyingSpeed),
        Ability.CODEC.listOf().optionalFieldOf("abilities", List.of()).forGetter(FormDefinition::abilities),
        Codec.unboundedMap(Codec.STRING, ModifierSpec.CODEC).optionalFieldOf("attributes", Map.of()).forGetter(FormDefinition::attributes),
        ActiveAbility.CODEC.optionalFieldOf("active").forGetter(FormDefinition::active),
        Weakness.CODEC.listOf().optionalFieldOf("weaknesses", List.of()).forGetter(FormDefinition::weaknesses),
        ENTITY_SELECTOR.listOf().optionalFieldOf("scares", List.of()).forGetter(FormDefinition::scares),
        ENTITY_SELECTOR.listOf().optionalFieldOf("hunted_by", List.of()).forGetter(FormDefinition::huntedBy),
        Codec.BOOL.optionalFieldOf("rare", false).forGetter(FormDefinition::rare)
    ).apply(i, FormDefinition::new));
    public FormDefinition {
        abilities = List.copyOf(abilities);
        attributes = Map.copyOf(attributes);
        weaknesses = List.copyOf(weaknesses);
        scares = List.copyOf(scares);
        huntedBy = List.copyOf(huntedBy);
    }
}
