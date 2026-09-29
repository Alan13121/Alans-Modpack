package dev.alan.shapeshift;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** Pure calculation of which attribute modifiers a form gives, keyed by attribute id. Unit tested. */
public final class FormPlan {
    /** Copied from the entity's own base stats so e.g. an iron golem hits hard and resists knockback. */
    public static final List<String> INHERITED = List.of(
        "minecraft:max_health", "minecraft:attack_damage", "minecraft:armor", "minecraft:armor_toughness",
        "minecraft:knockback_resistance", "minecraft:step_height", "minecraft:safe_fall_distance");
    public static final String MAX_HEALTH = "minecraft:max_health";
    public static final String CAMERA_DISTANCE = "minecraft:camera_distance";
    static final double PLAYER_HEIGHT = 1.8, PLAYER_CAMERA = 4.0;
    private FormPlan() {}

    /** "movement_speed" and "minecraft:movement_speed" mean the same attribute. */
    public static String attributeId(String key) {
        String id = key.strip().toLowerCase(Locale.ROOT);
        return id.contains(":") ? id : "minecraft:" + id;
    }

    /** Third-person camera distance that keeps the whole body in view: 4 blocks for player-sized forms. */
    public static double cameraDistance(double width, double height) {
        return Math.clamp(PLAYER_CAMERA * Math.max(width, height) / PLAYER_HEIGHT, 1.5, 16.0);
    }

    /**
     * @param mobBase    the entity's base values for {@link #INHERITED} attributes it has
     * @param playerBase the player's base values for the same attributes
     */
    public static Map<String, ModifierSpec> modifiers(Map<String, Double> mobBase, Map<String, Double> playerBase,
                                                      FormDefinition def, Set<Ability> abilities, double width, double height) {
        var result = new LinkedHashMap<String, ModifierSpec>();
        for (String id : INHERITED) {
            if (!mobBase.containsKey(id) || !playerBase.containsKey(id)) continue;
            double delta = mobBase.get(id) - playerBase.get(id);
            if (Math.abs(delta) > 1e-6) result.put(id, ModifierSpec.add(delta));
        }
        def.maxHealth().ifPresent(health -> {
            double delta = health - playerBase.getOrDefault(MAX_HEALTH, 20.0);
            if (Math.abs(delta) > 1e-6) result.put(MAX_HEALTH, ModifierSpec.add(delta));
            else result.remove(MAX_HEALTH);
        });
        double camera = cameraDistance(width, height) - PLAYER_CAMERA;
        if (Math.abs(camera) > 0.01) result.put(CAMERA_DISTANCE, ModifierSpec.add(camera));
        if (abilities.contains(Ability.NO_FALL_DAMAGE))
            result.put("minecraft:fall_damage_multiplier", new ModifierSpec(-1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        if (abilities.contains(Ability.SWIM_FAST))
            result.put("minecraft:water_movement_efficiency", ModifierSpec.add(1));
        def.attributes().forEach((key, spec) -> result.put(attributeId(key), spec));
        return result;
    }

    /** The max health the form ends up with. */
    public static double maxHealth(Map<String, Double> mobBase, FormDefinition def) {
        return def.maxHealth().orElse(mobBase.getOrDefault(MAX_HEALTH, 20.0));
    }
}
