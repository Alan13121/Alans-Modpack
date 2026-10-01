package dev.alan.combat;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Anvil upgrades for the bow: one item per level, each item feeding one upgrade. */
public final class BowUpgrades {
    /** Levels all upgrades together can give one bow, so a bow has to choose. */
    public static final int MAX_TOTAL = 8;

    private BowUpgrades() {}

    public enum Mod {
        DRAW("draw", Items.REDSTONE, 3),
        NO_DROP("no_drop", Items.ENDER_PEARL, 1),
        BURN("burn", Items.BLAZE_POWDER, 3),
        SLOW("slow", Items.SLIME_BALL, 3),
        EXPLODE("explode", Items.GUNPOWDER, 3);

        public final String key;
        public final Item item;
        public final int max;

        Mod(String key, Item item, int max) {
            this.key = key;
            this.item = item;
            this.max = max;
        }

        public static Mod of(Item item) {
            for (Mod mod : values()) if (mod.item == item) return mod;
            return null;
        }
    }

    public static ArmorUpgrades.Result plan(ItemStack bow, ItemStack material) {
        if (bow.isEmpty() || material.isEmpty() || !bow.is(Items.BOW)) return null;
        Mod mod = Mod.of(material.getItem());
        if (mod == null) return null;
        Upgrades current = bow.getOrDefault(CombatMod.BOW_UPGRADES, Upgrades.EMPTY);
        var plan = UpgradeRules.flatPlan(current.level(mod.key), current.total(), mod.max, MAX_TOTAL, material.getCount());
        if (plan.gained() == 0) return null;
        ItemStack result = bow.copy();
        result.set(CombatMod.BOW_UPGRADES, current.with(mod.key, current.level(mod.key) + plan.gained()));
        return new ArmorUpgrades.Result(result, plan.oreUsed());
    }
}
