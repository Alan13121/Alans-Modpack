package dev.alan.shapeshift;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import org.junit.jupiter.api.Test;

class FormPlanTest {
    private static final Map<String, Double> PLAYER = Map.of(
        "minecraft:max_health", 20.0, "minecraft:attack_damage", 1.0, "minecraft:armor", 0.0,
        "minecraft:armor_toughness", 0.0, "minecraft:knockback_resistance", 0.0,
        "minecraft:step_height", 0.6, "minecraft:safe_fall_distance", 3.0);

    @Test void inheritsCombatStatsAsDifferences() {
        var golem = Map.of("minecraft:max_health", 100.0, "minecraft:attack_damage", 15.0,
            "minecraft:knockback_resistance", 1.0, "minecraft:step_height", 1.0, "minecraft:safe_fall_distance", 3.0);
        var m = FormPlan.modifiers(golem, PLAYER, FormDefinition.EMPTY, Set.of(), 1.4, 2.7);
        assertEquals(ModifierSpec.add(80), m.get("minecraft:max_health"));
        assertEquals(ModifierSpec.add(14), m.get("minecraft:attack_damage"));
        assertEquals(ModifierSpec.add(1), m.get("minecraft:knockback_resistance"));
        assertEquals(0.4, m.get("minecraft:step_height").amount(), 1e-9);
        assertFalse(m.containsKey("minecraft:safe_fall_distance"), "equal values need no modifier");
        assertFalse(m.containsKey("minecraft:armor"), "missing stats are left alone");
    }
    @Test void passiveMobsKeepPlayerFists() {
        var pig = Map.of("minecraft:max_health", 10.0, "minecraft:step_height", 0.6);
        var m = FormPlan.modifiers(pig, PLAYER, FormDefinition.EMPTY, Set.of(), 0.9, 0.9);
        assertFalse(m.containsKey("minecraft:attack_damage"));
        assertEquals(ModifierSpec.add(-10), m.get("minecraft:max_health"));
    }
    @Test void definitionOverridesHealthAndAttributes() {
        var horseBase = Map.of("minecraft:max_health", 53.0, "minecraft:step_height", 1.0);
        var def = new FormDefinition(Optional.of(22.0), Optional.empty(), List.of(),
            Map.of("movement_speed", new ModifierSpec(1.0, Operation.ADD_MULTIPLIED_BASE),
                   "minecraft:step_height", ModifierSpec.add(0.9)), Optional.empty(), List.of(), List.of(), List.of(), false);
        var m = FormPlan.modifiers(horseBase, PLAYER, def, Set.of(), 1.4, 1.6);
        assertEquals(ModifierSpec.add(2), m.get("minecraft:max_health"));
        assertEquals(new ModifierSpec(1.0, Operation.ADD_MULTIPLIED_BASE), m.get("minecraft:movement_speed"));
        assertEquals(ModifierSpec.add(0.9), m.get("minecraft:step_height"), "explicit attributes win over inherited ones");
        assertEquals(22.0, FormPlan.maxHealth(horseBase, def));
        assertEquals(53.0, FormPlan.maxHealth(horseBase, FormDefinition.EMPTY));
    }
    @Test void healthOverrideEqualToPlayerRemovesModifier() {
        var def = new FormDefinition(Optional.of(20.0), Optional.empty(), List.of(), Map.of(), Optional.empty(), List.of(), List.of(), List.of(), false);
        var m = FormPlan.modifiers(Map.of("minecraft:max_health", 53.0), PLAYER, def, Set.of(), 0.6, 1.8);
        assertFalse(m.containsKey("minecraft:max_health"));
    }
    @Test void abilitiesAddTheirAttributes() {
        var m = FormPlan.modifiers(Map.of(), PLAYER, FormDefinition.EMPTY, Set.of(Ability.NO_FALL_DAMAGE, Ability.SWIM_FAST), 0.6, 1.8);
        assertEquals(new ModifierSpec(-1, Operation.ADD_MULTIPLIED_TOTAL), m.get("minecraft:fall_damage_multiplier"));
        assertEquals(ModifierSpec.add(1), m.get("minecraft:water_movement_efficiency"));
    }
    @Test void cameraFollowsBodySize() {
        assertEquals(4.0, FormPlan.cameraDistance(0.6, 1.8), 1e-9);
        assertEquals(1.5, FormPlan.cameraDistance(0.3, 0.4), 1e-9, "tiny forms use a minimum distance");
        assertEquals(4.0 * 0.7 / 1.8, FormPlan.cameraDistance(0.4, 0.7), 1e-9);
        assertEquals(4.0 * 4 / 1.8, FormPlan.cameraDistance(4, 4), 1e-9);
        assertFalse(FormPlan.modifiers(Map.of(), PLAYER, FormDefinition.EMPTY, Set.of(), 0.6, 1.8).containsKey(FormPlan.CAMERA_DISTANCE));
        assertTrue(FormPlan.modifiers(Map.of(), PLAYER, FormDefinition.EMPTY, Set.of(), 4, 4).containsKey(FormPlan.CAMERA_DISTANCE));
    }
    @Test void attributeKeysAreNormalized() {
        assertEquals("minecraft:movement_speed", FormPlan.attributeId(" Movement_Speed "));
        assertEquals("othermod:mana", FormPlan.attributeId("othermod:mana"));
    }
    @Test void parsesJsonDefinition() {
        var json = JsonParser.parseString("""
            {"max_health": 22, "flying_speed": 0.06, "abilities": ["flight", "night_vision"],
             "attributes": {"movement_speed": {"amount": 1.0, "operation": "add_multiplied_base"}, "jump_strength": {"amount": 0.2}}}
            """);
        var def = FormDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(Optional.of(22.0), def.maxHealth());
        assertEquals(Optional.of(0.06f), def.flyingSpeed());
        assertEquals(List.of(Ability.FLIGHT, Ability.NIGHT_VISION), def.abilities());
        assertEquals(ModifierSpec.add(0.2), def.attributes().get("jump_strength"));
        assertEquals(FormDefinition.EMPTY, FormDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow());
    }
    @Test void parsesActiveAbilityAndWeaknesses() {
        var json = JsonParser.parseString("""
            {"active": {"type": "explode", "power": 4, "fuse": 0}, "weaknesses": ["water_damage"]}
            """);
        var def = FormDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        var active = def.active().orElseThrow();
        assertEquals(ActiveType.EXPLODE, active.type());
        assertEquals(4f, active.powerValue());
        assertEquals(0, active.fuseTicks());
        assertEquals(0, active.cooldownTicks(), "explode has no cooldown by default");
        assertEquals(List.of(Weakness.WATER_DAMAGE), def.weaknesses());
        var fireball = ActiveAbility.of(ActiveType.SMALL_FIREBALL);
        assertEquals(6, fireball.cooldownTicks());
        assertEquals(ActiveAbility.DEFAULT_FUSE, ActiveAbility.of(ActiveType.EXPLODE).fuseTicks());
        assertTrue(FormDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"active\": {\"type\": \"laser\"}}")).isError());
        assertTrue(FormDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"weaknesses\": [\"garlic\"]}")).isError());
    }
    @Test void parsesReactions() {
        var json = JsonParser.parseString("{\"scares\": [\"minecraft:creeper\", \"#minecraft:skeletons\"], \"hunted_by\": [\"minecraft:wolf\"]}");
        var def = FormDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(List.of("minecraft:creeper", "#minecraft:skeletons"), def.scares());
        assertEquals(List.of("minecraft:wolf"), def.huntedBy());
        assertTrue(FormDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"scares\": [\"Not An Id!\"]}")).isError());
    }
    @Test void rejectsUnknownAbilitiesAndBadHealth() {
        assertTrue(FormDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"abilities\": [\"teleport\"]}")).isError());
        assertTrue(FormDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"max_health\": 0}")).isError());
    }
}
