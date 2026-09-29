package dev.alan.shapeshift;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** Server-side application of a form's attributes, effects and flight. Climbing lives in a mixin. */
final class Passives {
    static final Identifier MODIFIER = ShapeshiftMod.id("form");
    /** Modifier id used by version 0.1.0 for max health only. */
    private static final Identifier LEGACY_HEALTH = ShapeshiftMod.id("form_health");
    private static final Map<Ability, Holder<MobEffect>> EFFECTS = Map.of(
        Ability.NIGHT_VISION, MobEffects.NIGHT_VISION,
        Ability.FIRE_IMMUNE, MobEffects.FIRE_RESISTANCE,
        Ability.WATER_BREATHING, MobEffects.WATER_BREATHING,
        Ability.SLOW_FALLING, MobEffects.SLOW_FALLING);
    private static final float DEFAULT_FLYING_SPEED = 0.05f;
    private Passives() {}

    static void apply(ServerPlayer player, FormDefinitions.Resolved form) {
        form.modifiers().forEach((id, spec) -> {
            var holder = BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(id));
            var instance = holder.map(player::getAttribute).orElse(null);
            if (instance == null) {
                ShapeshiftMod.LOG.warn("Player has no attribute {}; form modifier skipped", id);
                return;
            }
            instance.addOrReplacePermanentModifier(new AttributeModifier(MODIFIER, spec.amount(), spec.operation()));
        });
        ensure(player, form);
    }

    /** Re-grants effects and flight that something else removed (milk, game mode change). Cheap to call often. */
    static void ensure(ServerPlayer player, FormDefinitions.Resolved form) {
        EFFECTS.forEach((ability, effect) -> {
            if (form.has(ability) && !isOurs(player.getEffect(effect)))
                player.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, 0, true, false, true), null);
        });
        if (form.has(Ability.FLIGHT)) {
            var abilities = player.getAbilities();
            if (!abilities.mayfly || abilities.getFlyingSpeed() != form.flyingSpeed()) {
                abilities.mayfly = true;
                if (!player.isCreative() && !player.isSpectator()) abilities.setFlyingSpeed(form.flyingSpeed());
                player.onUpdateAbilities();
            }
        }
    }

    static void clear(ServerPlayer player) {
        BuiltInRegistries.ATTRIBUTE.listElements().forEach(holder -> {
            var instance = player.getAttribute(holder);
            if (instance != null) {
                instance.removeModifier(MODIFIER);
                instance.removeModifier(LEGACY_HEALTH);
            }
        });
        EFFECTS.values().forEach(effect -> {
            if (isOurs(player.getEffect(effect))) player.removeEffect(effect);
        });
        if (!player.isCreative() && !player.isSpectator()) {
            var abilities = player.getAbilities();
            abilities.mayfly = false;
            abilities.flying = false;
            abilities.setFlyingSpeed(DEFAULT_FLYING_SPEED);
            player.onUpdateAbilities();
        }
    }

    /** Effects granted by a form are infinite, ambient and particle-free; potions the player drank are left alone. */
    private static boolean isOurs(MobEffectInstance effect) {
        return effect != null && effect.isInfiniteDuration() && effect.isAmbient() && !effect.isVisible();
    }
}
