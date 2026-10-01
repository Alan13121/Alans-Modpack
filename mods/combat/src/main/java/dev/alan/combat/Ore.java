package dev.alan.combat;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** The eight ores an anvil can hammer into armor, and the attribute each one improves per level. */
public enum Ore {
    COAL("coal", List.of(Items.COAL), Attributes.KNOCKBACK_RESISTANCE, 0.05, AttributeModifier.Operation.ADD_VALUE),
    COPPER("copper", List.of(Items.COPPER_INGOT, Items.RAW_COPPER), Attributes.ARMOR, 0.5, AttributeModifier.Operation.ADD_VALUE),
    IRON("iron", List.of(Items.IRON_INGOT, Items.RAW_IRON), Attributes.ARMOR_TOUGHNESS, 0.5, AttributeModifier.Operation.ADD_VALUE),
    GOLD("gold", List.of(Items.GOLD_INGOT, Items.RAW_GOLD), Attributes.LUCK, 0.5, AttributeModifier.Operation.ADD_VALUE),
    REDSTONE("redstone", List.of(Items.REDSTONE), Attributes.MOVEMENT_SPEED, 0.02, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
    LAPIS("lapis", List.of(Items.LAPIS_LAZULI), Attributes.OXYGEN_BONUS, 0.5, AttributeModifier.Operation.ADD_VALUE),
    DIAMOND("diamond", List.of(Items.DIAMOND), Attributes.MAX_HEALTH, 1.0, AttributeModifier.Operation.ADD_VALUE),
    EMERALD("emerald", List.of(Items.EMERALD), Attributes.ATTACK_DAMAGE, 0.5, AttributeModifier.Operation.ADD_VALUE);

    public final String key;
    public final List<Item> items;
    public final Holder<Attribute> attribute;
    public final double perLevel;
    public final AttributeModifier.Operation operation;

    Ore(String key, List<Item> items, Holder<Attribute> attribute, double perLevel, AttributeModifier.Operation operation) {
        this.key = key;
        this.items = items;
        this.attribute = attribute;
        this.perLevel = perLevel;
        this.operation = operation;
    }

    public static Ore of(Item item) {
        for (Ore ore : values()) if (ore.items.contains(item)) return ore;
        return null;
    }
}
