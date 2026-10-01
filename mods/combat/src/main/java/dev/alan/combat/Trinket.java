package dev.alan.combat;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;

/**
 * One kind of trinket: the item that represents it and what it does while worn.
 * It may grant a potion effect, an attribute boost, and any number of {@link Perk}s that other code looks for.
 */
public record Trinket(String name, Item item, Holder<MobEffect> effect, Boost boost, Set<Perk> perks) {
    /** Behaviour that is implemented where it happens (damage events, ticking, loot) and looked up by {@link Trinkets#has}. */
    public enum Perk { FALL_IMMUNE, SKILL_FOCUS, REGEN, MAGNET, BLAST_WARD, THORNS, HUNTER, FORM_MASTER }

    public record Boost(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {}

    /** Collects what a trinket does before its item is registered. */
    public static final class Spec {
        Holder<MobEffect> effect;
        Boost boost;
        final Set<Perk> perks = EnumSet.noneOf(Perk.class);

        public Spec effect(Holder<MobEffect> effect) { this.effect = effect; return this; }
        public Spec boost(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
            this.boost = new Boost(attribute, amount, operation);
            return this;
        }
        public Spec perk(Perk perk) { perks.add(perk); return this; }
    }
}
