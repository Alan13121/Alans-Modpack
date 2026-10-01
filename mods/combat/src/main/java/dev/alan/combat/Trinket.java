package dev.alan.combat;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;

/** One kind of trinket: the item that represents it and what it does while worn. {@code effect} may be null. */
public record Trinket(String name, Item item, Holder<MobEffect> effect, boolean fallImmune) {
}
