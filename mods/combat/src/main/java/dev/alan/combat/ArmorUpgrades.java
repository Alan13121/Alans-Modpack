package dev.alan.combat;

import java.util.ArrayList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** Applies ore upgrades to armor: the level counts go in a component and the stats into the item's attribute modifiers. */
public final class ArmorUpgrades {
    private static final String ID_PREFIX = "upgrade/";

    private ArmorUpgrades() {}

    /** What the anvil produces for this armor and ore, or null when this is not an upgrade (or nothing more can be added). */
    public record Result(ItemStack stack, int oreUsed) {}

    public static EquipmentSlot armorSlot(ItemStack armor) {
        var equippable = armor.get(DataComponents.EQUIPPABLE);
        if (equippable == null || !equippable.slot().isArmor()) return null;
        // Skulls and elytra are equippable too; only real armor has an armor value.
        var modifiers = armor.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (var entry : modifiers.modifiers()) if (entry.attribute().equals(Attributes.ARMOR)) return equippable.slot();
        return null;
    }

    public static Result plan(ItemStack armor, ItemStack oreStack) {
        if (armor.isEmpty() || oreStack.isEmpty()) return null;
        EquipmentSlot slot = armorSlot(armor);
        Ore ore = Ore.of(oreStack.getItem());
        if (slot == null || ore == null) return null;
        // Mending a worn piece with its own material is the anvil's normal job and keeps priority.
        if (armor.isDamaged() && armor.isValidRepairItem(oreStack)) return null;
        Upgrades current = armor.getOrDefault(CombatMod.UPGRADES, Upgrades.EMPTY);
        var plan = UpgradeRules.plan(current.level(ore.key), current.total(), oreStack.getCount());
        if (plan.gained() == 0) return null;
        ItemStack result = armor.copy();
        Upgrades next = current.with(ore.key, current.level(ore.key) + plan.gained());
        result.set(CombatMod.UPGRADES, next);
        result.set(DataComponents.ATTRIBUTE_MODIFIERS, withUpgrades(armor, slot, next));
        return new Result(result, plan.oreUsed());
    }

    /** The armor's modifiers with every earlier upgrade modifier replaced by the ones for {@code upgrades}. */
    private static ItemAttributeModifiers withUpgrades(ItemStack armor, EquipmentSlot slot, Upgrades upgrades) {
        var kept = new ArrayList<ItemAttributeModifiers.Entry>();
        for (var entry : armor.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            Identifier id = entry.modifier().id();
            if (!(id.getNamespace().equals(CombatMod.MOD_ID) && id.getPath().startsWith(ID_PREFIX))) kept.add(entry);
        }
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(slot);
        for (Ore ore : Ore.values()) {
            int level = upgrades.level(ore.key);
            if (level <= 0) continue;
            // The slot is part of the id so a full set of upgraded armor never applies the same id twice.
            var modifier = new AttributeModifier(CombatMod.id(ID_PREFIX + slot.getName() + "/" + ore.key), ore.perLevel * level, ore.operation);
            kept.add(new ItemAttributeModifiers.Entry(ore.attribute, modifier, group));
        }
        return new ItemAttributeModifiers(kept);
    }
}
